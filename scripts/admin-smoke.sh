#!/usr/bin/env bash
set -euo pipefail

API_BASE_URL="${API_BASE_URL:-http://localhost:8015}"

echo "Checking service health..."
curl -fsS "${API_BASE_URL}/health" >/dev/null

echo "Checking admin endpoint rejects missing token..."
missing_status="$(curl -sS -o /dev/null -w '%{http_code}' "${API_BASE_URL}/api/admin/stats")"
if [ "${missing_status}" != "401" ] && [ "${missing_status}" != "503" ]; then
  echo "Expected admin stats without token to return 401 or 503, got ${missing_status}" >&2
  exit 1
fi

if [ "${ADMIN_TOKEN:-}" = "" ]; then
  echo "ADMIN_TOKEN is not set; skipping authorized admin stats check."
  exit 0
fi

echo "Checking authorized admin stats..."
curl -fsS "${API_BASE_URL}/api/admin/stats" -H "Authorization: Bearer ${ADMIN_TOKEN}" >/dev/null

echo "Admin smoke checks passed."
