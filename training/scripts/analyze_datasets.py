#!/usr/bin/env python3
"""
Comprehensive Dataset Quality Analysis
=======================================
Analyzes all 8 JSONL dataset files for:
- JSON validity & schema compliance
- Duplicate detection (exact + near-duplicate)
- Response quality (length, think tags, filler words, truncation)
- Content quality (AI mentions, meta-commentary, code presence)
- Cross-category duplicate detection
- Statistical summary
"""

import json
import os
import hashlib
import re
import sys
from collections import Counter, defaultdict

DATASET_DIR = os.path.join(os.path.dirname(__file__), "datasets")

# ──────────────────────────────────────────────────────────────
# Quality Patterns
# ──────────────────────────────────────────────────────────────

THINK_PATTERN = re.compile(r'<think>.*?</think>', re.DOTALL)
FILLER_STARTS = [
    "sure,", "sure!", "of course", "great question", "that's a great",
    "absolutely!", "certainly!", "definitely!", "no problem",
    "i'd be happy to", "i'm happy to", "let me help",
    "well,", "so,", "okay,", "alright,",
]
AI_MENTIONS = [
    "as an ai", "as a language model", "i'm an ai", "i am an ai",
    "as an assistant", "i don't have personal", "i cannot feel",
    "as a chatbot", "as a virtual assistant", "openai", "chatgpt",
    "gemini", "claude", "llama",
]
TRUNCATION_INDICATORS = [
    "...\n", "…\n", # trailing ellipsis
]
META_COMMENTARY = [
    "here's a", "here is a", "below is", "i'll provide",
    "let me explain", "i hope this helps", "feel free to ask",
    "don't hesitate to", "happy to help", "hope that helps",
]

