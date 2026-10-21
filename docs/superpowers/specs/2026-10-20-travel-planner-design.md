# Travel Planner — Design Spec

Date: 2026-10-20
Status: draft, awaiting review

## 1. Purpose

A personal web app to organize a trip and keep itinerary, bookings, documents
and checklists in one place, used mostly from a phone — including during the
trip, often with poor or no connectivity.

It starts as a personal app (one owner, possibly a few close users) but is
built so it can later be published to the public without a rewrite: real
per-user data isolation enforced on the server, open registration behind a
switch, and a standard stack.

**Success criterion:** during a trip you open the app on your phone, it opens
on today's day, and you find the day's activities, bookings and the attached
tickets — even offline.

**Example:** create "Guatemala e Belize", add daily stops, attach the tickets,
prepare the packing list. During the trip, open the current day and find
activities and bookings.

### Conventions

- Code, API, identifiers, comments and documentation: **English**.
- User interface: **Italian**. All UI strings live in one messages module
  (`frontend/src/i18n/it.ts`) so a second language can be added later; an
  i18n library (e.g. i18next) replaces it only when that happens.

## 2. MVP scope

1. **Trips** — create/edit name, destinations, dates, cover image. Status
   (upcoming / in progress / past) is derived from dates. Lists of upcoming and
   past trips.
2. **Itinerary** — per-day view of activities with optional time, place and
   notes; Google Maps link; add, edit, delete, reorder.
3. **Bookings** — flights, accommodation, transport, with dates, booking
   reference and notes, attached to a trip.
4. **Documents** — upload PDF and images, attach to a trip or to a single
   booking; view, download, delete.
5. **Checklists** — packing and pre-departure lists; add, edit, delete items;
   check items; completion indicator.
6. **Access** — personal accounts with authentication; each user accesses only
   their own trips and files.
7. **Offline viewing** — trips, itinerary, bookings, checklists and cached
   documents are readable offline. Editing requires a connection.

### Out of scope for the MVP

Sharing/collaboration between users, offline editing and sync, payments,
admin panel, reusable checklist templates, embedded maps and coordinates,
type-specific booking fields (e.g. separate flight number field), drag-and-drop
reordering, dark theme, push notifications, monitoring/alerting, image
registry and staging environments.

**Required before a public launch (not MVP):** account deletion with all data
(GDPR), privacy page, periodic cleanup of orphaned files.

## 3. Architecture

### Stack

| Concern | Choice |
|---|---|
| Frontend | React + TypeScript + Vite, single-page app |
| PWA | `vite-plugin-pwa` (Workbox service worker, web manifest) |
| UI components | Mantine; native `<input type="date/time">` for pickers |
| Data fetching / offline cache | TanStack Query, cache persisted to IndexedDB |
| Backend | Spring Boot (current stable release, pinned in the plan), Java LTS, Maven |
| HTTP API | Spring Web, JSON, errors as RFC 9457 Problem Details |
| Auth | Spring Security, server-side sessions via Spring Session JDBC |
| Persistence | PostgreSQL, Spring Data JPA, Flyway migrations |
| Validation | Bean Validation |
| Email | Spring Mail over external SMTP (e.g. Brevo, Resend) |
| Rate limiting | In-memory token buckets (Bucket4j) |
| Tests | JUnit 5, Spring Boot Test, Testcontainers; Vitest + Testing Library; Playwright |
| CI | GitHub Actions |
| Backups | restic to Infomaniak Swiss Backup (S3) |

### Repository layout

```
frontend/   React PWA
backend/    Spring Boot application
deploy/     docker-compose.yml, .env.example, backup scripts
docs/       specs, plans, operations notes
Dockerfile  multi-stage: build frontend, build backend, single runtime image
```

### Runtime topology

```
Internet ──HTTPS──> Nginx Proxy Manager (server A)
                         │  HTTP over internal network
                         ▼
                   server B (Debian, rootless Docker, dedicated user, ufw)
                   ├── app  (Spring Boot: /api + static PWA)  <internal-IP>:8081 -> 8080
                   ├── db   (PostgreSQL, internal Docker network only)
                   └── volumes: db-data, files
```

- **One application image.** Spring Boot serves both the API (`/api/**`) and
  the built PWA. Unknown non-API paths fall back to `index.html` so client
  routes (e.g. `/trips/42`) work on reload. `sw.js`, `index.html` and the
  manifest are served with `Cache-Control: no-cache`; hashed assets are
  cached long-term.
- **Same origin** for PWA and API: no CORS configuration.
- **Port exposure.** The app publishes its port only on server B's internal
  network IP. ufw on server B allows that port only from the NPM server's IP.
  With rootless Docker, published ports are ordinary user-space listeners, so
  ufw rules apply to them.
