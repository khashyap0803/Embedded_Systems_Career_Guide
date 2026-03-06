#!/usr/bin/env python3
"""
Fine-Tune Qwen3-14B-Instruct with Full LoRA on H100
====================================================
Uses Unsloth for optimized LoRA training on 30K+ dataset.
Full LoRA (BF16 base, no quantization) for maximum quality.

Usage:
  python3 train_14b.py \
    --dataset-dir /mnt/backup/merged_datasets \
    --output-dir /mnt/ssd1/training_output \
    --epochs 2
"""

import os
import sys
import json
import argparse
import logging
from datetime import datetime

def setup_logging(output_dir):
    os.makedirs(output_dir, exist_ok=True)
    log_file = os.path.join(output_dir, f"training_{datetime.now():%Y%m%d_%H%M%S}.log")
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(message)s",
        handlers=[
            logging.FileHandler(log_file),
            logging.StreamHandler()
        ]
    )
    return logging.getLogger(__name__)


def merge_datasets(dataset_dir, output_file, logger):
    """Merge all category JSONL files into a single training file."""
    logger.info(f"Merging datasets from {dataset_dir}...")

    all_examples = []
    seen_hashes = set()

    for filename in sorted(os.listdir(dataset_dir)):
        if not filename.endswith('.jsonl'):
            continue
        filepath = os.path.join(dataset_dir, filename)
        count = 0
        with open(filepath, 'r', encoding='utf-8') as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    example = json.loads(line)
                    # Validate format
                    if 'messages' not in example:
                        continue
                    messages = example['messages']
                    if len(messages) < 2:
                        continue
                    if messages[0]['role'] != 'user' or messages[1]['role'] != 'assistant':
                        continue
                    # Check minimum response length
                    if len(messages[1]['content']) < 100:
                        continue

                    import hashlib
                    h = hashlib.md5(line.encode()).hexdigest()
                    if h not in seen_hashes:
                        seen_hashes.add(h)
                        all_examples.append(example)
                        count += 1
                except json.JSONDecodeError:
                    continue
        logger.info(f"  {filename}: {count} valid examples")

    # Shuffle
    import random
    random.seed(42)
    random.shuffle(all_examples)

    # Write merged file
    with open(output_file, 'w', encoding='utf-8') as f:
        for example in all_examples:
            f.write(json.dumps(example, ensure_ascii=False) + '\n')

    logger.info(f"Merged: {len(all_examples)} total examples → {output_file}")
    size_mb = os.path.getsize(output_file) / (1024 * 1024)
    logger.info(f"File size: {size_mb:.1f} MB")
    return len(all_examples)


