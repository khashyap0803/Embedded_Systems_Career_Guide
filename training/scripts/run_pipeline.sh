#!/bin/bash
# ============================================================
# Master Runner — Run everything on a GPU VM
# Usage: bash run_pipeline.sh [1|2]
# ============================================================

set -e

GPU_ID=${1:-1}

echo "╔══════════════════════════════════════════════════════╗"
echo "║  Embedded Systems Career Guide — Training Pipeline  ║"
echo "║  GPU-$GPU_ID                                           ║"
echo "╚══════════════════════════════════════════════════════╝"

SCRIPTS_DIR="$(cd "$(dirname "$0")" && pwd)"

if [ "$GPU_ID" = "1" ]; then
    WORK_DIR="/mnt/ssd1"
    PORT=8000
else
    WORK_DIR="/mnt/ssd2"
    PORT=8001
fi

# ───────────────────────────────────────────
# Phase 1: Copy scripts to working directory
# ───────────────────────────────────────────
echo "[Phase 1] Copying scripts..."
cp $SCRIPTS_DIR/*.py $WORK_DIR/scripts/ 2>/dev/null || true
cp $SCRIPTS_DIR/*.sh $WORK_DIR/scripts/ 2>/dev/null || true
echo "✅ Scripts copied to $WORK_DIR/scripts/"

# ───────────────────────────────────────────
# Phase 2: Generate datasets
# ───────────────────────────────────────────
echo "[Phase 2] Starting dataset generation..."

python3 $WORK_DIR/scripts/generate_datasets.py \
    --gpu $GPU_ID \
    --output-dir $WORK_DIR/datasets \
    --port $PORT

echo "✅ Dataset generation complete!"

# ───────────────────────────────────────────
# Phase 3: GPU-1 only — Validation & Training
# ───────────────────────────────────────────
if [ "$GPU_ID" = "1" ]; then
    echo "[Phase 3] Syncing GPU-2 data and merging..."

    # Final sync from GPU-2
    bash $WORK_DIR/scripts/sync_backup.sh

    # Merge all datasets into backup dir
    mkdir -p /mnt/backup/merged_datasets
    cp /mnt/backup/datasets_gpu1/*.jsonl /mnt/backup/merged_datasets/ 2>/dev/null || true
    cp /mnt/backup/datasets_gpu2/*.jsonl /mnt/backup/merged_datasets/ 2>/dev/null || true

    echo "[Phase 3.1] Validating datasets..."
    python3 $WORK_DIR/scripts/validate_datasets.py \
        --input-dir /mnt/backup/merged_datasets \
        --output-dir /mnt/backup/clean_datasets

    echo "[Phase 3.2] Training Qwen3-14B..."
    python3 $WORK_DIR/scripts/train_14b.py \
        --dataset-dir /mnt/backup/clean_datasets \
        --output-dir $WORK_DIR/training_output \
        --epochs 2

    # Sync training output to backup
    rsync -av $WORK_DIR/training_output/ /mnt/backup/training_output/

    echo "[Phase 3.3] Quantizing to GGUF..."
    bash $WORK_DIR/scripts/quantize.sh $WORK_DIR/training_output/merged_model

    echo "[Phase 3.4] Uploading to Google Drive..."
    bash $WORK_DIR/scripts/upload_gdrive.sh

    echo ""
    echo "╔══════════════════════════════════════════════════╗"
    echo "║  ✅ FULL PIPELINE COMPLETE!                      ║"
    echo "║  GGUF files are on Google Drive.                 ║"
    echo "║  DO NOT delete GCP resources until user says so. ║"
    echo "╚══════════════════════════════════════════════════╝"
else
    echo ""
    echo "╔══════════════════════════════════════════════════╗"
    echo "║  ✅ GPU-2 DATA GENERATION COMPLETE!              ║"
    echo "║  Data saved to /mnt/ssd2/datasets/              ║"
    echo "║  GPU-1 will sync and merge this data.           ║"
    echo "║  DO NOT stop this VM until GPU-1 finishes sync. ║"
    echo "╚══════════════════════════════════════════════════╝"
fi
