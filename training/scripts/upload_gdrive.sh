#!/bin/bash
# ============================================================
# Upload Everything to Google Drive via rclone
# Run on GPU-1 VM (which has SSD-3/backup mounted)
#
# First-time setup:
#   1. Install rclone: curl https://rclone.org/install.sh | sudo bash
#   2. Configure: rclone config
#      - New remote → name: gdrive → Google Drive → default settings
#      - Follow OAuth flow (will give you a URL to visit)
#   3. Test: rclone lsd gdrive:
# ============================================================

set -e

BACKUP_DIR="/mnt/backup"
GDRIVE_REMOTE="gdrive"
GDRIVE_FOLDER="ES_Career_Guide_Model"

echo "============================================"
echo "  Uploading to Google Drive"
echo "  From: $BACKUP_DIR"
echo "  To:   $GDRIVE_REMOTE:$GDRIVE_FOLDER"
echo "============================================"

# ───────────────────────────────────────────
# Step 1: Check rclone
# ───────────────────────────────────────────
if ! command -v rclone &> /dev/null; then
    echo "Installing rclone..."
    curl https://rclone.org/install.sh | sudo bash
fi

# Verify remote is configured
if ! rclone listremotes | grep -q "$GDRIVE_REMOTE:"; then
    echo "ERROR: rclone remote '$GDRIVE_REMOTE' not configured!"
    echo "Run: rclone config"
    echo "  → New remote → name: gdrive → Google Drive → follow OAuth flow"
    exit 1
fi

echo "✅ rclone configured with remote: $GDRIVE_REMOTE"

# ───────────────────────────────────────────
# Step 2: Upload datasets
# ───────────────────────────────────────────
echo "[1/4] Uploading datasets..."

rclone copy $BACKUP_DIR/datasets_gpu1/ \
    $GDRIVE_REMOTE:$GDRIVE_FOLDER/datasets/gpu1/ \
    --progress --transfers=4

rclone copy $BACKUP_DIR/datasets_gpu2/ \
    $GDRIVE_REMOTE:$GDRIVE_FOLDER/datasets/gpu2/ \
    --progress --transfers=4

echo "✅ Datasets uploaded"

# ───────────────────────────────────────────
# Step 3: Upload training output
# ───────────────────────────────────────────
echo "[2/4] Uploading training output..."

if [ -d "$BACKUP_DIR/training_output" ]; then
    # Upload LoRA adapter (small, fast)
    rclone copy $BACKUP_DIR/training_output/lora_adapter/ \
        $GDRIVE_REMOTE:$GDRIVE_FOLDER/lora_adapter/ \
        --progress --transfers=4

    # Upload training logs
    rclone copy $BACKUP_DIR/training_output/ \
        $GDRIVE_REMOTE:$GDRIVE_FOLDER/training_output/ \
        --progress --transfers=4 \
        --include="*.log" --include="*.json" --include="*.jsonl"

    echo "✅ Training output uploaded"
else
    echo "No training output found, skipping"
fi

# ───────────────────────────────────────────
# Step 4: Upload GGUF files
# ───────────────────────────────────────────
echo "[3/4] Uploading GGUF files..."

if [ -d "$BACKUP_DIR/gguf" ]; then
    rclone copy $BACKUP_DIR/gguf/ \
        $GDRIVE_REMOTE:$GDRIVE_FOLDER/gguf/ \
        --progress --transfers=2

    echo "✅ GGUF files uploaded"
else
    echo "No GGUF files found, skipping"
fi

# ───────────────────────────────────────────
# Step 5: Upload logs
# ───────────────────────────────────────────
echo "[4/4] Uploading logs..."

rclone copy $BACKUP_DIR/logs_gpu1/ \
    $GDRIVE_REMOTE:$GDRIVE_FOLDER/logs/gpu1/ \
    --progress

rclone copy $BACKUP_DIR/logs_gpu2/ \
    $GDRIVE_REMOTE:$GDRIVE_FOLDER/logs/gpu2/ \
    --progress 2>/dev/null || true

echo "✅ Logs uploaded"

# ───────────────────────────────────────────
# Summary
# ───────────────────────────────────────────
echo ""
echo "============================================"
echo "  ✅ Google Drive Upload Complete!"
echo "============================================"
echo ""
echo "Uploaded to: Google Drive → $GDRIVE_FOLDER/"
echo ""
rclone size $GDRIVE_REMOTE:$GDRIVE_FOLDER/
echo ""
echo "Contents:"
rclone lsd $GDRIVE_REMOTE:$GDRIVE_FOLDER/
echo ""
echo "Download locally from Google Drive whenever ready."
echo "DO NOT delete GCP resources until user explicitly says to."
