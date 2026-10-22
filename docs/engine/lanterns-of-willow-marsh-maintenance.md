# Lanterns of Willow Marsh maintenance

The reference pack is a product asset and acceptance fixture. Trusted mechanics live in `rules.json`, `places.json`, and `scenes/en/story.json`; generated artifacts may only replace presentation.

When mechanics change, bump the pack version and update the scene catalog, affected fallbacks, quality corpus, route tests, CLI transcripts, recovery evidence, and this traceability table. Review each ending against every reachable objective state. Setup values—including the transient story wish—must never change IDs, graph shape, requirements, effects, objectives, outcomes, or call limits.

Do not add a locale until it has complete strings, fallbacks, quality profile, deterministic corpus, and human review.

## Traceability

| Contract | Pack evidence | Automated evidence |
|---|---|---|
| Full-game phases and exact length (27.4) | `generation/catalog.json` | `WillowMarshPackValidationTest` |
| Story plan and continuity (27.5) | scenario plus 12 scene fallbacks | pack validation and fallback parsing |
| Challenge consequences (27.6) | authored scene effects | route contract validation |
| Outcomes and endings (27.7) | rules plus three ending resources | result-class assertions |
| Safe setup (27.8) | `generation/setup.json` | setup routing assertions |
| Normal CLI contract (27.9) | pack README stable scripts | existing CLI suite |
| Persistence/recovery (27.10) | no pack-specific runtime state | existing session recovery suite |
| Quality consistency (27.11) | quality profile and reviewed fallbacks | generation quality suite |

Human-review evidence may contain only review ID, pack version, route/outcome, content mode, issue codes/severity, resolution, and regression-test link. Never commit names, contact data, raw child statements, setup text, or provider conversations.
