# Content inventory — capybee-new-hive 1.0.0

Locale/default: **pl**. Authored-only: no GENERATION feature, provider, setup inputs or free text.

Editorial pass in this implementation: Polish spelling/diacritics, optional cooperation, valid rest, no pressure to stop missing home, no scoring of feelings, lantern motif for the Old World. Independent native-speaker/device/assistive-technology product review remains release acceptance (29.5).

## Places (Honeycomb Board)

| Public ID | Polish label | Axial x,y |
|---|---|---|
| `hive-square` | Polana Nowego Ula | 0,0 |
| `old-lantern` | Latarnia Wspomnień | -1,0 |
| `quiet-meadow` | Cicha Łąka | 0,-1 |
| `reed-bank` | Trzcinowy Brzeg | 1,-1 |
| `willow-glen` | Wierzbowa Zatoczka | 1,0 |
| `hive-workshop` | Warsztat Plastra | 0,1 |
| `small-crossing` | Mostek Dwóch Świateł | 1,1 |

Links are declared in `places.json`. `availableMoves` alone authorizes a direct MOVE. A MOVE changes the public place, not the scene; choices open the reviewed encounter/expedition scenes. Direct movement and revisits are optional. The authored scene choices also supply GO_TO effects, so every resolution is playable without spatial inference.

## Fictional companions

| Public actor reference | Name/species | Trait | Need | Optional rule |
|---|---|---|---|---|
| `actor:mela-bee` | Mela, bee | Patient | Quiet to choose a direction | `help-mela` sets `mela-joined=true` |
| `actor:borys-beaver` | Borys, beaver | Resourceful | Someone to hold a reed while he ties a rail | `help-borys` sets `borys-joined=true` |
| `actor:luna-moth` | Luna, moth | Attentive | A clear sign for a rest stop | `help-luna` sets `luna-joined=true` |

The existing authored runtime carries these IDs as public block refs. Names, traits, needs and dialogue are supplied in Polish narrative blocks through the shared scene renderer. No hidden cast/snapshot inspection or generation-only character panel is required. Joining records fictional cooperation. Flags are booleans, never affection/health scores; revisits cannot farm counters. Each encounter may be declined or resolved by rest.

## Scenes, choices and state effects

`arrival` starts with explicit Continue to `board`. `memory-light` and the three `*-thanks` scenes also use explicit Continue. Entering `board` relocates to `hive-square`.

| Scene | Choice / Continue | Destination | Effects / requirement |
|---|---|---|---|
| `arrival` | Continue | `board` | none |
| `board` | `visit-lantern` | `lantern` | GO_TO:old-lantern |
| `board` | `meet-mela` | `mela` | GO_TO:quiet-meadow |
| `board` | `meet-borys` | `borys` | GO_TO:reed-bank |
| `board` | `meet-luna` | `luna` | GO_TO:willow-glen |
| `board` | `visit-workshop` | `workshop` | GO_TO:hive-workshop |
| `board` | `rest-now` | `ending-rest` | GO_TO:hive-square |
| `lantern` | `carry-light` | `memory-light` | FLAG_SET:remembered-home=true |
| `lantern` | `back-to-board` | `board` | GO_TO:hive-square |
| `lantern` | `rest-now` | `ending-rest` | GO_TO:hive-square |
| `memory-light` | Continue | `board` | none |
| `mela` | `help-mela` | `mela-thanks` | FLAG_SET:mela-joined=true |
| `mela` | `wave-mela` | `board` | GO_TO:hive-square |
| `mela` | `rest-now` | `ending-rest` | GO_TO:hive-square |
| `mela-thanks` | Continue | `board` | none |
| `borys` | `help-borys` | `borys-thanks` | FLAG_SET:borys-joined=true |
| `borys` | `wave-borys` | `board` | GO_TO:hive-square |
| `borys` | `rest-now` | `ending-rest` | GO_TO:hive-square |
| `borys-thanks` | Continue | `board` | none |
| `luna` | `help-luna` | `luna-thanks` | FLAG_SET:luna-joined=true |
| `luna` | `wave-luna` | `board` | GO_TO:hive-square |
| `luna` | `rest-now` | `ending-rest` | GO_TO:hive-square |
| `luna-thanks` | Continue | `board` | none |
| `workshop` | `tie-rail` | `crossing` | FLAG_SET:rail-ready=true, GO_TO:small-crossing |
| `workshop` | `back-to-board` | `board` | GO_TO:hive-square |
| `workshop` | `rest-now` | `ending-rest` | GO_TO:hive-square |
| `crossing` | `light-new-path` | `ending-new` | none |
| `crossing` | `keep-two-lights` | `ending-two` | none; requires remembered-home=true (public disabled hint supplied) |
| `crossing` | `back-to-board` | `board` | GO_TO:hive-square |
| `crossing` | `rest-now` | `ending-rest` | GO_TO:hive-square |
| `ending-new` | ending | `new-path` | no mutation or web reward call |
| `ending-two` | ending | `two-lights` | no mutation or web reward call |
| `ending-rest` | ending | `rest-here` | no mutation or web reward call |

## Objective and result rules

All flags start false. The three companion objectives and remembering home are explicitly labeled optional. An active optional objective does not prevent any basic ending or reduce its result class.

| Objective | Completion condition |
|---|---|
| `prepare-rail` | `rail-ready=true` |
| `carry-memory` | `remembered-home=true` |
| `listen-mela` | `mela-joined=true` |
| `help-borys` | `borys-joined=true` |
| `mark-luna-rest` | `luna-joined=true` |

`rail-ready` is set once by `tie-rail`; it records the expedition preparation, not emotional progress. Result facts report only fictional cooperation and carrying the lantern. All three outcomes use the same technical SUCCESS class; rest is a valid resolution, not a penalty.

## Endings and proposed host bindings

These are reviewed proposals for the host-owned 29.4 policy, **not automatic Mission creation/acceptance/completion or rewards in this Pack**. No host binding or production eligibility is enabled by 29.1.

| Ending / outcome | Optional real-world step | Existing catalog code / proposal version |
|---|---|---|
| `ending-new` / `new-path` | Thank someone for a small help; rest remains fine | `say_thank_you_helper` / 1 |
| `ending-two` / `two-lights` | Listen to a song that reminds you of home; quiet rest remains fine | `listen_song_reminds_home` / 1 |
| `ending-rest` / `rest-here` | Comfortable pause, no Mission required | `rest` / 1 |

Abandon is distinct from all three completed outcomes and offers no completion reward. Every ending explicitly says that its suggestion is optional and does not save or complete anything.

## Validation and frozen resources

Indexed resource SHA-256: `2d3440263967bb1b12b2efc4a938cef2925713930f9a54655a37882c3241d439`.

Digest input: lexicographically sorted `pack.json` plus every manifest `files` path; for each, UTF-8 path, NUL byte, then exact file bytes. Documentation and route evidence are outside the runtime index. The same resources are copied unfiltered into the server JAR and CLI distribution.

The data-driven route suite is `../../fixtures/reference-authored-routes.json`; strict offline CLI scripts are in `../../certification/authored-reference-v1/routes/`. Tests serialize/resume every accepted route revision, reject an unavailable move without state changes, explore all flag/place/scene mechanical states and prove a path to a resolution, and start a fresh provider-disabled CLI process after a saved revision. The declared flag-only rule vocabulary is checked before applying that finite-state key.
