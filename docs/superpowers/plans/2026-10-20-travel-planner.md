# Travel Planner Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Travel Planner MVP: a mobile-first PWA for trips, itinerary, bookings, documents and checklists with per-user isolation and offline viewing, deployed as one Docker image behind Nginx Proxy Manager.

**Architecture:** Spring Boot serves a JSON API under `/api` and the built React PWA from one image. PostgreSQL stores data, files live on a volume, sessions are server-side (Spring Session JDBC). The frontend caches API responses in IndexedDB and document files in the service worker cache for offline reading.

**Tech Stack:** Java 21 LTS, Spring Boot 4.1.1, Maven, PostgreSQL 18.6, Flyway, Spring Security, Spring Session JDBC, Bucket4j 8.21.0 + Caffeine, JUnit 5, Testcontainers 2.0.5 · React 19, TypeScript, Vite 8, Mantine 9, TanStack Query 5, React Router 8, vite-plugin-pwa 2, Vitest, Playwright · Docker, restic 0.19.1.

**Spec:** `docs/superpowers/specs/2026-10-20-travel-planner-design.md` — read it before starting any task.

## Global Constraints

- Code, identifiers, API, comments, docs: English. UI copy: Italian, only in `frontend/src/i18n/it.ts`.
- Java 21; Spring Boot 4.1.1 (Boot 4 artifact names: `spring-boot-starter-webmvc`, `spring-boot-starter-flyway`, `spring-boot-starter-session-jdbc`, test starters `spring-boot-starter-webmvc-test`, `spring-boot-starter-security-test`, `spring-boot-starter-data-jpa-test`, `spring-boot-testcontainers`; Testcontainers 2 artifact `org.testcontainers:testcontainers-postgresql`, class `org.testcontainers.postgresql.PostgreSQLContainer`).
- Maven groupId `com.travelplanner`, root package `com.travelplanner`.
- Docker images: `postgres:18.6` (also in Testcontainers), `eclipse-temurin:21-jre`, `maven:3.9-eclipse-temurin-21`, `node:22`, `restic/restic:0.19.1`.
- Frontend scaffolded with `npm create vite@latest frontend -- --template react-ts`; keep the TypeScript/ESLint versions the template installs.
- All primary keys UUID; `created_at`/`updated_at` (`timestamptz`) on every table.
- Booking and activity times are local, no time zone (`timestamp without time zone`, `time`).
- Upload limit 25 MB; allowed types PDF, JPEG, PNG, WebP detected from leading bytes; covers images only.
- Session idle timeout 30 days; verification token 24 h; reset token 1 h; tokens single-use, stored as SHA-256 hash.
- Password length 10–128, no composition rules.
- Rate limits: login 10 / 15 min per IP and per email; register, forgot-password, resend-verification 5 / hour per IP; 429 when exceeded.
- Trip length ≤ 365 days; `end_date >= start_date`.
- Not-owned resources return 404, indistinguishable from missing.
- Errors use RFC 9457 Problem Details; validation errors carry `errors: {field: message}`.

### Decisions this plan adds to the spec

- `GET /api/auth/config` → `{ "registrationOpen": boolean }` (public), so the UI can hide sign-up.
- Duplicate registration email → 409. Login with unverified email → 403 with `code: "EMAIL_NOT_VERIFIED"`. Wrong credentials → 401.
- `maps_url` must start with `https://` (it is rendered as a link; this blocks `javascript:` URLs).
- Field limits: trip name 1–120; up to 20 destinations of 1–80; titles 1–200; provider/reference/origin 0–200; location 0–300; `maps_url` ≤ 2000; notes ≤ 5000; checklist name 1–80; item text 1–300; display name 1–80.
- Forwarded headers use `server.forward-headers-strategy: framework` (not `native` as in the spec): Spring's `ForwardedHeaderFilter` also runs under MockMvc, so client-IP and `Secure`-cookie behaviour is testable. Trusting all forwarded headers is safe because ufw restricts the port to NPM.
- Local backend dev uses `mvn spring-boot:test-run` (Testcontainers Postgres, `dev` profile); no dev compose file.

## Review Focus

1. **Offline start with no network:** `auth/me` failing with a network error must not log the user out or clear caches; only a real HTTP 401 does. (Task 17)
2. **"Today" near midnight / abroad:** today must come from local date parts, never `toISOString()` (which is UTC and shifts the date). (Task 16)
3. **Non-ASCII and quoted filenames** ("Biglietto ✈ Città \"A\".pdf") must download with the right name (`filename*=UTF-8''…`). (Task 11)
4. **Email case:** `Luca@Example.IT` at registration, `luca@example.it` at login must work; registering the other case is a duplicate. (Tasks 3, 4)
5. **iPhone HEIC photos and other unsupported files:** rejected with 415 and an Italian message the user understands, not a generic error. (Tasks 10, 11, 22)

---

## Phase A — Backend

### Task 1: Backend skeleton, test harness, health

**Files:**
- Create: `backend/pom.xml`, `backend/src/main/java/com/travelplanner/TravelPlannerApplication.java`
- Create: `backend/src/main/resources/application.yml`, `application-dev.yml`, `application-prod.yml`
- Create: `backend/src/test/java/com/travelplanner/TestcontainersConfiguration.java`, `TestTravelPlannerApplication.java`, `support/IntegrationTest.java`, `HealthTest.java`
- Create: `.gitignore` (Maven `target/`, `node_modules/`, `dist/`, `deploy/.env`, `deploy/backup.env`, `backend/data/`)

**Interfaces:**
- Produces: `IntegrationTest` — abstract base: `@SpringBootTest @AutoConfigureMockMvc @Import(TestcontainersConfiguration.class) @ActiveProfiles("test")`, protected `MockMvc mvc`, protected `JdbcTemplate jdbc`; `@BeforeEach` truncates all application tables (list grows as tables are added; use `TRUNCATE ... CASCADE`).
- Produces: `TestcontainersConfiguration` — `@Bean @ServiceConnection PostgreSQLContainer postgres()` with image `postgres:18.6`.
- Produces: `TestTravelPlannerApplication.main` — runs the app with `TestcontainersConfiguration` and profile `dev` (for `mvn spring-boot:test-run`).

- [ ] **Step 1: Write `pom.xml`** — parent `spring-boot-starter-parent` 4.1.1, `java.version` 21. Dependencies: webmvc, security, data-jpa, validation, mail, session-jdbc, actuator, flyway + `org.flywaydb:flyway-database-postgresql`, `org.postgresql:postgresql` (runtime), `com.bucket4j:bucket4j_jdk17-core:8.21.0`, `com.github.ben-manes.caffeine:caffeine`; test: `spring-boot-starter-test`, webmvc-test, security-test, data-jpa-test, `spring-boot-testcontainers`, `org.testcontainers:testcontainers-postgresql`, `org.testcontainers:testcontainers-junit-jupiter`.

- [ ] **Step 2: Write `application.yml`** — `spring.jpa.open-in-view: false`, `spring.jpa.hibernate.ddl-auto: validate`, `spring.session.jdbc.initialize-schema: never`, `server.servlet.session.timeout: 30d`, `spring.servlet.multipart.max-file-size: 25MB`, `max-request-size: 26MB`, `management.endpoints.web.exposure.include: health`, `server.forward-headers-strategy: framework`, `app.registration-open: false`, `app.base-url`, `app.storage-dir`. `dev`: registration open, `app.base-url: http://localhost:5173`, `app.storage-dir: ./data/files`. `test` (in `src/test/resources/application-test.yml`): `app.storage-dir: ${java.io.tmpdir}/travel-planner-test-files`. `prod`: everything from env vars (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `SMTP_*`, `APP_BASE_URL`, `APP_REGISTRATION_OPEN`, `MAIL_FROM`), `app.storage-dir: /data/files`.

- [ ] **Step 3: Write the failing test**

```java
class HealthTest extends IntegrationTest {
    @Test void healthIsUpAndPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
           .andExpect(jsonPath("$.status").value("UP"));
    }
    @Test void otherActuatorEndpointsAreNotExposed() throws Exception {
        mvc.perform(get("/actuator/env")).andExpect(status().is4xxClientError());
    }
}
```

- [ ] **Step 4: Run** `cd backend && ./mvnw test -Dtest=HealthTest` (generate the wrapper first with `mvn -N wrapper:wrapper`). Expected: FAIL (no app / Flyway has no migrations / security blocks health).

- [ ] **Step 5: Implement** the application class and a placeholder `SecurityConfig` permitting `/actuator/health` (Task 3 replaces it). Add `db/migration/V1__sessions.sql` containing Spring Session JDBC's PostgreSQL schema (copy `org/springframework/session/jdbc/schema-postgresql.sql` from the spring-session-jdbc jar) so Flyway has a baseline.

- [ ] **Step 6: Run** `./mvnw test`. Expected: PASS.

- [ ] **Step 7: Commit** `git commit -m "feat(backend): skeleton with health endpoint and test harness"`

### Task 2: Error handling

