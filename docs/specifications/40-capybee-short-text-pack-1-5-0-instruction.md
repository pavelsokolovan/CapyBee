# Spec 40 — CapyBee: Short-text pack 1.5.0

**Depends on:** 30.2, 30.3, 30.6, 33 and 37

**Baseline:** `4c4f71b` plus specs 36–39

Ship `capybee-new-hive` **1.5.0** with the same story, places, rules, artwork
and companions as 1.4.0, but with much less to read: one short bubble per beat,
labels of a few words, and no duplicate travel choices on the hub. Prior runs
stay pinned to their exact retained pack.

Only authored Polish text changes here. Limits for AI-generated text are spec
41.

## Decisions to confirm

1. **Remove the five "visit" choices from the `board` scene** (`visit-lantern`,
   `meet-mela`, `meet-borys`, `meet-luna`, `visit-workshop`). In 1.4.0 each place
   already has `entry.sceneId`, so the map's **Idź** enters the same scenes and
   the hub lists every destination twice. `rest-now` stays. The hub then has one
   action and fits the pinned footer from spec 37. This changes mechanics only
   by retiring those option ids in 1.5.0; scene ids, flags, places and rules
   are untouched. Say so before implementation if you want the hub choices kept;
   the rest of this spec still applies.
2. **Endings are unchanged**, byte for byte. They are reviewed, bound to
   `GameCompletionPolicy` outcomes, and read once.

## Text budgets (authored pack)

Enforced by a test (see Verification) for every non-ending scene:

- one `NARRATION` block per scene, at most **2 paragraphs** (separated by `\n`,
  as the endings already do) and at most **32 words** in total;
- at most **14 words per sentence**;
- option labels at most **5 words** and **36 characters**, first person, present
  tense, verb first;
- place descriptions and hints in `strings/pl.json` at most **13 words**.

Keep the voice from the concept: informal "ty", short sentences, homesickness is
valid, old and new coexist, nothing mandatory, a rest option on every choice
scene, and the fiction cue ("baśń", "w tej historii") so companions are never
presented as replacement friends.

## Scene changes

Scene ids, option ids, `next`, `effects`, `requires`, `unmetHint`, `start`,
`end` and `outcome` stay exactly as in 1.4.0 except the `board` options in
decision 1. Only `text` and `label` change. `rest-now` stays
"Na dziś wybieram odpoczynek" everywhere.

| Scene | New text |
|---|---|
| `arrival` | Nowy ul, dwa światła. CapyBee rozkłada plaster miodu.<br>Możesz tęsknić za dawnym domem i zauważać coś nowego. Oba miejsca są ważne. To baśń — jej zwierzęta mieszkają tylko w tej historii. |
| `board` | Wybierz miejsce na mapie. Każde spotkanie jest dobrowolne. Możesz też odpocząć. |
| `lantern` | W Latarni Wspomnień pali się ciepłe światło. Może pachnieć kuchnią, brzmieć jak piosenka albo zwykły wieczór w dawnym domu.<br>Nie musisz nikomu opowiadać, jakie to wspomnienie. CapyBee zostawia obok mały lampion. |
| `memory-light` | Mały lampion świeci spokojnie. Dawny dom nie musi zniknąć, żeby było miejsce na nowe. Wspomnienie może iść z tobą. |
| `mela` | Mela, cierpliwa pszczoła, szuka chwili ciszy przed lotem.<br>„Możesz posiedzieć obok albo iść dalej. Obie drogi są w porządku” — mówi. |
| `mela-thanks` | Przez chwilę słuchacie szelestu kwiatów. Mela wybiera kierunek i dołącza do wyprawy w tej baśni. |
| `borys` | Borys, pomysłowy bóbr, wiąże poręcz z trzciny. Przyda mu się pomoc przy trzcinie.<br>„Możemy zrobić mały kawałek razem, jeśli masz ochotę” — mówi. |
| `borys-thanks` | Borys kończy węzeł, dziękuje i dołącza do wyprawy. Nikt nie musi się spieszyć. |
| `luna` | Luna, uważna ćma, szuka czytelnego znaku, by znaleźć miejsce na odpoczynek.<br>„Lubię wiedzieć, gdzie można przysiąść” — mówi spod liścia. |
| `luna-thanks` | Układacie z liści prosty znak. Luna pokazuje zatoczkę do odpoczynku i dołącza do wyprawy. |
| `workshop` | W Warsztacie Plastra czeka krótka poręcz z trzciny. CapyBee sprawdza z tobą węzeł.<br>Spotkania ze zwierzętami są miłe, ale nie są obowiązkowe. |
| `crossing` | Przy Mostku Dwóch Świateł zaczyna się ścieżka do nowego zakątka. Jest też miejsce na lampion ze wspomnieniem.<br>Tęsknota nie musi zniknąć przed następnym krokiem. |

Option labels:

