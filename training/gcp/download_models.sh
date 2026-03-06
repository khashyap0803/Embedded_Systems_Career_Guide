#!/bin/bash
# download_models.sh — Download finished GGUF model from GCS to local Windows PC
# Run from PowerShell/Git Bash on the Windows machine with RTX 5060 Ti
#
# Prerequisites:
#   - gcloud CLI installed: https://cloud.google.com/sdk/docs/install
#   - gcloud auth login
#
# Usage: bash download_models.sh [BUCKET_NAME]

set -euo pipefail

BUCKET_NAME="${1:-es-tutor-training}"
OUTPUT_DIR="$(dirname "$0")/../models"
mkdir -p "$OUTPUT_DIR"

echo "📥 Downloading fine-tuned GGUF models from gs://$BUCKET_NAME/output/"
echo "   Destination: $OUTPUT_DIR"
echo ""

# List available models
echo "📋 Available models in bucket:"
gsutil ls "gs://$BUCKET_NAME/output/*.gguf" 2>/dev/null || {
    echo "❌ No GGUF files found in gs://$BUCKET_NAME/output/"
    echo "   Make sure merge_and_quantize.sh has completed on the GCP VM"
    exit 1
}
echo ""

# Download all GGUF files
echo "⬇️  Downloading (this may take 15-30 minutes for ~16GB files)..."
gsutil -m cp "gs://$BUCKET_NAME/output/*.gguf" "$OUTPUT_DIR/"

# Download the Modelfile too
gsutil cp "gs://$BUCKET_NAME/output/Modelfile" "$OUTPUT_DIR/" 2>/dev/null || true

echo ""
echo "✅ Download complete!"
ls -lh "$OUTPUT_DIR"/*.gguf
echo ""
echo "Next steps:"
echo "  1. Install Ollama for Windows: https://ollama.com/download/windows"
echo "  2. Import model: ollama create embedded-tutor -f $OUTPUT_DIR/Modelfile"
echo "  3. Test: ollama run embedded-tutor"
echo "  4. Install Tailscale: https://tailscale.com/download/windows"
echo "  5. Expose: tailscale funnel 11434"
