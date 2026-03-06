#!/usr/bin/env python3
"""Strip <think>...</think> blocks from all existing JSONL files."""
import json, sys, os, re, glob

data_dir = sys.argv[1]
files = glob.glob(os.path.join(data_dir, "*.jsonl"))

for fpath in sorted(files):
    fname = os.path.basename(fpath)
    with open(fpath, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    cleaned = 0
    clean_lines = []
    for line in lines:
        ex = json.loads(line)
        resp = ex["messages"][1]["content"]
        if "<think>" in resp:
            resp = re.sub(r'<think>.*?</think>', '', resp, flags=re.DOTALL).strip()
            ex["messages"][1]["content"] = resp
            cleaned += 1
        clean_lines.append(json.dumps(ex, ensure_ascii=False) + '\n')
    
    with open(fpath, 'w', encoding='utf-8') as f:
        f.writelines(clean_lines)
    
    print(f"{fname}: {len(lines)} examples, {cleaned} cleaned")

print("Done!")
