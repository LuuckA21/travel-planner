#!/usr/bin/env bash
# Weekly integrity check of the restic repository. Run from cron.
set -euo pipefail
cd "$(dirname "$0")"

docker compose run --rm -T backup check
