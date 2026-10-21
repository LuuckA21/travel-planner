#!/usr/bin/env bash
# Weekly integrity check of the restic repository. Run from cron.
set -euo pipefail
cd "$(dirname "$0")"
NAME="BACKUP CHECK" PING_VAR=BACKUP_CHECK_PING_URL
# shellcheck source=backup-lib.sh
. ./backup-lib.sh

docker compose run --rm -T backup check
