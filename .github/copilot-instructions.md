# CapyBee Copilot Guidance

Children's companion app for relocated kids; friendship tracking is private.

## Universal Rules
- No inter-child sharing or social features; minimal PII: nicknames, no real names, addresses, or precise location.
- No third-party analytics, ads, or tracking.
- Google OAuth2 + server sessions; preserve the existing PWA restore-token flow, never introduce new long-lived client auth tokens.
- CapyBee's voice is warm, casual, and validates feelings; all user-facing copy is EN/PL.
- Authenticated data mutations use the offline queue + sync engine.
- Follow nearby implementations; keep changes focused.
- Use concise, simple English; summarize changes and checks without repeating code.

## Read Only Relevant Guidance
- Before changing product behavior, data models, onboarding, or architecture, read the [overview](instructions/project-overview.instructions.md), linked concept, and relevant specifications.
- Read matching [scoped instructions](instructions/); use relevant [skills](skills/) for procedures and open only needed references. Do not load the entire guidance tree.
- Run the applicable verification gates in the [build guide](instructions/build-workflow.instructions.md) for application changes.