def analyze_file(filepath):
    """Analyze a single JSONL dataset file."""
    filename = os.path.basename(filepath)
    results = {
        "filename": filename,
        "total_lines": 0,
        "valid_json": 0,
        "invalid_json": 0,
        "invalid_json_lines": [],
        "schema_errors": 0,
        "schema_error_details": [],
        # Duplicates
        "exact_duplicates": 0,
        "near_duplicates": 0,
        # Response stats
        "response_lengths": [],
        "user_lengths": [],
        "empty_responses": 0,
        "short_responses": 0,  # < 100 chars
        "very_long_responses": 0,  # > 10000 chars
        # Quality issues
        "think_tags": 0,
        "filler_starts": 0,
        "filler_examples": [],
        "ai_mentions": 0,
        "ai_mention_examples": [],
        "meta_commentary": 0,
        "truncation_suspected": 0,
        # Content analysis
        "has_code": 0,
        "has_markdown_headers": 0,
        "has_bullet_points": 0,
        "has_numbered_lists": 0,
        # Hashes for cross-category dedup
        "hashes": set(),
        "user_hashes": set(),
    }

    seen_hashes = {}
    seen_user_first50 = Counter()

    with open(filepath, 'r', encoding='utf-8') as f:
        for line_num, line in enumerate(f, 1):
            results["total_lines"] += 1
            line = line.strip()
            if not line:
                continue

            # JSON validity
            try:
                data = json.loads(line)
            except json.JSONDecodeError as e:
                results["invalid_json"] += 1
                if len(results["invalid_json_lines"]) < 5:
                    results["invalid_json_lines"].append(f"Line {line_num}: {str(e)[:80]}")
                continue

            results["valid_json"] += 1

            # Schema validation
            if "messages" not in data:
                results["schema_errors"] += 1
                if len(results["schema_error_details"]) < 3:
                    results["schema_error_details"].append(f"Line {line_num}: missing 'messages' key")
                continue

            msgs = data["messages"]
            if not isinstance(msgs, list) or len(msgs) < 2:
                results["schema_errors"] += 1
                if len(results["schema_error_details"]) < 3:
                    results["schema_error_details"].append(f"Line {line_num}: messages not a list or < 2 items")
                continue

            # Check roles
            roles = [m.get("role") for m in msgs]
            if "user" not in roles or "assistant" not in roles:
                results["schema_errors"] += 1
                if len(results["schema_error_details"]) < 3:
                    results["schema_error_details"].append(f"Line {line_num}: missing user/assistant role")
                continue

            user_msg = next((m["content"] for m in msgs if m["role"] == "user"), "")
            asst_msg = next((m["content"] for m in msgs if m["role"] == "assistant"), "")

            # Exact duplicate check
            h = hashlib.md5(json.dumps(data, sort_keys=True).encode()).hexdigest()
            if h in seen_hashes:
                results["exact_duplicates"] += 1
            else:
                seen_hashes[h] = line_num
            results["hashes"].add(h)

            # Near-duplicate check (first 50 chars of user message)
            user_prefix = user_msg[:50].lower().strip()
            results["user_hashes"].add(hashlib.md5(user_msg.encode()).hexdigest())
            seen_user_first50[user_prefix] += 1

            # Response length stats
            resp_len = len(asst_msg)
            user_len = len(user_msg)
            results["response_lengths"].append(resp_len)
            results["user_lengths"].append(user_len)

            if resp_len == 0:
                results["empty_responses"] += 1
            elif resp_len < 100:
                results["short_responses"] += 1
            elif resp_len > 10000:
                results["very_long_responses"] += 1

            # Think tags
            if '<think>' in asst_msg:
                results["think_tags"] += 1

            # Filler starts
            asst_lower = asst_msg.lower().strip()
            for filler in FILLER_STARTS:
                if asst_lower.startswith(filler):
                    results["filler_starts"] += 1
                    if len(results["filler_examples"]) < 3:
                        results["filler_examples"].append(asst_msg[:100])
                    break

            # AI mentions
            for mention in AI_MENTIONS:
                if mention in asst_lower:
                    results["ai_mentions"] += 1
                    if len(results["ai_mention_examples"]) < 3:
                        idx = asst_lower.find(mention)
                        context = asst_msg[max(0,idx-30):idx+50]
                        results["ai_mention_examples"].append(context)
                    break

            # Meta commentary
            for meta in META_COMMENTARY:
                if asst_lower.startswith(meta):
                    results["meta_commentary"] += 1
                    break

            # Truncation detection
            if asst_msg.rstrip().endswith('...') or asst_msg.rstrip().endswith('…'):
                results["truncation_suspected"] += 1

            # Content richness
            if '```' in asst_msg or '    #include' in asst_msg or 'void ' in asst_msg:
                results["has_code"] += 1
            if re.search(r'^#{1,4}\s', asst_msg, re.MULTILINE):
                results["has_markdown_headers"] += 1
            if re.search(r'^[\-\*]\s', asst_msg, re.MULTILINE):
                results["has_bullet_points"] += 1
            if re.search(r'^\d+[\.\)]\s', asst_msg, re.MULTILINE):
                results["has_numbered_lists"] += 1

    # Near-duplicate count (user prompts appearing 3+ times with same prefix)
    results["near_duplicates"] = sum(count - 1 for count in seen_user_first50.values() if count >= 3)

    return results


