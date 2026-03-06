#!/bin/bash
# ============================================================
# VM Setup Script — Run on EACH H100 VM after creation
# Usage: bash setup_vm.sh [1|2]
#   1 = GPU-1 (has SSD-1 + SSD-backup)
#   2 = GPU-2 (has SSD-2 only)
# ============================================================

set -e

GPU_ID=${1:-1}
echo "================================================"
echo "  Setting up GPU-$GPU_ID VM"
echo "================================================"

# ───────────────────────────────────────────
# Step 1: Mount SSDs
# ───────────────────────────────────────────
echo "[1/5] Mounting SSDs..."

if [ "$GPU_ID" = "1" ]; then
    # GPU-1: Mount SSD-1 and SSD-backup
    # Check if SSD-1 has a filesystem
    if ! blkid /dev/disk/by-id/google-ssd-gpu1 &>/dev/null; then
        echo "Formatting SSD-1..."
        sudo mkfs.ext4 -F /dev/disk/by-id/google-ssd-gpu1
    fi
    sudo mkdir -p /mnt/ssd1
    sudo mount /dev/disk/by-id/google-ssd-gpu1 /mnt/ssd1
    sudo chmod 777 /mnt/ssd1

    if ! blkid /dev/disk/by-id/google-ssd-backup &>/dev/null; then
        echo "Formatting SSD-backup..."
        sudo mkfs.ext4 -F /dev/disk/by-id/google-ssd-backup
    fi
    sudo mkdir -p /mnt/backup
    sudo mount /dev/disk/by-id/google-ssd-backup /mnt/backup
    sudo chmod 777 /mnt/backup

    WORK_DIR="/mnt/ssd1"
    echo "  SSD-1 mounted at /mnt/ssd1"
    echo "  SSD-backup mounted at /mnt/backup"
else
    # GPU-2: Mount SSD-2 only
    if ! blkid /dev/disk/by-id/google-ssd-gpu2 &>/dev/null; then
        echo "Formatting SSD-2..."
        sudo mkfs.ext4 -F /dev/disk/by-id/google-ssd-gpu2
    fi
    sudo mkdir -p /mnt/ssd2
    sudo mount /dev/disk/by-id/google-ssd-gpu2 /mnt/ssd2
    sudo chmod 777 /mnt/ssd2

    WORK_DIR="/mnt/ssd2"
    echo "  SSD-2 mounted at /mnt/ssd2"
fi

# Create directories
mkdir -p $WORK_DIR/datasets
mkdir -p $WORK_DIR/models
mkdir -p $WORK_DIR/logs
mkdir -p $WORK_DIR/scripts

# ───────────────────────────────────────────
# Step 2: Install dependencies
# ───────────────────────────────────────────
echo "[2/5] Installing dependencies..."

pip install --upgrade pip
pip install vllm==0.8.5 transformers accelerate huggingface_hub
pip install unsloth
pip install rclone-python

echo "✅ Dependencies installed"

# ───────────────────────────────────────────
# Step 3: Download Qwen3-30B-A3B
# ───────────────────────────────────────────
echo "[3/5] Downloading Qwen3-30B-A3B (for dataset generation)..."

python3 -c "
from huggingface_hub import snapshot_download
snapshot_download(
    'Qwen/Qwen3-30B-A3B',
    local_dir='$WORK_DIR/models/Qwen3-30B-A3B',
    max_workers=4
)
print('✅ Qwen3-30B-A3B downloaded')
"

# ───────────────────────────────────────────
# Step 4: Verify GPU
# ───────────────────────────────────────────
echo "[4/5] Verifying GPU..."
nvidia-smi --query-gpu=name,memory.total,driver_version --format=csv,noheader
python3 -c "import torch; print(f'CUDA available: {torch.cuda.is_available()}, Device: {torch.cuda.get_device_name(0)}')"

# ───────────────────────────────────────────
# Step 5: Start vLLM server
# ───────────────────────────────────────────
echo "[5/5] Starting vLLM server..."

# Use different ports for each GPU VM
if [ "$GPU_ID" = "1" ]; then
    PORT=8000
else
    PORT=8001
fi

screen -dmS vllm bash -c "
    python3 -m vllm.entrypoints.openai.api_server \
        --model $WORK_DIR/models/Qwen3-30B-A3B \
        --dtype bfloat16 \
        --max-model-len 8192 \
        --gpu-memory-utilization 0.90 \
        --port $PORT \
        --trust-remote-code \
        2>&1 | tee $WORK_DIR/logs/vllm.log
"

echo "Waiting for vLLM to start (this takes ~2-3 minutes)..."
for i in $(seq 1 60); do
    if curl -s http://localhost:$PORT/health > /dev/null 2>&1; then
        echo "✅ vLLM server running on port $PORT"
        break
    fi
    sleep 5
    echo "  Waiting... ($((i*5))s)"
done

echo ""
echo "================================================"
echo "  ✅ GPU-$GPU_ID Setup Complete!"
echo "  Work dir: $WORK_DIR"
echo "  vLLM port: $PORT"
echo "  Next: Copy and run generate_datasets.py"
echo "================================================"
