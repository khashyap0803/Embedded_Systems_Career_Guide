#!/usr/bin/env python3
"""Quick quality analysis of generated datasets."""
import json, sys, os, glob

files = glob.glob(sys.argv[1] + "/*.jsonl")
for fpath in sorted(files):
    fname = os.path.basename(fpath)
    with open(fpath) as f:
        lines = f.readlines()
    
    total = len(lines)
    think_count = 0
    think_lens = []
    content_lens = []
    short_count = 0
    filler_count = 0
    
    fillers = ["Sure!", "Of course!", "Certainly!", "Absolutely!", "Great question!"]
    
    for line in lines:
        ex = json.loads(line)
        resp = ex["messages"][1]["content"]
        
        if "<think>" in resp:
            think_count += 1
            if "</think>" in resp:
                t_start = resp.index("<think>")
                t_end = resp.index("</think>") + len("</think>")
                think_len = t_end - t_start
                think_lens.append(think_len)
                content = resp[t_end:].strip()
                content_lens.append(len(content))
            else:
                think_lens.append(len(resp))
                content_lens.append(0)
        else:
            content_lens.append(len(resp))
        
        # Check for filler starts
        actual = resp
        if "</think>" in resp:
            actual = resp[resp.index("</think>")+len("</think>"):].strip()
        for f in fillers:
            if actual.startswith(f):
                filler_count += 1
                break
        
        if len(actual) < 200:
            short_count += 1
    
    avg_think = sum(think_lens) / len(think_lens) if think_lens else 0
    avg_content = sum(content_lens) / len(content_lens) if content_lens else 0
    overhead = avg_think / (avg_think + avg_content) * 100 if avg_think > 0 else 0
    
    print(f"\n=== {fname} ({total} examples) ===")
    print(f"  Think tags: {think_count}/{total} ({think_count*100//total}%)")
    print(f"  Avg think length: {avg_think:.0f} chars")
    print(f"  Avg content length: {avg_content:.0f} chars")
    print(f"  Think overhead: {overhead:.1f}%")
    print(f"  Short responses (<200 chars): {short_count}")
    print(f"  Filler starts: {filler_count}")
    
    # Show a clean content sample
    if total > 10:
        sample = json.loads(lines[10])
        resp = sample["messages"][1]["content"]
        if "</think>" in resp:
            resp = resp[resp.index("</think>")+len("</think>"):].strip()
        print(f"  --- Sample (first 500 chars) ---")
        print(f"  {resp[:500]}")
