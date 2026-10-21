# Project notes for Claude

Travel Planner: a Spring Boot API that also serves a React PWA (`frontend/`), deployed with Docker Compose (`deploy/`). Specs and plans are in `docs/superpowers/`; operations and deploy guides are `docs/operations.md` and `docs/deploy.md`.

## Language
- Code, identifiers, API, comments, commit messages and docs: English.
- UI copy: Italian, and only in `frontend/src/i18n/it.ts` (`m`). No literal UI text in components, and server error text is never shown to users.

## Backend (`backend/`)
- Java 21, Spring Boot 4.1.1, Maven. groupId `me.luucka`, root package `me.luucka.travelplanner`, one package per feature (controller → service → repository).
- Ownership:
  - Load every trip-scoped resource through `TripAccess.requireOwned`, or through a repository query joined on the trip owner (`findByIdAndTripOwnerId`).
  - A resource that is not the user's returns 404, the same as a missing one.
  - Every new endpoint gets an `OwnershipTest` row: user B gets 404 and the data is unchanged, then the same request as the owner is not 404.
- Errors are RFC 9457 Problem Details (`ApiException`, `NotFoundException`, `InvalidFieldException`). Field errors go under `errors.<field>`.
- Migrations: add a new Flyway file `db/migration/V<n>__name.sql`. Never edit an applied one.
- Activity and booking times are local, with no time zone (`LocalTime` / `LocalDateTime` on `time` / `timestamp`).
- Stored files: write the file, then the row. Delete only after commit (`StoredFiles.deleteAfterCommit`).

## Frontend (`frontend/`)
- React 19, TypeScript, Mantine, TanStack Query, React Router. Lint is `oxlint`.
- Dates are `YYYY-MM-DD` strings. Never parse them with `new Date('YYYY-MM-DD')`, and get today from `todayLocal()`.
- Render query `data` before checking `isError`: a failed background refetch must not replace content that is already on screen.
- Every mutation failure is shown to the user (Italian copy). Mutating controls are disabled offline (`useOnline`, `OnlineLinkButton`).
- Offline data is cleared only on logout or when a different user logs in (`offline/persistence.ts`), not on a 401.

## Checks before committing
- Backend: `cd backend && ./mvnw verify` (needs Docker for Testcontainers).
- Frontend: `cd frontend && npm test -- --run && npm run typecheck && npm run lint && npm run build`.
- Deploy scripts: `cd deploy && shellcheck -x ./*.sh`, then `bash deploy/update-test.sh`.
- CI (`.github/workflows/ci.yml`) runs all of these plus the Playwright e2e job.

## Git
- Merge pull requests into `main` with squash (merge commits are disabled on this repository).
