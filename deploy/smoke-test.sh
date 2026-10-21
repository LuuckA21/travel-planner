#!/usr/bin/env bash
# Builds the production image, boots it with a throwaway database and exercises it over HTTP.
set -euo pipefail
cd "$(dirname "$0")"

PROJECT=tp-smoke
PORT=8081
BASE=http://127.0.0.1:$PORT
WORK=$(mktemp -d)
export ENV_FILE=$WORK/env

sed -e 's/^APP_BIND_IP=.*/APP_BIND_IP=127.0.0.1/' \
    -e "s|^APP_BASE_URL=.*|APP_BASE_URL=$BASE|" \
    -e 's/^POSTGRES_PASSWORD=.*/POSTGRES_PASSWORD=smoke-db-password/' \
    -e 's/^DB_PASSWORD=.*/DB_PASSWORD=smoke-db-password/' \
    .env.example > "$ENV_FILE"

dc() { docker compose -p "$PROJECT" --env-file "$ENV_FILE" "$@"; }
cleanup() { dc down -v --remove-orphans >/dev/null 2>&1 || true; rm -rf "$WORK"; }
trap cleanup EXIT
fail() { echo "SMOKE FAIL: $*" >&2; dc logs --tail 60 app >&2 || true; exit 1; }

dc up -d --build

health=
for _ in $(seq 1 90); do
  health=$(curl -fs "$BASE/actuator/health" || true)
  [[ $health == *'"status":"UP"'* ]] && break
  sleep 2
done
[[ $health == *'"status":"UP"'* ]] || fail "app did not become healthy"

[ "$(dc run --rm -T --no-deps --entrypoint id app -u)" = 10001 ] || fail "app does not run as uid 10001"

printf 'supersecret1\nsupersecret1\n' | dc run --rm -T app create-user smoke@example.it >/dev/null \
  || fail "create-user failed"

JAR=$WORK/cookies
xsrf() { awk '$6 == "XSRF-TOKEN" { print $7 }' "$JAR"; }
curl -fs -c "$JAR" -o /dev/null "$BASE/api/auth/config"
[ -n "$(xsrf)" ] || fail "no XSRF-TOKEN cookie"

post() { # path json [extra curl args]
  curl -s -b "$JAR" -c "$JAR" -o /dev/null -w '%{http_code}' -X POST "$BASE$1" \
    -H "X-XSRF-TOKEN: $(xsrf)" -H 'Content-Type: application/json' -d "$2" "${@:3}"
}

[ "$(post /api/auth/login '{"email":"smoke@example.it","password":"supersecret1"}')" = 200 ] || fail "login failed"
index=$(curl -fs "$BASE/") || fail "GET / failed"
shopt -s nocasematch
[[ $index == *'<!doctype html'* ]] || fail "GET / is not HTML"
shopt -u nocasematch
[ "$(curl -fs -b "$JAR" "$BASE/api/trips")" = '[]' ] || fail "GET /api/trips is not []"

# Rate limiting keys on the rightmost X-Forwarded-For entry (the hop NPM appends), not on a spoofed first one.
for i in 1 2 3 4 5 6; do
  code=$(post /api/auth/forgot-password '{"email":"nobody@example.it"}' -H "X-Forwarded-For: 198.51.100.$i, 203.0.113.9")
  if [ "$i" -lt 6 ] && [ "$code" = 429 ]; then fail "request $i was rate limited too early"; fi
done
[ "$code" = 429 ] || fail "6th forgot-password request returned $code, expected 429"

echo "SMOKE OK"
