#!/bin/bash
# ============================================================
# Quantize Fine-Tuned Qwen3-14B → GGUF (Q5_K_M + Q6_K)
# Run on H100 after training completes
# Usage: bash quantize.sh /mnt/ssd1/training_output/merged_model
# ============================================================

set -e

MODEL_DIR=${1:-"/mnt/ssd1/training_output/merged_model"}
OUTPUT_DIR="/mnt/backup/gguf"
MODEL_NAME="es-career-guide-qwen3-14b"

echo "============================================"
echo "  GGUF Quantization Pipeline"
echo "  Model: $MODEL_DIR"
echo "  Output: $OUTPUT_DIR"
echo "============================================"

mkdir -p $OUTPUT_DIR

# ───────────────────────────────────────────
# Step 1: Install llama.cpp
# ───────────────────────────────────────────
echo "[1/4] Setting up llama.cpp..."

if [ ! -d "/tmp/llama.cpp" ]; then
    cd /tmp
    git clone https://github.com/ggml-org/llama.cpp.git
    cd llama.cpp
    pip install -r requirements.txt
else
    cd /tmp/llama.cpp
    git pull
fi

echo "✅ llama.cpp ready"

# ───────────────────────────────────────────
# Step 2: Convert to GGUF (FP16)
# ───────────────────────────────────────────
echo "[2/4] Converting to GGUF FP16..."

FP16_GGUF="$OUTPUT_DIR/${MODEL_NAME}-FP16.gguf"

if [ ! -f "$FP16_GGUF" ]; then
    python3 /tmp/llama.cpp/convert_hf_to_gguf.py \
        "$MODEL_DIR" \
        --outfile "$FP16_GGUF" \
        --outtype f16

    echo "✅ FP16 GGUF created: $(du -h $FP16_GGUF | cut -f1)"
else
    echo "FP16 GGUF already exists, skipping"
fi

# ───────────────────────────────────────────
# Step 3: Build llama-quantize (if needed)
# ───────────────────────────────────────────
echo "[3/4] Building quantizer..."

cd /tmp/llama.cpp
if [ ! -f "build/bin/llama-quantize" ]; then
    cmake -B build -DGGML_CUDA=ON
    cmake --build build --target llama-quantize -j$(nproc)
fi

QUANTIZE="/tmp/llama.cpp/build/bin/llama-quantize"

# ───────────────────────────────────────────
# Step 4: Quantize to Q5_K_M and Q6_K
# ───────────────────────────────────────────
echo "[4/4] Quantizing..."

# Q5_K_M — best balance for 16GB GPU (~10GB)
Q5_FILE="$OUTPUT_DIR/${MODEL_NAME}-Q5_K_M.gguf"
if [ ! -f "$Q5_FILE" ]; then
    echo "Creating Q5_K_M..."
    $QUANTIZE "$FP16_GGUF" "$Q5_FILE" Q5_K_M
    echo "✅ Q5_K_M: $(du -h $Q5_FILE | cut -f1)"
else
    echo "Q5_K_M already exists"
fi

# Q6_K — higher quality (~12GB), also fits 16GB
Q6_FILE="$OUTPUT_DIR/${MODEL_NAME}-Q6_K.gguf"
if [ ! -f "$Q6_FILE" ]; then
    echo "Creating Q6_K..."
    $QUANTIZE "$FP16_GGUF" "$Q6_FILE" Q6_K
    echo "✅ Q6_K: $(du -h $Q6_FILE | cut -f1)"
else
    echo "Q6_K already exists"
fi

# Summary
echo ""
echo "============================================"
echo "  ✅ Quantization Complete!"
echo "============================================"
echo ""
ls -lh $OUTPUT_DIR/*.gguf
echo ""
echo "Files ready for download/upload:"
echo "  Q5_K_M: $Q5_FILE (~10 GB, fits 16GB GPU with 8K context)"
echo "  Q6_K:   $Q6_FILE (~12 GB, fits 16GB GPU with 4K context)"
echo "  FP16:   $FP16_GGUF (for re-quantization if needed)"
echo ""
echo "Next: Run upload_gdrive.sh to backup to Google Drive"