**Files:**
- Create: `backend/src/main/java/com/travelplanner/common/NotFoundException.java`, `ApiException.java`, `ApiExceptionHandler.java`
- Test: `backend/src/test/java/com/travelplanner/common/ApiExceptionHandlerTest.java` (uses a test-only `@RestController` in the test sources)

**Interfaces:**
- Produces: `NotFoundException()` (→ 404); `ApiException(HttpStatus status, String code, String detail)` (→ that status, Problem Detail with property `code`); `ApiExceptionHandler` (`@RestControllerAdvice` extending `ResponseEntityExceptionHandler`) mapping `MethodArgumentNotValidException` → 400 with `errors` map, `MaxUploadSizeExceededException` → 413, `NotFoundException` → 404, `ApiException` → its status.

- [ ] **Step 1: Write failing tests** — test controller endpoints that throw each exception or take an `@Valid` record with `@NotBlank String name`:

```java
@Test void validationErrorListsFields() throws Exception {
    mvc.perform(post("/test/validate").with(csrf()).contentType(APPLICATION_JSON).content("{\"name\":\"\"}"))
       .andExpect(status().isBadRequest())
       .andExpect(jsonPath("$.errors.name").exists());
}
@Test void notFoundIsProblemDetail() // status 404, content type application/problem+json
@Test void apiExceptionCarriesCode() // ApiException(FORBIDDEN, "EMAIL_NOT_VERIFIED", ...) -> 403, $.code == "EMAIL_NOT_VERIFIED"
```

The test controller needs an authenticated request: use `with(user("x"))` from security-test.

- [ ] **Step 2: Run** `./mvnw test -Dtest=ApiExceptionHandlerTest`. Expected: FAIL.
- [ ] **Step 3: Implement** the three classes; enable `spring.mvc.problemdetails.enabled: true`.
- [ ] **Step 4: Run** the test. Expected: PASS.
- [ ] **Step 5: Commit** `feat(backend): problem-details error handling`

### Task 3: User accounts, security config, login/logout/me, create-user command

**Files:**
- Create: `backend/src/main/resources/db/migration/V2__users.sql`
- Create in `com.travelplanner.auth`: `UserAccount.java`, `UserAccountRepository.java`, `AppUserDetails.java`, `AppUserDetailsService.java`, `SecurityConfig.java` (replaces placeholder), `AuthController.java`, `AuthService.java`, `AuthProperties.java`, `CreateUserCommand.java`, dto records `LoginRequest`, `MeResponse`, `AuthConfigResponse`
- Create: `backend/src/test/java/com/travelplanner/support/TestUsers.java`
- Test: `backend/src/test/java/com/travelplanner/auth/LoginTest.java`, `CreateUserCommandTest.java`

**Interfaces:**
- Produces: table `user_account(id uuid pk, email text unique not null, password_hash text not null, display_name text not null, email_verified_at timestamptz, created_at, updated_at)`; table `auth_token(id uuid pk, user_id uuid fk → user_account on delete cascade, purpose text not null, token_hash text unique not null, expires_at timestamptz not null, used_at timestamptz, created_at, updated_at)` (used from Task 4).
- Produces: `UserAccountRepository.findByEmail(String lowercasedEmail): Optional<UserAccount>`.
- Produces: `AppUserDetails implements UserDetails` with `UUID id()`; username = email. Controllers get the current user with `@AuthenticationPrincipal AppUserDetails user`.
- Produces: `AuthProperties` record bound to `app`: `boolean registrationOpen`, `String baseUrl`.
- Produces: `TestUsers` (Spring `@Component` in test sources): `UUID create(String email, String password)` (verified user), `RequestPostProcessor as(UUID userId)` (= `user(new AppUserDetails(...))`), `Cookie login(String email, String password)` (performs the real `POST /api/auth/login` and returns the `SESSION` cookie; auth tests send it back), and `IntegrationTest` exposes `testUsers`.
- Produces endpoints: `POST /api/auth/login {email,password}` → 200 `MeResponse{id,email,displayName}`; `POST /api/auth/logout` → 204; `GET /api/auth/me` → 200 or 401; `GET /api/auth/config` → `{registrationOpen}`.

- [ ] **Step 1: Write failing tests** in `LoginTest`:

```java
@Test void loginWithValidCredentialsCreatesSession()   // 200, $.email; then GET /api/auth/me with the returned session -> 200
@Test void loginIsCaseInsensitiveOnEmail()            // user created as "luca@example.it"; login "Luca@Example.IT" -> 200
@Test void loginRotatesSessionId()                    // session id before login != after
@Test void wrongPasswordIs401()
@Test void unverifiedUserIs403WithCode()              // $.code == "EMAIL_NOT_VERIFIED"
@Test void meWithoutSessionIs401()                    // and is JSON problem, not a redirect to a login page
@Test void stateChangingRequestWithoutCsrfIs403()     // POST /api/auth/logout without csrf() -> 403
@Test void logoutInvalidatesSession()                 // me after logout with same session -> 401
@Test void sessionCookieIsHttpOnlyAndSecureBehindHttps() // request with header X-Forwarded-Proto: https -> Set-Cookie contains HttpOnly, Secure, SameSite=Lax
@Test void configReportsRegistrationClosedByDefault() // GET /api/auth/config -> registrationOpen == false
```

`CreateUserCommandTest`: running `CreateUserCommand.run("create-user", "Me@Example.it")` with stdin `"supersecret1\nsupersecret1\n"` creates a verified user with email `me@example.it`; mismatched passwords → no user, non-zero exit code.

- [ ] **Step 2: Run** `./mvnw test -Dtest='LoginTest,CreateUserCommandTest'`. Expected: FAIL.

- [ ] **Step 3: Implement.**
  - `SecurityConfig`: `csrf(c -> c.spa())`; permit `/api/auth/login`, `/api/auth/register`, `/api/auth/verify-email`, `/api/auth/resend-verification`, `/api/auth/forgot-password`, `/api/auth/reset-password`, `/api/auth/config`, `/actuator/health`, and all non-`/api/**` paths (static PWA); authenticate `/api/**`; `exceptionHandling` with an entry point that writes a 401 Problem Detail; no form login, no HTTP basic; `PasswordEncoderFactories.createDelegatingPasswordEncoder()`; expose `AuthenticationManager`.
  - `AuthController.login`: authenticate via `AuthenticationManager`; check `email_verified_at` (throw `ApiException(FORBIDDEN, "EMAIL_NOT_VERIFIED", …)`); apply `ChangeSessionIdAuthenticationStrategy`; save context with `HttpSessionSecurityContextRepository`. Map `BadCredentialsException` → 401 Problem Detail.
  - Emails are lowercased (`toLowerCase(Locale.ROOT)`, trimmed) at every entry point.
  - `CreateUserCommand implements ApplicationRunner`: active only when the first non-option arg is `create-user`; reads the password twice (from `System.console()` if present, else stdin lines); validates length 10–128; creates a verified user; then `System.exit` with 0/1 via `SpringApplication.exit`. Display name = part of the email before `@`.

- [ ] **Step 4: Run** the tests. Expected: PASS.
- [ ] **Step 5: Commit** `feat(auth): user accounts, session login, create-user command`

### Task 4: Registration, email verification, mailer

**Files:**
- Create in `com.travelplanner.auth`: `Mailer.java`, `SmtpMailer.java`, `LoggingMailer.java`, `AuthToken.java`, `AuthTokenRepository.java`, `AuthTokenService.java`, dto `RegisterRequest`, `TokenRequest`, `EmailRequest`
- Modify: `AuthController.java`, `AuthService.java`, `SecurityConfig.java` (nothing new if paths already permitted)
- Create: `backend/src/test/java/com/travelplanner/support/RecordingMailer.java`
- Test: `backend/src/test/java/com/travelplanner/auth/RegistrationTest.java`

**Interfaces:**
- Produces: `interface Mailer { void send(String to, String subject, String body); }`; `SmtpMailer` (`@Profile("prod")`, uses `JavaMailSender`, from `app.mail-from`), `LoggingMailer` (`@Profile("!prod")`, logs the body). Tests replace it with `RecordingMailer` (`@Primary` test bean) exposing `List<SentMail> sent()` and `String lastLinkTo(String email)` (extracts the URL from the body).
- Produces: `AuthTokenService.issue(UUID userId, TokenPurpose purpose, Duration ttl): String rawToken`; `AuthTokenService.consume(String rawToken, TokenPurpose purpose): Optional<UUID userId>` (marks used; empty if unknown, expired, used or wrong purpose). `enum TokenPurpose { VERIFY_EMAIL, RESET_PASSWORD }`. Raw token: 32 random bytes from `SecureRandom`, base64url.
- Produces endpoints: `POST /api/auth/register {email,password,displayName}` → 201 (403 if closed, 409 if email exists); `POST /api/auth/verify-email {token}` → 204 or 400; `POST /api/auth/resend-verification {email}` → 202 always.
- Email link format: `${app.base-url}/verify-email?token=<raw>`. Italian subject/body text lives in the mailer call site as constants.

- [ ] **Step 1: Write failing tests** (set `app.registration-open=true` via `@TestPropertySource` except the closed test, which uses a separate nested class or `@SpringBootTest(properties=...)`):

