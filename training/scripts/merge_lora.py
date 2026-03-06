#!/usr/bin/env python3
"""
Merge LoRA adapter into base model using PEFT (fallback for Unsloth merge error).
Unsloth's save_pretrained_merged doesn't support embed_tokens/lm_head LoRA targets,
so we use PEFT's merge_and_unload() instead.
"""
import os
import sys
import torch

sys.path.insert(0, os.path.expanduser("~/.local/lib/python3.10/site-packages"))

print("=== Merging LoRA into Base Model ===")

BASE_MODEL = "/mnt/ssd1/qwen3-14b"
LORA_DIR = "/mnt/ssd1/training_output/lora_adapter"
OUTPUT_DIR = "/mnt/ssd1/training_output/merged_model"

# Step 1: Load base model + LoRA adapter using PEFT
print("[1/3] Loading base model + LoRA adapter...")
from peft import PeftModel, PeftConfig
from transformers import AutoModelForCausalLM, AutoTokenizer

tokenizer = AutoTokenizer.from_pretrained(LORA_DIR, trust_remote_code=True)

model = AutoModelForCausalLM.from_pretrained(
    BASE_MODEL,
    torch_dtype=torch.bfloat16,
    device_map="auto",
    trust_remote_code=True,
)
print(f"  Base model loaded: {sum(p.numel() for p in model.parameters()):,} params")

model = PeftModel.from_pretrained(model, LORA_DIR)
print(f"  LoRA adapter loaded from: {LORA_DIR}")

# Step 2: Merge LoRA weights into base model
print("[2/3] Merging LoRA weights...")
model = model.merge_and_unload()
print(f"  Merged model: {sum(p.numel() for p in model.parameters()):,} params")

# Step 3: Save merged model
print(f"[3/3] Saving merged model to: {OUTPUT_DIR}")
os.makedirs(OUTPUT_DIR, exist_ok=True)
model.save_pretrained(OUTPUT_DIR, safe_serialization=True)
tokenizer.save_pretrained(OUTPUT_DIR)

size_gb = sum(
    os.path.getsize(os.path.join(OUTPUT_DIR, f))
    for f in os.listdir(OUTPUT_DIR)
    if f.endswith('.safetensors')
) / (1024**3)
print(f"  Saved! Model size: {size_gb:.1f} GB")
print("=== Merge Complete ===")
