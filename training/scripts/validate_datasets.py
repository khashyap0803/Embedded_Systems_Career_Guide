#!/usr/bin/env python3
"""
Dataset Quality Validator & Filter
===================================
Scans all generated JSONL files and removes low-quality examples.
Run AFTER dataset generation, BEFORE training.

Checks:
  1. No leaked system prompts or meta-text
  2. No excessive repetition
  3. Minimum response length
  4. Code examples have comments
  5. Quiz format validation
  6. Flashcard format validation
  7. Semantic deduplication (TF-IDF similarity)
  8. Beginner-friendly language check
  9. Train/validation split (5%)

Usage:
  python3 validate_datasets.py --input-dir /mnt/backup/merged_datasets --output-dir /mnt/backup/clean_datasets
"""

import os
import re
import json
import random
import hashlib
import logging
import argparse
from datetime import datetime
from collections import Counter

# ──────────────────────────────────────────────────────────────
# Banned patterns (system prompt leaks / meta-text)
# ──────────────────────────────────────────────────────────────
BANNED_PATTERNS = [
    r"(?i)you are an? (expert|AI|language model|helpful|assistant)",
    r"(?i)as an? (AI|language model|artificial intelligence)",
    r"(?i)I'?m (just )?an? (AI|language model|assistant|chatbot)",
    r"(?i)I cannot|I can'?t (provide|help|assist|generate)",
    r"(?i)my (knowledge|training) (cutoff|data)",
    r"(?i)I don'?t have (access|the ability|personal)",
    r"(?i)generate (comprehensive|focused|practical|detailed),? (learning )?content for",
    r"(?i)you are (creating|generating|writing) (focused|practical|comprehensive)",
    r"(?i)specific stage of a personalized learning path",
    r"(?i)here'?s? (the|an?|my) (response|answer|output) (to|for) (your|the) (prompt|request|query)",
    r"(?i)sure!? (here|let me|I'?d be happy)",
    r"(?i)of course!? (here|let me|I'?ll)",
    r"(?i)certainly!? (here|let me)",
    r"(?i)absolutely!? (here|let me)",
]

BANNED_COMPILED = [re.compile(p) for p in BANNED_PATTERNS]


def setup_logging(output_dir):
    os.makedirs(output_dir, exist_ok=True)
    log_file = os.path.join(output_dir, f"validation_{datetime.now():%Y%m%d_%H%M%S}.log")
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(message)s",
        handlers=[logging.FileHandler(log_file), logging.StreamHandler()]
    )
    return logging.getLogger(__name__)


# ──────────────────────────────────────────────────────────────
# Quality Checks
# ──────────────────────────────────────────────────────────────

def check_banned_patterns(text):
    """Check if response contains banned meta-text / leaked prompts."""
    for pattern in BANNED_COMPILED:
        match = pattern.search(text[:500])  # Only check first 500 chars
        if match:
            return False, f"Banned pattern: '{match.group()}'"
    return True, ""


def check_repetition(text, max_repeats=3):
    """Check if any sentence repeats more than max_repeats times."""
    sentences = re.split(r'[.!?\n]', text)
    sentences = [s.strip().lower() for s in sentences if len(s.strip()) > 20]
    counts = Counter(sentences)
    for sent, count in counts.most_common(5):
        if count >= max_repeats:
            return False, f"Repeated {count}x: '{sent[:60]}...'"
    return True, ""


def check_min_length(text, min_chars=200):
    """Check minimum response length."""
    if len(text) < min_chars:
        return False, f"Too short: {len(text)} chars (min {min_chars})"
    return True, ""


def check_code_comments(text):
    """If response contains code blocks, check they have comments."""
    code_blocks = re.findall(r'```[\w]*\n(.*?)```', text, re.DOTALL)
    if not code_blocks:
        return True, ""  # No code blocks = skip check

    for block in code_blocks:
        lines = [l for l in block.split('\n') if l.strip()]
        if len(lines) < 5:
            continue  # Short snippets are fine
        comment_lines = sum(1 for l in lines if l.strip().startswith(('//','/*','*','#','--')))
        ratio = comment_lines / len(lines) if lines else 0
        if ratio < 0.1:  # Less than 10% comments
            return False, f"Code block with {len(lines)} lines but only {comment_lines} comments"
    return True, ""


