# Embedded Systems Career Guide — LLM Training Pipeline

Fine-tune **Qwen3-30B-A3B** (MoE) to replace the Gemini API for the Embedded Systems Career Guide Android app.

## Quick Start

### 1. Generate Training Data (on your Windows PC)
```powershell
cd training\datasets
pip install google-genai tqdm
$env:GEMINI_API_KEY = "your-key"

# Stage content (50K examples) — the biggest dataset
python generate_stage_content.py --output stage_content_dataset.jsonl --count 50000

# Quiz + Flashcard (30K + 20K examples)
python generate_quiz_flashcard.py

# Chat tutor (10K multi-turn conversations)
python generate_chat_tutor.py
```

### 2. Create GCP Training VM
```bash
bash training/gcp/create_vm.sh YOUR_PROJECT_ID es-tutor-training
```

### 3. Fine-Tune on GCP L4
```bash
# SSH into the VM
gcloud compute ssh es-trainer --zone=asia-south1-b

# Train stage content model (~32 GPU hours)
python /root/training/finetune_stage_content.py \
    --dataset /root/training/datasets/stage_content_dataset.jsonl

# Train quiz/flashcard model (~16 GPU hours)
python /root/training/finetune_quiz_flashcard.py \
    --quiz-dataset /root/training/datasets/quiz_dataset.jsonl \
    --flash-dataset /root/training/datasets/flashcard_dataset.jsonl
```

### 4. Merge & Quantize
```bash
bash training/merge_and_quantize.sh --lora-path /root/training/output/stage-content-lora
```

### 5. Deploy on Windows (Ollama + Tailscale)
```powershell
# Download model from GCS
bash training/gcp/download_models.sh es-tutor-training

# Import into Ollama
ollama create embedded-tutor -f training\models\Modelfile

# Expose via Tailscale Funnel
tailscale funnel 11434
```

## Directory Structure
```
training/
├── datasets/
│   ├── generate_stage_content.py    # 50K stage content examples
│   ├── generate_quiz_flashcard.py   # 30K quiz + 20K flashcard examples
│   └── generate_chat_tutor.py       # 10K chat tutor conversations
├── gcp/
│   ├── create_vm.sh                 # Create L4 Spot VM
│   ├── startup.sh                   # VM auto-setup script
│   └── download_models.sh           # Download GGUF to Windows
├── finetune_stage_content.py        # QLoRA training (stage content)
├── finetune_quiz_flashcard.py       # QLoRA training (quiz + flashcard)
├── merge_and_quantize.sh            # Merge LoRA + quantize to GGUF
├── requirements.txt                 # Python dependencies
└── README.md                        # This file
```

## Cost Estimate
| Phase | GPU Hours | Cost (INR) |
|-------|-----------|------------|
| Dataset generation | 8-32h | ₹200-750 |
| Fine-tuning (stage content) | 32h | ₹800-1,000 |
| Fine-tuning (quiz/flashcard) | 16h | ₹400-500 |
| Merge + quantize | 4h | ₹100 |
| **Total** | **~92h** | **~₹2,300-2,850** |
