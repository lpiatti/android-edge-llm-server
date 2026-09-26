#!/usr/bin/env bash
# Benchmark TTFT (prefill) e velocità di decode via HTTP, in streaming.
# Uso: ./benchmark_prefill.sh <IP_TELEFONO> [PORTA] [API_KEY]
# Stessa misura del pulsante [ BENCHMARK ] nel tab TEST dell'app.
DEVICE_IP="${1:-192.168.1.100}" PORT="${2:-8080}" API_KEY="${3:-}" python3 - <<'PY'
import json, os, time, urllib.request

url = f"http://{os.environ['DEVICE_IP']}:{os.environ['PORT']}/v1/chat/completions"
key = os.environ.get("API_KEY", "")

def document(tokens):
    parts, i = [], 1
    while sum(len(p) for p in parts) < tokens * 4:
        parts.append(f"Paragraph {i}: warehouse {i*7%13} shipped {i*37%500} parcels to district {i*11%29} "
                     f"on day {i%30+1}, with {i*3%17} late deliveries caused by weather or traffic.\n")
        i += 1
    return "".join(parts)

def stream(messages, max_tokens):
    body = json.dumps({"model": "edge", "messages": messages, "stream": True,
                       "stream_options": {"include_usage": True}, "temperature": 0,
                       "max_tokens": max_tokens}).encode()
    headers = {"Content-Type": "application/json"}
    if key:
        headers["Authorization"] = f"Bearer {key}"
    start, ttft, text, usage = time.time(), None, "", {}
    with urllib.request.urlopen(urllib.request.Request(url, body, headers), timeout=600) as r:
        for raw in r:
            line = raw.decode().strip()
            if not line.startswith("data:") or line == "data: [DONE]":
                continue
            chunk = json.loads(line[5:])
            if "error" in chunk:
                raise RuntimeError(chunk["error"])
            for c in chunk.get("choices", []):
                piece = (c.get("delta") or {}).get("content") or ""
                if piece:
                    ttft = ttft or time.time() - start
                    text += piece
            usage = chunk.get("usage") or usage
    total = time.time() - start
    ttft = ttft or total
    completion = usage.get("completion_tokens", 0)
    tps = (completion - 1) / (total - ttft) if completion > 1 and total > ttft else 0
    cached = (usage.get("prompt_tokens_details") or {}).get("cached_tokens", 0)
    return text, usage.get("prompt_tokens", 0), cached, ttft * 1000, tps

print(f"Target: {url}\n")
print("| Prompt | Prompt tok | Cached tok | TTFT ms | Decode tok/s |\n|---|---|---|---|---|")
last = None
for target in (500, 2000, 4000):
    msgs = [{"role": "user", "content": document(target) + "\nIn one sentence, what is this document about?"}]
    try:
        text, p, c, ttft, tps = stream(msgs, 48)
        print(f"| ~{target} cold | {p} | {c} | {ttft:.0f} | {tps:.1f} |")
        last = (target, msgs, text)
    except Exception as e:
        print(f"| ~{target} cold | FAIL: {e} |")
if last:
    target, msgs, text = last
    msgs += [{"role": "assistant", "content": text},
             {"role": "user", "content": "How many parcels did paragraph 2 mention? Answer with the number only."}]
    text, p, c, ttft, tps = stream(msgs, 16)
    print(f"| ~{target} + follow-up (warm) | {p} | {c} | {ttft:.0f} | {tps:.1f} |")
    print(f"\nFollow-up answer (expected 74): {text.strip()[:80]}")
print("\nRiporta la tabella in STATE.md (sezione Misure).")
PY
