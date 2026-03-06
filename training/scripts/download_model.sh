#!/bin/bash
set -e

export PATH=/home/nani0/.local/bin:/usr/local/bin:/usr/bin:/bin:$PATH

echo "=== Downloading Qwen3-14B Model ==="

export HF_HOME=/mnt/ssd1/hf_cache
export HF_HUB_ENABLE_HF_TRANSFER=1

for MODEL_NAME in "Qwen/Qwen3-14B-Instruct" "Qwen/Qwen3-14B" "unsloth/Qwen3-14B-Instruct"; do
    echo "  Trying: $MODEL_NAME"
    if huggingface-cli download "$MODEL_NAME" --local-dir /mnt/ssd1/qwen3-14b --local-dir-use-symlinks False 2>&1; then
        echo "SUCCESS: Downloaded $MODEL_NAME"
        du -sh /mnt/ssd1/qwen3-14b
        exit 0
    else
        echo "  Failed: $MODEL_NAME"
        rm -rf /mnt/ssd1/qwen3-14b
    fi
done

echo "FAILED: All model names failed."
exit 1
