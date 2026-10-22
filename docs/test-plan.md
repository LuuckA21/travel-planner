# Manual test plan

Acceptance tests to run by hand on the deployed server and on an iPhone with the app installed. The automated suites (backend, frontend, deploy scripts, e2e in CI) do not cover these: real HTTPS and NPM, real SMTP, the iPhone home-screen app, airplane mode, cron and backups.

Fill in the **Result** column with `OK`, `KO` or `Redo`, plus a short note for anything that is not OK. An interactive Italian copy of this plan, which saves results from the phone, is kept as a private artifact: https://claude.ai/artifact/2KNPr4wj9LMUjem3GAEioC (only the owner can open it). It has the same 62 tests with the same IDs. When tests change, update both.

Placeholders: `<domain>` is the public host name from `APP_BASE_URL`, `<APP_BIND_IP>` and `<APP_PORT>` come from `deploy/.env`, and `<APP_DIR>` is where the repository is cloned.

Order: run the areas top to bottom. AUTH-06 creates the second user that PRIV needs, and OPS-07 needs a real new version on `main`.

## SRV — Server and network

| ID | What to test | Expected | Result |
|---|---|---|---|
| SRV-01 | Open `https://<domain>`, then `http://<domain>` | Login page over HTTPS with a valid certificate; `http://` redirects to `https://` | |
| SRV-02 | From a machine that is not the NPM server (e.g. a phone on mobile data, or another LAN host): `curl -m 5 http://<APP_BIND_IP>:<APP_PORT>/actuator/health`. Then the same from the NPM server | From elsewhere: timeout or refused. From NPM: `{"status":"UP"}` | |
| SRV-03 | Open `https://<domain>/actuator/health` and `https://<domain>/actuator/env` | `health` returns `{"status":"UP"}`; `env` returns 404 | |
| SRV-04 | `curl -sI https://<domain>/` | Headers include `Content-Security-Policy`, `X-Frame-Options: DENY`, `Referrer-Policy: same-origin` | |
| SRV-05 | Open `https://<domain>/missing.png`, then `https://<domain>/trips/123` while logged out | `missing.png` returns 404; `/trips/123` shows the login page | |
| SRV-06 | Reboot server B, wait a few minutes, then run `docker compose ps` in `<APP_DIR>/deploy` and open the site | Containers running and healthy without any manual command; the site works | |

## AUTH — Access

| ID | What to test | Expected | Result |
|---|---|---|---|
| AUTH-01 | Log in with the right email and password | Dashboard "I miei viaggi" | |
| AUTH-02 | Log in with a wrong password | "Email o password non corrette." and you stay on the login page | |
| AUTH-03 | Log in typing the email with some capital letters | Login works | |
| AUTH-04 | Enter a wrong password 11 times in a row | From the 11th attempt: "Troppi tentativi. Riprova più tardi."; after 15 minutes the right password works again | |
| AUTH-05 | On the login page, look for a sign-up link; then open `https://<domain>/signup` and try to register | No sign-up link; registering shows "Le registrazioni sono chiuse." | |
| AUTH-06 | Set `APP_REGISTRATION_OPEN=true` in `.env` and run `docker compose up -d`. Register a second account with another email. Log in before confirming, then open the email link, then log in again. Set the flag back to `false` and run `docker compose up -d` | The sign-up link appears; the confirmation email arrives within a minute or two; before confirming, login says "Devi prima verificare la tua email."; after the link, login works; with the flag back to `false` the link disappears | |
| AUTH-07 | Stay logged in on a second device. On the first, use "Password dimenticata", open the email link and set a new password | Email arrives; the new password works and the old one does not; refreshing the second device sends it to the login page | |
| AUTH-08 | "Password dimenticata" with an email that has no account | The same confirmation as for a real account, and no email | |
| AUTH-09 | Log out, then press the browser's back button | Login page; no trip data visible | |
| AUTH-10 | Log in on the phone, close the app, reopen it the next day | Still logged in | |

## TRIP — Trips

| ID | What to test | Expected | Result |
|---|---|---|---|
| TRIP-01 | Create a trip with a name, two destinations and future dates | It appears under "In arrivo" with "tra N giorni" | |
| TRIP-02 | Save a trip with the end date before the start date, then one longer than 365 days | An Italian error under the end date each time; nothing is saved | |
| TRIP-03 | Add a JPEG photo as cover, replace it with a transparent PNG, then remove it | The cover shows on the card; the PNG has a white background, not black; after removal the card has no cover | |
| TRIP-04 | Create a trip that includes today, and one that ended in the past; open both from the dashboard | Today's trip is under "In corso" with an "Oggi" button and "Giorno X di Y"; the past one is under "Passati"; tapping either card opens the trip | |
| TRIP-05 | Edit a trip's name and shorten its dates so an activity falls outside | Changes saved; the activity is kept under "Fuori dalle date del viaggio" | |
| TRIP-06 | Copy a document's link from the trip, then delete the trip (confirm) | The trip disappears; opening the copied document link gives 404 | |

## ITIN — Itinerary

| ID | What to test | Expected | Result |
|---|---|---|---|
| ITIN-01 | Open the itinerary and add an activity with a time and a place | One section per day with a header like "Ven 12 mar · Giorno 3"; the activity shows with its time and place | |
| ITIN-02 | Tap the activity's Maps link. Then set a custom link `https://maps.app.goo.gl/...`. Then try `http://example.com` | Google Maps opens a search for the place; with a custom link that link is used; `http://` is rejected with an Italian error | |
| ITIN-03 | With three activities in a day, use ↑ and ↓, then reload | The order changes and stays after reload; ↑ on the first and ↓ on the last are disabled | |
| ITIN-04 | Change an activity's date to another day of the trip | It moves to the end of that day | |
| ITIN-05 | On a trip in progress, open the itinerary (or tap "Oggi") | The page opens on today's day | |
| ITIN-06 | Edit the title of an activity left outside the trip dates (from TRIP-05) | Saved; the date is not forced to change | |

