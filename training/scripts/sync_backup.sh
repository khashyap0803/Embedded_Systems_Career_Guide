#!/bin/bash
# ============================================================
# Sync Backup Script — Runs as cron job every 30 minutes
# Syncs SSD-1 and SSD-2 data to SSD-3 (backup)
# Only runs on GPU-1 VM (which has SSD-3 mounted)
#
# Install: crontab -e → add:
#   */30 * * * * /mnt/ssd1/scripts/sync_backup.sh >> /mnt/backup/sync.log 2>&1
# ============================================================

BACKUP_DIR="/mnt/backup"
LOG_FILE="$BACKUP_DIR/sync.log"
TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')

echo "[$TIMESTAMP] Starting backup sync..."

# ───────────────────────────────────────────
# Sync SSD-1 (local on this VM)
# ───────────────────────────────────────────
echo "[$TIMESTAMP] Syncing SSD-1 → backup..."
rsync -av --progress /mnt/ssd1/datasets/ $BACKUP_DIR/datasets_gpu1/ 2>&1
rsync -av --progress /mnt/ssd1/logs/ $BACKUP_DIR/logs_gpu1/ 2>&1

# Also sync training output if it exists
if [ -d "/mnt/ssd1/training_output" ]; then
    rsync -av --progress /mnt/ssd1/training_output/ $BACKUP_DIR/training_output/ 2>&1
fi

echo "[$TIMESTAMP] SSD-1 sync complete"

# ───────────────────────────────────────────
# Sync SSD-2 (from GPU-2 VM via SSH)
# Only if GPU-2 is reachable
# ───────────────────────────────────────────
GPU2_IP=$(gcloud compute instances describe es-gpu2 \
    --zone=asia-south1-c \
    --format='get(networkInterfaces[0].networkIP)' 2>/dev/null)

if [ -n "$GPU2_IP" ]; then
    echo "[$TIMESTAMP] Syncing SSD-2 from GPU-2 ($GPU2_IP) → backup..."

    # Try SSH sync (using internal GCP network)
    rsync -avz -e "ssh -o StrictHostKeyChecking=no -o ConnectTimeout=10" \
        $GPU2_IP:/mnt/ssd2/datasets/ $BACKUP_DIR/datasets_gpu2/ 2>&1 || \
        echo "[$TIMESTAMP] WARNING: Could not reach GPU-2 for sync"

    rsync -avz -e "ssh -o StrictHostKeyChecking=no -o ConnectTimeout=10" \
        $GPU2_IP:/mnt/ssd2/logs/ $BACKUP_DIR/logs_gpu2/ 2>&1 || true

    echo "[$TIMESTAMP] SSD-2 sync complete"
else
    echo "[$TIMESTAMP] GPU-2 not running, skipping SSD-2 sync"
fi

# ───────────────────────────────────────────
# Log summary
# ───────────────────────────────────────────
TIMESTAMP2=$(date '+%Y-%m-%d %H:%M:%S')
echo "[$TIMESTAMP2] Backup sync complete. Contents:"

# Count examples per file
for f in $BACKUP_DIR/datasets_gpu1/*.jsonl $BACKUP_DIR/datasets_gpu2/*.jsonl; do
    if [ -f "$f" ]; then
        count=$(wc -l < "$f")
        size=$(du -h "$f" | cut -f1)
        echo "  $(basename $f): $count examples ($size)"
    fi
done

TOTAL=$(cat $BACKUP_DIR/datasets_gpu1/*.jsonl $BACKUP_DIR/datasets_gpu2/*.jsonl 2>/dev/null | wc -l)
echo "  TOTAL: $TOTAL examples"
echo "---"
