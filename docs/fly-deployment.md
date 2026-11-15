# Automatic Fly.io deployment

Pushing to `main` runs **Build, Test & Deploy**. Deployment starts only after
both the standalone engine and application test jobs pass. Pull requests and
`develop` pushes run checks without deploying. The workflow can also be run
manually on `main` from GitHub Actions.

The deploy job checks out the tested commit, builds the repository Dockerfile
on Fly's remote builder, deploys app `capybee`, waits for Fly's health checks and
checks `https://capybee.fly.dev/actuator/health`. Production deployments are
serialized; an active deployment is not cancelled by another push.

## One-time credential setup

On a computer authenticated to the existing Fly organization:

```sh
fly auth login
fly tokens create deploy --app capybee --expiry 8760h
```

Store the resulting token as **FLY_API_TOKEN** in GitHub repository
**Settings → Secrets and variables → Actions → New repository secret**.
Use an app-scoped deploy token. Do not put the token in source files, issues,
workflow logs or chat. Rotate it before expiry. No Fly credentials enter the
Docker image.

## Fly application configuration

`fly.toml` targets the existing `capybee` app in `fra`, uses port 8080 and the
`fly` Spring profile, and checks `/actuator/health` with a five-minute startup
grace period. Preserve the app's existing private PostgreSQL attachment and
runtime secrets, including:

- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`

Manage runtime secrets through Fly's dashboard or `fly secrets set --app capybee`.
They are separate from GitHub's deploy token. Google OAuth must allow the
production callback `https://capybee.fly.dev/login/oauth2/code/google`.
The Fly profile enables the Adventures entry and authored Polish Pack
(`capybee-new-hive`) by default. `CAPYBEE_GAME_ENABLED`,
`CAPYBEE_GAME_WEB_CLIENT_ENABLED` and `CAPYBEE_GAME_PACK_IDS` remain explicit
operator overrides. AI generation, dynamic starts, transient AI preparation and
completion rollout retain their disabled defaults.

The deploy workflow preserves an existing `CAPYBEE_GAME_COMMAND_HMAC_KEY` or
`APP_SECRET`. If neither exists, it generates and stages a 48-byte random game
key through Fly secrets management before deployment. The value is never
committed or printed; later deployments preserve it. Manual deployments must
configure one of these stable secrets (at least 32 bytes) first. A failed secret
setup stops deployment rather than starting the game without a key. To hide
the entry without removing saved runs, set `CAPYBEE_GAME_WEB_CLIENT_ENABLED=false`;
keep `CAPYBEE_GAME_ENABLED=true` for saved-run API access.

The existing zero-idle-machine policy is preserved: Fly can suspend the app
when idle and automatically wake it on a request. To intentionally keep one
machine running, set `min_machines_running = 1` in `fly.toml` (this changes
idle compute costs).

## Troubleshooting and rollback

A failed build/test blocks deployment. A missing deploy token produces an
explicit credential error before contacting Fly. For a failed deploy, inspect
the deploy job and `fly logs --app capybee`; confirm runtime secrets, database
availability and OAuth settings without printing their values.

For an application rollback, prefer reverting the offending Git commit and
pushing to `main` so tests run before deployment. Database migrations remain
forward-only. Retain snapshot-envelope compatibility described in
`engine/web-preparation.md` when rolling back a version that has saved dynamic
runs. The production deployment job must succeed before claiming a release is
running successfully on Fly.
