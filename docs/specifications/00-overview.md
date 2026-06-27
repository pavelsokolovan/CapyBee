# CapyBee Overview

## Product Goal

CapyBee helps children build a sense of safety and momentum after moving to a new country. The first implementation should feel calm, welcoming, and simple to understand.

## First Release

- Google account sign-in and account creation
- A friendly first page that explains the app and invites the user in
- Persistent storage for user profile data and early app activity
- Deployment on Fly.io with a single container

## Initial Pages

- Landing page
- Google sign-in callback and account bootstrap flow
- Minimal authenticated home page
- Placeholder areas for check-ins, missions, and saved data

## Database Shape

The first schema should be small and practical:

- `users` - identity, email, display name, avatar, and timestamps
- `check_ins` - daily mood or status notes linked to a user
- `missions` - small tasks or prompts shown to the user
- `mission_completions` - history of completed missions

## Backend Shape

- Spring Security handles Google OAuth login
- Spring Data JPA handles persistence
- Flyway manages schema migrations
- REST endpoints expose health, profile, and app data

## Frontend Shape

- React landing page with a strong visual identity
- Clear sign-in action
- Simple dashboard shell for later features
- Reusable UI sections for future pages

## Deployment Shape

- Build the frontend into static files
- Package the backend and static assets into one Docker image
- Run the image on Fly.io
- Attach PostgreSQL via Fly.io or an external managed Postgres instance