```java
@Test void registerSendsVerificationAndBlocksLoginUntilVerified() // 201; login -> 403 EMAIL_NOT_VERIFIED; POST verify-email with token from recordingMailer.lastLinkTo(email) -> 204; login -> 200
@Test void registrationClosedIs403()
@Test void duplicateEmailInDifferentCaseIs409()      // register "Luca@Example.IT" after "luca@example.it"
@Test void passwordShorterThan10Is400()             // $.errors.password exists; also 129 chars -> 400
@Test void verificationTokenIsSingleUse()           // second verify-email with same token -> 400
@Test void expiredVerificationTokenIs400()          // set expires_at in the past via jdbc
@Test void resendVerificationIs202ForUnknownEmail() // and sends no mail
@Test void tokenIsStoredHashed()                    // auth_token.token_hash != raw token
```

- [ ] **Step 2: Run** `./mvnw test -Dtest=RegistrationTest`. Expected: FAIL.
- [ ] **Step 3: Implement.** Token TTL for verification: `Duration.ofHours(24)`.
- [ ] **Step 4: Run** the tests. Expected: PASS.
- [ ] **Step 5: Commit** `feat(auth): registration with email verification`

### Task 5: Password reset with session invalidation

**Files:**
- Modify: `AuthController.java`, `AuthService.java`; create dto `ResetPasswordRequest`
- Test: `backend/src/test/java/com/travelplanner/auth/PasswordResetTest.java`

**Interfaces:**
- Consumes: `AuthTokenService` (Task 4), `RecordingMailer`.
- Produces endpoints: `POST /api/auth/forgot-password {email}` → 202 always; `POST /api/auth/reset-password {token,password}` → 204 or 400. Link: `${app.base-url}/reset-password?token=<raw>`, TTL 1 h.

- [ ] **Step 1: Write failing tests:**

```java
@Test void resetChangesPasswordAndClosesAllSessions() // log in twice (two sessions); forgot -> reset with new password -> both sessions' /me -> 401; login with old password -> 401; new -> 200
@Test void forgotPasswordResponseIsSameForUnknownEmail() // same status and body as for a known email; no mail sent
@Test void forgotPasswordWorksWithDifferentCaseEmail()
@Test void resetTokenExpiresAfterOneHour()            // expires_at moved into the past -> 400
@Test void resetTokenIsSingleUse()
@Test void verifyEmailTokenCannotResetPassword()      // purpose mismatch -> 400
```

- [ ] **Step 2: Run** `./mvnw test -Dtest=PasswordResetTest`. Expected: FAIL.
- [ ] **Step 3: Implement.** Invalidate sessions with `FindByIndexNameSessionRepository.findByPrincipalName(email)` and `deleteById` for each. A reset also sets `email_verified_at` if null (the user proved control of the inbox).
- [ ] **Step 4: Run** the tests. Expected: PASS.
- [ ] **Step 5: Commit** `feat(auth): password reset`

### Task 6: Rate limiting

**Files:**
- Create: `backend/src/main/java/com/travelplanner/auth/RateLimiter.java`
- Modify: `AuthController.java` (call the limiter), `support/IntegrationTest.java` (reset limiter in `@BeforeEach`)
- Test: `backend/src/test/java/com/travelplanner/auth/RateLimitTest.java`

**Interfaces:**
- Produces: `RateLimiter.check(String key, Limit limit)` — throws `ApiException(TOO_MANY_REQUESTS, "RATE_LIMITED", …)`; `enum Limit { LOGIN(10, 15 min), SENSITIVE(5, 1 h) }`; `RateLimiter.reset()` (tests only). Buckets in a Caffeine cache with `expireAfterAccess` equal to the limit window, so unused keys are evicted.
- Keys: login → `"login-ip:" + ip` and `"login-email:" + email`; register/forgot/resend → `"<endpoint>-ip:" + ip`. IP = `request.getRemoteAddr()` (already the client IP thanks to `forward-headers-strategy: framework`).

- [ ] **Step 1: Write failing tests:**

```java
@Test void eleventhLoginAttemptFromSameIpIs429()          // 10 wrong-password attempts -> 401 each; 11th -> 429
@Test void loginLimitIsPerEmailAcrossIps()                // 10 attempts for one email with different X-Forwarded-For values; 11th -> 429
@Test void sixthForgotPasswordPerHourIs429()
@Test void forwardedForIsUsedAsClientIp()                 // 10 attempts with X-Forwarded-For 203.0.113.1, then 1 attempt from 203.0.113.2 with another email -> 401 not 429
```

- [ ] **Step 2: Run** `./mvnw test -Dtest=RateLimitTest`. Expected: FAIL.
- [ ] **Step 3: Implement.** Add a comment in `application.yml` next to `forward-headers-strategy` that forwarded headers are trusted because ufw restricts the port to NPM (spec §3).
- [ ] **Step 4: Run** `./mvnw test` (whole suite, to ensure the reset hook keeps other tests green). Expected: PASS.
- [ ] **Step 5: Commit** `feat(auth): rate limiting on auth endpoints`

### Task 7: Trips CRUD and ownership

**Files:**
- Create: `db/migration/V3__trips.sql`
- Create in `com.travelplanner.trip`: `Trip.java`, `TripRepository.java`, `TripAccess.java`, `TripService.java`, `TripController.java`, dto `TripRequest`, `TripSummary`, `TripDetail`
- Test: `backend/src/test/java/com/travelplanner/trip/TripApiTest.java`, `backend/src/test/java/com/travelplanner/OwnershipTest.java`

**Interfaces:**
- Produces: tables `trip(id, owner_id fk → user_account on delete cascade, name, start_date date, end_date date, cover_storage_key text null, created_at, updated_at)`, `trip_destination(trip_id fk → trip on delete cascade, position int, name text, pk(trip_id, position))`; JPA `@ElementCollection @OrderColumn(name="position") List<String> destinations`.
- Produces: `TripAccess.requireOwned(UUID tripId, UUID userId): Trip` (throws `NotFoundException`) — every later feature service uses this, never `TripService`.
- Produces: `TripRequest(String name, List<String> destinations, LocalDate startDate, LocalDate endDate)` with Bean Validation per Global Constraints and a class-level check for `endDate >= startDate` and length ≤ 365 days.
- Produces: `TripSummary(UUID id, String name, List<String> destinations, LocalDate startDate, LocalDate endDate, String coverUrl)`; `TripDetail` = same fields as `TripSummary` in this task; Tasks 8, 9, 11 and 13 each add one component (`List<ActivityResponse> activities`, `List<BookingResponse> bookings`, `List<DocumentResponse> documents`, `List<ChecklistResponse> checklists`) and fill it in `TripService`.
- Produces endpoints: `GET /api/trips` (owned trips, ordered by `start_date`), `POST /api/trips` → 201 `TripDetail`, `GET /api/trips/{id}` → `TripDetail`, `PUT /api/trips/{id}` → `TripDetail`, `DELETE /api/trips/{id}` → 204.
- Produces: `OwnershipTest` — parameterized test over a `static Stream<Arguments> endpoints()` of `(HttpMethod, String uriTemplate, String jsonBody)`; for each, user A owns the fixture, user B calls it and gets 404. Each later task adds its rows. Fixture: `@BeforeEach` creates for user A one trip (and later one activity, booking, document, checklist, item — each task extends the fixture), stored in fields the templates reference (`{trip}`, `{activity}`, …).

- [ ] **Step 1: Write failing tests:**

```java
@Test void createAndGetTrip()            // POST {name:"Guatemala e Belize", destinations:["Guatemala","Belize"], startDate:"2027-03-10", endDate:"2027-03-24"} -> 201; GET -> same fields, destinations order kept
@Test void listReturnsOnlyOwnTripsOrderedByStart()
@Test void endBeforeStartIs400()
@Test void tripLongerThan365DaysIs400()  // 2027-01-01..2028-01-02 -> 400; ..2028-01-01 (365 days later) -> 201
@Test void tooManyDestinationsIs400()    // 21 entries
@Test void updateReplacesDestinations()
@Test void deleteRemovesTrip()           // GET afterwards -> 404
@Test void anonymousIs401()
```

`OwnershipTest` rows: `GET /api/trips/{trip}`, `PUT /api/trips/{trip}` (valid body), `DELETE /api/trips/{trip}`; plus assert user B's `GET /api/trips` does not contain A's trip.

