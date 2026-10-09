# CapyBee Guidance Size Budget

## Loading Model
- The [root guidance](copilot-instructions.md) supplies universal constraints and selective routing.
- [Scoped instructions](instructions/) attach through `applyTo` file patterns or are discovered through task-specific `description` metadata. Keep patterns relevant, not repo-wide by default.
- Skills expose `name` and `description` for discovery. A relevant skill's body is loaded on demand, followed by only the references needed for the task.
- `applyTo` belongs to instruction files, not skill frontmatter. Skills are selected by task relevance or explicit invocation.
- References use context when opened; moving text into references helps only if agents open them selectively.

## Ownership And Size
- Root: aim for about 20 lines. Retain safety, privacy, auth, tone, offline-first, concise responses, and routing; move stack maps and procedures elsewhere.
- Scoped instructions: one detailed owner per concern. Keep standards and obligations here; link commands to the build guide and procedures to the relevant skill.
- `SKILL.md`: scope, ordered procedure, essential completion checks, verification routing, and explicit reference links. Prefer a short workflow; review bodies above 100 lines and keep them below 250 unless necessary.
- `references/`: substantial examples and troubleshooting only. Reuse existing files and actual code patterns; do not copy every reference into the main skill.
- A brief safety reminder at a critical boundary is useful; repeated policy sections and command lists are not.

## Skill Metadata Example
```yaml
---
name: new-crud-feature
description: "Use when adding a new child-owned CRUD feature or scaffolding a domain entity in CapyBee."
---
```

The folder name must match `name`. Describe specific tasks; avoid triggers so broad that unrelated work loads the workflow.

## Validation
1. Measure line and character counts before and after edits; these are size proxies, not token counts or billing estimates.
2. Validate YAML frontmatter and resolve relative links. Link references directly using `./references/<existing-file>.md` rather than a vague directory listing.
3. Check `applyTo` against representative UI, CSS, Java, SQL, CI, and script paths. Keep the product overview task-discoverable rather than attaching it to every source file.
4. In fresh VS Code chats, inspect customization/context diagnostics for actual instruction and skill loading on routine UI, service-test, migration, and product tasks. Links and descriptions guide discovery, not a guarantee of invocation.
5. Confirm universal safeguards, scoped test obligations, and the [build gates](instructions/build-workflow.instructions.md) remain intact.

Do not publish cost-saving percentages without measured token usage. Recheck sizes and routing when guidance changes instead of retaining stale before/after tables.

