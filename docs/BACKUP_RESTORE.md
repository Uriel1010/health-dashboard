# Backup And Restore

Postgres data is stored in the Docker Compose `db` service volume. The backup scripts run Postgres tools inside that container, so the host machine only needs Docker Compose.

## Required Environment

The scripts use these variables, matching `docker-compose.yml` and `.env`:

- `POSTGRES_DB`, default `health_dashboard`
- `POSTGRES_USER`, default `health`
- `POSTGRES_SERVICE`, default `db`
- `BACKUP_DIR`, default `backups`

## One-Command Backup

```bash
./scripts/backup-db.sh
```

This creates a timestamped SQL dump like:

```text
backups/health_dashboard_20260628T184500Z.sql
```

## List Backups

```bash
ls -lh backups/
```

## Verify A Backup Exists And Is Not Empty

```bash
test -s backups/health_dashboard_YYYYMMDDTHHMMSSZ.sql
```

The backup script also performs this check automatically and exits with an error if the output file is empty.

## One-Command Restore

Restore is destructive because the backup is created with `pg_dump --clean --if-exists`. It may drop and recreate database objects from the backup file.

```bash
CONFIRM_RESTORE=RESTORE_HEALTH_DASHBOARD ./scripts/restore-db.sh backups/health_dashboard_YYYYMMDDTHHMMSSZ.sql
```

After restore, verify the API can read the database:

```bash
curl -sS http://localhost:8015/health
curl -sS http://localhost:8015/api/v1/devices
```

## Test Restore Locally

The safest restore test is to copy this project directory or use a separate Compose project name and volume, then restore into that isolated stack. Do not test restore against the only live copy unless you have already confirmed the backup file is present and non-empty.

Example isolated flow:

```bash
COMPOSE_PROJECT_NAME=health-dashboard-restore-test docker compose up -d db
CONFIRM_RESTORE=RESTORE_HEALTH_DASHBOARD COMPOSE_PROJECT_NAME=health-dashboard-restore-test ./scripts/restore-db.sh backups/health_dashboard_YYYYMMDDTHHMMSSZ.sql
COMPOSE_PROJECT_NAME=health-dashboard-restore-test docker compose down
```

## Notes

- Keep `backups/` private. Backups contain sensitive health data.
- Copy important backups to another disk or encrypted backup location.
- The scripts do not schedule backups; use cron or a systemd timer if you want automatic backups.