def check_quiz_format(text, category):
    """For quiz categories, check proper format."""
    if 'quiz' not in category.lower():
        return True, ""

    # Check for options (A, B, C, D pattern)
    options = re.findall(r'[A-D]\)', text) + re.findall(r'[A-D]\.', text)
    if len(options) < 4:
        # Also check for lettered lists
        options = re.findall(r'(?:^|\n)\s*[A-D][\)\.:]', text)
        if len(options) < 4:
            return False, f"Quiz missing options (found {len(options)})"

    # Check for correct answer indication
    if not re.search(r'(?i)(correct|answer)[\s:]+[A-D]', text):
        return False, "Quiz missing correct answer indication"

    return True, ""


def check_flashcard_format(text, category):
    """For flashcard categories, check front/back format."""
    if 'flashcard' not in category.lower():
        return True, ""

    # Check for front/back or Q/A pattern
    has_format = (
        re.search(r'(?i)(front|question|Q)\s*:', text) and
        re.search(r'(?i)(back|answer|A)\s*:', text)
    )
    if not has_format:
        has_format = re.search(r'(?i)card\s*\d+', text)

    if not has_format:
        return False, "Flashcard missing Front/Back format"
    return True, ""


def check_beginner_friendly(text, category):
    """Check for beginner-friendly language indicators."""
    # Only strict check for beginner-tagged content
    if 'beginner' not in text.lower()[:200]:
        return True, ""

    friendly_indicators = [
        r'(?i)(think of|imagine|like a|similar to|analogy|for example|simply put)',
        r'(?i)(step[- ]by[- ]step|first.*then.*finally|let\'?s break)',
        r'(?i)(in simple (terms|words)|basically|essentially)',
    ]

    found = sum(1 for p in friendly_indicators if re.search(p, text))
    if found < 1:
        return False, "Beginner content lacks analogies/simple explanations"
    return True, ""


# ──────────────────────────────────────────────────────────────
# Semantic Deduplication (TF-IDF based)
# ──────────────────────────────────────────────────────────────
def compute_simple_hash(text, n=3):
    """Compute n-gram hash for similarity detection."""
    words = text.lower().split()[:100]  # First 100 words
    ngrams = set()
    for i in range(len(words) - n + 1):
        ngrams.add(' '.join(words[i:i+n]))
    return ngrams


def is_too_similar(ngrams1, ngrams2, threshold=0.7):
    """Check if two texts are too similar via Jaccard similarity."""
    if not ngrams1 or not ngrams2:
        return False
    intersection = len(ngrams1 & ngrams2)
    union = len(ngrams1 | ngrams2)
    similarity = intersection / union if union > 0 else 0
    return similarity > threshold


# ──────────────────────────────────────────────────────────────
# Main Validator
# ──────────────────────────────────────────────────────────────
def validate_file(filepath, category_name, logger):
    """Validate a single JSONL file. Returns (clean_examples, stats)."""
    logger.info(f"\nValidating: {os.path.basename(filepath)}")

    stats = {
        "total": 0, "passed": 0,
        "rejected_banned": 0, "rejected_repetition": 0,
        "rejected_short": 0, "rejected_code_comments": 0,
        "rejected_quiz_format": 0, "rejected_flashcard_format": 0,
        "rejected_beginner": 0, "rejected_similar": 0,
        "rejected_json": 0,
    }

    clean_examples = []
    seen_ngrams = []  # For similarity check

    with open(filepath, 'r', encoding='utf-8') as f:
        for line_num, line in enumerate(f, 1):
            line = line.strip()
            if not line:
                continue
            stats["total"] += 1

            # Parse JSON
            try:
                example = json.loads(line)
                messages = example.get("messages", [])
                if len(messages) < 2:
                    stats["rejected_json"] += 1
                    continue
                assistant_text = messages[1].get("content", "")
            except json.JSONDecodeError:
                stats["rejected_json"] += 1
                continue

            # Run all checks
            checks = [
                (check_banned_patterns(assistant_text), "rejected_banned"),
                (check_repetition(assistant_text), "rejected_repetition"),
                (check_min_length(assistant_text, 200), "rejected_short"),
                (check_code_comments(assistant_text), "rejected_code_comments"),
                (check_quiz_format(assistant_text, category_name), "rejected_quiz_format"),
                (check_flashcard_format(assistant_text, category_name), "rejected_flashcard_format"),
                (check_beginner_friendly(assistant_text, category_name), "rejected_beginner"),
            ]

            rejected = False
            for (passed, reason), stat_key in checks:
                if not passed:
                    stats[stat_key] += 1
                    if stats["total"] <= 5:  # Log first few rejections per file
                        logger.debug(f"  Rejected #{line_num}: {reason}")
                    rejected = True
                    break  # Stop at first failure

            if rejected:
                continue

            # Semantic similarity check (sample-based for speed)
            ngrams = compute_simple_hash(assistant_text)
            if len(seen_ngrams) > 0:
                # Check against last 200 examples (sliding window)
                for prev_ngrams in seen_ngrams[-200:]:
                    if is_too_similar(ngrams, prev_ngrams, threshold=0.7):
                        stats["rejected_similar"] += 1
                        rejected = True
                        break

            if rejected:
                continue

            seen_ngrams.append(ngrams)
            clean_examples.append(example)
            stats["passed"] += 1

    # Log stats
    reject_total = stats["total"] - stats["passed"]
    reject_pct = (reject_total / stats["total"] * 100) if stats["total"] > 0 else 0
    logger.info(f"  Total: {stats['total']} | Passed: {stats['passed']} | "
                f"Rejected: {reject_total} ({reject_pct:.1f}%)")

    for key, val in stats.items():
        if key.startswith("rejected_") and val > 0:
            logger.info(f"    {key}: {val}")

    return clean_examples, stats


