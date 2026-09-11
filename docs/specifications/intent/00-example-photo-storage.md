# Intent: photo storage for Memories

Status: example only — dropped for now, not currently active
Date: 2026-09-08

## Problem

SCR-09 (Memory Space) currently supports text/media preview placeholders,
but there's no real photo upload — a child can't attach an actual photo of
their old home or a new-world moment to a memory card. This was explicitly
deferred to a future pass during earlier spec work.

## Who it affects

Child, on the Memories screen (`SCR-09`, Old World + New World tabs). No
parent-facing surface currently planned.

## Constraints

- No PII / real names — photos themselves could contain identifying info
  (faces, addresses); needs a plan for what happens if a child uploads
  something sensitive.
- Bilingual EN/PL required for any new upload UI copy.
- No new social features — photos stay private to the child's own account,
  never shared between users.
- Fly.io cost impact — this is the constraint that actually deferred it.
  Photo storage means either a persistent volume or an object storage
  add-on, both of which break the "near-zero at low traffic" cost model
  the rest of the app relies on.

## Open questions

- Object storage (S3-compatible, e.g. Cloudflare R2/Tigris) vs. a Fly.io
  volume — which fits the existing single-container deploy model better?
- Client-side compression/resizing before upload, to cap storage growth?
- Any moderation/safety pass needed on uploaded images, given this is a
  children's app?
- Does this get its own numbered spec, or fold into a future Memories
  redesign spec (like `10-memories-screen-redesign-instruction.md` was)?
