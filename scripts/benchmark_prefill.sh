#!/usr/bin/env bash
# Benchmark Prefill Time-To-First-Token (TTFT) Sessione S1
DEVICE_IP="${1:-192.168.1.100}"
PORT="${2:-8080}"
URL="http://${DEVICE_IP}:${PORT}/v1/chat/completions"

echo "=== Benchmark Prefill Time-To-First-Token (TTFT) ==="
echo "Target: $URL"

run_test() {
  local tokens=$1
  local chars=$((tokens * 4))
  local text=$(python3 -c "print('L\'architettura edge computing su Android permette inferenza neurale locale sicura. ' * 50)" | cut -c 1-$chars)

  echo -n "Test contesto ~ $tokens token ($chars caratteri)... "
  START=$(date +%s%3N)
  RES=$(curl -s -X POST "$URL" \
    -H "Content-Type: application/json" \
    -d "{\"model\":\"gemma-4-E2B-it\",\"messages\":[{\"role\":\"user\",\"content\":\"Documento: $text\\n\\nRiassumi in una riga.\"}],\"stream\":false}")
  END=$(date +%s%3N)
  DURATION=$((END - START))
  echo "OK in ${DURATION} ms"
  echo "Risposta: $RES"
  echo ""
}

run_test 500
run_test 2000
run_test 4000
