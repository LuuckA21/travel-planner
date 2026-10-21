# Operations guide

Practical notes for running Travel Planner on server B (Debian, rootless Docker, behind Nginx Proxy Manager on server A). Everything below is run as the dedicated user, from `<APP_DIR>/deploy`, unless stated otherwise. `<APP_DIR>` is where you cloned the repository, for example `/srv/apps/travel-planner` or `~/travel-planner`. Use the full path in crontab.

## 1. Server prerequisites

As root, once:

```bash
apt-get install -y uidmap dbus-user-session git ufw curl
adduser --disabled-password --gecos "" travel     # the dedicated user
loginctl enable-linger travel                     # keep the user's daemon running without a login
```

As `travel`, install rootless Docker (see the Docker docs: `dockerd-rootless-setuptool.sh install`), enable it with `systemctl --user enable --now docker`, and put `export DOCKER_HOST=unix://$XDG_RUNTIME_DIR/docker.sock` in `~/.bashrc`. Verify with `docker compose version`. Cron jobs also need `DOCKER_HOST`; the crontab lines in section 7 set it.

Firewall (as root). Replace `<NPM-IP>` with server A's address:

```bash
ufw allow from <NPM-IP> to any port <APP_PORT> proto tcp   # APP_PORT from deploy/.env, default 8081
ufw allow OpenSSH && ufw enable
```

The app trusts `X-Forwarded-For` and `X-Forwarded-Proto` because only NPM can reach the app port (this ufw rule). Rate limiting keys on the rightmost `X-Forwarded-For` entry, i.e. the address NPM appended, so NPM's default `$proxy_add_x_forwarded_for` is enough: no custom header directives are needed in NPM.

## 2. First deploy

```bash
git clone <repo-url> <APP_DIR> && cd <APP_DIR>/deploy
cp .env.example .env && chmod 600 .env
$EDITOR .env
```

