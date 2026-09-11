# Intent notes

This folder holds **proto-specs** — short, rough notes written *before* a full
numbered spec in `docs/specifications/`. Purpose: catch half-formed ideas
early, before time goes into a detailed spec for something that isn't
actually baked yet.

An intent note is not implementation-ready. It has no file paths, no API
contracts, no acceptance criteria. It just answers four questions. If you
can't answer them yet, the idea isn't ready to spec.

## Workflow

1. Have an idea. Write it down here as `NN-short-name.md` using
   `_TEMPLATE.md`. Takes 5 minutes, not 30.
2. Let it sit. Re-read it a day later, or talk it through with Claude/Copilot
   Chat to poke holes in it.
3. Once the problem, scope, and constraints feel solid, turn it into the next
   numbered spec in `docs/specifications/NN-kebab-case-name-instruction.md`,
   following the existing spec format there.
4. Mark the intent note's status as `→ spec 20` (or whichever number it
   became) at the top of the file. Don't delete it — it's a record of what
   was originally wanted, useful if the spec drifts from the original ask.
5. If an idea gets dropped instead of specced, mark it `status: dropped` and
   say why in one line. Don't delete these either — stops the same idea
   getting re-litigated from scratch in six months.

## Naming

`NN-short-kebab-name.md` — own numbering sequence, separate from
`docs/specifications/` numbering. Doesn't need to be sequential without gaps;
just needs to not collide.

## What does NOT need an intent note

Small, obviously-scoped fixes (a copy tweak, a CSS fix, a one-line bug fix)
go straight to a Copilot Chat prompt or straight to code — no intent note, no
spec. Intent notes are for things substantial enough that a full spec is the
likely next step: a new screen, a new feature, a data model change, anything
touching the safety/privacy rules in `copilot-instructions.md`.

## Index

| File | Status | Became |
|---|---|---|
| `00-example-photo-storage.md` | example only | — |
