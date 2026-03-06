#!/bin/bash
# startup.sh — Auto-setup script for GCP training VM
# Runs automatically when the VM boots (via metadata startup-script-url)
# Handles: driver check, Unsloth install, dataset download, training resume
set -euo pipefail

LOG="/var/log/training.log"
exec > >(tee -a "$LOG") 2>&1

echo "═══════════════════════════════════════════════════════"
echo "  Embedded Systems Tutor — Training VM Startup"
echo "  $(date '+%Y-%m-%d %H:%M:%S')"
echo "═══════════════════════════════════════════════════════"

# Get the bucket name from instance metadata
BUCKET=$(curl -s -H "Metadata-Flavor: Google" \
    http://metadata.google.internal/computeMetadata/v1/instance/attributes/bucket-name)
echo "📦 Bucket: gs://$BUCKET"

WORKDIR="/root/training"
mkdir -p "$WORKDIR/checkpoints" "$WORKDIR/datasets" "$WORKDIR/output"

# --- 1. Verify GPU ---
echo ""
echo "🔍 Checking GPU..."
if ! nvidia-smi &>/dev/null; then
    echo "❌ nvidia-smi failed. Installing CUDA drivers..."
    /opt/deeplearning/install-driver.sh
fi
nvidia-smi --query-gpu=name,memory.total --format=csv
echo "✅ GPU ready"

# --- 2. Install Python dependencies ---
echo ""
echo "📦 Installing dependencies..."
pip install --upgrade pip
pip install "unsloth[colab-new] @ git+https://github.com/unslothai/unsloth.git"
pip install --no-deps trl peft accelerate bitsandbytes
pip install datasets transformers sentencepiece protobuf
pip install google-cloud-storage
echo "✅ Dependencies installed"

# --- 3. Download datasets from GCS ---
echo ""
echo "📥 Downloading datasets..."
gsutil -m cp "gs://$BUCKET/datasets/*.jsonl" "$WORKDIR/datasets/" 2>/dev/null || echo "⚠️  No datasets found in bucket yet"
ls -lh "$WORKDIR/datasets/"

# --- 4. Check for existing checkpoints ---
echo ""
echo "🔍 Checking for checkpoints..."
CHECKPOINT_EXISTS=false
if gsutil ls "gs://$BUCKET/checkpoints/" &>/dev/null; then
    echo "📥 Downloading latest checkpoint..."
    gsutil -m rsync -r "gs://$BUCKET/checkpoints/" "$WORKDIR/checkpoints/"
    CHECKPOINT_EXISTS=true
    echo "✅ Checkpoint restored"
else
    echo "ℹ️  No checkpoints found, will start fresh"
fi

# --- 5. Download base model if not cached ---
echo ""
echo "🤖 Ensuring base model is available..."
python3 -c "
from unsloth import FastLanguageModel
model, tokenizer = FastLanguageModel.from_pretrained(
    model_name='Qwen/Qwen3-30B-A3B',
    max_seq_length=8192,
    dtype=None,
    load_in_4bit=True,
)
print('✅ Base model loaded and cached')
del model, tokenizer
"

echo ""
echo "═══════════════════════════════════════════════════════"
echo "  ✅ VM READY FOR TRAINING"
echo "  Datasets: $(ls $WORKDIR/datasets/*.jsonl 2>/dev/null | wc -l) files"
echo "  Checkpoint: $CHECKPOINT_EXISTS"
echo "  Start training with:"
echo "    python3 $WORKDIR/finetune_stage_content.py"
echo "═══════════════════════════════════════════════════════"
