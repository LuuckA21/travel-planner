#!/usr/bin/env bash
# Runs backup.sh against a throwaway stack with a local restic repo, restores it and checks the result.
set -euo pipefail
cd "$(dirname "$0")"

PORT=8081
BASE=http://127.0.0.1:$PORT
WORK=$(mktemp -d)
mkdir "$WORK/repo" "$WORK/restore"

# backup.sh and the compose commands below pick these up from the environment.
export COMPOSE_PROJECT_NAME=tp-backup
export COMPOSE_FILE=docker-compose.yml:$WORK/override.yml
export COMPOSE_ENV_FILES=$WORK/env
export ENV_FILE=$WORK/env
export BACKUP_ENV_FILE=$WORK/backup.env

sed -e 's/^APP_BIND_IP=.*/APP_BIND_IP=127.0.0.1/' \
    -e "s|^APP_BASE_URL=.*|APP_BASE_URL=$BASE|" \
    -e 's/^POSTGRES_PASSWORD=.*/POSTGRES_PASSWORD=backup-db-password/' \
    -e 's/^DB_PASSWORD=.*/DB_PASSWORD=backup-db-password/' \
    .env.example > "$ENV_FILE"
printf 'RESTIC_REPOSITORY=/repo\nRESTIC_PASSWORD=test-restic-password\n' > "$BACKUP_ENV_FILE"
cat > "$WORK/override.yml" <<YML
services:
  backup:
    volumes:
      - $WORK/repo:/repo
      - $WORK/restore:/restore
YML

dc() { docker compose "$@"; }
cleanup() {
  # Restored files are owned by the app uid; remove them as root inside the container.
  dc run --rm -T --no-deps --entrypoint sh backup -c 'rm -rf /repo/* /restore/*' >/dev/null 2>&1 || true
  dc --profile tools down -v --remove-orphans >/dev/null 2>&1 || true
  rm -rf "$WORK"
}
trap cleanup EXIT
fail() { echo "BACKUP FAIL: $*" >&2; exit 1; }

dc up -d

health=
for _ in $(seq 1 90); do
  health=$(curl -fs "$BASE/actuator/health" || true)
  [[ $health == *'"status":"UP"'* ]] && break
  sleep 2
done
[[ $health == *'"status":"UP"'* ]] || fail "app did not become healthy"

printf 'supersecret1\nsupersecret1\n' | dc run --rm -T app create-user backup@example.it >/dev/null \
  || fail "create-user failed"

JAR=$WORK/cookies
xsrf() { awk '$6 == "XSRF-TOKEN" { print $7 }' "$JAR"; }
curl -fs -c "$JAR" -o /dev/null "$BASE/api/auth/config"
curl -fs -b "$JAR" -c "$JAR" -o /dev/null -X POST "$BASE/api/auth/login" \
  -H "X-XSRF-TOKEN: $(xsrf)" -H 'Content-Type: application/json' \
  -d '{"email":"backup@example.it","password":"supersecret1"}' || fail "login failed"
trip=$(curl -fs -b "$JAR" -c "$JAR" -X POST "$BASE/api/trips" \
  -H "X-XSRF-TOKEN: $(xsrf)" -H 'Content-Type: application/json' \
  -d '{"name":"Backup trip","destinations":["Lisbon"],"startDate":"2026-12-01","endDate":"2026-12-05"}') \
  || fail "create trip failed"
trip_id=$(grep -o '"id":"[^"]*"' <<< "$trip" | head -1 | cut -d'"' -f4)

# A PDF-looking file with random content, so a byte-identical restore means something.
DOC=$WORK/ticket.pdf
{ printf '%%PDF-1.4\n'; head -c 100000 /dev/urandom; } > "$DOC"
upload=$(curl -s -b "$JAR" -w ' %{http_code}' -X POST "$BASE/api/trips/$trip_id/documents" \
  -H "X-XSRF-TOKEN: $(xsrf)" -F "file=@$DOC;type=application/pdf")
[[ $upload == *' 201' ]] || fail "document upload failed: $upload"
doc_id=$(grep -o '"id":"[^"]*"' <<< "$upload" | head -1 | cut -d'"' -f4)
want=$(sha256sum "$DOC" | cut -d' ' -f1)

./backup.sh || fail "backup.sh failed"
./backup-check.sh || fail "backup-check.sh failed"

snapshots=$(dc run --rm -T backup snapshots --json)
[[ $snapshots == *'"db"'* && $snapshots == *'"files"'* ]] || fail "missing db or files snapshot"

sql=$(dc run --rm -T backup dump latest --tag db /db.sql) || fail "restic dump failed"
[[ $sql == *'CREATE TABLE public.trip '* ]] || fail "db.sql has no CREATE TABLE public.trip"

dc run --rm -T backup restore latest --tag files --target /restore >/dev/null || fail "restic restore failed"
# Hash inside the container: restored files keep the app uid and mode 600.
got=$(dc run --rm -T --no-deps --entrypoint sh backup -c 'find /restore -type f -exec sha256sum {} +')
[[ $got == *"$want"* ]] || fail "uploaded document was not restored byte-identical"

# Full restore as documented in docs/operations.md: empty db, dump into it, files into a fresh volume.
dc --profile tools down -v >/dev/null 2>&1
dc up -d --wait db
# shellcheck disable=SC2016 # the variables expand inside the db container
dc run --rm -T backup dump latest --tag db /db.sql \
  | dc exec -T db sh -c 'psql -v ON_ERROR_STOP=1 -q -U "$POSTGRES_USER" -d "$POSTGRES_DB"' >/dev/null \
  || fail "restoring the dump failed"
dc run --rm -T --no-deps -v "${COMPOSE_PROJECT_NAME}_files:/restore-files" backup restore latest:data/files --tag files --target /restore-files >/dev/null \
  || fail "restoring the files volume failed"
dc up -d --wait app
curl -fs -c "$JAR" -o /dev/null "$BASE/api/auth/config"
curl -fs -b "$JAR" -c "$JAR" -o /dev/null -X POST "$BASE/api/auth/login" \
  -H "X-XSRF-TOKEN: $(xsrf)" -H 'Content-Type: application/json' \
  -d '{"email":"backup@example.it","password":"supersecret1"}' || fail "login after restore failed"
[ "$(curl -fs -b "$JAR" "$BASE/api/documents/$doc_id/file" | sha256sum | cut -d' ' -f1)" = "$want" ] \
  || fail "document served after a full restore differs from the original"

echo "BACKUP OK"
