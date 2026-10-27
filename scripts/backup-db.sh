#!/usr/bin/env bash
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-backups}"
COMPOSE_SERVICE="${POSTGRES_SERVICE:-db}"
DB_NAME="${POSTGRES_DB:-health_dashboard}"
DB_USER="${POSTGRES_USER:-health}"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
BACKUP_FILE="${BACKUP_DIR}/health_dashboard_${TIMESTAMP}.sql"

mkdir -p "${BACKUP_DIR}"

docker compose exec -T "${COMPOSE_SERVICE}" pg_dump -U "${DB_USER}" -d "${DB_NAME}" --clean --if-exists > "${BACKUP_FILE}"

if [ ! -s "${BACKUP_FILE}" ]; then
  echo "Backup failed or produced an empty file: ${BACKUP_FILE}" >&2
  exit 1
fi

BYTES="$(wc -c < "${BACKUP_FILE}")"
echo "Created backup: ${BACKUP_FILE} (${BYTES} bytes)"
