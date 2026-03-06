#!/bin/bash
# ============================================================
# Create GCP Infrastructure for Qwen3-14B Fine-Tuning Pipeline
# 3 SSDs + 2 H100 VMs in asia-south1
# ============================================================

set -e

PROJECT="gemini-bot-436805"
ZONE="asia-south1-c"
REGION="asia-south1"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}  Creating GCP Infrastructure${NC}"
echo -e "${GREEN}  Project: $PROJECT | Zone: $ZONE${NC}"
echo -e "${GREEN}============================================${NC}"

# ───────────────────────────────────────────
# Step 1: Create 3 Persistent SSDs
# ───────────────────────────────────────────
echo -e "\n${YELLOW}[STEP 1] Creating 3 Persistent SSDs...${NC}"

echo "Creating SSD-1 (200GB) for GPU-1..."
gcloud compute disks create es-ssd-gpu1 \
    --project=$PROJECT \
    --zone=$ZONE \
    --size=200GB \
    --type=pd-ssd

echo "Creating SSD-2 (200GB) for GPU-2..."
gcloud compute disks create es-ssd-gpu2 \
    --project=$PROJECT \
    --zone=$ZONE \
    --size=200GB \
    --type=pd-ssd

echo "Creating SSD-3 (500GB) for backup storage..."
gcloud compute disks create es-ssd-backup \
    --project=$PROJECT \
    --zone=$ZONE \
    --size=500GB \
    --type=pd-ssd

echo -e "${GREEN}✅ All 3 SSDs created${NC}"

# ───────────────────────────────────────────
# Step 2: Create VM-1 (H100 GPU-1)
# ───────────────────────────────────────────
echo -e "\n${YELLOW}[STEP 2] Creating VM-1 (H100 GPU-1)...${NC}"

gcloud compute instances create es-gpu1 \
    --project=$PROJECT \
    --zone=$ZONE \
    --machine-type=a3-highgpu-1g \
    --accelerator=type=nvidia-h100-80gb,count=1 \
    --maintenance-policy=TERMINATE \
    --provisioning-model=SPOT \
    --instance-termination-action=STOP \
    --boot-disk-size=100GB \
    --boot-disk-type=pd-ssd \
    --image-family=pytorch-latest-gpu \
    --image-project=deeplearning-platform-release \
    --disk=name=es-ssd-gpu1,device-name=ssd-gpu1,mode=rw \
    --disk=name=es-ssd-backup,device-name=ssd-backup,mode=rw \
    --metadata=install-nvidia-driver=True \
    --scopes=default,storage-rw

echo -e "${GREEN}✅ VM-1 created with SSD-1 + SSD-3(backup)${NC}"

# ───────────────────────────────────────────
# Step 3: Create VM-2 (H100 GPU-2)
# ───────────────────────────────────────────
echo -e "\n${YELLOW}[STEP 3] Creating VM-2 (H100 GPU-2)...${NC}"

gcloud compute instances create es-gpu2 \
    --project=$PROJECT \
    --zone=$ZONE \
    --machine-type=a3-highgpu-1g \
    --accelerator=type=nvidia-h100-80gb,count=1 \
    --maintenance-policy=TERMINATE \
    --provisioning-model=SPOT \
    --instance-termination-action=STOP \
    --boot-disk-size=100GB \
    --boot-disk-type=pd-ssd \
    --image-family=pytorch-latest-gpu \
    --image-project=deeplearning-platform-release \
    --disk=name=es-ssd-gpu2,device-name=ssd-gpu2,mode=rw \
    --metadata=install-nvidia-driver=True \
    --scopes=default,storage-rw

echo -e "${GREEN}✅ VM-2 created with SSD-2${NC}"

# ───────────────────────────────────────────
# Step 4: Print connection info
# ───────────────────────────────────────────
echo -e "\n${GREEN}============================================${NC}"
echo -e "${GREEN}  ✅ ALL INFRASTRUCTURE CREATED${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
echo "SSDs:"
echo "  es-ssd-gpu1   (200GB) → attached to es-gpu1"
echo "  es-ssd-gpu2   (200GB) → attached to es-gpu2"
echo "  es-ssd-backup (500GB) → attached to es-gpu1"
echo ""
echo "VMs:"
echo "  es-gpu1 (H100, Spot) → SSD-1 + SSD-3(backup)"
echo "  es-gpu2 (H100, Spot) → SSD-2"
echo ""
echo "Connect:"
echo "  gcloud compute ssh es-gpu1 --zone=$ZONE --project=$PROJECT"
echo "  gcloud compute ssh es-gpu2 --zone=$ZONE --project=$PROJECT"
echo ""
echo "NEXT: Run setup_vm.sh on each VM"
