# Operations guide

Practical notes for running Travel Planner on server B (Debian, rootless Docker, behind Nginx Proxy Manager on server A). Everything below is run as the dedicated user, from `~/travel-planner/deploy`, unless stated otherwise.

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
ufw allow from <NPM-IP> to any port 8081 proto tcp
ufw allow OpenSSH && ufw enable
```

The app trusts `X-Forwarded-For` and `X-Forwarded-Proto` because only NPM can reach port 8081 (this ufw rule). Rate limiting keys on the rightmost `X-Forwarded-For` entry, i.e. the address NPM appended, so NPM's default `$proxy_add_x_forwarded_for` is enough: no custom header directives are needed in NPM.

## 2. First deploy

```bash
git clone <repo-url> ~/travel-planner && cd ~/travel-planner/deploy
cp .env.example .env && chmod 600 .env
$EDITOR .env
```

Set at least `APP_BIND_IP` (server B's internal IP, the one NPM reaches), `APP_BASE_URL` (the public https URL), the SMTP settings, and a strong database password.

- `POSTGRES_USER`/`POSTGRES_PASSWORD` must equal `DB_USER`/`DB_PASSWORD`. Set them before the first start: the database is created with them once.
- Use `deploy/.env`: it is the default and compose reads it for both the app and the database. If you keep the settings in a different file, pass it on every compose command with `--env-file`, e.g. `docker compose --env-file /path/to/prod.env up -d`; otherwise the db container starts with blank `POSTGRES_*`. Also export `ENV_FILE=/path/to/prod.env` so the app reads the same file.
- A `$` inside a value must be written `$$`.

```bash
docker compose up -d --build
curl -s http://<internal-IP>:8081/actuator/health      # {"status":"UP",...}
```

Flyway applies the migrations at startup. Only `/actuator/health` is exposed.

## 3. Nginx Proxy Manager

On server A, add a Proxy Host:

- Domain: the public name from `APP_BASE_URL`; scheme `http`; forward host/port `<internal-IP>` / `8081`.
- Websockets support: off. Block common exploits: on.
- SSL tab: request a Let's Encrypt certificate, enable Force SSL.
- Advanced tab: `client_max_body_size 25m;` (the app's per-file upload limit).

## 4. Users

Registration is closed by default (`APP_REGISTRATION_OPEN=false`). Create the first user from the command line (it prompts for the password twice):

```bash
docker compose run --rm app create-user you@example.com
```

To open registration for a while, set `APP_REGISTRATION_OPEN=true` in `.env` and run `docker compose up -d`; set it back to `false` and run `docker compose up -d` again when done.

## 5. Updating

```bash
cd ~/travel-planner/deploy
git pull && docker compose up -d --build
```

Take a backup first (`./backup.sh`) when the update includes database migrations.

## 6. Backups

Backups use restic, encrypted client-side, stored on Infomaniak Swiss Backup over S3. Each run saves a `pg_dump` of the database (snapshot tag `db`, file `db.sql`) and the `files` volume (tag `files`), then keeps 14 daily, 8 weekly and 6 monthly snapshots. The `backup` service in `docker-compose.yml` runs restic in a container (as root, so it can read the uploaded files, which are mode 600 and owned by uid 10001, without any host UID mapping).

Create the Swiss Backup S3 bucket and access keys in the Infomaniak panel, then:

```bash
cp backup.env.example backup.env && chmod 600 backup.env
$EDITOR backup.env        # endpoint, bucket, keys, region, and a long random RESTIC_PASSWORD
./backup.sh               # the first run creates the repository
./backup-check.sh
```

Keep the `RESTIC_PASSWORD` in a password manager as well. Without it the backups cannot be restored, and nobody can recover it.

Useful commands:

```bash
docker compose run --rm backup snapshots
docker compose run --rm backup stats
```

## 7. Cron

`crontab -e` as the dedicated user:

```cron
DOCKER_HOST=unix:///run/user/<uid>/docker.sock
15 3 * * * cd ~/travel-planner/deploy && ./backup.sh >> ~/backup.log 2>&1
45 4 * * 0 cd ~/travel-planner/deploy && ./backup-check.sh >> ~/backup.log 2>&1
```

(`id -u travel` gives `<uid>`.) Each script exits non-zero if any step fails; look at `~/backup.log` now and then, or set `MAILTO=` if the server can send mail.

## 8. Restore

Test this once right after the first deploy (on a scratch machine or by restoring next to the live data), not on the day you need it. `deploy/backup-test.sh` exercises the same steps against a throwaway stack.

On a new server: do sections 1 and 2 up to and including `.env` and `backup.env` (same `POSTGRES_*` values as the original, `RESTIC_PASSWORD` from the password manager), but do not start the app yet.

```bash
cd ~/travel-planner/deploy
docker compose run --rm backup snapshots                       # check the repository is readable

# 1. database: into the empty database, before the app has run its migrations
docker compose up -d --wait db
docker compose run --rm -T backup dump latest --tag db /db.sql \
  | docker compose exec -T db sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'

# 2. files: into the files volume (named <project>_files, the project is the directory name, "deploy")
docker compose run --rm --no-deps -v deploy_files:/restore-files \
  backup restore latest:data/files --tag files --target /restore-files

# 3. start everything
docker compose up -d --build
```

To restore an older state use a snapshot id from `snapshots` instead of `latest`.

The database must be empty when the dump is loaded, which it is on a fresh server because the app has not started yet. To inspect files without touching the volume, restore into a host directory instead (`-v "$PWD/restore:/restore" ... --target /restore`); restored files keep owner uid 10001 and mode 600.
