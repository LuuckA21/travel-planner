# Deploy and update

Step-by-step checklist for putting Travel Planner on the server and keeping it up to date. Details and background for each step are in [operations.md](operations.md); the section numbers below point there.

Setup assumed: server B runs Debian with rootless Docker under the dedicated user `travel`; Nginx Proxy Manager (NPM) runs on server A and reaches server B over the internal network. Unless stated otherwise, run every command as `travel` from `~/travel-planner/deploy`.

## First deploy

### 1. Prepare server B (once, as root)

```bash
apt-get install -y uidmap dbus-user-session git ufw curl
adduser --disabled-password --gecos "" travel
loginctl enable-linger travel
ufw allow from <NPM-IP> to any port 8081 proto tcp
ufw allow OpenSSH && ufw enable
```

Then, as `travel`, install rootless Docker (`dockerd-rootless-setuptool.sh install`), run `systemctl --user enable --now docker`, and add `export DOCKER_HOST=unix://$XDG_RUNTIME_DIR/docker.sock` to `~/.bashrc`. Check it with `docker compose version`. See operations.md §1.

### 2. Get the code

```bash
git clone https://github.com/LuuckA21/travel-planner.git ~/travel-planner
cd ~/travel-planner/deploy
```

If the repository is private, clone it with a read-only deploy key (GitHub → repository Settings → Deploy keys) or a fine-grained token with read access.

### 3. Configure

```bash
cp .env.example .env && chmod 600 .env
$EDITOR .env
```

Fill in:

- `APP_BIND_IP`: server B's internal IP, the one NPM reaches.
- `APP_BASE_URL`: the public URL, for example `https://viaggi.example.it`.
- The SMTP settings: host, port, user, password, `MAIL_FROM`.
- A strong database password, set in **both** `POSTGRES_PASSWORD` and `DB_PASSWORD`. Use the same value in both, and write any `$` as `$$`.

See operations.md §2.

### 4. Start

```bash
docker compose up -d --build
docker compose ps                                       # app and db "running", db "healthy"
curl -s http://<APP_BIND_IP>:8081/actuator/health       # {"status":"UP",...}
```

The first build takes a few minutes, because it downloads Node, Maven and the dependencies.

### 5. Publish through NPM (on server A)

Add a Proxy Host:

- Domain: the host name from `APP_BASE_URL`.
- Forward to: `http`, `<APP_BIND_IP>`, port `8081`.
- SSL tab: Let's Encrypt certificate, Force SSL.
- Advanced tab: `client_max_body_size 26m;`

See operations.md §3. Then open `APP_BASE_URL` in the browser: the login page must appear.

### 6. Create your account

```bash
docker compose run --rm app create-user you@example.com   # asks for the password twice (10–128 characters)
```

Log in. On the iPhone, open the site in Safari and use Share → Add to Home Screen. The offline features work best from the installed app.

### 7. Backups (do not skip)

```bash
cp backup.env.example backup.env && chmod 600 backup.env
$EDITOR backup.env          # Swiss Backup S3 endpoint, bucket, keys; RESTIC_PASSWORD from `openssl rand -base64 32`
./backup.sh                 # first run creates the repository; must end with "BACKUP OK"
```

- Save `RESTIC_PASSWORD` in your password manager. Without it the backups cannot be read.
- Add the two cron lines and, ideally, a healthchecks.io monitor (operations.md §7).
- Test a restore once now, as a separate project (operations.md §8).

### 8. Reboot test (recommended)

Reboot server B once, then check that everything comes back by itself:

```bash
docker compose ps
curl -s http://<APP_BIND_IP>:8081/actuator/health
```

`loginctl enable-linger` starts the rootless Docker daemon without a login, and the containers have `restart: unless-stopped`.

## Updating after a new version

New versions arrive as commits on `main`.

```bash
cd ~/travel-planner/deploy
git log -1 --oneline > ~/last-deployed.txt     # remember what is running now
./backup.sh                                    # backup first: must end with "BACKUP OK"
git pull
docker compose up -d --build
docker compose ps
curl -s http://<APP_BIND_IP>:8081/actuator/health
```

- The `app` container is rebuilt and restarted. The database and the uploaded files are in volumes and are kept.
- Database migrations (Flyway) run by themselves when the app starts. If the app does not come up, check `docker compose logs --tail 100 app`.
- `.env` and `backup.env` are not in git, so `git pull` never touches them. If a new version needs a new setting, compare with `git diff HEAD@{1} -- .env.example backup.env.example` and add the new variables to your files.
- Installed apps on phones show "Nuova versione disponibile · Aggiorna" the next time they open. Tap it to load the new version.
- Free the disk space taken by old images from time to time: `docker image prune -f`.

### If an update goes wrong

Go back to the previous version:

```bash
git checkout "$(cut -d' ' -f1 ~/last-deployed.txt)"
docker compose up -d --build
```

This is enough when the new version added no database migration. If it did (new files under `backend/src/main/resources/db/migration/` in `git diff --stat <old>..HEAD`), the old code may not start against the migrated database. In that case, restore the backup you took before the update (operations.md §8) on top of the old version.

When the problem is fixed upstream, return to the latest version with `git checkout main && git pull && docker compose up -d --build`.

## Useful commands

| What | Command |
|---|---|
| Status | `docker compose ps` |
| App logs | `docker compose logs -f --tail 100 app` |
| Restart the app | `docker compose restart app` |
| Stop everything (data kept) | `docker compose down` |
| Open registration for a while | set `APP_REGISTRATION_OPEN=true` in `.env`, then `docker compose up -d`; set it back to `false` afterwards |
| List backups | `docker compose run --rm backup snapshots` |

Never run `docker compose down -v` on the server: `-v` deletes the database and the uploaded files.
