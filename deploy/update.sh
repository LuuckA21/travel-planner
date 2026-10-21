#!/usr/bin/env bash
# Update to the latest main: backup, pull, rebuild, wait until healthy. Never rolls back by itself:
# after a database migration the old code may not run on the migrated database.
set -euo pipefail
cd "$(dirname "$0")"
NAME=UPDATE PING_VAR=UPDATE_PING_URL
# shellcheck source=backup-lib.sh
. ./backup-lib.sh

[ "$(git symbolic-ref --short -q HEAD || true)" = main ] || { log "not on branch main: run 'git checkout main' first"; exit 1; }
old=$(git rev-parse HEAD)
git fetch --quiet origin main
if [ "$(git rev-parse origin/main)" = "$old" ]; then
  log "already up to date: $(git log -1 --format='%h %s')"
  exit 0
fi

./backup.sh                       # a failed backup stops the update here (set -e)
git pull --ff-only --quiet origin main
log "updating $(git rev-parse --short "$old") -> $(git log -1 --format='%h %s')"

migrations=$(git diff --name-only "$old" HEAD -- ../backend/src/main/resources/db/migration)
config=$(git diff --name-only "$old" HEAD -- .env.example backup.env.example)
[ -z "$config" ] || log "NOTE: example settings changed, add any new variables to your .env/backup.env: $config"

if ! docker compose up -d --build --wait; then
  log "the new version did not become healthy; check: docker compose logs --tail 100 app"
  if [ -n "$migrations" ]; then
    log "it ran database migrations ($migrations): going back to $old also needs the backup just taken restored (docs/operations.md section 8)"
  else
    log "to go back: git checkout $old && docker compose up -d --build --wait"
  fi
  exit 1
fi
docker image prune -f >/dev/null  # drop the dangling images of previous builds
