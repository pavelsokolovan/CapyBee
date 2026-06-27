# CapyBee — Developer Goals & AI-Assisted Workflow

> Personal goal: Use CapyBee as a real project to practice the **full modern AI-assisted development lifecycle** — from concept to production.

---

## The Big Picture

This project is dual-purpose:
1. **Build a meaningful app** — CapyBee helps kids adapt to a new country
2. **Master AI-assisted development** — practice every phase of modern software delivery using AI tools

The goal is not just to ship CapyBee — it's to learn **how to build software in 2025 the new way**: where AI is a co-pilot at every single stage.

---

## Full Lifecycle — Phase by Phase

### Phase 1 — Concept & Vision 🧠
*Status: ✅ Done*

**What:** Define the problem, the user, the character, the emotional core, the name, the brand.

**AI tools used:**
- Claude — brainstorming, concept refinement, naming, tone of voice

**Output:**
- `CapyBee_concept.md` — full product concept document

**Lesson:** AI can be a genuine thinking partner at the concept stage — not just autocomplete, but pushing back, suggesting angles, catching weak ideas.

---

### Phase 2 — Specification 📋
*Status: 🔲 Next*

**What:** Turn concept into a structured technical specification — user stories, data models, API contracts, screen flows.

**AI tools used:**
- Claude — generate user stories from concept doc
- Claude — propose database schema
- Claude — draft API endpoint list (REST)
- Claude — create screen flow diagrams

**Outputs to produce:**
- `CapyBee_spec_user_stories.md`
- `CapyBee_spec_data_model.md`
- `CapyBee_spec_api.md`
- `CapyBee_spec_screens.md`

**Practice goal:** Learn to prompt AI to produce specification artifacts, then critically review and correct them — not blindly accept.

---

### Phase 3 — Architecture & Project Setup 🏗️
*Status: 🔲 Upcoming*

**What:** Scaffold the project, set up the monorepo, configure tooling.

**AI tools used:**
- Claude — generate Spring Boot project structure
- Claude — generate React app scaffold
- Claude — write initial `Dockerfile`
- Claude — write `fly.toml` configuration
- GitHub Copilot — inline code suggestions during setup

**Outputs to produce:**
- Working Spring Boot skeleton (Java 21, Spring Security, JPA)
- Working React skeleton (Tailwind, Framer Motion)
- Single Docker container build (Spring Boot serves React build)
- Fly.io deployment config

**Practice goal:** Use AI to generate boilerplate fast — focus human attention on architecture decisions, not repetitive setup.

---

### Phase 4 — Vibe Coding 🎨
*Status: 🔲 Upcoming*

**What:** Build features through AI-assisted coding — describing what you want, reviewing output, iterating.

**Approach:**
- Describe a feature in plain language → AI generates code → review → refine → integrate
- Use AI for both backend (Java/Spring) and frontend (React/Tailwind)
- Keep commits small and focused

**AI tools used:**
- Claude — feature implementation (describe → generate → review)
- GitHub Copilot — inline completion during coding
- Claude — code review ("what's wrong with this?", "how can this be improved?")

**Features to build in order:**
1. Auth flow (parent + child accounts, JWT)
2. Daily check-in screen
3. Honeycomb map component (main visual)
4. Old World / New World spaces
5. Mission system
6. Friendship tracker
7. Bilingual support (EN/PL)
8. Feelings toolkit mini-games

**Practice goal:** Get comfortable with the "describe → generate → review → refine" loop. Learn where AI is fast and reliable vs. where human judgment is essential.

---

### Phase 5 — Automated Testing 🧪
*Status: 🔲 Upcoming*

**What:** Write tests with AI assistance — unit, integration, and end-to-end.

**AI tools used:**
- Claude — generate unit tests for Spring Boot services
- Claude — generate integration tests for REST endpoints
- Claude — generate Playwright or Cypress E2E test scripts
- Claude — identify edge cases human might miss

**Test layers:**

| Layer | Tool | What it tests |
|---|---|---|
| Unit | JUnit 5 + Mockito | Service logic, domain rules |
| Integration | Spring Boot Test | API endpoints, DB interaction |
| E2E | Playwright | Full user flows in browser |
| Security | OWASP ZAP (optional) | Auth, injection, XSS basics |

**Practice goal:** Learn to use AI to generate test cases, then critically evaluate coverage. Understand what AI misses (edge cases, business logic nuance).

---

### Phase 6 — Bug Fixing with AI 🐛
*Status: 🔲 Ongoing throughout*

**What:** Use AI as first-line debugger — paste error, get diagnosis, verify fix.

**Workflow:**
1. Test fails or bug found
2. Paste error + relevant code to Claude
3. Claude diagnoses and proposes fix
4. Developer understands the fix before applying
5. Fix verified by test

**Practice goal:** Never blindly apply AI fixes. Always understand *why* the fix works. Build a habit of asking "why did this fail?" not just "make it work."

---

### Phase 7 — CI/CD Pipeline 🚀
*Status: 🔲 Upcoming*

**What:** Set up automated build, test, and deploy pipeline.

**Stack:**
- GitHub Actions — pipeline orchestration
- Docker — build artifact
- Fly.io — deployment target

**Pipeline stages:**

```
Push to main
    → Run unit tests (JUnit)
    → Run integration tests
    → Build React frontend
    → Build Docker image
    → Run E2E tests against staging
    → Deploy to Fly.io production
    → Health check
```

**AI tools used:**
- Claude — generate GitHub Actions YAML workflow
- Claude — troubleshoot pipeline failures
- Claude — optimize build times

**Practice goal:** Understand every step of the pipeline — don't just copy AI-generated YAML, know what each step does and why.

---

### Phase 8 — Monitoring & Iteration 📊
*Status: 🔲 Post-launch*

**What:** Observe the app in production, catch issues, iterate.

**Tools:**
- Fly.io built-in metrics
- Sentry (error tracking — free tier)
- Simple logging in Spring Boot

**AI tools used:**
- Claude — analyze error logs
- Claude — suggest performance improvements
- Claude — help prioritize next features based on usage patterns

---

## AI Tools Reference

| Tool | Used for |
|---|---|
| **Claude** | Concept, spec, architecture, code generation, review, test generation, debugging, CI/CD config |
| **GitHub Copilot** | Inline code completion during active coding |
| **Playwright** | AI-assisted E2E test generation |
| **GitHub Actions** | CI/CD pipeline (config generated with AI help) |

---

## Personal Learning Principles

1. **Understand before applying** — never blindly use AI output
2. **AI generates, human reviews** — always know why code works
3. **Prompt quality matters** — practice writing precise, context-rich prompts
4. **Use AI to go faster, not to think less** — stay engaged
5. **Document the process** — note what AI did well and where it struggled
6. **Treat bugs as learning** — don't just fix, understand

---

## Success Metrics

By the end of this project I will have practiced:

- [ ] AI-assisted product specification
- [ ] AI-assisted architecture design
- [ ] Vibe coding (describe → generate → review → ship)
- [ ] AI-generated test suites (unit + integration + E2E)
- [ ] AI-assisted debugging workflow
- [ ] Full CI/CD pipeline setup with AI help
- [ ] Auto-deployment to Fly.io on every push to main
- [ ] Production monitoring setup

---

## Notes

- This document lives alongside `CapyBee_concept.md`
- Both docs should be kept in the project repo root under `/docs`
- Update this doc as each phase completes — mark lessons learned

---

*"The best way to learn AI-assisted development is to build something that matters."*
