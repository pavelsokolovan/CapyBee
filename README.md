# CapyBee

CapyBee is a Spring Boot + React web app for kids who are adapting to a new country. The first release focuses on Google sign-in, a welcoming landing page, and a small set of database-backed user data.

## Repository Layout

- `app/server` - Spring Boot backend, security, persistence, and API endpoints
- `app/ui` - React frontend, landing page, and app shell
- `docs` - concept notes and implementation specifications

## Initial Scope

- Google account sign-in and account bootstrap
- First landing page with clear entry points into the app
- Database storage for user profile, check-ins, and early app activity
- Fly.io deployment using a single container

## Stack

- Backend: Java 21, Spring Boot, Spring Security, Spring Data JPA, Flyway
- Frontend: React, TypeScript, Vite, Tailwind CSS
- Database: PostgreSQL
- Deployment: Fly.io

## Local Development

The project is scaffolded as a mono-repo, so the backend and frontend can be developed independently and deployed together.

### Suggested workflow

1. Copy `.env.example` to `.env` and fill in the Google OAuth and database values.
2. Run the backend from `app/server` with Java 21 and PostgreSQL available.
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

