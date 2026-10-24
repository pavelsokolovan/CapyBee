---
description: "Use when building, testing, syncing static assets, running local servers, changing CI workflows, or configuring deployment. Owns verification commands and gates."
applyTo: "app/engine/**/*,app/pom.xml,app/**/*,Dockerfile,fly.toml,scripts/dev-workflow.ps1,.vscode/tasks.json,.github/workflows/**/*.yml,.github/workflows/**/*.yaml"
---

# Build And Verification

Owns development commands and final application gates. Reuse the [workflow script](../../scripts/dev-workflow.ps1) and [VS Code tasks](../../.vscode/tasks.json), rather than inventing build/copy sequences.

## UI Build And Static Sync
- After UI changes, run `./scripts/dev-workflow.ps1 -Action sync` from the repo root, or the `Workflow: Sync UI + Static` task. Both build the UI and copy `dist/` into **both** `app/server/src/main/resources/static` and `app/server/target/classes/static`.
- `-SkipBuild` copies an existing build only; use it only when `dist/` is already current.
- The running Maven dev server reads `target/classes/static`; packaging uses `src/main/resources/static`. Verify the integrated Spring Boot-served UI after sync to catch stale bundles.

## Local Servers
- `Dev: Start All` starts Spring Boot (8080) and Vite in parallel. Individually: `mvn spring-boot:run` in `app/server`, `npm run dev` in `app/ui`.
- `./scripts/dev-workflow.ps1 -Action all` builds, copies, and starts managed servers; add `-ForceRestart` only when a restart is needed. The `Workflow: Build + Copy + Start All` task includes that flag.
- Stop managed servers with `./scripts/dev-workflow.ps1 -Action stop` or `Workflow: Stop All`.
- In PowerShell, use `npm.cmd` if the `npm.ps1` execution policy blocks npm commands.

## Required Gates
- During iteration, run focused checks for the changed behavior first.
- Engine SDK changes: `mvn -f app/engine/pom.xml clean verify` from the repo root
	(plain JUnit, boundary/index lint, 70% line coverage per engine package).
	No Spring, database, frontend, or integrated app startup is needed.
- `mvn -f app/pom.xml verify` from the repo root aggregates engine and backend checks; the
	modules have independent parents and can also be developed separately.
- Backend changes: run the same command as GitHub Actions from the repo root: `mvn -B -f app/pom.xml -pl server -am verify` (JUnit + JaCoCo minimum 70% line coverage on `com.capybee.server.service.*`). Use this exact command for final backend build verification. Add tests, never lower the gate.
- Frontend changes: `npm test` (or `npm run test:coverage`) in `app/ui`, then the build-and-sync workflow above.
- Before shipping application changes, start the integrated app and manually verify affected user flows, not just Vite.
- Guidance-only edits: validate frontmatter, links, routing, and preserved rules; application builds, static sync, and test suites are not required.

## Deployment
- The [Dockerfile](../../Dockerfile) builds UI assets, embeds them in the Spring Boot JAR, then runs one container. [fly.toml](../../fly.toml) selects the `fly` Spring profile.
- Keep Fly-specific Spring settings in `application-fly.yml`; supply credentials through environment/secrets, never hardcode them.