def print_report(all_results):
    """Print comprehensive quality report."""
    
    print("=" * 80)
    print("  COMPREHENSIVE DATASET QUALITY ANALYSIS")
    print("=" * 80)
    
    total_examples = 0
    total_valid = 0
    total_invalid = 0
    total_schema_errors = 0
    total_exact_dupes = 0
    total_near_dupes = 0
    total_think_tags = 0
    total_filler = 0
    total_ai_mentions = 0
    total_meta = 0
    total_truncation = 0
    total_empty = 0
    total_short = 0
    all_response_lengths = []
    all_user_lengths = []
    all_hashes = set()
    all_user_hashes = set()
    
    for r in all_results:
        print(f"\n{'─' * 80}")
        print(f"  📁 {r['filename']}")
        print(f"{'─' * 80}")
        
        total_examples += r['total_lines']
        total_valid += r['valid_json']
        total_invalid += r['invalid_json']
        total_schema_errors += r['schema_errors']
        total_exact_dupes += r['exact_duplicates']
        total_near_dupes += r['near_duplicates']
        total_think_tags += r['think_tags']
        total_filler += r['filler_starts']
        total_ai_mentions += r['ai_mentions']
        total_meta += r['meta_commentary']
        total_truncation += r['truncation_suspected']
        total_empty += r['empty_responses']
        total_short += r['short_responses']
        all_response_lengths.extend(r['response_lengths'])
        all_user_lengths.extend(r['user_lengths'])
        
        # Cross-category dedup tracking
        cross_dupes = len(r['hashes'] & all_hashes)
        cross_user_dupes = len(r['user_hashes'] & all_user_hashes)
        all_hashes.update(r['hashes'])
        all_user_hashes.update(r['user_hashes'])
        
        # Basic stats
        print(f"  Total examples:     {r['total_lines']:,}")
        print(f"  Valid JSON:         {r['valid_json']:,} ({r['valid_json']/max(r['total_lines'],1)*100:.1f}%)")
        if r['invalid_json'] > 0:
            print(f"  ❌ Invalid JSON:    {r['invalid_json']:,}")
            for err in r['invalid_json_lines']:
                print(f"     → {err}")
        if r['schema_errors'] > 0:
            print(f"  ❌ Schema errors:   {r['schema_errors']:,}")
            for err in r['schema_error_details']:
                print(f"     → {err}")
        
        # Duplicates
        print(f"\n  Duplicates:")
        print(f"    Exact duplicates:       {r['exact_duplicates']:,}")
        print(f"    Near-duplicates:        {r['near_duplicates']:,} (same first 50 chars, 3+ occurrences)")
        if cross_dupes > 0:
            print(f"    ⚠️  Cross-category dupes: {cross_dupes:,}")
        if cross_user_dupes > 0:
            print(f"    Cross-cat user dupes:   {cross_user_dupes:,}")
        
        # Response length stats
        if r['response_lengths']:
            lengths = r['response_lengths']
            avg_len = sum(lengths) / len(lengths)
            sorted_lens = sorted(lengths)
            median_len = sorted_lens[len(sorted_lens) // 2]
            min_len = min(lengths)
            max_len = max(lengths)
            p10 = sorted_lens[int(len(sorted_lens) * 0.1)]
            p90 = sorted_lens[int(len(sorted_lens) * 0.9)]
            
            print(f"\n  Response Length (chars):")
            print(f"    Min / P10 / Median / Avg / P90 / Max:")
            print(f"    {min_len:,} / {p10:,} / {median_len:,} / {avg_len:,.0f} / {p90:,} / {max_len:,}")
            print(f"    Empty (0 chars):     {r['empty_responses']:,}")
            print(f"    Short (<100 chars):  {r['short_responses']:,}")
            print(f"    Very long (>10K):    {r['very_long_responses']:,}")
        
        # User prompt length
        if r['user_lengths']:
            u_avg = sum(r['user_lengths']) / len(r['user_lengths'])
            print(f"\n  User Prompt Avg Length: {u_avg:,.0f} chars")
        
        # Quality issues
        print(f"\n  Quality Issues:")
        print(f"    <think> tags:        {r['think_tags']:,}  {'✅ Clean' if r['think_tags'] == 0 else '❌ CONTAMINATED'}")
        print(f"    Filler starts:       {r['filler_starts']:,}  {'✅ Clean' if r['filler_starts'] == 0 else '⚠️  Has fillers'}")
        if r['filler_examples']:
            for ex in r['filler_examples'][:2]:
                print(f"      → \"{ex}...\"")
        print(f"    AI self-mentions:    {r['ai_mentions']:,}  {'✅ Clean' if r['ai_mentions'] == 0 else '⚠️  Has AI refs'}")
        if r['ai_mention_examples']:
            for ex in r['ai_mention_examples'][:2]:
                print(f"      → \"...{ex}...\"")
        print(f"    Meta-commentary:     {r['meta_commentary']:,}")
        print(f"    Truncation suspect:  {r['truncation_suspected']:,}")
        
        # Content richness
        n = max(r['valid_json'], 1)
        print(f"\n  Content Richness:")
        print(f"    Has code blocks:     {r['has_code']:,} ({r['has_code']/n*100:.1f}%)")
        print(f"    Has headers (##):    {r['has_markdown_headers']:,} ({r['has_markdown_headers']/n*100:.1f}%)")
        print(f"    Has bullet points:   {r['has_bullet_points']:,} ({r['has_bullet_points']/n*100:.1f}%)")
        print(f"    Has numbered lists:  {r['has_numbered_lists']:,} ({r['has_numbered_lists']/n*100:.1f}%)")
    
    # ──────────────────────────────────────────────────
    # GRAND SUMMARY
    # ──────────────────────────────────────────────────
    print(f"\n{'=' * 80}")
    print(f"  📊 GRAND SUMMARY")
    print(f"{'=' * 80}")
    
    print(f"\n  Total examples:       {total_examples:,}")
    print(f"  Valid JSON:           {total_valid:,} ({total_valid/total_examples*100:.2f}%)")
    print(f"  Invalid JSON:         {total_invalid:,}")
    print(f"  Schema errors:        {total_schema_errors:,}")
    
    error_rate = (total_invalid + total_schema_errors) / total_examples * 100
    print(f"\n  ⚡ ERROR RATE:        {error_rate:.2f}%")
    
    print(f"\n  Duplicates:")
    print(f"    Exact (within cat):  {total_exact_dupes:,}")
    print(f"    Near (within cat):   {total_near_dupes:,}")
    cross_total = total_examples - len(all_hashes)
    print(f"    Cross-category:      {cross_total:,}")
    unique_pct = len(all_hashes) / total_examples * 100
    print(f"    Unique examples:     {len(all_hashes):,} ({unique_pct:.1f}%)")
    
    print(f"\n  Quality Issues Summary:")
    print(f"    <think> tags:        {total_think_tags:,}  {'✅' if total_think_tags == 0 else '❌'}")
    print(f"    Filler starts:       {total_filler:,}  ({total_filler/total_examples*100:.1f}%)")
    print(f"    AI self-mentions:    {total_ai_mentions:,}  ({total_ai_mentions/total_examples*100:.1f}%)")
    print(f"    Meta-commentary:     {total_meta:,}  ({total_meta/total_examples*100:.1f}%)")
    print(f"    Truncation suspect:  {total_truncation:,}  ({total_truncation/total_examples*100:.1f}%)")
    print(f"    Empty responses:     {total_empty:,}")
    print(f"    Short (<100 chars):  {total_short:,}")
    
    if all_response_lengths:
        lengths = all_response_lengths
        avg_len = sum(lengths) / len(lengths)
        sorted_lens = sorted(lengths)
        median_len = sorted_lens[len(sorted_lens) // 2]
        total_tokens_est = sum(lengths) / 4  # rough char-to-token ratio
        
        print(f"\n  Response Length (all categories):")
        print(f"    Average:             {avg_len:,.0f} chars (~{avg_len/4:,.0f} tokens)")
        print(f"    Median:              {median_len:,} chars")
        print(f"    Total content:       {sum(lengths)/1024/1024:.1f} MB")
        print(f"    Est. total tokens:   {total_tokens_est/1e6:.2f}M tokens")
    
    # Overall quality score
    issues = total_think_tags + total_invalid + total_schema_errors + total_empty
    quality_score = max(0, 100 - (issues / total_examples * 100) - (total_exact_dupes / total_examples * 50))
    
    print(f"\n  {'=' * 40}")
    if quality_score >= 95:
        grade = "A+"
    elif quality_score >= 90:
        grade = "A"
    elif quality_score >= 80:
        grade = "B"
    elif quality_score >= 70:
        grade = "C"
    else:
        grade = "D"
    print(f"  🏆 OVERALL QUALITY SCORE: {quality_score:.1f}/100 ({grade})")
    print(f"  {'=' * 40}")


def main():
    dataset_files = sorted([
        os.path.join(DATASET_DIR, f) 
        for f in os.listdir(DATASET_DIR) 
        if f.endswith('.jsonl')
    ])
    
    if not dataset_files:
        print(f"No .jsonl files found in {DATASET_DIR}")
        sys.exit(1)
    
    print(f"Found {len(dataset_files)} dataset files in {DATASET_DIR}")
    print(f"Analyzing...\n")
    
    all_results = []
    for filepath in dataset_files:
        print(f"  Analyzing {os.path.basename(filepath)}...", end=" ", flush=True)
        result = analyze_file(filepath)
        print(f"OK ({result['total_lines']:,} examples)")
        all_results.append(result)
    
    print_report(all_results)


if __name__ == "__main__":
    main()
