# Multi-user upgrade

The multi-user release changes database ownership and browser authentication. Do not deploy it without a verified SQL backup.

## Safe upgrade

1. Create and verify a backup:

   ```bash
   ./scripts/backup-db.sh
   test -s backups/health_dashboard_YYYYMMDDTHHMMSSZ.sql
   ```

2. Stop API and frontend writers while leaving PostgreSQL available:

   ```bash
   docker compose stop api frontend
   ```

3. Build the new images:

   ```bash
   docker compose build api frontend
   ```

4. Run the interactive bootstrap. The container applies Alembic migrations first, assigns every existing device and meal to the sole administrator, and preserves the current `INGEST_API_TOKEN`:

   ```bash
   docker compose run --rm api python -m app.manage
   ```

5. Start the application:

   ```bash
   docker compose up -d api frontend
   docker compose ps
   ```

6. Sign in, confirm the device/snapshot/meal counts, and verify that the existing Android sender continues syncing.

7. Remove `READ_TOKEN` from `.env`; it is no longer used.

## New users

Only the sole administrator can create users. Open **User administration**, enter a username and display name, and copy the generated temporary password. It is displayed once. The user must replace it at first login.

Each user opens **Account & ingestion** to generate their own Android ingest token. Rotating or revoking it invalidates the previous value immediately.

## Nutrition boundary

Each user selects an hourly reset from 00:00 through 23:00. The server interprets it using `APP_TIMEZONE`. With a 04:00 reset, meals from 00:00 through 03:59 belong to the previous nutrition day. Historical timestamps are never rewritten.

## Rollback

Restoring the pre-upgrade SQL backup is the supported rollback because the migration changes ownership constraints. Follow [BACKUP_RESTORE.md](BACKUP_RESTORE.md) and restore only after explicitly confirming the target database.
