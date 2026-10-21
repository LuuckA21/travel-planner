# shellcheck shell=bash
# Sourced by backup.sh and backup-check.sh. Needs NAME and PING_VAR set by the caller.
# Gives every run a timestamped final "<NAME> OK" / "<NAME> FAILED" line and, if the ping URL is set in
# backup.env, a dead-man ping: the URL on success, URL/fail on failure (healthchecks.io style).
log() { echo "$(date -Is) $*"; }
ping_url=$(sed -n "s/^$PING_VAR=//p" "${BACKUP_ENV_FILE:-backup.env}" 2>/dev/null | tail -n 1) || true
ping_url=${ping_url%/}
ping() { [ -z "$ping_url" ] || curl -fsS -m 10 --retry 3 -o /dev/null "$ping_url$1" || true; }
finish() {
  local rc=$?
  if declare -F cleanup >/dev/null; then cleanup || true; fi
  if [ "$rc" = 0 ]; then log "$NAME OK"; ping ""; else log "$NAME FAILED (exit $rc)"; ping /fail; fi
}
trap finish EXIT
log "$NAME started"