- [ ] **Step 2: Run** `./mvnw test -Dtest='TripApiTest,OwnershipTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.** `TripRepository.findByIdAndOwnerId`, `findByOwnerIdOrderByStartDateAsc`. Add `trip`, `trip_destination` to the truncate list.
- [ ] **Step 4: Run** the tests. Expected: PASS.
- [ ] **Step 5: Commit** `feat(trip): trips CRUD with ownership checks`

### Task 8: Activities (itinerary)

**Files:**
- Create: `db/migration/V4__activities.sql`
- Create in `com.travelplanner.itinerary`: `Activity.java`, `ActivityRepository.java`, `ActivityService.java`, `ActivityController.java`, `MapsLinks.java`, dto `ActivityRequest`, `ActivityResponse`, `ReorderRequest`
- Modify: `TripService.java` (fill `activities` in `TripDetail`), `OwnershipTest.java`
- Test: `backend/src/test/java/com/travelplanner/itinerary/ActivityApiTest.java`, `MapsLinksTest.java`

**Interfaces:**
- Consumes: `TripAccess.requireOwned`.
- Produces: table `activity(id, trip_id fk on delete cascade, date date, position int, title, start_time time null, location null, maps_url null, notes null, created_at, updated_at)`, index `(trip_id, date, position)`.
- Produces: `ActivityRequest(LocalDate date, String title, LocalTime startTime, String location, String mapsUrl, String notes)`; `ActivityResponse(UUID id, LocalDate date, int position, String title, LocalTime startTime, String location, String mapsUrl, String mapsLink, String notes)`; `ReorderRequest(LocalDate date, List<UUID> activityIds)`.
- Produces: `MapsLinks.of(String location, String mapsUrl): String` (nullable) — `mapsUrl` if non-blank, else `https://www.google.com/maps/search/?api=1&query=` + `URLEncoder.encode(location, UTF_8)` with `+` replaced by `%20`, else null.
- Produces: `ActivityService.listForTrip(UUID tripId): List<ActivityResponse>` ordered by date, position (used by `TripService`).
- Produces endpoints: `POST /api/trips/{id}/activities` → 201, `PUT /api/activities/{id}`, `DELETE /api/activities/{id}` → 204, `PUT /api/trips/{id}/activities/order` → 204.

- [ ] **Step 1: Write failing tests.** `MapsLinksTest`:

```java
@Test void customUrlWins()          // of("x", "https://maps.app.goo.gl/abc") -> "https://maps.app.goo.gl/abc"
@Test void encodesLocation()        // of("Café & Bar #1, Antigua", null) -> "https://www.google.com/maps/search/?api=1&query=Caf%C3%A9%20%26%20Bar%20%231%2C%20Antigua"
@Test void nullWhenBothBlank()      // of(" ", "") -> null
```

`ActivityApiTest`:

```java
@Test void createAppendsAtEndOfDay()           // two activities on 2027-03-12 -> positions 0, 1
@Test void dateOutsideTripIs400()
@Test void nonHttpsMapsUrlIs400()              // "javascript:alert(1)" and "http://x" -> 400
@Test void reorderSetsPositions()              // order [b, a] -> trip detail lists b before a
@Test void reorderWithIncompleteListIs400()    // ids missing one activity of that date, or containing one from another date/trip -> 400
@Test void movingToAnotherDayAppendsAtEnd()    // PUT with new date -> position = count of that day
@Test void shrinkingTripDatesKeepsActivities() // PUT trip with later startDate -> activity still in trip detail
@Test void detailIncludesMapsLink()
```

`OwnershipTest` rows: `POST /api/trips/{trip}/activities`, `PUT /api/activities/{activity}`, `DELETE /api/activities/{activity}`, `PUT /api/trips/{trip}/activities/order`.

- [ ] **Step 2: Run** `./mvnw test -Dtest='MapsLinksTest,ActivityApiTest,OwnershipTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.** Activity lookups for update/delete join through the trip owner (`findByIdAndTripOwnerId`). Validation of `date` within trip dates happens on create/update only.
- [ ] **Step 4: Run** the tests. Expected: PASS.
- [ ] **Step 5: Commit** `feat(itinerary): activities with reorder and maps links`

### Task 9: Bookings

**Files:**
- Create: `db/migration/V5__bookings.sql`
- Create in `com.travelplanner.booking`: `Booking.java`, `BookingType.java`, `BookingRepository.java`, `BookingService.java`, `BookingController.java`, dto `BookingRequest`, `BookingResponse`
- Modify: `TripService.java` (fill `bookings`), `OwnershipTest.java`
- Test: `backend/src/test/java/com/travelplanner/booking/BookingApiTest.java`

**Interfaces:**
- Produces: table `booking(id, trip_id fk on delete cascade, type text, title, provider null, reference null, start_at timestamp not null, end_at timestamp null, origin null, location null, notes null, created_at, updated_at)`.
- Produces: `enum BookingType { FLIGHT, ACCOMMODATION, TRANSPORT }`; `BookingRequest(BookingType type, String title, String provider, String reference, LocalDateTime startAt, LocalDateTime endAt, String origin, String location, String notes)`; `BookingResponse(UUID id, BookingType type, String title, String provider, String reference, LocalDateTime startAt, LocalDateTime endAt, String origin, String location, String notes)`.
- Produces: `BookingService.listForTrip(UUID tripId)` ordered by `start_at`; `BookingService.requireOwned(UUID bookingId, UUID userId): Booking` (used by Task 11).
- Produces endpoints: `POST /api/trips/{id}/bookings` → 201, `PUT /api/bookings/{id}`, `DELETE /api/bookings/{id}` → 204.

- [ ] **Step 1: Write failing tests:**

```java
@Test void localTimesRoundTripUnchanged()   // startAt "2027-03-10T10:00:00", endAt "2027-03-10T15:40:00" -> same strings back, regardless of JVM default zone (run with TimeZone set to Pacific/Auckland in the test)
@Test void endBeforeStartIs400()
@Test void unknownTypeIs400()
@Test void listOrderedByStart()
@Test void deleteBooking()
```

`OwnershipTest` rows: `POST /api/trips/{trip}/bookings`, `PUT /api/bookings/{booking}`, `DELETE /api/bookings/{booking}`.

- [ ] **Step 2: Run** `./mvnw test -Dtest='BookingApiTest,OwnershipTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.** Map `startAt`/`endAt` as `LocalDateTime` on `timestamp` (no zone) columns; do not set `hibernate.jdbc.time_zone`.
- [ ] **Step 4: Run** the tests. Expected: PASS.
- [ ] **Step 5: Commit** `feat(booking): bookings`

### Task 10: File storage and type detection

**Files:**
- Create in `com.travelplanner.common.storage`: `FileStorage.java`, `FileTypeDetector.java`, `UnsupportedFileTypeException.java`
- Modify: `ApiExceptionHandler.java` (415 for `UnsupportedFileTypeException`, `code: "UNSUPPORTED_FILE_TYPE"`)
- Test: `backend/src/test/java/com/travelplanner/common/storage/FileTypeDetectorTest.java`, `FileStorageTest.java`

**Interfaces:**
- Produces: `FileTypeDetector.detect(byte[] head): Optional<String>` returning `application/pdf` (`%PDF-`), `image/jpeg` (`FF D8 FF`), `image/png` (`89 50 4E 47 0D 0A 1A 0A`), `image/webp` (`RIFF....WEBP`); reads only the first 12 bytes.
- Produces: `FileStorage` (bean, root from `app.storage-dir`, created on startup): `String store(InputStream in)` → new random UUID key, writes to a temp file in the root then atomic move; `Path path(String key)`; `void delete(String key)` (no error if missing); keys validated as UUID strings (rejects path traversal).

- [ ] **Step 1: Write failing tests:**

```java
@Test void detectsAllowedTypes()        // minimal byte headers for each of the 4 types
@Test void rejectsExeRenamedPdf()      // "MZ" header -> empty
@Test void rejectsHeic()               // bytes "....ftypheic" -> empty
@Test void rejectsShortInput()         // 2 bytes -> empty
@Test void storeAndDelete()            // store -> file exists under root; delete -> gone; delete again -> no exception
@Test void pathRejectsNonUuidKey()     // path("../etc/passwd") -> IllegalArgumentException
```

