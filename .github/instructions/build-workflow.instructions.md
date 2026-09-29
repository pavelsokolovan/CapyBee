---
applyTo: "app/**/*,Dockerfile,fly.toml"
---

# Build, deployment, and development workflow

Reference for local development setup, CI/CD, and containerization.

## Local development workflow

### UI build → Spring Boot static resources

When you modify `app/ui/src/`:

1. Run `npm run build` in `app/ui` to generate `dist/`
2. Copy `dist/*` into **both**:
   - `app/server/src/main/resources/static` (for next build)
   - `app/server/target/classes/static` (for current dev server)

**Why both?** The running dev server reads from `target/classes/static`; a stale copy is a common source of "missing feature" bugs.

**Automation:** Use VS Code tasks:
- `Workflow: Sync UI + Static` — just copy (for quick iteration)
- `Workflow: Build + Copy + Start All` — full build, copy, and restart all servers

Or run `scripts/dev-workflow.ps1 -Action sync` / `-Action all -ForceRestart` directly.

### Running local servers

- **`Dev: Start All`** task: starts Spring Boot (8080) and Vite dev server in parallel.
- Or run individually:
  - Backend: `mvn spring-boot:run` in `app/server/`
  - Frontend: `npm run dev` in `app/ui/`

## Containerization and deployment

### Build process

- Root `Dockerfile` orchestrates the build:
  1. Builds UI (`npm run build` → `dist/`)
  2. Copies `dist/` into `app/server/src/main/resources/static/`
  3. Builds Spring Boot JAR with embedded static resources
  4. Final image runs the single JAR

### Fly.io deployment

- `fly.toml` defines app configuration.
- Environment: `SPRING_PROFILES_ACTIVE=fly`
- Database: private Postgres network (managed by Fly.io).
- **Never hardcode fly-specific config outside `application-fly.yml`.**

## Verification before shipping

- **Backend:** Run `mvn verify` in `app/server/` (JUnit 5 + JaCoCo 70% gate on `service.*`).
- **Frontend:** Run `npm test` (or `npm run test:coverage`) in `app/ui/` (Vitest).
- **Integration:** Run the full `Dev: Start All` locally and manually test key user flows before pushing.

## Common gotchas

- Stale UI static files causing "feature not showing" bugs — always re-copy after build.
- Missing JaCoCo tests preventing backend verification — add tests, don't lower the threshold.
- Fly.io config changes checked into version control instead of `application-fly.yml` — use profiles.
