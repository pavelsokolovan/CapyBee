# Intent — Illustrated Honeycomb RPG

**Status:** Product intent supplied by the user, 2026-11-15
**First implementation specification:** [30.1](../30.1-visual-design-and-svg-catalog-instruction.md)

CapyBee combines three directions: Honeycomb Board supplies the hex-map geography;
Befriend & Recruit supplies animal or abstract heroes with traits and needs;
Hive Expeditions supplies illustrated encounters with dialogue and choices.
Capy moves cell by cell. Helping a hero can recruit that hero to the party.

Keep the existing warm application style and make the game feel like a calm,
illustrated 2D RPG. A run begins with a theme derived from preferences, today's
optional mood and an optional fictional wish. Content follows that theme.
Arrival, obstacle, helper, choice and resolution form a loose, repeatable arc.
Prefetch hides ordinary generation latency; authored fallbacks preserve play.

Runtime AI never draws: it references IDs from a fixed SVG catalog rendered by
React. No human figures, hearts or padlocks. The Old World motif is a lantern.
Sessions are short, skippable at any moment, without streaks or competition.
Neither remembering the old home nor discovering the new one is a wrong choice.

The repository supplies mechanics and text foundations. The visual board,
visible Capy, illustrated encounters and recruited party are the remaining visual
work. Moving to a place currently does not automatically enter its encounter.

The user-provided Spec 30 roadmap assigns visual design/catalog to 30.1; public
presentation contracts to 30.2; board and destination-to-encounter behavior to
30.3; recruitment display to 30.4; illustrated encounters/text mode to 30.5;
generation/prefetch to 30.6; and complete release acceptance to 30.7. These are
planned boundaries, not claims that those specifications or features exist.
