# 14 — Session Persistence Across Fly.io Cold Starts

## Purpose

Fix the actual cause of "kid was logged in, comes back later, has to sign in again": `SecurityConfig.java` uses Spring Security's default in-memory `HttpSession` store. `fly.toml` has `auto_stop_machines = "stop"` and `min_machines_running = 0`, so the single Fly machine stops when idle. Every stop wipes all in-memory sessions. A returning child isn't just facing a slow request — they're bounced to the Google OAuth screen every time the app has slept, regardless of how long the browser cookie itself would otherwise last.

This spec moves session storage into the existing Fly Postgres database via Spring Session JDBC, and makes the session cookie itself long-lived so it survives the app being closed and reopened. This must ship before or alongside `14-offline-write-queue-instruction.md` — the offline queue only helps once the user is reliably still logged in.

No frontend changes required for this spec.

---

## 1. Add Spring Session JDBC dependency

### `app/server/pom.xml`

Add inside `<dependencies>`, near the other `spring-boot-starter-*` entries:

```xml
<dependency>
  <groupId>org.springframework.session</groupId>
  <artifactId>spring-session-jdbc</artifactId>
</dependency>
```

No version needed — it's managed by `spring-boot-starter-parent` (already `3.4.2` in this project).

---

## 2. Add the Spring Session schema via Flyway

Spring Session ships an official Postgres schema. Rather than letting Spring Session auto-create tables (`spring.session.jdbc.initialize-schema=always`), add it as a proper Flyway migration so it's versioned like the rest of the schema in `db/migration/`.

### `app/server/src/main/resources/db/migration/V6__spring_session_schema.sql`

```sql
CREATE TABLE SPRING_SESSION (
    PRIMARY_ID CHAR(36) NOT NULL,
    SESSION_ID CHAR(36) NOT NULL,
    CREATION_TIME BIGINT NOT NULL,
    LAST_ACCESS_TIME BIGINT NOT NULL,
    MAX_INACTIVE_INTERVAL INT NOT NULL,
    EXPIRY_TIME BIGINT NOT NULL,
    PRINCIPAL_NAME VARCHAR(100),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36) NOT NULL,
    ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES BYTEA NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
);
```

This is the standard `org/springframework/session/jdbc/schema-postgresql.sql` shipped with `spring-session-jdbc`, copied in so Flyway owns it. Do not also enable `spring.session.jdbc.initialize-schema` — leave it unset/`never` since Flyway handles creation.

---

## 3. Configure Spring Session

### `app/server/src/main/resources/application.yml`

Add under the existing `spring:` block (alongside `datasource`, `jpa`, etc.):

```yaml
  session:
    store-type: jdbc
    jdbc:
      initialize-schema: never
    timeout: 30d
```

`timeout: 30d` extends the session lifetime from Spring's default 30 minutes to 30 days — a check-in app for a kid who might open it once a day or less shouldn't force a re-login on every visit. Adjust downward later if COPPA/parental-control requirements call for a shorter window; 30 days is a reasonable MVP default given no sensitive payment data is involved.

---

## 4. Make the session cookie itself long-lived

By default, Spring's `JSESSIONID` cookie has no `Max-Age`, which makes it a browser session cookie — some mobile browsers will drop it when the browser process is fully killed, independent of server-side session lifetime. Set an explicit max age so the cookie persists across app closes too.

### `app/server/src/main/resources/application.yml`

Add under `server:` (alongside the existing `port` and `error` keys):

```yaml
server:
  port: ${PORT:8080}
  error:
    include-message: always
  servlet:
    session:
      cookie:
        max-age: 30d
        same-site: lax
```

`same-site: lax` keeps the existing OAuth2 redirect flow working (Google's redirect back to `/login/oauth2/code/google` is a top-level GET, which `lax` allows) while still giving reasonable CSRF protection.

---

## 5. Verify after deploy

Manual check once deployed to Fly:

1. Sign in, confirm `/api/auth-status` returns `authenticated: true`.
2. Force the machine to stop (`fly machine stop <id>`, or just wait out the idle timeout).
3. Reopen the app after the machine has fully stopped. Expect: a normal cold-start delay (still addressed separately by `14`/`15`), but landing in the authenticated home — **not** the sign-in screen.
4. Confirm rows appear in the `spring_session` table in Postgres after login (`SELECT * FROM spring_session;`), confirming sessions are actually being persisted rather than silently falling back to in-memory.

---

## Out of scope for this pass

- Any change to the OAuth2 login flow itself (`SecurityConfig.java` authorization rules are unchanged).
- Refresh-token handling for the Google OAuth client — not needed since this is login-session persistence, not an API access-token refresh problem.
- Reducing the 30-day window per-user or adding a "remember me" toggle — flag as a future parental-control setting if it comes up, not required for MVP.

---

## Traceability

- Directly enables the "user was already logged in, comes back later" requirement raised in the offline-sync discussion; without this, `14-offline-write-queue-instruction.md` only papers over a symptom while the real cause (forced re-auth) remains.
- Touches `SCR-01`/`SCR-02`/`SCR-10` (Landing, Auth Redirect, Session Expired) from `04-screen-descriptions.md` — after this change, the `SCR-10` "Session Expired" state should become rare, triggered only by genuine 30-day inactivity or explicit logout, not by routine Fly machine sleep/wake cycles.
- Supports US-018 (data stays private within the family account) indirectly — no session behavior change affects data exposure, but confirms the auth boundary is stable rather than accidentally reset by infrastructure behavior.
