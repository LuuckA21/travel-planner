#!/usr/bin/env bash
# Tests update.sh against throwaway git repos with a fake `docker` (no containers needed).
set -euo pipefail
ROOT=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
mkdir "$T/bin"
cat > "$T/bin/docker" <<'EOF'
#!/usr/bin/env bash
echo "$*" >> "$CALLS"
[[ -n "${FAIL_BACKUP:-}" && "$*" == *"backup snapshots"* ]] && exit 1
[[ -n "${FAIL_UP:-}" && "$*" == *"up -d --build --wait"* ]] && exit 1
exit 0
EOF
chmod +x "$T/bin/docker"
export PATH="$T/bin:$PATH" CALLS="$T/calls"

git clone -q --bare "$ROOT" "$T/origin.git"
git -C "$T/origin.git" update-ref refs/heads/main "$(git -C "$ROOT" rev-parse HEAD)"  # test the committed tree
git -C "$T/origin.git" symbolic-ref HEAD refs/heads/main
publish() {  # add a commit to origin/main touching $1
  rm -rf "${T:?}/dev"; git clone -q "$T/origin.git" "$T/dev"
  mkdir -p "$(dirname "$T/dev/$1")"; echo x >> "$T/dev/$1"
  git -C "$T/dev" add -A && git -C "$T/dev" -c user.name=t -c user.email=t@t commit -qm "change $1"
  git -C "$T/dev" push -q origin HEAD:main
}
run() { rm -rf "${T:?}/srv"; git clone -q "$T/origin.git" "$T/srv"; : > "$CALLS"; }
fail() { echo "FAIL: $*"; cat "$T/out"; exit 1; }
up() { "$T/srv/deploy/update.sh" > "$T/out" 2>&1; }

# already up to date: no backup, no rebuild
run; up || fail "up to date should succeed"
if ! { grep -q "already up to date" "$T/out" && ! grep -q "backup" "$CALLS"; }; then fail "up to date ran a backup"; fi

# new version: backup before pull, rebuild with --wait, ends UPDATE OK
run; publish docs/x.md; up || fail "update should succeed"
if ! { grep -q "backup snapshots" "$CALLS" && grep -q "up -d --build --wait" "$CALLS" && grep -q "UPDATE OK" "$T/out"; }; then fail "update steps"; fi
[ "$(git -C "$T/srv" rev-parse HEAD)" = "$(git -C "$T/origin.git" rev-parse main)" ] || fail "not at origin/main"

# failed backup: stops before pull
run; old=$(git -C "$T/srv" rev-parse HEAD); publish docs/y.md
if FAIL_BACKUP=1 up; then fail "backup failure should fail"; fi
if ! { [ "$(git -C "$T/srv" rev-parse HEAD)" = "$old" ] && ! grep -q "up -d" "$CALLS"; }; then fail "pulled or rebuilt after a failed backup"; fi

# unhealthy new version with a migration: fails and warns that a restore is needed
run; publish backend/src/main/resources/db/migration/V99__x.sql
if FAIL_UP=1 up; then fail "unhealthy should fail"; fi
if ! { grep -q "ran database migrations" "$T/out" && grep -q "UPDATE FAILED" "$T/out"; }; then fail "migration warning"; fi

# unhealthy without migration: prints the way back
run; old=$(git -C "$T/srv" rev-parse HEAD); publish docs/z.md
if FAIL_UP=1 up; then fail "unhealthy should fail"; fi
grep -q "to go back: git checkout $old" "$T/out" || fail "rollback hint"

# not on main: refuses before doing anything
run; git -C "$T/srv" checkout -q --detach
if up; then fail "detached should fail"; fi
if ! { grep -q "not on branch main" "$T/out" && [ ! -s "$CALLS" ]; }; then fail "detached ran commands"; fi

echo "UPDATE TEST OK"
