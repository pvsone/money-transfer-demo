#!/bin/bash
# Starts 30 Human-In-Loop transfers through the web UI so the approvals page has work to show.
# Requires the web UI to be running (startlocalwebui.sh or startcloudwebui.sh).
# Usage: ./startapprovals.sh [ui-url]   (default http://localhost:7070)

UI_URL=${1:-http://localhost:7070}
FROM_ACCOUNTS=("Checking" "Savings")
TO_ACCOUNTS=("Justine Morris" "Raul Ruidíaz" "Ian Wu" "Emma Stockton")

for i in $(seq 1 30); do
  from=${FROM_ACCOUNTS[$((RANDOM % ${#FROM_ACCOUNTS[@]}))]}
  to=${TO_ACCOUNTS[$((RANDOM % ${#TO_ACCOUNTS[@]}))]}
  amount=$((RANDOM % 991 + 10))  # $10 - $1000

  response=$(curl -s -X POST "$UI_URL/runWorkflow" \
    -H 'Content-Type: application/json' \
    -d "{\"amount\":$amount,\"fromAccount\":\"$from\",\"toAccount\":\"$to\",\"scenario\":\"HUMAN_IN_LOOP\"}")

  echo "$i: \$$amount from $from to $to -> $response"
done