- [ ] **Step 2: Run** `./mvnw test -Dtest='FileTypeDetectorTest,FileStorageTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** the tests. Expected: PASS.
- [ ] **Step 5: Commit** `feat(storage): file storage and type detection`

### Task 11: Documents

**Files:**
- Create: `db/migration/V6__documents.sql`
- Create in `com.travelplanner.document`: `TripDocument.java`, `TripDocumentRepository.java`, `DocumentService.java`, `DocumentController.java`, dto `DocumentResponse`
- Modify: `TripService.java` (fill `documents`; on trip delete remove document files), `OwnershipTest.java`
- Test: `backend/src/test/java/com/travelplanner/document/DocumentApiTest.java`

**Interfaces:**
- Consumes: `TripAccess.requireOwned`, `BookingService.requireOwned`, `FileStorage`, `FileTypeDetector`.
- Produces: table `trip_document(id, trip_id fk on delete cascade, booking_id fk → booking on delete set null, original_filename, content_type, size_bytes bigint, storage_key text unique, created_at, updated_at)`.
- Produces: `DocumentResponse(UUID id, UUID bookingId, String filename, String contentType, long sizeBytes, Instant createdAt, String fileUrl)` with `fileUrl = "/api/documents/{id}/file"`.
- Produces: `DocumentService.listForTrip(UUID tripId)`; `DocumentService.storageKeysForTrip(UUID tripId): List<String>`.
- Produces endpoints: `POST /api/trips/{id}/documents` (multipart `file`, optional `bookingId`) → 201 `DocumentResponse`; `GET /api/documents/{id}/file[?download=true]`; `DELETE /api/documents/{id}` → 204.
- Response headers on file: `Content-Type` = stored type, `X-Content-Type-Options: nosniff`, `Content-Disposition: inline|attachment` built with Spring's `ContentDisposition.builder(...).filename(name, UTF_8)` (emits `filename*`), `Cache-Control: private, no-cache`.

- [ ] **Step 1: Write failing tests:**

```java
@Test void uploadAndDownloadPdf()             // bytes match; Content-Type application/pdf; nosniff; Content-Disposition inline
@Test void downloadParamSetsAttachment()
@Test void unicodeFilenameIsPreserved()       // "Biglietto ✈ Città \"A\".pdf" -> Content-Disposition contains filename*=UTF-8''Biglietto%20%E2%9C%88%20Citt%C3%A0%20%22A%22.pdf; GET trip detail filename equals original
@Test void declaredTypeIsIgnored()            // PDF bytes uploaded as image/png named x.png -> stored contentType application/pdf
@Test void exeRenamedToPdfIs415()             // $.code == "UNSUPPORTED_FILE_TYPE"
@Test void heicIs415()
@Test void fileOver25MbIs413()
@Test void bookingFromAnotherTripIs404()      // bookingId belonging to another of A's trips
@Test void deletingBookingKeepsDocument()     // document's bookingId becomes null, file still downloadable
@Test void deletingDocumentRemovesFile()      // file gone from FileStorage root
@Test void deletingTripRemovesFiles()
@Test void failedInsertRemovesStoredFile()    // @MockitoSpyBean TripDocumentRepository whose save throws -> 500, and no file left in the storage root
```

`OwnershipTest` rows: `POST /api/trips/{trip}/documents` (multipart), `GET /api/documents/{document}/file`, `DELETE /api/documents/{document}`, and upload to B's own trip with A's `bookingId` → 404.

- [ ] **Step 2: Run** `./mvnw test -Dtest='DocumentApiTest,OwnershipTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.** Read the first 12 bytes through a `BufferedInputStream` with `mark/reset`, detect, then store. Order: store file → insert row (on exception delete file and rethrow). Delete: row first, then file. Trip delete: collect keys, delete trip, then delete files.
- [ ] **Step 4: Run** the tests. Expected: PASS.
- [ ] **Step 5: Manual check** (record result in the commit message body): run `mvn spring-boot:test-run`, upload a PDF, open `/api/documents/{id}/file` in Chrome and Safari; it must render inline. If the CSP added in Task 14 later blocks the viewer, Task 14 excludes file responses from CSP.
- [ ] **Step 6: Commit** `feat(document): upload, download and delete documents`

### Task 12: Trip cover image

**Files:**
- Modify: `TripController.java`, `TripService.java` (cover endpoints; `coverUrl` in DTOs; delete cover file on trip delete and on replace), `OwnershipTest.java`
- Test: `backend/src/test/java/com/travelplanner/trip/CoverApiTest.java`

**Interfaces:**
- Produces endpoints: `PUT /api/trips/{id}/cover` (multipart `file`) → 200 `TripSummary`; `GET /api/trips/{id}/cover` → image (same headers as documents, `inline`); `DELETE /api/trips/{id}/cover` → 204.
- Produces: `coverUrl = "/api/trips/{id}/cover?v=" + coverStorageKey`, null without cover.

- [ ] **Step 1: Write failing tests:**

```java
@Test void uploadCoverSetsVersionedUrl()   // coverUrl ends with ?v=<key>; GET returns image/jpeg
@Test void pdfCoverIs415()
@Test void replacingCoverDeletesOldFile()  // new key differs; old file gone
@Test void deleteCover()                   // coverUrl null; GET cover -> 404
```

`OwnershipTest` rows: `PUT/GET/DELETE /api/trips/{trip}/cover`.