def train(args):
    logger = setup_logging(args.output_dir)
    logger.info("=" * 60)
    logger.info("  Qwen3-14B Fine-Tuning with Full LoRA")
    logger.info("=" * 60)

    # Step 1: Merge datasets
    merged_file = os.path.join(args.output_dir, "training_data_merged.jsonl")
    if not os.path.exists(merged_file):
        total = merge_datasets(args.dataset_dir, merged_file, logger)
    else:
        with open(merged_file, 'r') as f:
            total = sum(1 for _ in f)
        logger.info(f"Using existing merged file: {total} examples")

    # Step 2: Load model with Unsloth
    logger.info("\n[STEP 2] Loading Qwen3-14B-Instruct with Unsloth...")

    from unsloth import FastLanguageModel
    import torch

    # Use local model path if available, else download from HF
    model_path = args.model_path if hasattr(args, 'model_path') and args.model_path else "Qwen/Qwen3-14B"
    logger.info(f"Loading model from: {model_path}")

    model, tokenizer = FastLanguageModel.from_pretrained(
        model_name=model_path,
        max_seq_length=8192,           # 8K to capture longest responses (cat2 avg 18K chars)
        dtype=torch.bfloat16,         # Full BF16 — no quantization
        load_in_4bit=False,            # Full LoRA, NOT QLoRA
        trust_remote_code=True,
    )

    logger.info(f"Model loaded: {model.config._name_or_path}")
    logger.info(f"Parameters: {sum(p.numel() for p in model.parameters()):,}")

    # Step 3: Add LoRA adapters
    logger.info("\n[STEP 3] Adding LoRA adapters...")

    model = FastLanguageModel.get_peft_model(
        model,
        r=128,                         # LoRA rank 128 — maximum capacity for domain knowledge
        lora_alpha=256,                # Scaling factor (alpha/r = 2)
        lora_dropout=0.05,             # Small dropout for regularization
        target_modules=[               # Apply LoRA to ALL layers for maximum adaptation
            "q_proj", "k_proj", "v_proj", "o_proj",
            "gate_proj", "up_proj", "down_proj",
            "embed_tokens", "lm_head",  # Train embeddings + output head too
        ],
        bias="none",
        use_gradient_checkpointing="unsloth",  # Memory-efficient
        random_state=42,
    )

    trainable = sum(p.numel() for p in model.parameters() if p.requires_grad)
    total_params = sum(p.numel() for p in model.parameters())
    logger.info(f"Trainable parameters: {trainable:,} ({100*trainable/total_params:.2f}%)")

    # Step 4: Prepare dataset
    logger.info("\n[STEP 4] Preparing dataset...")

    from datasets import load_dataset
    from trl import SFTTrainer, SFTConfig

    dataset = load_dataset("json", data_files=merged_file, split="train")
    logger.info(f"Dataset loaded: {len(dataset)} examples")

    # Format into chat template
    def format_chat(example):
        messages = example["messages"]
        text = tokenizer.apply_chat_template(
            messages,
            tokenize=False,
            add_generation_prompt=False,
        )
        return {"text": text}

    dataset = dataset.map(format_chat, num_proc=4)
    logger.info("Dataset formatted with chat template")

    # Step 5: Training configuration
    logger.info("\n[STEP 5] Starting training...")

    training_args = SFTConfig(
        output_dir=os.path.join(args.output_dir, "checkpoints"),
        per_device_train_batch_size=2,
        gradient_accumulation_steps=8,          # Effective batch = 16
        num_train_epochs=args.epochs,
        learning_rate=1.5e-4,                   # Slower LR for stable convergence
        lr_scheduler_type="cosine",             # Cosine decay
        warmup_ratio=0.10,                      # 10% warmup — gradual start
        weight_decay=0.01,
        max_seq_length=8192,                    # 8K to capture all content
        bf16=True,                              # BF16 training
        logging_steps=10,
        save_steps=500,                         # Checkpoint every 500 steps
        save_total_limit=3,                     # Keep last 3 checkpoints
        optim="adamw_8bit",                     # 8-bit AdamW for memory
        seed=42,
        dataset_text_field="text",
        max_grad_norm=1.0,
        packing=True,                           # Pack short sequences
        report_to="none",                       # No W&B etc
    )

    trainer = SFTTrainer(
        model=model,
        tokenizer=tokenizer,
        train_dataset=dataset,
        args=training_args,
    )

    # Log estimated training info
    total_steps = len(dataset) * args.epochs // (2 * 8)  # batch * grad_accum
    logger.info(f"Estimated steps: {total_steps}")
    logger.info(f"Epochs: {args.epochs}")
    logger.info(f"Effective batch size: {2 * 8} = 16")
    logger.info(f"Learning rate: 2e-4 with cosine decay")

    # Train
    import time
    train_start = time.time()
    train_result = trainer.train()
    train_time = time.time() - train_start

    logger.info(f"\n{'='*60}")
    logger.info(f"  Training Complete!")
    logger.info(f"  Time: {train_time/3600:.1f} hours")
    logger.info(f"  Loss: {train_result.training_loss:.4f}")
    logger.info(f"{'='*60}")

    # Step 6: Save merged model (LoRA → full model)
    logger.info("\n[STEP 6] Merging LoRA and saving full model...")

    save_dir = os.path.join(args.output_dir, "merged_model")

    # Save LoRA adapter separately first (backup)
    lora_dir = os.path.join(args.output_dir, "lora_adapter")
    model.save_pretrained(lora_dir)
    tokenizer.save_pretrained(lora_dir)
    logger.info(f"LoRA adapter saved to: {lora_dir}")

    # Merge and save full model
    model.save_pretrained_merged(
        save_dir,
        tokenizer,
        save_method="merged_16bit",   # Full FP16 merged model
    )
    logger.info(f"Merged FP16 model saved to: {save_dir}")

    # Step 7: Quick validation
    logger.info("\n[STEP 7] Quick validation...")

    FastLanguageModel.for_inference(model)

    test_prompts = [
        "Explain I2C protocol in simple terms with a code example.",
        "Generate 3 quiz questions about FreeRTOS task management.",
        "What is the difference between polling and interrupt-driven I/O?",
    ]

    for prompt in test_prompts:
        messages = [{"role": "user", "content": prompt}]
        inputs = tokenizer.apply_chat_template(messages, return_tensors="pt", add_generation_prompt=True)
        inputs = inputs.to("cuda")
        outputs = model.generate(inputs, max_new_tokens=256, temperature=0.7)
        response = tokenizer.decode(outputs[0][inputs.shape[-1]:], skip_special_tokens=True)
        logger.info(f"\nPrompt: {prompt[:60]}...")
        logger.info(f"Response: {response[:200]}...")

    logger.info("\n✅ Training pipeline complete!")
    logger.info(f"Next: Run quantize.sh to create GGUF files")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Fine-tune Qwen3-14B")
    parser.add_argument("--dataset-dir", required=True, help="Directory with category JSONL files")
    parser.add_argument("--output-dir", required=True, help="Output directory for model + checkpoints")
    parser.add_argument("--model-path", default=None, help="Local model path (default: download from HF)")
    parser.add_argument("--epochs", type=int, default=3, help="Training epochs (default: 3)")
    args = parser.parse_args()
    train(args)
