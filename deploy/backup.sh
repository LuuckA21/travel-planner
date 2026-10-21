#!/usr/bin/env bash
# Nightly backup: database dump + stored files to restic, then retention. Run from cron.
set -euo pipefail
cd "$(dirname "$0")"

restic() { docker compose run --rm -T backup "$@"; }

restic snapshots >/dev/null || restic init

# The container's own POSTGRES_* variables are used, so no credentials are needed on the host.
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB"' \
  | restic backup --stdin --stdin-filename db.sql --tag db
restic backup /data/files --tag files
restic forget --prune --keep-daily 14 --keep-weekly 8 --keep-monthly 6