- [ ] **Step 2: Run** `./mvnw test -Dtest='CoverApiTest,OwnershipTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.** Only `image/*` results from `FileTypeDetector` are accepted.
- [ ] **Step 4: Run** the tests. Expected: PASS.
- [ ] **Step 5: Commit** `feat(trip): cover image`

### Task 13: Checklists

**Files:**
- Create: `db/migration/V7__checklists.sql`
- Create in `com.travelplanner.checklist`: `Checklist.java`, `ChecklistItem.java`, `ChecklistRepository.java`, `ChecklistItemRepository.java`, `ChecklistService.java`, `ChecklistController.java`, dto `ChecklistRequest`, `ChecklistResponse`, `ItemRequest`, `ItemPatch`, `ItemResponse`
- Modify: `TripService.java` (create defaults on trip creation, fill `checklists`), `OwnershipTest.java`
- Test: `backend/src/test/java/com/travelplanner/checklist/ChecklistApiTest.java`

**Interfaces:**
- Produces: tables `checklist(id, trip_id fk on delete cascade, name, position, created_at, updated_at)`, `checklist_item(id, checklist_id fk on delete cascade, text, done boolean default false, position, created_at, updated_at)`.
- Produces: `ChecklistResponse(UUID id, String name, int position, List<ItemResponse> items)`; `ItemResponse(UUID id, String text, boolean done, int position)`; `ItemPatch(String text, Boolean done)` (null = unchanged).
- Produces: `ChecklistService.createDefaults(Trip trip)` creating "Bagaglio" (position 0) and "Prima di partire" (position 1); `ChecklistService.listForTrip(UUID tripId)`.
- Produces endpoints: `POST /api/trips/{id}/checklists {name}` → 201, `PUT /api/checklists/{id} {name}`, `DELETE /api/checklists/{id}` → 204, `POST /api/checklists/{id}/items {text}` → 201, `PATCH /api/checklist-items/{id}`, `DELETE /api/checklist-items/{id}` → 204.

- [ ] **Step 1: Write failing tests:**

```java
@Test void newTripHasTwoDefaultChecklists() // names in order "Bagaglio", "Prima di partire", no items
@Test void addAndToggleItem()               // POST item -> done false; PATCH {done:true} -> true; text unchanged
@Test void patchTextOnly()
@Test void itemsAppendInOrder()
@Test void deleteChecklistDeletesItems()
@Test void blankItemTextIs400()
```

`OwnershipTest` rows: `POST /api/trips/{trip}/checklists`, `PUT/DELETE /api/checklists/{checklist}`, `POST /api/checklists/{checklist}/items`, `PATCH/DELETE /api/checklist-items/{item}`.

- [ ] **Step 2: Run** `./mvnw test -Dtest='ChecklistApiTest,OwnershipTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.** The default names are UI copy stored as data; keep them as constants in `ChecklistService` with a comment.
- [ ] **Step 4: Run** `./mvnw test` (full suite). Expected: PASS.
- [ ] **Step 5: Commit** `feat(checklist): checklists and items`

### Task 14: Serving the PWA and security headers

**Files:**
- Create: `backend/src/main/java/com/travelplanner/web/SpaWebConfig.java`
- Modify: `SecurityConfig.java` (headers)
- Create: `backend/src/test/resources/static/index.html`, `static/sw.js`, `static/assets/app-abc123.js` (tiny fixtures)
- Test: `backend/src/test/java/com/travelplanner/web/SpaServingTest.java`

**Interfaces:**
- Produces: non-`/api` GET requests for missing resources return `index.html`; `/api/**` misses stay 404 JSON.
- Headers: `/assets/**` → `Cache-Control: public, max-age=31536000, immutable`; `index.html`, `sw.js`, `manifest.webmanifest` → `Cache-Control: no-cache`. On all responses: CSP `default-src 'self'; img-src 'self' blob: data:; style-src 'self' 'unsafe-inline'; object-src 'none'; frame-ancestors 'none'; base-uri 'self'` (Mantine injects inline styles), `Referrer-Policy: same-origin`, `X-Frame-Options: DENY`.

- [ ] **Step 1: Write failing tests:**

```java
@Test void clientRouteFallsBackToIndex()     // GET /trips/123 -> 200, body of index.html
@Test void apiMissIsNot404Html()             // GET /api/nope (authenticated) -> 404 problem json
@Test void hashedAssetsAreImmutable()
@Test void serviceWorkerIsNoCache()          // GET /sw.js -> Cache-Control no-cache
@Test void cspAndFrameHeadersPresent()
```

- [ ] **Step 2: Run** `./mvnw test -Dtest=SpaServingTest`. Expected: FAIL.
- [ ] **Step 3: Implement** with `WebMvcConfigurer.addResourceHandlers` and a `PathResourceResolver` that falls back to `index.html` when the resource is missing and the path does not start with `api/`.
- [ ] **Step 4: Run** `./mvnw test`. Expected: PASS. Repeat the manual PDF check from Task 11; if Chrome's PDF viewer is blocked, skip the CSP header for `/api/documents/*/file` and `/api/trips/*/cover` and add a test asserting it.
- [ ] **Step 5: Commit** `feat(web): serve PWA with cache and security headers`

## Phase B — Frontend

### Task 15: Frontend skeleton, API client, i18n

**Files:**
- Create via template: `frontend/` (`npm create vite@latest frontend -- --template react-ts`)
- Create: `frontend/src/api/client.ts`, `frontend/src/api/types.ts`, `frontend/src/i18n/it.ts`, `frontend/src/App.tsx` (router + `MantineProvider` + `QueryClientProvider`), `frontend/src/test/setup.ts`
- Modify: `frontend/vite.config.ts` (`server.proxy` and `preview.proxy`: `/api` → `http://localhost:8080`, Vitest `environment: 'jsdom'`, `setupFiles`), `frontend/package.json` (scripts `test`, `typecheck`)
- Test: `frontend/src/api/client.test.ts`

**Interfaces:**
- Produces: `apiFetch<T>(path: string, init?: RequestInit): Promise<T>` — prefixes `/api`, sends JSON, `credentials: 'same-origin'`, sets `X-XSRF-TOKEN` from the `XSRF-TOKEN` cookie on non-GET; 204 → `undefined`; non-2xx → throws `ApiError`.
- Produces: `class ApiError extends Error { status: number; code?: string; fieldErrors?: Record<string,string> }`; network failure → throws `NetworkError` (distinct class; Task 17 relies on it).
- Produces: `apiUpload<T>(path: string, form: FormData, method?: 'POST'|'PUT'): Promise<T>`.
- Produces: `types.ts` mirroring backend DTOs: `TripSummary`, `TripDetail`, `Activity`, `Booking` (`type: 'FLIGHT'|'ACCOMMODATION'|'TRANSPORT'`), `DocumentMeta`, `Checklist`, `ChecklistItem`, `Me`, `AuthConfig`. Dates as `string` (`YYYY-MM-DD`), times `HH:mm[:ss]`, local date-times `YYYY-MM-DDTHH:mm[:ss]`.
- Produces: `it.ts` — `export const m = { ... } as const` grouped by screen; components read `m.dashboard.title` etc. No other file contains Italian UI text.
- Dependencies to install: `@mantine/core @mantine/hooks @tanstack/react-query react-router`; dev: `vitest jsdom @testing-library/react @testing-library/user-event @testing-library/jest-dom`.

- [ ] **Step 1: Write failing tests:**

```ts
test('sends XSRF header from cookie on POST', ...)   // document.cookie = 'XSRF-TOKEN=abc'; fetch mock receives header X-XSRF-TOKEN: abc
test('does not send XSRF header on GET', ...)
test('throws ApiError with status, code and field errors', ...) // 400 problem {errors:{name:'...'}} and 403 {code:'EMAIL_NOT_VERIFIED'}
test('throws NetworkError when fetch rejects', ...)
test('returns undefined for 204', ...)
```

- [ ] **Step 2: Run** `cd frontend && npm test -- --run src/api`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `npm test -- --run && npm run lint && npm run typecheck && npm run build`. Expected: all pass.
- [ ] **Step 5: Commit** `feat(frontend): skeleton, api client, italian messages`

### Task 16: Date and itinerary logic

**Files:**
- Create: `frontend/src/lib/dates.ts`, `frontend/src/lib/itinerary.ts`
- Test: `frontend/src/lib/dates.test.ts`, `frontend/src/lib/itinerary.test.ts`

**Interfaces:**
- Produces: `todayLocal(now?: Date): string` (`YYYY-MM-DD` from `getFullYear/getMonth/getDate`); `tripStatus(trip: {startDate: string; endDate: string}, today: string): 'upcoming'|'inProgress'|'past'`; `dayNumber(trip, date: string): number` (1-based); `daysUntil(date: string, today: string): number`; `formatDayHeader(date: string, dayNumber: number): string` → e.g. `"Ven 12 mar · Giorno 3"` (Italian short weekday/month via `Intl.DateTimeFormat('it-IT')`, capitalised).
- Produces: `groupByDay(trip: TripDetail): { days: DaySection[]; outside: Activity[] }` where `DaySection = { date: string; dayNumber: number; activities: Activity[]; bookings: Booking[] }` — one section per trip date; activities by position; a booking appears on `startAt`'s date and, if different, `endAt`'s date; activities and bookings outside the trip dates go to `outside` (activities) or are omitted from days (bookings still appear in the Bookings tab).
- Produces: `nextItems(trip: TripDetail, now: Date): { activities: Activity[]; booking?: Booking }` — up to 3 activities from today on (today's untimed ones count; timed ones only if `startTime` ≥ now's local time), and the first booking with `startAt` ≥ now (local).

- [ ] **Step 1: Write failing tests:**

```ts
test('todayLocal uses local date, not UTC', ...) // vi.setSystemTime(new Date(2027, 2, 12, 23, 30)) with TZ=America/Guatemala in vitest env -> '2027-03-12'
test('tripStatus boundaries', ...)               // today == start -> inProgress; == end -> inProgress; day after end -> past
test('formatDayHeader', ...)                     // ('2027-03-12', 3) -> 'Ven 12 mar · Giorno 3'
test('booking spanning days appears on start and end day only', ...) // accommodation 10th→14th: in sections 10 and 14, not 11–13
test('overnight flight appears on both days', ...)
test('activities outside trip dates go to outside', ...)
test('nextItems skips past timed activities today', ...)
```

Set `TZ: 'America/Guatemala'` for the test process (`"test": "TZ=America/Guatemala vitest"` in package.json).

- [ ] **Step 2: Run** `npm test -- --run src/lib`. Expected: FAIL.
- [ ] **Step 3: Implement.** Parse `YYYY-MM-DD` by splitting into numbers; never `new Date('YYYY-MM-DD')` (parsed as UTC).
- [ ] **Step 4: Run** the tests. Expected: PASS.
- [ ] **Step 5: Commit** `feat(frontend): date and itinerary logic`

### Task 17: Auth screens and session handling

**Files:**
- Create in `frontend/src/auth/`: `useMe.ts`, `RequireAuth.tsx`, `LoginPage.tsx`, `SignupPage.tsx`, `VerifyEmailPage.tsx`, `ForgotPasswordPage.tsx`, `ResetPasswordPage.tsx`
- Modify: `App.tsx` (routes `/login`, `/signup`, `/verify-email`, `/forgot-password`, `/reset-password`; everything else inside `RequireAuth`), `it.ts`
- Test: `frontend/src/auth/RequireAuth.test.tsx`

**Interfaces:**
- Consumes: `apiFetch`, `ApiError`, `NetworkError`.
- Produces: `useMe()` — TanStack query `['me']` on `GET /auth/me`, `retry: false`, returns `{ me?: Me; status: 'loading'|'authenticated'|'anonymous'|'offline' }`: `ApiError` 401 → `anonymous`; `NetworkError` → `offline` (keep last known `me` from the persisted cache, Task 24).
- Produces: `RequireAuth` — `anonymous` → redirect to `/login`; `offline` with a cached `me` → render children; `loading` → loader.
- Produces: `logout(queryClient)` in `useMe.ts` — `POST /auth/logout`, then `clearOfflineData()` (stub here that clears the query client; Task 24 extends it), navigate to `/login`.
- Login form errors: 401 → `m.auth.wrongCredentials`; 403 `EMAIL_NOT_VERIFIED` → `m.auth.notVerified` with a "resend" link; 429 → `m.common.tooManyRequests`. Sign-up link shown only if `GET /auth/config` says `registrationOpen`. `VerifyEmailPage` / `ResetPasswordPage` read `token` from the query string.

- [ ] **Step 1: Write failing tests:**

```ts
test('redirects to login on 401', ...)
test('renders children when offline with cached user', ...)   // fetch rejects -> NetworkError; queryClient pre-seeded with ['me'] -> protected content visible, no redirect
test('network error does not clear cached data', ...)         // after offline render, queryClient still has ['trips'] data
```

- [ ] **Step 2: Run** `npm test -- --run src/auth`. Expected: FAIL.
- [ ] **Step 3: Implement** pages with Mantine `TextInput`/`PasswordInput`/`Button`; all text from `m`.
- [ ] **Step 4: Run** `npm test -- --run && npm run typecheck`. Expected: PASS. Manual: with backend on `spring-boot:test-run` and `npm run dev`, sign up, open the link logged in the backend console, log in.
- [ ] **Step 5: Commit** `feat(frontend): auth screens and session handling`

### Task 18: Dashboard and trip form

**Files:**
- Create: `frontend/src/api/queries.ts`, `frontend/src/lib/images.ts`, `frontend/src/trips/DashboardPage.tsx`, `TripCard.tsx`, `TripFormPage.tsx`
- Modify: `App.tsx` (routes `/`, `/trips/new`, `/trips/:id/edit`), `it.ts`
- Test: `frontend/src/trips/DashboardPage.test.tsx`

**Interfaces:**
- Produces in `queries.ts`: `useTrips()` (`['trips']`), `useTrip(id)` (`['trip', id]`), `useSaveTrip()`, `useDeleteTrip()`, `useUploadCover()`, `useDeleteCover()`; every mutation invalidates `['trips']` and `['trip', id]`. Later tasks add their mutations here, each invalidating `['trip', tripId]`.
- Produces: `compressImage(file: File, maxEdge: number, quality = 0.8): Promise<Blob>` — `createImageBitmap` + canvas → `image/jpeg`; returns the original file if it is not JPEG/PNG/WebP or already within `maxEdge`. Cover `maxEdge` 1600, documents 2560.
- Dashboard: sections in-progress (with "Oggi" button → `/trips/:id/itinerary?day=<today>`), upcoming (start asc), past (end desc), using `tripStatus(trip, todayLocal())`. Empty state with "Nuovo viaggio".
- Trip form: name, destinations (add/remove rows), start/end `<input type="date">`, cover picker; shows server field errors from `ApiError.fieldErrors`. Delete trip with confirmation modal.

- [ ] **Step 1: Write failing tests:**

```ts
test('groups trips into in progress, upcoming and past in the right order', ...) // fixed system time; 4 trips
test('Today button links to current day', ...)
```

- [ ] **Step 2: Run** `npm test -- --run src/trips`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `npm test -- --run && npm run typecheck`. Expected: PASS. Manual: create, edit, delete a trip with cover on a phone-sized viewport (375 px).
- [ ] **Step 5: Commit** `feat(frontend): dashboard and trip form`

### Task 19: Trip page with tabs and summary

**Files:**
- Create: `frontend/src/trips/TripPage.tsx`, `SummaryTab.tsx`
- Modify: `App.tsx` (routes `/trips/:id/:tab?` with tabs `summary|itinerary|bookings|documents|checklists`), `it.ts`
- Test: `frontend/src/trips/SummaryTab.test.tsx`

**Interfaces:**
- Consumes: `useTrip`, `tripStatus`, `dayNumber`, `daysUntil`, `nextItems`.
- Produces: `TripPage` header (cover, name, destinations, dates, `m.trip.inDays(n)` or `m.trip.dayOf(n, total)`), a fixed bottom tab bar, renders the tab component for `:tab` (default `summary`). Tabs receive `{ trip: TripDetail }` as props.

- [ ] **Step 1: Write failing tests:**

```ts
test('shows "tra 12 giorni" before the trip', ...)
test('shows "Giorno 3 di 10" during the trip', ...)
test('summary lists next activities and next booking', ...)
```

- [ ] **Step 2: Run** `npm test -- --run src/trips/SummaryTab`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `npm test -- --run && npm run typecheck`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(frontend): trip page and summary`

### Task 20: Itinerary tab

**Files:**
- Create: `frontend/src/trips/ItineraryTab.tsx`, `ActivityForm.tsx`
- Modify: `queries.ts` (`useSaveActivity`, `useDeleteActivity`, `useReorderActivities`), `it.ts`
- Test: `frontend/src/trips/ItineraryTab.test.tsx`

**Interfaces:**
- Consumes: `groupByDay`, `formatDayHeader`, `todayLocal`.
- Produces: one section per `DaySection` with `id="day-YYYY-MM-DD"`; on mount scrolls to `?day=` or, if the trip is in progress, to today. Activity row: time, title, location, Maps link (`target="_blank" rel="noopener"`), notes; ↑/↓ buttons call `PUT /trips/{id}/activities/order` with the day's ids swapped; first ↑ / last ↓ disabled. Booking rows in the day are read-only links to the Bookings tab. An "outside the trip dates" section lists `outside` activities when non-empty. Add activity per day opens `ActivityForm` (date prefilled).

- [ ] **Step 1: Write failing tests:**

```ts
test('renders a section per day with header', ...)
test('move down sends reordered ids for that day', ...) // fetch mock asserts body {date, activityIds:[b,a]}
test('first item has move-up disabled', ...)
test('shows outside-dates section only when needed', ...)
```

- [ ] **Step 2: Run** `npm test -- --run src/trips/ItineraryTab`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `npm test -- --run && npm run typecheck`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(frontend): itinerary tab`

### Task 21: Bookings tab

**Files:**
- Create: `frontend/src/trips/BookingsTab.tsx`, `BookingForm.tsx`, `BookingDetail.tsx`
- Modify: `queries.ts` (`useSaveBooking`, `useDeleteBooking`), `it.ts`
- Test: `frontend/src/trips/BookingForm.test.tsx`

**Interfaces:**
- Produces: chronological list with an icon per type; detail at `/trips/:id/bookings/:bookingId` showing all fields, attached documents (filter `trip.documents` by `bookingId`; Task 22 adds the upload button here). Form uses `<input type="datetime-local">`; values sent as typed (`YYYY-MM-DDTHH:mm`), never converted with `Date`.

- [ ] **Step 1: Write failing test:**

```ts
test('submits local date-times unchanged', ...) // type 2027-03-10T10:00 -> request body startAt === '2027-03-10T10:00'
test('shows field error from server', ...)
```

- [ ] **Step 2: Run** `npm test -- --run src/trips/BookingForm`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `npm test -- --run && npm run typecheck`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(frontend): bookings tab`

### Task 22: Documents tab

**Files:**
- Create: `frontend/src/trips/DocumentsTab.tsx`, `DocumentUploadButton.tsx`
- Modify: `queries.ts` (`useUploadDocument`, `useDeleteDocument`), `BookingDetail.tsx` (upload button), `it.ts`
- Test: `frontend/src/trips/DocumentUploadButton.test.tsx`

**Interfaces:**
- Produces: `DocumentUploadButton({ tripId, bookingId? })` — accepts `application/pdf,image/jpeg,image/png,image/webp`; images go through `compressImage(file, 2560)` keeping the original filename; 415 → `m.documents.unsupportedType` (mentions PDF/JPEG/PNG/WebP and suggests exporting iPhone photos as JPEG); 413 → `m.documents.tooLarge`.
- Documents list: filename, size, linked booking title; tap opens `fileUrl` in a new tab; download link `fileUrl?download=true`; delete with confirmation. "Rendi disponibili offline" button — placeholder disabled here, wired in Task 24.

- [ ] **Step 1: Write failing tests:**

```ts
test('shows Italian message on 415', ...)
test('shows Italian message on 413', ...)
test('sends bookingId when given', ...)
```

- [ ] **Step 2: Run** `npm test -- --run src/trips/DocumentUploadButton`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `npm test -- --run && npm run typecheck`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(frontend): documents tab`

### Task 23: Checklists tab

**Files:**
- Create: `frontend/src/trips/ChecklistsTab.tsx`
- Modify: `queries.ts` (`useSaveChecklist`, `useDeleteChecklist`, `useAddItem`, `usePatchItem`, `useDeleteItem`), `it.ts`
- Test: `frontend/src/trips/ChecklistsTab.test.tsx`

**Interfaces:**
- Produces: per checklist a progress bar and `done/total` label, checkbox items (toggle → `PATCH {done}` with optimistic update), inline edit of item text, quick-add input (Enter submits and keeps focus), add/rename/delete checklist.

- [ ] **Step 1: Write failing tests:**

```ts
test('shows 1/3 progress', ...)
test('toggling sends PATCH and updates optimistically', ...)
test('enter in quick-add posts item and clears input', ...)
```

- [ ] **Step 2: Run** `npm test -- --run src/trips/ChecklistsTab`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `npm test -- --run && npm run typecheck`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(frontend): checklists tab`

### Task 24: PWA and offline viewing

**Files:**
- Create in `frontend/src/offline/`: `persistence.ts`, `useOnline.ts`, `OfflineBanner.tsx`, `documentsCache.ts`, `UpdatePrompt.tsx`
- Create: `frontend/public/icons/` (192, 512, maskable 512 PNG), `frontend/src/sw.ts` only if `injectManifest` is needed (prefer `generateSW`)
- Modify: `vite.config.ts` (`VitePWA`), `App.tsx` (`PersistQueryClientProvider`, banner, prompt), `DashboardPage.tsx` (prefetch), `DocumentsTab.tsx` (offline button), `useMe.ts` (`clearOfflineData`), every form/button that mutates (disable when offline), `it.ts`
- Test: `frontend/src/offline/persistence.test.ts`, `OfflineBanner.test.tsx`

**Interfaces:**
- Produces: `VitePWA({ registerType: 'prompt', manifest: { name: 'Travel Planner', short_name: 'Viaggi', lang: 'it', display: 'standalone', start_url: '/', icons }, workbox: { navigateFallback: '/index.html', navigateFallbackDenylist: [/^\/api\//], runtimeCaching: [{ urlPattern: /\/api\/documents\/[^/]+\/file$/ and /\/api\/trips\/[^/]+\/cover/, handler: 'CacheFirst', options: { cacheName: 'documents', cacheableResponse: { statuses: [200] } } }] } })` — no other `/api` route cached by the service worker. Downloads with `?download=true` are not cached.
- Produces: `createPersister()` using `@tanstack/query-async-storage-persister` over `idb-keyval`; `PersistQueryClientProvider` with `maxAge` 30 days and `buster` = app version; persisted state includes `ownerId` (current user id).
- Produces: `clearOfflineData(queryClient)` — `queryClient.clear()`, remove persisted state, `caches.delete('documents')`. Called on logout and when `useMe` returns a user id different from the persisted `ownerId`.
- Produces: `useOnline(): boolean` (`navigator.onLine` + `online`/`offline` events; also `false` after a `NetworkError` until the next successful request). `OfflineBanner` shows `m.offline.banner(time)` with the latest `dataUpdatedAt` among cached queries; mutating controls use `disabled={!online}`.
- Produces: `makeTripOffline(trip: TripDetail): Promise<{ ok: number; failed: number }>` — `fetch` each `fileUrl` and the cover URL (the service worker caches them); button shows the result.
- Dashboard prefetch: after `useTrips` succeeds and online, `queryClient.prefetchQuery(['trip', id])` for every in-progress and upcoming trip.
- `UpdatePrompt` uses `useRegisterSW` from `virtual:pwa-register/react`: shows `m.pwa.updateAvailable` with an "Aggiorna" button calling `updateServiceWorker(true)`.

- [ ] **Step 1: Write failing tests:**

```ts
test('clearOfflineData clears query cache, persisted state and documents cache', ...) // mock caches + idb-keyval
test('different user triggers clear', ...)
test('banner shows last update time when offline', ...)
test('mutating controls are disabled offline', ...) // render ChecklistsTab with useOnline mocked false -> checkboxes and add input disabled
```

- [ ] **Step 2: Run** `npm test -- --run src/offline`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `npm test -- --run && npm run typecheck && npm run build`. Expected: PASS; `dist/` contains `sw.js` and `manifest.webmanifest`.
- [ ] **Step 5: Manual check:** `npm run build && npm run preview` with backend running (preview proxy `/api` too); in Chrome DevTools set offline, reload: dashboard, a prefetched trip and an opened document still show; edit buttons disabled.
- [ ] **Step 6: Commit** `feat(frontend): PWA install and offline viewing`

## Phase C — Delivery

### Task 25: Docker image and production compose

**Files:**
- Create: `Dockerfile`, `.dockerignore`, `deploy/docker-compose.yml`, `deploy/.env.example`
- Test: `deploy/smoke-test.sh`

**Interfaces:**
- `Dockerfile` stages: `node:22` (`npm ci && npm run build` in `frontend/`) → `maven:3.9-eclipse-temurin-21` (copy `frontend/dist` to `backend/src/main/resources/static/`, `./mvnw -B package -DskipTests`) → `eclipse-temurin:21-jre` (user `app` uid 10001, `/data/files` owned by it, `ENTRYPOINT ["java","-jar","/app/app.jar"]`, `SPRING_PROFILES_ACTIVE=prod`, `HEALTHCHECK` with `wget`/`curl` on `/actuator/health`, ensure the tool exists in the image).
- `docker-compose.yml`: service `app` (build `..`, `ports: ["${APP_BIND_IP}:8081:8080"]`, `env_file: .env`, volume `files:/data/files`, `depends_on: db: condition: service_healthy`); service `db` (`postgres:18.6`, volume `db-data`, `pg_isready` healthcheck, no ports); service `backup` (added in Task 26).
- `.env.example`: `APP_BIND_IP`, `APP_BASE_URL`, `APP_REGISTRATION_OPEN=false`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `DB_URL=jdbc:postgresql://db:5432/${POSTGRES_DB}`, `DB_USER`, `DB_PASSWORD`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, `MAIL_FROM`.
- `smoke-test.sh`: copies `.env.example` to a temp env with `APP_BIND_IP=127.0.0.1` and test passwords, `docker compose -p tp-smoke up -d --build`, waits for health, creates a user with `printf 'supersecret1\nsupersecret1\n' | docker compose run --rm -T app create-user smoke@example.it`, logs in via `curl` (cookie jar + XSRF), checks `GET /` returns HTML and `GET /api/trips` returns `[]`, then `down -v`.

- [ ] **Step 1: Write** `smoke-test.sh` (it fails now: no Dockerfile).
- [ ] **Step 2: Run** `bash deploy/smoke-test.sh`. Expected: FAIL.
- [ ] **Step 3: Implement** Dockerfile, compose, `.env.example`.
- [ ] **Step 4: Run** `bash deploy/smoke-test.sh`. Expected: prints `SMOKE OK`, exit 0. Also `docker compose -p tp-smoke run --rm app id -u` prints `10001`.
- [ ] **Step 5: Commit** `feat(deploy): docker image and compose`

### Task 26: Backups and operations guide

**Files:**
- Create: `deploy/backup.sh`, `deploy/backup-check.sh`, `deploy/backup.env.example`, `docs/operations.md`
- Modify: `deploy/docker-compose.yml` (service `backup`)
- Test: `deploy/backup-test.sh`

**Interfaces:**
- `backup` service: image `restic/restic:0.19.1`, `profiles: ["tools"]`, `env_file: backup.env`, volumes `files:/data/files:ro` and `restic-cache:/root/.cache/restic`.
- `backup.env.example`: `RESTIC_REPOSITORY=s3:https://<swiss-backup-s3-endpoint>/<bucket>`, `RESTIC_PASSWORD`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_DEFAULT_REGION` (values from the Infomaniak Swiss Backup panel).
- `backup.sh` (run from `deploy/`, `set -euo pipefail`): `restic snapshots >/dev/null || restic init` on first run; `docker compose exec -T db pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB" | docker compose run --rm -T backup backup --stdin --stdin-filename db.sql --tag db`; `docker compose run --rm backup backup /data/files --tag files`; `docker compose run --rm backup forget --prune --keep-daily 14 --keep-weekly 8 --keep-monthly 6`. Any failing step exits non-zero (cron mails/logs it).
- `backup-check.sh`: `docker compose run --rm backup check`.
- `backup-test.sh`: runs `backup.sh` against the smoke stack with a throwaway local repo (`RESTIC_REPOSITORY=/repo`, extra `-v <tmpdir>:/repo` via a compose override file created by the test), then restores into a second temp dir and asserts `db.sql` contains `CREATE TABLE public.trip` and a document uploaded during the test is restored byte-identical.
- `docs/operations.md` (English): server prerequisites (rootless Docker, `loginctl enable-linger <user>`), first deploy, `.env` and `backup.env` (`chmod 600`), ufw rule `ufw allow from <NPM-IP> to any port 8081 proto tcp`, NPM proxy host (forward to `<internal-IP>:8081`, websockets off, `client_max_body_size 25m;` in Advanced), create first user, updating (`git pull && docker compose up -d --build`), opening registration, crontab lines (`15 3 * * * cd ~/travel-planner/deploy && ./backup.sh >> ~/backup.log 2>&1`, `45 4 * * 0 … ./backup-check.sh`), keeping the restic password in a password manager, full restore procedure (`restic restore latest --tag files`, `restic dump latest --tag db db.sql | docker compose exec -T db psql …` into an empty database), and "test the restore once after the first deploy".

- [ ] **Step 1: Write** `backup-test.sh`.
- [ ] **Step 2: Run** `bash deploy/backup-test.sh`. Expected: FAIL (no backup service/scripts).
- [ ] **Step 3: Implement** the service, scripts, env example and `docs/operations.md`.
- [ ] **Step 4: Run** `bash deploy/backup-test.sh` and `shellcheck deploy/*.sh`. Expected: `BACKUP OK`; shellcheck clean.
- [ ] **Step 5: Commit** `feat(deploy): restic backups and operations guide`

### Task 27: End-to-end test and CI

**Files:**
- Create: `frontend/playwright.config.ts`, `frontend/e2e/main-flow.spec.ts`, `frontend/e2e/fixtures/ticket.pdf` (tiny valid PDF), `.github/workflows/ci.yml`
- Modify: `frontend/package.json` (script `e2e`), `deploy/smoke-test.sh` (option `--keep` to leave the stack running for e2e)

**Interfaces:**
- `playwright.config.ts`: `baseURL` from `E2E_BASE_URL` (default `http://127.0.0.1:8081`), Chromium only, viewport 390×844.
- `main-flow.spec.ts`: logs in as the smoke user, creates "Guatemala e Belize" with dates containing today, adds an activity on today with location "Tikal", uploads `ticket.pdf`, opens it once, presses "Rendi disponibili offline", then `context.setOffline(true)`, reloads, asserts: dashboard shows the trip, itinerary shows "Tikal" for today, the documents tab lists `ticket.pdf` and opening it returns the PDF, the add-activity button is disabled, and the offline banner is visible.
- `ci.yml` jobs on push and pull_request: `backend` (`setup-java` 21 temurin, `./mvnw -B verify` — Docker is available on `ubuntu-latest` for Testcontainers); `frontend` (`setup-node` 22, `npm ci`, `npm run lint`, `npm run typecheck`, `npm test -- --run`, `npm run build`); `e2e` (needs both; `bash deploy/smoke-test.sh --keep`, `npx playwright install --with-deps chromium`, `npm run e2e`, then compose down).

- [ ] **Step 1: Write** `main-flow.spec.ts`.
- [ ] **Step 2: Run** `bash deploy/smoke-test.sh --keep && cd frontend && npm run e2e`. Expected: PASS if Tasks 15–25 are correct; any failure is a real bug to fix in the owning task's code. (Locally in this container use the preinstalled Chromium: `PLAYWRIGHT_BROWSERS_PATH` is set; do not run `playwright install`.)
- [ ] **Step 3: Write** `ci.yml`.
- [ ] **Step 4: Push** the branch and check that all three CI jobs pass on GitHub.
- [ ] **Step 5: Commit** `test: end-to-end main flow and CI`