- **Database** has no published ports.
- **Forwarded headers.** Spring trusts `X-Forwarded-For` / `X-Forwarded-Proto`
  (`server.forward-headers-strategy=framework`) so that rate limiting sees the
  real client IP and cookies are marked `Secure`. This is safe because only
  NPM can reach the port.
- **Upload limits** are 25 MB per file, set in Spring
  (`spring.servlet.multipart.max-file-size`) and, if needed, in NPM's proxy
  host advanced config (`client_max_body_size 25m;`).
- The app runs as a **non-root user inside the container**.

### Backend structure

Packages by feature, each with controller → service → repository:
`auth`, `trip`, `itinerary`, `booking`, `document`, `checklist`, plus `common`
(error handling, current-user access, storage).

**Ownership rule:** only `Trip` has an owner. Every service method that touches
a trip or a child resource first loads the trip with
`findByIdAndOwnerId(tripId, currentUserId)` (for child resources, by joining
through the trip). A miss returns 404. This check lives in one place per
feature, in the service layer, and is covered by a dedicated test suite
(§8).

## 4. Data model

All primary keys are UUIDs. Timestamps `created_at` / `updated_at` on every
table.

```
user ──< trip ──< activity
             ├──< booking ──< document (optional link)
             ├──< document
             └──< checklist ──< checklist_item
```

### user_account
`id`, `email` (unique, stored lowercased), `password_hash` (PBKDF2 as the
default of a `DelegatingPasswordEncoder`; bcrypt would reject passwords over
72 bytes), `display_name`,
`email_verified_at` (nullable).

### auth_token
One-time tokens for email verification and password reset.
`id`, `user_id`, `purpose` (`VERIFY_EMAIL` | `RESET_PASSWORD`), `token_hash`
(SHA-256 of the random token; the raw token exists only in the email link),
`expires_at`, `used_at` (nullable).

### Sessions
Spring Session JDBC tables, created by Flyway.

### trip
`id`, `owner_id`, `name`, `start_date`, `end_date` (both required,
`end_date >= start_date`, at most 365 days long), `cover_storage_key`
(nullable).

`trip_destination` (element collection): `trip_id`, `position`, `name` —
an ordered list of destination names.

**Status is not stored.** The frontend derives it from the device's local
date: *upcoming* (`today < start_date`), *in progress*
(`start_date <= today <= end_date`), *past* (`today > end_date`). It uses the
device date because "today" depends on where the traveller is.

### activity
`id`, `trip_id`, `date`, `position` (order within the day), `title`,
`start_time` (nullable, local time), `location` (nullable text),
`maps_url` (nullable), `notes` (nullable).

- Days are not a table; they are derived from the trip's dates.
- Within a day, activities are ordered by `position` only; the time is shown
  but does not reorder.
- Maps link: `maps_url` if set, otherwise
  `https://www.google.com/maps/search/?api=1&query=<url-encoded location>`;
  no link when both are empty.
- On create/update, `date` must be within the trip's dates. If the trip's
  dates later change, activities left outside are kept and shown in an
  "outside the trip dates" group.

### booking
`id`, `trip_id`, `type` (`FLIGHT` | `ACCOMMODATION` | `TRANSPORT`), `title`,
`provider` (nullable), `reference` (nullable), `start_at` (required),
`end_at` (nullable, `>= start_at`), `origin` (nullable), `location`
(nullable; destination or address), `notes` (nullable).

- **Times are local, without a time zone** (`timestamp without time zone`),
  stored and displayed exactly as written on the ticket. A Rome 10:00 →
  Guatemala City 15:40 flight shows those two times; no conversion.
- A booking appears in the itinerary on the day of `start_at` and, if
  different, on the day of `end_at` (e.g. check-in and check-out).

### document
`id`, `trip_id` (required), `booking_id` (nullable; must belong to the same
trip), `original_filename`, `content_type`, `size_bytes`, `storage_key`
(random UUID; the original name is never used on disk).

### checklist / checklist_item
`checklist`: `id`, `trip_id`, `name`, `position`.
`checklist_item`: `id`, `checklist_id`, `text`, `done`, `position`.
Creating a trip creates two empty checklists, "Bagaglio" and "Prima di
partire". Completion (e.g. 7/12) is computed.

### Deletion rules
- Deleting a **trip** deletes all its children and their files on disk.
- Deleting a **booking** keeps its documents in the trip
  (`booking_id` set to null).
- Deleting a **checklist** deletes its items.

### File storage
Files live in the `files` volume, outside any publicly served path, at
`<root>/<storage_key>`. Upload writes the file first, then the database row;
if the row fails, the file is deleted. Deletion removes the row, then the
file.

## 5. Authentication and security

### Registration
- `app.registration-open` (default `false`). While closed,
  `POST /api/auth/register` returns 403 and the UI hides the sign-up link.
- First account: `docker compose run --rm -it app create-user <email>` — the
  application started with the `create-user` argument prompts for the
  password, creates a verified user and exits.
