---
description: "Use when changing CapyBee product behavior, data models, onboarding, emotional experience, requirements, or architecture. Provides product context and project orientation."
---

# CapyBee Project Overview

CapyBee helps relocated children around age 12 process homesickness (My Old World) and build a new life (My New World), through check-ins, real-world missions, a private friendship tracker, and a honeycomb progress map.

Before changing product behavior, data models, onboarding, or emotional experience, read the [concept](../../docs/CapyBee_concept.md) and the relevant [specifications](../../docs/specifications/). Universal constraints live in the [root guidance](../copilot-instructions.md).

## Project Map
- `app/server`: Spring Boot, Java, Maven; package `com.capybee.server`, with `config`, `web`, `service`, `repository`, and `domain` layers. Flyway migrations live in `src/main/resources/db/migration/`.
- `app/ui`: React, TypeScript, Vite, Tailwind, Framer Motion; screens in `src/AuthenticatedHome.tsx`, reusable UI in `src/components/`, offline writes in `src/offline/`, PWA via `vite-plugin-pwa`. Check manifests for current versions.
- Deployment: one container serves the backend and built UI. See the [build guide](build-workflow.instructions.md) for workflow and deployment details.

## Implementation Owners
- [Java](java-server.instructions.md): layering, dependency injection, authorization, service testing.
- [API](api.instructions.md): HTTP contracts and idempotency.
- [Database](database.instructions.md): persistence models and migrations.
- [UI](ui.instructions.md): components, mobile layout, localized copy, offline integration.

Read only the owners relevant to the task; use existing implementations as examples.
