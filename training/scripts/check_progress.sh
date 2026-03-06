#!/bin/bash
# Comprehensive training progress check
echo "=== TRAINING PROGRESS ==="

LOG=/mnt/ssd1/training.log

# Current step
STEP=$(sed 's/\r/\n/g' $LOG | grep -oP '\d+/1101' | tail -1)
echo "Step: $STEP"

# Loss values (logged every 10 steps)
echo ""
echo "=== LOSS CURVE ==="
sed 's/\r/\n/g' $LOG | grep -oP "'loss':\s*[\d.]+" | tail -10

# Learning rate
echo ""
echo "=== LEARNING RATE ==="
sed 's/\r/\n/g' $LOG | grep -oP "'learning_rate':\s*[\d.e-]+" | tail -3

# Epoch info
echo ""
echo "=== EPOCH ==="
sed 's/\r/\n/g' $LOG | grep -oP "'epoch':\s*[\d.]+" | tail -3

# Errors
echo ""
echo "=== ERRORS ==="
grep -ci 'error\|exception\|traceback\|cuda\|oom\|killed' $LOG 2>/dev/null || echo "0"

# Warnings
echo ""
echo "=== WARNINGS ==="
grep -i 'warning' $LOG 2>/dev/null | wc -l

# Process status
echo ""
echo "=== PROCESS ==="
ps aux | grep train_14b | grep -v grep | wc -l

# GPU
echo ""
echo "=== GPU ==="
nvidia-smi --query-gpu=utilization.gpu,memory.used,memory.total,temperature.gpu --format=csv,noheader

# Speed estimate
echo ""
echo "=== SPEED ==="
sed 's/\r/\n/g' $LOG | grep -oP '[\d.]+s/it' | tail -1
