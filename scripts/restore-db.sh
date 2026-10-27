#!/usr/bin/env bash
set -euo pipefail

if [ "${1:-}" = "" ]; then
  echo "Usage: $0 backups/health_dashboard_YYYYMMDDTHHMMSSZ.sql" >&2
  exit 2
fi

BACKUP_FILE="$1"
COMPOSE_SERVICE="${POSTGRES_SERVICE:-db}"
DB_NAME="${POSTGRES_DB:-health_dashboard}"
DB_USER="${POSTGRES_USER:-health}"
CONFIRM="${CONFIRM_RESTORE:-}"

if [ ! -s "${BACKUP_FILE}" ]; then
  echo "Backup file does not exist or is empty: ${BACKUP_FILE}" >&2
  exit 1
fi

if [ "${CONFIRM}" != "RESTORE_HEALTH_DASHBOARD" ]; then
  echo "Restore is destructive. Re-run with CONFIRM_RESTORE=RESTORE_HEALTH_DASHBOARD." >&2
  exit 3
fi

cat "${BACKUP_FILE}" | docker compose exec -T "${COMPOSE_SERVICE}" psql -U "${DB_USER}" -d "${DB_NAME}"

echo "Restored backup: ${BACKUP_FILE}"
