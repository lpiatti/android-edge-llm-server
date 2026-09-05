#!/usr/bin/env bash
# Test Conversazione Multi-Turn (Verifica Full-Context S1)
DEVICE_IP="${1:-192.168.1.100}"
PORT="${2:-8080}"
URL="http://${DEVICE_IP}:${PORT}/v1/chat/completions"

echo "=== Test Conversazione Multi-Turn ==="
echo "Target: $URL"

curl -X POST "$URL" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma-4-E2B-it",
    "messages": [
      {"role": "user", "content": "Il mio nome in codice è Aquila."},
      {"role": "assistant", "content": "Ricevuto, ti chiamerò Aquila."},
      {"role": "user", "content": "Qual era il mio nome in codice?"}
    ],
    "temperature": 0.2,
    "stream": false
  }'
echo ""