- Password: minimum 10 characters, maximum 128, no composition rules.
- Email verification is required before the first login; link valid 24 h,
  single use. In the `dev` profile emails are logged to the console instead
  of sent; production requires SMTP settings.

### Login and sessions
- `POST /api/auth/login` (JSON) creates a server session; cookie `HttpOnly`,
  `Secure`, `SameSite=Lax`. Session id is rotated on login (Spring default).
- Session idle timeout: **30 days**.
- `POST /api/auth/logout` invalidates the session; the frontend clears all
  offline caches (§7).
- `GET /api/auth/me` returns the current user or 401.

### Password reset
- `POST /api/auth/forgot-password` always returns the same response whether
  or not the email exists. Link valid 1 h, single use.
- `POST /api/auth/reset-password` sets the new password and invalidates all
  of the user's sessions.

### CSRF
Spring Security's SPA setup: `CookieCsrfTokenRepository` with a readable
`XSRF-TOKEN` cookie; the frontend sends it back in `X-XSRF-TOKEN` on every
state-changing request.

### Rate limiting
In-memory token buckets keyed by client IP and by email (single instance, so
in-memory is enough):
- login: 10 attempts per 15 min per IP and per email;
- register, forgot-password, resend verification: 5 per hour per IP.

Exceeding a limit returns 429.

### File safety
- Allowed types: PDF, JPEG, PNG, WebP, detected from the file's leading
  bytes; the declared content type and extension are not trusted. Covers
  allow only the image types.
- Max 25 MB per file. Images are compressed in the browser before upload.
- Downloads are served with the stored, verified content type,
  `X-Content-Type-Options: nosniff`, and `Content-Disposition: inline` (view)
  or `attachment` (download, `?download=true`).

### Security headers
A Content Security Policy restricted to self, plus `Referrer-Policy` and
frame denial, set by Spring Security.

## 6. HTTP API

All endpoints under `/api`, JSON unless noted. All endpoints except the
public auth ones require a session.

| Area | Endpoints |
|---|---|
| Auth | `POST auth/register`, `POST auth/login`, `POST auth/logout`, `GET auth/me`, `POST auth/verify-email`, `POST auth/resend-verification`, `POST auth/forgot-password`, `POST auth/reset-password` |
| Trips | `GET trips` (summaries), `POST trips`, `GET trips/{id}` (aggregate), `PUT trips/{id}`, `DELETE trips/{id}`, `PUT trips/{id}/cover` (multipart), `DELETE trips/{id}/cover`, `GET trips/{id}/cover` |
| Activities | `POST trips/{id}/activities`, `PUT activities/{id}`, `DELETE activities/{id}`, `PUT trips/{id}/activities/order` |
| Bookings | `POST trips/{id}/bookings`, `PUT bookings/{id}`, `DELETE bookings/{id}` |
| Documents | `POST trips/{id}/documents` (multipart, optional `bookingId`), `GET documents/{id}/file`, `DELETE documents/{id}` |
| Checklists | `POST trips/{id}/checklists`, `PUT checklists/{id}`, `DELETE checklists/{id}`, `POST checklists/{id}/items`, `PATCH checklist-items/{id}`, `DELETE checklist-items/{id}` |

- **`GET trips`** returns, per trip: id, name, destinations, dates, cover URL.
- **`GET trips/{id}`** returns the whole trip in one response: trip fields,
  activities, bookings, document metadata (no file bytes) and checklists with
  items. The trip page needs one request, and offline caching needs one
  cached response per trip.
- **Reorder:** `PUT trips/{id}/activities/order` with
  `{ "date": "...", "activityIds": [...] }` sets positions for that day. The
  list must contain exactly the activities of that date. Moving an activity
  to another day is a `PUT activities/{id}` with a new `date`; it is appended
  at the end of that day.
- **Cover URL** includes a version (`?v=<cover_storage_key>`); a new cover
  gets a new key, so cached covers never go stale.
- **Concurrent edits:** last write wins.

### Errors (Problem Details)

| Status | Meaning |
|---|---|
| 400 | Validation failed; per-field errors in the response |
| 401 | Not authenticated |
| 403 | Registration closed, or CSRF token missing/invalid |
| 404 | Resource does not exist **or is not owned by the current user** (indistinguishable on purpose) |
| 413 | File too large |
| 415 | File type not allowed |
| 429 | Rate limit exceeded |

## 7. Frontend

Mobile-first; works on desktop.

### Screens
- **Login, sign-up, verify email, forgot/reset password.**
- **Dashboard:** the trip *in progress* on top with a "Today" button that
  opens the current day; then *upcoming* trips (soonest first) and *past*
  trips (most recent first). Cards show cover, name, destinations, dates.
  "New trip" button.
