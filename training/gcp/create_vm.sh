#!/bin/bash
# create_vm.sh — Create a GCP Spot VM with L4 GPU for fine-tuning
# Usage: bash create_vm.sh [PROJECT_ID] [BUCKET_NAME]
#
# Prerequisites:
#   gcloud auth login
#   gcloud config set project YOUR_PROJECT_ID
#   gsutil mb -l asia-south1 gs://YOUR_BUCKET_NAME/

set -euo pipefail

PROJECT_ID="${1:-$(gcloud config get-value project)}"
BUCKET_NAME="${2:-es-tutor-training}"
ZONE="asia-south1-b"
INSTANCE_NAME="es-trainer"
MACHINE_TYPE="g2-standard-8"  # 8 vCPU, 32 GB RAM, 1x L4 GPU

echo "🚀 Creating GCP Spot VM for fine-tuning"
echo "   Project: $PROJECT_ID"
echo "   Zone: $ZONE"
echo "   Machine: $MACHINE_TYPE (NVIDIA L4 24GB)"
echo "   Bucket: gs://$BUCKET_NAME"
echo ""

# --- 1. Create GCS bucket if it doesn't exist ---
if ! gsutil ls "gs://$BUCKET_NAME" &>/dev/null; then
    echo "📦 Creating GCS bucket: gs://$BUCKET_NAME"
    gsutil mb -p "$PROJECT_ID" -l asia-south1 "gs://$BUCKET_NAME/"
fi

# --- 2. Upload startup script ---
echo "📤 Uploading startup script..."
gsutil cp "$(dirname "$0")/startup.sh" "gs://$BUCKET_NAME/startup.sh"

# --- 3. Upload datasets if they exist ---
DATASET_DIR="$(dirname "$0")/../datasets"
if ls "$DATASET_DIR"/*.jsonl &>/dev/null; then
    echo "📤 Uploading datasets..."
    gsutil -m cp "$DATASET_DIR"/*.jsonl "gs://$BUCKET_NAME/datasets/"
fi

# --- 4. Create the Spot VM ---
echo "🖥️  Creating VM: $INSTANCE_NAME"
gcloud compute instances create "$INSTANCE_NAME" \
    --project="$PROJECT_ID" \
    --zone="$ZONE" \
    --machine-type="$MACHINE_TYPE" \
    --accelerator=type=nvidia-l4,count=1 \
    --image-family=common-cu121-debian-11 \
    --image-project=deeplearning-platform-release \
    --boot-disk-size=200GB \
    --boot-disk-type=pd-ssd \
    --provisioning-model=SPOT \
    --instance-termination-action=STOP \
    --maintenance-policy=TERMINATE \
    --scopes=storage-rw \
    --metadata=startup-script-url="gs://$BUCKET_NAME/startup.sh",bucket-name="$BUCKET_NAME"

echo ""
echo "✅ VM created! Connect with:"
echo "   gcloud compute ssh $INSTANCE_NAME --zone=$ZONE"
echo ""
echo "📊 Monitor training:"
echo "   gcloud compute ssh $INSTANCE_NAME --zone=$ZONE -- tail -f /var/log/training.log"
echo ""
echo "💰 Estimated cost: ~₹25-30/hour (L4 Spot)"
echo "   Monitor: https://console.cloud.google.com/billing"