def main():
    parser = argparse.ArgumentParser(description="Validate and filter datasets")
    parser.add_argument("--input-dir", required=True, help="Directory with raw JSONL files")
    parser.add_argument("--output-dir", required=True, help="Directory for clean JSONL files")
    parser.add_argument("--val-split", type=float, default=0.05, help="Validation split ratio")
    args = parser.parse_args()

    logger = setup_logging(args.output_dir)
    logger.info("=" * 60)
    logger.info("  Dataset Quality Validation & Filtering")
    logger.info("=" * 60)

    all_clean = []
    total_stats = Counter()

    # Process each file
    for filename in sorted(os.listdir(args.input_dir)):
        if not filename.endswith('.jsonl'):
            continue
        filepath = os.path.join(args.input_dir, filename)
        category = filename.replace('.jsonl', '')

        clean, stats = validate_file(filepath, category, logger)
        all_clean.extend(clean)
        total_stats.update(stats)

        # Save per-category clean file
        clean_file = os.path.join(args.output_dir, filename)
        with open(clean_file, 'w', encoding='utf-8') as f:
            for ex in clean:
                f.write(json.dumps(ex, ensure_ascii=False) + '\n')

    # Overall stats
    logger.info(f"\n{'='*60}")
    logger.info(f"  OVERALL RESULTS")
    logger.info(f"{'='*60}")
    logger.info(f"Total examples: {total_stats['total']}")
    logger.info(f"Passed: {total_stats['passed']}")
    rejected = total_stats['total'] - total_stats['passed']
    logger.info(f"Rejected: {rejected} ({rejected/total_stats['total']*100:.1f}%)")
    for key in sorted(total_stats):
        if key.startswith("rejected_") and total_stats[key] > 0:
            logger.info(f"  {key}: {total_stats[key]}")

    # Shuffle and split train/val
    logger.info(f"\nCreating train/val split ({1-args.val_split:.0%}/{args.val_split:.0%})...")
    random.seed(42)
    random.shuffle(all_clean)

    val_size = int(len(all_clean) * args.val_split)
    val_set = all_clean[:val_size]
    train_set = all_clean[val_size:]

    # Write train file
    train_file = os.path.join(args.output_dir, "train.jsonl")
    with open(train_file, 'w', encoding='utf-8') as f:
        for ex in train_set:
            f.write(json.dumps(ex, ensure_ascii=False) + '\n')

    # Write val file
    val_file = os.path.join(args.output_dir, "val.jsonl")
    with open(val_file, 'w', encoding='utf-8') as f:
        for ex in val_set:
            f.write(json.dumps(ex, ensure_ascii=False) + '\n')

    logger.info(f"Train: {len(train_set)} examples → {train_file}")
    logger.info(f"Val:   {len(val_set)} examples → {val_file}")
    logger.info(f"\n✅ Validation complete! Use train.jsonl for training.")


if __name__ == "__main__":
    main()
