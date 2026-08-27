# CapyBee

CapyBee ("Razem budujemy nowy ul" / "Together we build a new hive") is a
Spring Boot + React companion app for ~12-year-olds adapting to a new
country. It pairs a daily mood check-in with small real-world missions, a
private friendship tracker, and an "Old World / New World" memory space, all
visualized on a growing honeycomb progress map.

## Repository Layout

- `app/server` - Spring Boot backend, security, persistence, and API endpoints
- `app/ui` - React frontend, landing page, and app shell
- `docs` - concept notes and implementation specifications

## Implemented Functionality

- Google account sign-in (Spring Security OAuth2) with server-side sessions
  and a localStorage restore-token fallback for installed PWAs
- Child profile setup (nickname, birth year, locale, avatar)
- Daily mood check-in (heavy / okay / good) with optional note and bilingual
  CapyBee responses
- Honeycomb progress map combining check-ins, missions, friendships, and
  memories into one growing hive
- Missions ("Capy Quests") with categories, completion notes, and history
- Private friendship tracker (noticed / was nice / talked / want to know
  better) - no social features between users
- Old World / New World memory space with favorites
- Full EN/PL bilingual UI
- Installable PWA with offline app shell and an offline write queue (IndexedDB)
  that syncs check-ins, missions, friendships, and memories once back online
- Onboarding tutorial for first-time users
- Single-container Fly.io deployment with a private Postgres instance

## Stack

- Backend: Java 25, Spring Boot 4.1, Spring Security (OAuth2 client),
  Spring Data JPA, Spring Session JDBC, Flyway, Maven
- Frontend: React 19, TypeScript, Vite 6, Tailwind CSS 3, Framer Motion,
  `vite-plugin-pwa`, `idb-keyval` (offline queue)
- Database: PostgreSQL
- Deployment: Fly.io (single container, backend serves the React build)

## Local Development

The project is scaffolded as a mono-repo, so the backend and frontend can be developed independently and deployed together.

### Suggested workflow

1. Copy `.env.example` to `.env` and fill in the Google OAuth and database values.
2. Run the backend from `app/server` with Java 25 and PostgreSQL available.
3. Run the UI from `app/ui` with Node.js and Vite.
4. Build the Docker image from the repo root and deploy it to Fly.io.

### Fast local workflow script

Use the workflow script from the repository root when you want to validate changes quickly:

1. `scripts\dev-workflow.cmd -Action all -ForceRestart`
2. `scripts\dev-workflow.cmd -Action sync`
3. `scripts\dev-workflow.cmd -Action start`
4. `scripts\dev-workflow.cmd -Action stop`

What each action does:

- `all`: builds UI, copies `app/ui/dist` to both backend static folders, then starts backend + frontend
- `sync`: builds UI and copies static files only
- `start`: starts backend + frontend only
- `stop`: stops the processes started by this workflow

The script syncs UI artifacts to:

- `app/server/src/main/resources/static`
- `app/server/target/classes/static`

You can also run the same actions from VS Code tasks:

- `Workflow: Sync UI + Static`
- `Workflow: Build + Copy + Start All`
- `Workflow: Stop All`

### Fly.io notes

- `fly.toml` expects the app to listen on port `8080`.
- `Dockerfile` builds the UI first, then packages the backend with the static assets.
- Set `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `GOOGLE_CLIENT_ID`, and `GOOGLE_CLIENT_SECRET` as Fly secrets.

