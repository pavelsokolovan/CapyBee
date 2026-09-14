# CapyBee Skill Size Budget Pattern

## Rationale

Skills are loaded into context for every coding request. Larger skills = more tokens used, higher cost. This pattern balances comprehensiveness with efficiency by capping main skill files and moving detailed content to references.

## Budget Guidelines

### Main Skill File (`SKILL.md`)

| Section | Lines | Purpose |
|---------|-------|---------|
| Frontmatter + Description | 3-4 | YAML header (name, description, triggers) |
| "When to Use" section | 15-20 | 3-5 brief bullet points (not essays) |
| Key Principles/Checklist | 20-30 | Core rules, quick reference (bullets, not prose) |
| Main Workflow | 60-100 | 5-8 steps, each 2-4 lines + 1-2 code examples |
| Running/Verification | 20-30 | Commands, test procedures |
| Common Patterns | 15-25 | Quick mistakes + fixes (bullets) |
| **Total** | **150–200 lines** | Never exceed 250 |

### Reference Files (Separate Directory)

- **Location:** `skill-name/references/`
- **Scope:** Detailed docs, long code examples, troubleshooting, data tables
- **No penalty for length:** 300-500 lines per reference file is acceptable
- **Naming:** `topic-name.md` (e.g., `schema-conventions.md`, `mockito-patterns.md`)

### Scoped `applyTo` Patterns

- **Required:** All skills must have tight `applyTo` patterns in YAML frontmatter
- **Example:** `applyTo: app/server/**/*.java` (not `**/*`)
- **Benefit:** Prevents skill from being loaded for unrelated requests (e.g., UI skill won't load when editing Java files)

---

## Applied Pattern Results

### Before Optimization

| Skill | Main | References | Total |
|-------|------|------------|-------|
| flyway-migration | 500+ | 600+ | 1100+ |
| new-crud-feature | 600+ | 350+ | 950+ |
| service-unit-test-scaffold | 700+ | 1350+ | 2050+ |
| **Total** | **1800+** | **2300+** | **4100+** |

**Problem:** 4100+ lines loaded for every migration/feature/test request = significant token overhead + context bloat.

### After Optimization

| Skill | Main | References | Total | Reduction |
|-------|------|------------|-------|-----------|
| flyway-migration | **79** | 600+ | 679+ | **38% smaller** |
| new-crud-feature | **89** | 350+ | 439+ | **54% smaller** |
| service-unit-test-scaffold | **188** | 1350+ | 1538+ | **25% smaller** |
| **Total** | **356** | **2300+** | **2656** | **35% smaller** |

**Benefit:** Main skills now fit on 1-2 screens, references loaded only when needed.

---

## Key Optimizations Applied

### 1. Frontmatter
- Kept trigger phrases intact (essential for skill invocation)
- Condensed description to 1-2 sentences

### 2. "When to Use" Section
- Reduced from 5-8 lines of prose to 3-4 bullet points
- Removed examples/context (moved to references)

### 3. Workflow Steps
- Condensed step explanations: 1-2 lines instead of paragraphs
- Kept **one** quick code example per step
- Moved detailed patterns to references (`entity-template.md`, `mockito-patterns.md`)

### 4. Checklists
- Kept essential pass/fail items
- Removed explanatory text

### 5. Examples & Troubleshooting
- Moved verbose examples to `references/` directory
- Kept only "common mistakes" + quick fixes in main skill

### 6. References
- Split by topic (e.g., `schema-conventions.md`, `data-types.md`, `mockito-patterns.md`)
- Moved there: full code templates, detailed troubleshooting, data tables, migration examples
- Users access references via skill footer: "See `references/` for..."

---

## Metrics & Impact

### Token Efficiency
- **Before:** ~4100 tokens per main skill load
- **After:** ~356 tokens per main skill load (on-demand references load separately)
- **Savings:** ~89% reduction in context bloat for base skill load

### User Experience
- **Before:** Long, overwhelming skill files (hard to scan)
- **After:** 1-2 screen skills (fast to read), detailed references available for deep dives

### Maintenance
- **Before:** Large monolithic files (hard to update)
- **After:** Modular references (easier to update specific topics)

---

## Best Practices for New Skills

When adding skills to CapyBee, follow this pattern:

1. **Frontmatter:** Name + 1-2 sentence description + trigger phrases
2. **"When to Use":** 3-5 bullets (not prose)
3. **Core Workflow:** 5-8 steps, each 2-4 lines + 1 quick code example
4. **Checklists:** Pass/fail items only
5. **References:** Detailed docs, long examples, troubleshooting in separate files
6. **Total Main File:** Target 150–200 lines (max 250)

---

## Trigger Phrases & Scoping

Each skill includes YAML frontmatter with:
- `applyTo` pattern (scopes when skill is loaded)
- Trigger phrases in description (tells Copilot when to use it)

**Example:**
```yaml
---
name: new-crud-feature
description: End-to-end scaffold for new domain feature... Triggers on "add new feature", "scaffold new domain entity", "create new feature", "implement new domain".
applyTo: app/server/**/*.java,app/server/**/*.md
---
```

This ensures skills are only loaded when relevant, reducing unnecessary context overhead.

---

## Files Affected

- ✅ [`flyway-migration/SKILL.md`] — 79 lines (was 500+)
- ✅ [`new-crud-feature/SKILL.md`] — 89 lines (was 600+)
- ✅ [`service-unit-test-scaffold/SKILL.md`] — 188 lines (was 700+)
- ✓ Reference files remain in `references/` subdirectories (no changes needed)

---

## Future Maintenance

As new skills are added to CapyBee:
1. Measure the main file size
2. If >200 lines, move sections to `references/`
3. Keep trigger phrases in main skill header
4. Update `references/` index if adding new reference files
5. Test that trigger phrases invoke the skill correctly

