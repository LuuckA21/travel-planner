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
- Schedule the backups with cron: see [Scheduling backups with cron](#scheduling-backups-with-cron) below.
- Test a restore once now, as a separate project (operations.md §8).

### 8. Reboot test (recommended)

Reboot server B once, then check that everything comes back by itself:

```bash
docker compose ps
curl -s http://<APP_BIND_IP>:8081/actuator/health
```

`loginctl enable-linger` starts the rootless Docker daemon without a login, and the containers have `restart: unless-stopped`.

## Scheduling backups with cron

Run all of this as `travel`.

1. **Check that the backup works by hand first.** `./backup.sh` must end with `BACKUP OK` (step 7).

2. **Find two values for the crontab.**

   ```bash
   id -u                       # your uid, e.g. 1001
   command -v docker           # e.g. /usr/bin/docker; if it is in ~/bin, add that folder to PATH below
   ```

3. **Simulate cron's minimal environment.** Cron starts with almost no environment variables, so this catches PATH or `DOCKER_HOST` problems before the first night. Replace `<uid>`, then run:

   ```bash
   env -i HOME="$HOME" PATH=/usr/local/bin:/usr/bin:/bin \
     DOCKER_HOST=unix:///run/user/<uid>/docker.sock \
     sh -c 'cd "$HOME/travel-planner/deploy" && ./backup-check.sh'
   ```

   It must end with `BACKUP CHECK OK`.

4. **Add the jobs.** Run `crontab -e` (choose an editor the first time) and add these lines, replacing `<uid>`:

   ```cron
   PATH=/usr/local/bin:/usr/bin:/bin
   DOCKER_HOST=unix:///run/user/<uid>/docker.sock
   # every night at 03:15: database + files
   15 3 * * * cd "$HOME/travel-planner/deploy" && ./backup.sh >> "$HOME/backup.log" 2>&1
   # every Sunday at 04:45: integrity check of the backup repository
   45 4 * * 0 cd "$HOME/travel-planner/deploy" && ./backup-check.sh >> "$HOME/backup.log" 2>&1
   ```

   The times use the server's time zone (`timedatectl` shows it). Save, then check with `crontab -l`.

5. **Check the next morning.**

   ```bash
   tail -n 20 ~/backup.log                        # the last run must end with "BACKUP OK"
   docker compose run --rm backup snapshots       # a new "db" and a new "files" snapshot
   ```

6. **Get an alert when a backup fails (recommended).** Cron writes everything to the log and sends no email, so a broken job would go unnoticed.
   - On healthchecks.io (free), create two checks:
     - daily, with a grace time of a few hours;
     - weekly.
   - Put their ping URLs in `backup.env`, without quotes:

     ```
     BACKUP_PING_URL=https://hc-ping.com/<uuid-daily>
     BACKUP_CHECK_PING_URL=https://hc-ping.com/<uuid-weekly>
     ```

   The scripts ping the URL on success and `<URL>/fail` on failure. If no ping arrives at all, healthchecks.io alerts you too, for example when the server is down. See operations.md §7.

## Updating after a new version

New versions arrive as commits on `main`.

**Before updating, check that CI is green** on the latest `main` commit (GitHub → Actions, workflow "CI"). Update only when all four jobs pass:

| Job | What it checks |
|---|---|
| `backend` | `./mvnw verify`: all backend tests against a real PostgreSQL |
| `frontend` | lint, typecheck, unit tests and production build of the PWA |
| `deploy-scripts` | `shellcheck` on every script in `deploy/`, and `update-test.sh`, which exercises `update.sh` (up to date, success, failed backup, unhealthy version with and without migrations, not on `main`) |
| `e2e` | builds the Docker image, starts the stack with `smoke-test.sh`, and runs the Playwright test, offline part included |

A red `deploy-scripts` means `update.sh` itself may misbehave: do not run it until that job is fixed. You can also run the script test on any machine with git and bash: `bash deploy/update-test.sh`. It needs no Docker, and it ends with `UPDATE TEST OK`.

Then run:

```bash
~/travel-planner/deploy/update.sh
```

The script runs these steps in order and stops at the first one that fails:

1. Checks that the checkout is on `main`, then fetches. If nothing is new, it says so and stops.
2. Runs `./backup.sh`. A failed backup stops the update before anything changes.
3. Runs `git pull` (fast-forward only). It also warns if `.env.example` or `backup.env.example` changed, so you can add new variables to your `.env` / `backup.env`.
4. Runs `docker compose up -d --build --wait`, which waits until the app and the database report healthy.
5. Removes the dangling images left by previous builds.

The last line is `UPDATE OK` or `UPDATE FAILED (exit N)`.

- The database and the uploaded files are in volumes and are kept. Database migrations (Flyway) run by themselves when the app starts.
- Installed apps on phones show "Nuova versione disponibile · Aggiorna" the next time they open. Tap it to load the new version.
- To be alerted when an update fails (optional), add `UPDATE_PING_URL=https://hc-ping.com/<uuid>` to `backup.env`.

### If an update goes wrong

The script never rolls back by itself. When the new version does not become healthy, it prints:

- where to look: `docker compose logs --tail 100 app`;
- how to go back. With no database migration in the update, it prints the exact command, for example `git checkout <old> && docker compose up -d --build --wait`. With a migration, it warns that the old code may not start against the migrated database. In that case, restore the backup the script took just before the update (operations.md §8) on top of the old version.

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