| Option id | 1.4.0 | 1.5.0 |
|---|---|---|
| `carry-light` | Zabieram światło wspomnienia na drogę | Zabieram światło na drogę |
| `help-mela` | Siadam na chwilę i słucham Meli | Siadam obok Meli |
| `wave-mela`, `wave-borys`, `wave-luna` | Macham na pożegnanie i idę dalej | Macham i idę dalej |
| `help-luna` | Układam z Luną znak z liści | Układam z Luną znak |
| `tie-rail` | Wiążę poręcz i ruszam do mostku | Wiążę poręcz, idę do mostku |
| unchanged | `help-borys`, `back-to-board`, `light-new-path`, `keep-two-lights`, `rest-now` | same |

`strings/pl.json`: change only `navigation.choose-or-pause` to "Dokończ tę chwilę
albo zrób przerwę." (there is no longer a visible "Dalej" button to name). Keep
all place descriptions and the other hints; they already fit the budget.

## Pack and wiring changes

1. Copy `app/engine/reference-game-pack-versions/capybee-new-hive/1.4.0` to
   `1.5.0`. Set `version` to `1.5.0` in `pack.json`. Replace the README with a
   short note: shorter authored Polish text and no hub duplicates; mechanics,
   artwork bindings, places, rules and reviewed endings retain 1.4.0; keep prior
   saves pinned to their exact retained pack and timing.
2. Edit only `scenes/pl/story.json`, the matching
   `generation/fallbacks/pl/<scene>.json` files (`narration` and `choices` must
   equal the new scene text and option labels; the `board` fallback keeps only
   `rest-now`), and `strings/pl.json` as above. Do not touch `rules.json`,
   `places.json`, `presentation.json`, `assets/`, `generation/templates.json`,
   `generation/quality/pl.json` or `prompts/`.
3. `app/server/pom.xml`: point `embed-current-polish-pack` at `1.5.0` and add an
   `embed-retained-prefetch-polish-pack` execution that copies `1.4.0` to
   `retained/1.4.0`, in the style of the 1.3.0 execution.
4. `retained-versions.json`: add `"1.4.0"`.
5. `GameCompletionPolicy`: add `"1.5.0"` to the accepted version set. Outcome and
   scene bindings do not change.
6. Update version pins and expectations: `AuthoredPackResourceTest` (current is
   `1.5.0`, `1.4.0` is retained and byte-identical to its reference directory),
   `IllustratedPrefetchTest`, `GameCompletionPolicyTest`,
   `GameCompletionPostgresTest`, and the browser scripts
   `game-honeycomb-board.mjs`, `polish-authored-pack.mjs` and
   `polish-generated-pack.mjs`.
7. Browser scripts click story choices by exact label. Update every changed
   label, and replace the removed hub choices (for example
   "Odwiedzam Melę na Cichej Łące") with travel through the map sheet from spec 38
   (open **Mapa**, select the tile, tap **Idź**). The helpers `openExpeditionMap`
   and `checkBoardRoute` in `game-honeycomb-board.mjs` are the place to adapt.
   The special-case label in `polish-authored-pack.mjs` for Mela becomes
   "Siadam obok Meli".

## Out of scope

- Generation prompts, quality profile and validators (spec 41).
- English text; the pack is Polish-only (`locales: ["pl"]`).
- Reward, mission and completion logic.
- Changes to `places.json`, `rules.json`, artwork or the companions.

## Verification

1. **Pack text budget test** (new, `PackTextBudgetTest` in `app/engine/core` or
   `AuthoredPackResourceTest`): load 1.5.0 and assert, for every non-ending
   scene, the block, paragraph, sentence and label limits above, and for
   `strings/pl.json` the 13-word limit on `description.*` and hints. Assert that
   endings are byte-identical to 1.4.0, and that scene ids, option ids (apart from
   the five removed from `board`), `next`, `effects`, `requires`, `start`, `end`
   and `outcome` equal 1.4.0. Assert that fallbacks equal the story text and
   labels.
2. **Retention:** `1.4.0` resolves as a retained version, its bytes equal
   `reference-game-pack-versions/.../1.4.0`, and a run saved on 1.4.0 resumes with
   its six hub choices. A new run starts on 1.5.0. Prefetch tests still pass.
3. **Validator and quality:** the pack passes the existing pack loader,
   `hive-family-pl-v1` quality checks (including the forbidden and hostile-tone
   phrase sets) and the content policy tags (`personal-data`, `pressure`,
   `human-replacement`).
4. **Browser suite** (`game-honeycomb-board.mjs`, `game-expeditions.mjs`,
   `polish-authored-pack.mjs`): the full route (arrival, map travel to every
   place, companions, all three endings) still completes on 1.5.0; the hub shows
   exactly one action; at 360 × 640 the `arrival`, `board`, `lantern` and three
   encounter scenes show art, full text and the pinned actions without body
   scrolling (expected; the browser decides). Save screenshots to
   `docs/evidence/40/`.
5. **Gates:** `mvn` pack and server tests as in
   `.github/instructions/build-workflow.instructions.md`, `npm test` in `app/ui`,
   `./scripts/dev-workflow.ps1 -Action sync`, the Spring-served browser suite,
   and a manual read of every changed line in Polish for tone.
6. Add `docs/game-short-text-pack-acceptance.md` and list spec 40 in
   `docs/specifications/README.md`.