## BOOK — Bookings

| ID | What to test | Expected | Result |
|---|---|---|---|
| BOOK-01 | Add a flight departing 10:00 and arriving 15:40 | Exactly 10:00 and 15:40 are shown, in the bookings list and on that day in the itinerary | |
| BOOK-02 | Add a 3-night accommodation | It shows in the itinerary on the check-in day and the check-out day, not on the nights in between | |
| BOOK-03 | Save a booking with the end before the start | An Italian error under the end; nothing is saved | |
| BOOK-04 | Open a booking, attach a PDF, then delete the booking | The PDF shows in the booking and in Documenti with the booking's name; after the delete the PDF is still in Documenti, with no booking | |

## DOC — Documents

| ID | What to test | Expected | Result |
|---|---|---|---|
| DOC-01 | Upload a PDF, open it, download it | Listed with its size; it opens; the download keeps the file name | |
| DOC-02 | Upload a photo from the iPhone library | Uploaded (compressed) and it opens | |
| DOC-03 | Upload an original `.heic` file (from the Files app) | Message: "Formato non supportato. Puoi caricare file PDF, JPEG, PNG o WebP. …" | |
| DOC-04 | Upload a file larger than 25 MB | Message: "Il file è troppo grande (massimo 25 MB)." | |
| DOC-05 | Upload a file named `Biglietto ✈ Città.pdf`, then download it | The list and the downloaded file keep exactly that name | |
| DOC-06 | Copy a document's link, delete the document (confirm), open the link | The document disappears; the link gives 404 | |
| DOC-07 | After DOC-03 or DOC-04, pick a valid file in the same picker | The picker opens and the upload works | |

## CHK — Checklists

| ID | What to test | Expected | Result |
|---|---|---|---|
| CHK-01 | Open the checklists of a new trip | Two empty lists: "Bagaglio" and "Prima di partire" | |
| CHK-02 | Type an item and press Enter, then type another | The field clears and keeps focus; progress shows 1/1, then 2/2 | |
| CHK-03 | Tick five items quickly, wait a few seconds, reload | All five stay ticked | |
| CHK-04 | Rename a list, then delete it (two taps) | Renamed; deleted only after the confirmation tap | |

## OFF — iPhone app and offline

| ID | What to test | Expected | Result |
|---|---|---|---|
| OFF-01 | In Safari: Share → Add to Home Screen; open the app from the icon | Opens full screen without Safari bars; the bottom tab bar is not covered by the home indicator | |
| OFF-02 | Online, open the dashboard once. Then airplane mode, and open a future trip you never opened | The trip's contents are shown | |
| OFF-03 | Online, on a trip's Documenti tab, tap "Rendi disponibili offline" | "N file disponibili offline" with N = the number of files | |
| OFF-04 | Airplane mode: close the app completely, reopen it | Dashboard shown with "Sei offline · dati aggiornati alle HH:MM"; you are not logged out | |
| OFF-05 | Airplane mode: open today's itinerary, then a PDF, then swipe back | Both open; the swipe returns to the app. Note it if you get stuck in the PDF | |
| OFF-06 | Airplane mode: look for "Nuovo viaggio", "Modifica", the add buttons and the checklist ticks | All disabled | |
| OFF-07 | Turn airplane mode off | The offline bar disappears and editing works again | |
| OFF-08 | With the phone offline, create a new trip on the computer; then open the app on the phone | That trip is not available: "Non disponibile offline. …" or it is simply missing; nothing breaks | |
| OFF-09 | After an `update.sh` on the server (OPS-07), open the app | "Nuova versione disponibile" with an "Aggiorna" button; after tapping it, the new version loads | |

## PRIV — Privacy between users

| ID | What to test | Expected | Result |
|---|---|---|---|
| PRIV-01 | Log in as the second user (from AUTH-06) | None of the first user's trips are visible | |
| PRIV-02 | As the first user, copy a trip's address and a document's link; open both while logged in as the second user | Not found / 404; no data shown | |
| PRIV-03 | On the same phone: log out the first user, log in as the second, then airplane mode | Nothing of the first user is visible, online or offline | |

## OPS — Backups and updates

| ID | What to test | Expected | Result |
|---|---|---|---|
| OPS-01 | `<APP_DIR>/deploy/backup.sh`, then `docker compose run --rm backup snapshots` | Ends with `BACKUP OK`; there is a new `db` and a new `files` snapshot | |
| OPS-02 | The cron-environment simulation from `docs/deploy.md` (step 3 of "Scheduling backups with cron") | Ends with `BACKUP CHECK OK` | |
| OPS-03 | The morning after setting up cron: `tail -n 20 ~/backup.log` and the healthchecks.io dashboard | Last run ends with `BACKUP OK`; the daily check is green | |
| OPS-04 | Temporarily put a wrong `RESTIC_PASSWORD` in `backup.env`, run `backup.sh`, then restore the right value and run it again | First run ends with `BACKUP FAILED` and healthchecks.io sends an alert; second run is `BACKUP OK` and the check turns green | |
| OPS-05 | Restore test as a separate project (`COMPOSE_PROJECT_NAME=tp-restore-test`, operations.md §8) | The trip count matches production and the files are listed; the test project is removed afterwards | |
| OPS-06 | `<APP_DIR>/deploy/update.sh` with nothing new on `main` | "already up to date" and `UPDATE OK`; no backup is run | |
| OPS-07 | `update.sh` after a real new version reaches `main` (with CI green) | Backup, pull, rebuild, then `UPDATE OK`; the app works and your data is still there | |