- **Trip page:** header with cover, dates, destinations and "in 12 days" /
  "Day 3 of 10". Bottom tab bar: Summary · Itinerary · Bookings · Documents ·
  Checklists. Summary shows the next activities and the next booking.
- **Itinerary:** one section per day ("Gio 12 mar · Giorno 3") with that
  day's activities and bookings and their Maps links; opens on today when the
  trip is in progress. Reordering with ↑/↓ buttons; moving to another day by
  editing the date.
- **Bookings:** chronological list with a type icon; detail shows attached
  documents and an upload button.
- **Documents:** list; tap to open (PDF or image), download, delete with
  confirmation.
- **Checklists:** each list with a progress bar (7/12), checkable items and
  quick add.

### Offline viewing
- The service worker precaches the app shell, so the app opens offline.
- TanStack Query's cache is persisted to IndexedDB. The dashboard prefetches
  the aggregate (`GET trips/{id}`) of every in-progress and upcoming trip in
  the background, so they are available offline without being opened.
- Workbox runtime cache (cache-first) for `GET documents/{id}/file` and trip
  covers; documents are immutable (replacing one creates a new id). The
  Documents tab has a **"Make available offline"** button that fetches all of
  the trip's files into this cache.
- No other API responses are cached by the service worker.
- When offline: a banner "Sei offline · dati aggiornati alle HH:MM" and all
  editing controls disabled.
- Privacy: the persisted query cache and the document cache are tagged with
  the user id and cleared on logout, and whenever `auth/me` returns a
  different user.
- Updates: when a new service worker is waiting, show "Nuova versione
  disponibile · Aggiorna".
- iPhone: the app should be installed to the Home Screen; Safari may evict
  stored data of non-installed sites after a period of inactivity.

## 8. Testing

### Backend
JUnit 5 + Spring Boot Test + Testcontainers (real PostgreSQL).
- **Integration tests per API area:** happy path, validation errors, edge
  cases.
- **Ownership suite:** for every endpoint that takes a resource id, user B
  gets 404 on user A's resource (trips, activities, bookings, documents,
  checklists, items, cover, file download).
- **Auth:** registration open/closed, verification required before login,
  expired and reused tokens, sessions invalidated after password reset, CSRF
  required on state-changing requests, rate limits.
- **Files:** a renamed non-PDF/non-image is rejected, size limit, download
  headers, files removed from disk when a document or a trip is deleted,
  documents kept when their booking is deleted.
- **Unit tests:** Maps link builder, reorder validation.

### Frontend
- Vitest + Testing Library: trip status from dates, grouping of activities
  and bookings by day (including the "outside the trip dates" group),
  offline state handling, key components.
- **One Playwright end-to-end test** of the main use case: log in, create a
  trip, add an activity, upload a document, go offline, find everything.

### CI
GitHub Actions on every push: backend `mvn verify`; frontend lint,
type-check, tests, build.

## 9. Deployment and operations

### Server B
- Debian, **rootless Docker** under a dedicated user, with
  `loginctl enable-linger` so the daemon runs without a login session.
- Deploy: `git pull && docker compose up -d --build` from `deploy/`.
- Services: `app` and `db`; volumes `db-data` and `files`.
- Secrets (DB password, SMTP credentials) in `deploy/.env`, not committed;
  `deploy/.env.example` is committed.
- Flyway applies migrations at startup.
- Actuator exposes only `/actuator/health`, used as the container
  healthcheck.
- ufw: allow the app port only from the NPM server's IP.

### Backups
- **restic** to Infomaniak Swiss Backup over S3, encrypted client-side with a
  restic password.
- Runs as a `backup` service in `docker compose` using the official restic
  image, with the `files` volume mounted read-only. Running restic inside a
  container avoids rootless UID-mapping permission problems on the host.
- Nightly from the dedicated user's crontab:
  1. `pg_dump` from `db` piped into `restic backup --stdin`;
  2. `restic backup` of the `files` volume;
  3. `restic forget --prune --keep-daily 14 --keep-weekly 8 --keep-monthly 6`.
- Weekly `restic check`.
- S3 credentials and the restic password live in a file on server B readable
  only by the dedicated user, never in the repo. The restic password must
  also be kept elsewhere (e.g. a password manager); without it backups cannot
  be restored.
- Restore procedure (`restic restore` of the files plus importing the dump)
  documented in `docs/` and tested once right after the first deploy.

## 10. Risks and notes

- **Spring Security learning curve:** keep its configuration in one class,
  with comments explaining each choice.
- **SMTP is required in production** for verification and password reset;
  the free tiers of Brevo or Resend are enough.
- **iOS storage eviction** for non-installed PWAs; mitigated by installing
  to the Home Screen.
- **Single server, SQL database on one host:** fine for personal use and a
  moderate public launch; scaling out would need shared file storage and a
  shared rate-limit store.
