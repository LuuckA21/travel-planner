#!/usr/bin/env bash
# Nightly backup: database dump + stored files to restic, then retention. Run from cron.
set -euo pipefail
cd "$(dirname "$0")"
NAME=BACKUP PING_VAR=BACKUP_PING_URL
# shellcheck source=backup-lib.sh
. ./backup-lib.sh

restic() { docker compose run --rm -T backup "$@"; }

# Exit code 10 means "repository does not exist"; any other failure is real and must stop the run.
rc=0
restic snapshots >/dev/null || rc=$?
if [ "$rc" = 10 ]; then restic init; elif [ "$rc" != 0 ]; then exit "$rc"; fi

# Dump to a file first: a failing pg_dump must stop the run, not become an empty "latest" db snapshot.
# The container's own POSTGRES_* variables are used, so no credentials are needed on the host.
cleanup() { docker compose exec -T db rm -f /tmp/db.sql >/dev/null 2>&1; }
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB" > /tmp/db.sql && test -s /tmp/db.sql'
docker compose exec -T db cat /tmp/db.sql | restic backup --stdin --stdin-filename db.sql --tag db
restic backup /data/files --tag files
restic forget --prune --keep-daily 14 --keep-weekly 8 --keep-monthly 6