Set at least `APP_BIND_IP` (server B's internal IP, the one NPM reaches), `APP_PORT` if 8081 is already taken on the server, `APP_BASE_URL` (the public https URL), the SMTP settings, and a strong database password.

- `POSTGRES_USER`/`POSTGRES_PASSWORD` must equal `DB_USER`/`DB_PASSWORD`. Set them before the first start: the database is created with them once.
- Use `deploy/.env`: it is the default and compose reads it for both the app and the database. If you keep the settings in a different file, pass it on every compose command with `--env-file`, e.g. `docker compose --env-file /path/to/prod.env up -d`; otherwise the db container starts with blank `POSTGRES_*`. Also export `ENV_FILE=/path/to/prod.env` so the app reads the same file.
- A `$` inside a value must be written `$$`.

```bash
docker compose up -d --build
curl -s http://<internal-IP>:<APP_PORT>/actuator/health      # {"status":"UP",...}
```

Flyway applies the migrations at startup. Only `/actuator/health` is exposed.

## 3. Nginx Proxy Manager

On server A, add a Proxy Host:

- Domain: the public name from `APP_BASE_URL`; scheme `http`; forward host/port `<internal-IP>` / `<APP_PORT>` (default `8081`).
- Websockets support: off. Block common exploits: on.
- SSL tab: request a Let's Encrypt certificate, enable Force SSL.
- Advanced tab: `client_max_body_size 26m;` (the app's 25 MB per-file upload limit plus multipart overhead).

## 4. Users

Registration is closed by default (`APP_REGISTRATION_OPEN=false`). Create the first user from the command line (it prompts for the password twice):

```bash
docker compose run --rm app create-user you@example.com
```

To open registration for a while, set `APP_REGISTRATION_OPEN=true` in `.env` and run `docker compose up -d`; set it back to `false` and run `docker compose up -d` again when done.

## 5. Updating

```bash
<APP_DIR>/deploy/update.sh      # backup, pull, rebuild, wait until healthy
```

What it does and how to go back: [deploy.md](deploy.md).

## 6. Backups

Backups use restic, encrypted client-side, stored on Infomaniak Swiss Backup over S3. Each run saves a `pg_dump` of the database (snapshot tag `db`, file `db.sql`) and the `files` volume (tag `files`), then keeps 14 daily, 8 weekly and 6 monthly snapshots. The `backup` service in `docker-compose.yml` runs restic in a container (as root, so it can read the uploaded files, which are mode 600 and owned by uid 10001, without any host UID mapping).

Create the Swiss Backup S3 bucket and access keys in the Infomaniak panel, then:

```bash
cp backup.env.example backup.env && chmod 600 backup.env
$EDITOR backup.env        # endpoint, bucket, keys, region, and a long random RESTIC_PASSWORD (openssl rand -base64 32)
./backup.sh               # the first run creates the repository
./backup-check.sh
```

As in `.env`, a `$` in any value of `backup.env` (password, S3 secret) must be written `$$`.

Keep the `RESTIC_PASSWORD` in a password manager as well. Without it the backups cannot be restored, and nobody can recover it.

Useful commands:

```bash
docker compose run --rm backup snapshots
docker compose run --rm backup stats
```

## 7. Cron and monitoring

`crontab -e` as the dedicated user. Cron has a minimal environment, so set `PATH` and the rootless `DOCKER_HOST` at the top (`id -u` gives `<uid>`; cron does not expand `$(...)`):

```cron
PATH=/usr/local/bin:/usr/bin:/bin
DOCKER_HOST=unix:///run/user/<uid>/docker.sock
15 3 * * * cd <APP_DIR>/deploy && ./backup.sh >> "$HOME/backup.log" 2>&1
45 4 * * 0 cd <APP_DIR>/deploy && ./backup-check.sh >> "$HOME/backup.log" 2>&1
```

Each run writes timestamped lines and ends with `BACKUP OK` or `BACKUP FAILED (exit N)` (`BACKUP CHECK OK` / `BACKUP CHECK FAILED` for the weekly check), and exits non-zero on failure. Because the output is redirected to the log, cron will not mail anything: a failure is silent unless you monitor it.

Recommended: a dead-man monitor such as healthchecks.io (free tier). Create two checks (daily with a grace time of a few hours, weekly), then put their ping URLs in `backup.env` (no quotes):

```
BACKUP_PING_URL=https://hc-ping.com/<uuid-daily>
BACKUP_CHECK_PING_URL=https://hc-ping.com/<uuid-weekly>
```

On success the script requests the URL, on failure `<URL>/fail`. If the server is down, cron does not run, or the scripts never start, the missing ping raises the alert. Without a monitor, check `tail ~/backup.log` regularly.

## 8. Restore

**Warning: these commands are for an empty database and an empty files volume (a new server, or a separate test project). Never run them against a live installation: the dump would be applied on top of existing data and the files restore overwrites stored documents.**

Test the restore once right after the first deploy, not on the day you need it. On the production server, do it as a separate compose project so nothing touches the live volumes, and start only the database (the app's port is taken by the live app):

```bash
export COMPOSE_PROJECT_NAME=tp-restore-test      # separate containers and volumes; unset it afterwards
```

then follow steps 1 and 2 below (not step 3). Check the result with `docker compose exec db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select count(*) from trip"'` and `docker compose run --rm --no-deps -v "$FILES_VOL:/restore-files" --entrypoint ls backup -l /restore-files`, then remove the test with `docker compose --profile tools down -v` and `unset COMPOSE_PROJECT_NAME`. `deploy/backup-test.sh` runs the same steps against a throwaway stack.

On a new server: do sections 1 and 2 up to and including `.env` and `backup.env` (same `POSTGRES_*` values as the original, `RESTIC_PASSWORD` from the password manager), but do not start the app yet.

```bash
cd <APP_DIR>/deploy
docker compose run --rm backup snapshots                       # check the repository is readable

# 1. database: into the empty database, before the app has run its migrations
docker compose up -d --wait db
docker compose run --rm -T backup dump latest --tag db /db.sql \
  | docker compose exec -T db sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'

# 2. files: into the (empty) files volume. Create it through compose first so it carries compose's labels
docker compose --profile tools create backup
FILES_VOL=$(docker volume ls -q --filter label=com.docker.compose.volume=files \
  --filter label=com.docker.compose.project="${COMPOSE_PROJECT_NAME:-$(basename "$PWD")}")
echo "$FILES_VOL"                                              # must print exactly one name; else check `docker volume ls`
docker compose run --rm --no-deps -v "$FILES_VOL:/restore-files" \
  backup restore latest:data/files --tag files --target /restore-files

# 3. start everything
docker compose up -d --build
```

To restore an older state use a snapshot id from `snapshots` instead of `latest`. To inspect files without touching any volume, restore into a host directory instead (`-v "$PWD/restore:/restore" ... --target /restore`); restored files keep owner uid 10001 and mode 600.
