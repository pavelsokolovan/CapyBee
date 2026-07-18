# CapyBee Phrases — Design & Implementation Guide

## Purpose

Define all CapyBee spoken responses triggered by user actions in the app. Every action gets a warm, varied reply — never the same line twice in a row, never robotic, never preachy. This document covers the phrase library (English + Polish) and tells Copilot exactly how to select and display them.

## Tone rules (read before writing any new phrases)

- Short. Two sentences maximum. One is often better.
- Validates before acting. Never jumps straight to advice.
- Occasionally silly. CapyBee can be a little goofy — that's the capybara energy.
- Never says "it'll get better" or "you'll love it here" or "don't worry".
- Never lectures. Never uses the word "important" or "remember to".
- Uses 🍯 honey emoji sparingly — only when it really fits, not as punctuation.
- Polish uses casual "ty" register throughout. Short, natural sentences, not translated-from-English stiffness.

---

## Phrase selection logic

For every trigger, the app picks one phrase at random from the pool of 10, with one constraint:

```
function pickPhrase(pool, lastUsedIndex) {
  let index
  do {
    index = Math.floor(Math.random() * pool.length)
  } while (index === lastUsedIndex)
  setLastUsedIndex(index)
  return pool[index]
}
```

Store `lastUsedIndex` per trigger category in component state (not localStorage). This prevents the same phrase appearing twice in a row within a session. It resets on page reload — that is fine.

---

## Display component

All phrases display in the shared `<CapyBeeBubble>` component defined in `05-avatars-adding-instruction.md`. The bubble appears below the matching avatar and auto-dismisses after **4 seconds** for toast/feedback contexts. In empty-state contexts it stays visible permanently.

For action responses (post-save feedback), wrap the bubble in a fade-in / fade-out animation:
- Fade in: 200ms
- Visible: 4000ms
- Fade out: 300ms
- Total: ~4.5s then remove from DOM

Do not block the UI during this time. The phrase appears as an overlay bubble above the content, not in the flow.

---

## Section 1 — Mood check-in phrases

**Trigger:** `POST /api/check-ins` returns success.

**Avatar shown:** See `05-avatars-adding-instruction.md` Moment 2 rules (empathetic / face-okay / celebrating based on mood).

**Display duration:** 4 seconds, then fade out.

Three separate pools — one per mood value.

---

### Mood: `heavy`

Avatar: `capybee-empathetic.png`

| # | English | Polish |
|---|---|---|
| 1 | "That sounds really hard. I'm here." | "To brzmi naprawdę ciężko. Jestem tu." |
| 2 | "Heavy days happen. You showed up anyway." | "Ciężkie dni się zdarzają. I tak tu jesteś." |
| 3 | "I heard you. That's enough for today." | "Słyszę cię. To wystarczy na dziś." |
| 4 | "Missing things is not weakness. It just hurts." | "Tęsknota to nie słabość. Po prostu boli." |
| 5 | "Okay. Let's just sit with that for a moment." | "Okej. Usiądźmy z tym przez chwilę." |
| 6 | "You don't have to fix it today." | "Nie musisz tego dziś naprawiać." |
| 7 | "That took courage to write down." | "Odwaga — napisać to." |
| 8 | "Heavy is honest. I respect that." | "Ciężko — to szczere. Szanuję to." |
| 9 | "Even capybaras have bad days. 🍯" | "Nawet kapibary mają gorsze dni. 🍯" |
| 10 | "Thanks for telling me. Really." | "Dzięki, że mi powiedziałeś. Serio." |

---

### Mood: `okay`

Avatar: `capybee-face-okay.png` (displayed at 160×160px)

| # | English | Polish |
|---|---|---|
| 1 | "Okay is something. Good job showing up." | "Okej to też jest coś. Dobra robota, że tu jesteś." |
| 2 | "Solid okay. Honestly, that's fine." | "Solidne okej. Serio, to jest w porządku." |
| 3 | "Not every day has to be great. Okay works." | "Nie każdy dzień musi być super. Okej wystarczy." |
| 4 | "Noted. Okay is a perfectly good day." | "Przyjęto. Okej to całkowicie dobry dzień." |
| 5 | "Okay today. Maybe something small tomorrow." | "Okej dziś. Może coś małego jutro." |
| 6 | "Middle of the road. I'll take it. 🍯" | "Środek drogi. Biorę to. 🍯" |
| 7 | "You checked in. That alone counts." | "Zameldowałeś się. To samo w sobie się liczy." |
| 8 | "Okay and honest beats fake good any day." | "Szczere okej bije udawane dobrze każdego dnia." |
| 9 | "Cool. No pressure to make it more than that." | "Spoko. Nie musisz robić z tego więcej niż to." |
| 10 | "Okay noted. See you tomorrow?" | "Okej, przyjęto. Do jutra?" |

---

### Mood: `good`

Avatar: `capybee-celebrating.png`

| # | English | Polish |
|---|---|---|
| 1 | "That's great! I'm happy with you." | "To świetnie! Cieszę się razem z tobą." |
| 2 | "Good! Add it to the hive. 🍯" | "Dobrze! Dodajmy to do ula. 🍯" |
| 3 | "Something worked today. Remember that feeling." | "Coś dziś zadziałało. Zapamiętaj to uczucie." |
| 4 | "Good days exist. This is proof." | "Dobre dni istnieją. Masz na to dowód." |
| 5 | "Look at you, having a good one." | "No proszę, masz dobry dzień." |
| 6 | "Storing this one in the hive. 🍯" | "Zapisuję to w ulu. 🍯" |
| 7 | "Good! Tell me more sometime." | "Dobrze! Opowiedz mi o tym kiedyś więcej." |
| 8 | "Something went right. That matters." | "Coś poszło dobrze. To się liczy." |
| 9 | "Happy to hear it. Genuinely." | "Miło to słyszeć. Naprawdę." |
| 10 | "Yes! Good days deserve to be noticed." | "Tak! Dobre dni zasługują na uwagę." |

---

## Section 2 — Mission completion phrases

**Trigger:** `POST /api/missions/{missionId}/completions` returns success.

**Avatar shown:** `capybee-celebrating.png` at 80×80px inside the banner.

**Display duration:** 4 seconds banner at top of screen, then fade out.

Single pool of 10 — no mood split needed here.

| # | English | Polish |
|---|---|---|
| 1 | "Mission done! A new cell in your hive. 🍯" | "Misja wykonana! Nowa komórka w ulu. 🍯" |
| 2 | "You actually did it. That's the whole point." | "Naprawdę to zrobiłeś. O to właśnie chodzi." |
| 3 | "Small step. Real step. Big difference." | "Mały krok. Prawdziwy krok. Duża różnica." |
| 4 | "One more thing that's yours in this new place." | "Jeszcze jedna rzecz, która należy do ciebie w tym nowym miejscu." |
| 5 | "Hive is growing. Slowly, but it's growing." | "Ul rośnie. Powoli, ale rośnie." |
| 6 | "That was brave. Even if it didn't feel like it." | "To było odważne. Nawet jeśli tak nie czułeś." |
| 7 | "Done! I saw that. It counts." | "Zrobione! Widziałem. Liczy się." |
| 8 | "You did something today that mattered." | "Dziś zrobiłeś coś, co ma znaczenie." |
| 9 | "Look at the hive filling up. Look at that." | "Popatrz, jak ul się zapełnia. No popatrz." |
| 10 | "Okay I'm a little proud of you right now." | "Wiesz co, jestem teraz z ciebie trochę dumny." |

---

## Section 3 — Friendship tracker entry created

**Trigger:** `POST /api/friendships` returns success.

**Avatar shown:** `capybee-celebrating.png` at 48×48px inside toast.

**Display duration:** 4 seconds toast at bottom of screen, then fade out.

Single pool of 10.

| # | English | Polish |
|---|---|---|
| 1 | "Got it! Every step counts." | "Zapamiętałem! Każdy krok się liczy." |
| 2 | "You noticed someone. That's how it starts." | "Zauważyłeś kogoś. Tak to się zaczyna." |
| 3 | "A name in the hive. Good." | "Imię w ulu. Dobrze." |
| 4 | "Your people are out there. You're finding them." | "Twoja paczka gdzieś tam jest. Właśnie jej szukasz." |
| 5 | "One person closer to finding your hive." | "O jedną osobę bliżej do znalezienia swojego ula." |
| 6 | "Saved. Small things matter more than they look." | "Zapisane. Małe rzeczy mają większe znaczenie niż wyglądają." |
| 7 | "Friendships start exactly like this." | "Przyjaźnie zaczynają się dokładnie tak." |
| 8 | "I see it. I'm keeping track with you." | "Widzę to. Śledzę to razem z tobą." |
| 9 | "You're paying attention. That's a skill." | "Zwracasz uwagę. To jest umiejętność." |
| 10 | "The hive knows. 🍯" | "Ul wie. 🍯" |

---

## Section 4 — Memory saved phrases

Memories have two sub-triggers: Old World and New World. Use the `worldType` value from the active tab to pick the right pool.

**Trigger:** `POST /api/memories` returns success.

**Avatar shown:** `capybee-celebrating.png` at 48×48px inside toast.

**Display duration:** 4 seconds toast at bottom of screen, then fade out.

---

### Memory: Old World (`worldType: old_world`)

| # | English | Polish |
|---|---|---|
| 1 | "Saved. This will always be yours." | "Zapisane. To zawsze będzie twoje." |
| 2 | "The old home stays here. Safe." | "Stary dom zostaje tutaj. Bezpieczny." |
| 3 | "It's okay to miss it. It was real and it mattered." | "Wolno ci za tym tęsknić. To było prawdziwe i ważne." |
| 4 | "Keeping it. You can come back anytime." | "Przechowuję to. Możesz tu wracać kiedy chcesz." |
| 5 | "That memory is sealed in the hive now. 🍯" | "Ta wspomnienie jest teraz zapieczętowane w ulu. 🍯" |
| 6 | "Old doesn't mean gone. It's right here." | "Stare nie znaczy zniknięte. Jest tu." |
| 7 | "I'm glad you wrote that down." | "Cieszę się, że to zapisałeś." |
| 8 | "Home is more than one place. You're allowed that." | "Dom to więcej niż jedno miejsce. Masz do tego prawo." |
| 9 | "Stored safely. Nobody takes this from you." | "Przechowane bezpiecznie. Nikt ci tego nie zabierze." |
| 10 | "Even far away, it's still yours." | "Nawet daleko — wciąż twoje." |

---

### Memory: New World (`worldType: new_world`)

| # | English | Polish |
|---|---|---|
| 1 | "A new moment in the hive! 🍯" | "Nowa chwila w ulu! 🍯" |
| 2 | "Look — something new is yours here now." | "Patrz — coś nowego należy tu teraz do ciebie." |
| 3 | "The hive is getting real." | "Ul staje się prawdziwy." |
| 4 | "One more piece of the new place that's actually yours." | "Jeszcze jeden kawałek nowego miejsca, który jest naprawdę twój." |
| 5 | "New things can be good things. This is proof." | "Nowe rzeczy mogą być dobrymi rzeczami. Masz na to dowód." |
| 6 | "Building it, one memory at a time." | "Budujesz to, jedno wspomnienie na raz." |
| 7 | "That's yours. Nobody can tell you otherwise." | "To twoje. Nikt ci nie powie inaczej." |
| 8 | "The new hive has a new cell. I love that." | "Nowy ul ma nową komórkę. Uwielbiam to." |
| 9 | "Somewhere, this place just got a little more yours." | "Gdzieś, to miejsce stało się trochę bardziej twoje." |
| 10 | "Saved. The hive remembers. 🍯" | "Zapisane. Ul pamięta. 🍯" |

---

## Implementation instructions for Copilot

### Step 1 — Create the phrase data file

Create file: `src/data/capybeePhrases.js`

The file exports one object with five keys. Each key is an array of objects with `en` and `pl` fields.

```js
// src/data/capybeePhrases.js

export const capybeePhrases = {
  moodHeavy: [
    { en: "That sounds really hard. I'm here.", pl: "To brzmi naprawdę ciężko. Jestem tu." },
    { en: "Heavy days happen. You showed up anyway.", pl: "Ciężkie dni się zdarzają. I tak tu jesteś." },
    { en: "I heard you. That's enough for today.", pl: "Słyszę cię. To wystarczy na dziś." },
    { en: "Missing things is not weakness. It just hurts.", pl: "Tęsknota to nie słabość. Po prostu boli." },
    { en: "Okay. Let's just sit with that for a moment.", pl: "Okej. Usiądźmy z tym przez chwilę." },
    { en: "You don't have to fix it today.", pl: "Nie musisz tego dziś naprawiać." },
    { en: "That took courage to write down.", pl: "Odwaga — napisać to." },
    { en: "Heavy is honest. I respect that.", pl: "Ciężko — to szczere. Szanuję to." },
    { en: "Even capybaras have bad days. 🍯", pl: "Nawet kapibary mają gorsze dni. 🍯" },
    { en: "Thanks for telling me. Really.", pl: "Dzięki, że mi powiedziałeś. Serio." },
  ],
  moodOkay: [
    { en: "Okay is something. Good job showing up.", pl: "Okej to też jest coś. Dobra robota, że tu jesteś." },
    { en: "Solid okay. Honestly, that's fine.", pl: "Solidne okej. Serio, to jest w porządku." },
    { en: "Not every day has to be great. Okay works.", pl: "Nie każdy dzień musi być super. Okej wystarczy." },
    { en: "Noted. Okay is a perfectly good day.", pl: "Przyjęto. Okej to całkowicie dobry dzień." },
    { en: "Okay today. Maybe something small tomorrow.", pl: "Okej dziś. Może coś małego jutro." },
    { en: "Middle of the road. I'll take it. 🍯", pl: "Środek drogi. Biorę to. 🍯" },
    { en: "You checked in. That alone counts.", pl: "Zameldowałeś się. To samo w sobie się liczy." },
    { en: "Okay and honest beats fake good any day.", pl: "Szczere okej bije udawane dobrze każdego dnia." },
    { en: "Cool. No pressure to make it more than that.", pl: "Spoko. Nie musisz robić z tego więcej niż to." },
    { en: "Okay noted. See you tomorrow?", pl: "Okej, przyjęto. Do jutra?" },
  ],
  moodGood: [
    { en: "That's great! I'm happy with you.", pl: "To świetnie! Cieszę się razem z tobą." },
    { en: "Good! Add it to the hive. 🍯", pl: "Dobrze! Dodajmy to do ula. 🍯" },
    { en: "Something worked today. Remember that feeling.", pl: "Coś dziś zadziałało. Zapamiętaj to uczucie." },
    { en: "Good days exist. This is proof.", pl: "Dobre dni istnieją. Masz na to dowód." },
    { en: "Look at you, having a good one.", pl: "No proszę, masz dobry dzień." },
    { en: "Storing this one in the hive. 🍯", pl: "Zapisuję to w ulu. 🍯" },
    { en: "Good! Tell me more sometime.", pl: "Dobrze! Opowiedz mi o tym kiedyś więcej." },
    { en: "Something went right. That matters.", pl: "Coś poszło dobrze. To się liczy." },
    { en: "Happy to hear it. Genuinely.", pl: "Miło to słyszeć. Naprawdę." },
    { en: "Yes! Good days deserve to be noticed.", pl: "Tak! Dobre dni zasługują na uwagę." },
  ],
  missionComplete: [
    { en: "Mission done! A new cell in your hive. 🍯", pl: "Misja wykonana! Nowa komórka w ulu. 🍯" },
    { en: "You actually did it. That's the whole point.", pl: "Naprawdę to zrobiłeś. O to właśnie chodzi." },
    { en: "Small step. Real step. Big difference.", pl: "Mały krok. Prawdziwy krok. Duża różnica." },
    { en: "One more thing that's yours in this new place.", pl: "Jeszcze jedna rzecz, która należy do ciebie w tym nowym miejscu." },
    { en: "Hive is growing. Slowly, but it's growing.", pl: "Ul rośnie. Powoli, ale rośnie." },
    { en: "That was brave. Even if it didn't feel like it.", pl: "To było odważne. Nawet jeśli tak nie czułeś." },
    { en: "Done! I saw that. It counts.", pl: "Zrobione! Widziałem. Liczy się." },
    { en: "You did something today that mattered.", pl: "Dziś zrobiłeś coś, co ma znaczenie." },
    { en: "Look at the hive filling up. Look at that.", pl: "Popatrz, jak ul się zapełnia. No popatrz." },
    { en: "Okay I'm a little proud of you right now.", pl: "Wiesz co, jestem teraz z ciebie trochę dumny." },
  ],
  friendshipAdded: [
    { en: "Got it! Every step counts.", pl: "Zapamiętałem! Każdy krok się liczy." },
    { en: "You noticed someone. That's how it starts.", pl: "Zauważyłeś kogoś. Tak to się zaczyna." },
    { en: "A name in the hive. Good.", pl: "Imię w ulu. Dobrze." },
    { en: "Your people are out there. You're finding them.", pl: "Twoja paczka gdzieś tam jest. Właśnie jej szukasz." },
    { en: "One person closer to finding your hive.", pl: "O jedną osobę bliżej do znalezienia swojego ula." },
    { en: "Saved. Small things matter more than they look.", pl: "Zapisane. Małe rzeczy mają większe znaczenie niż wyglądają." },
    { en: "Friendships start exactly like this.", pl: "Przyjaźnie zaczynają się dokładnie tak." },
    { en: "I see it. I'm keeping track with you.", pl: "Widzę to. Śledzę to razem z tobą." },
    { en: "You're paying attention. That's a skill.", pl: "Zwracasz uwagę. To jest umiejętność." },
    { en: "The hive knows. 🍯", pl: "Ul wie. 🍯" },
  ],
  memoryOldWorld: [
    { en: "Saved. This will always be yours.", pl: "Zapisane. To zawsze będzie twoje." },
    { en: "The old home stays here. Safe.", pl: "Stary dom zostaje tutaj. Bezpieczny." },
    { en: "It's okay to miss it. It was real and it mattered.", pl: "Wolno ci za tym tęsknić. To było prawdziwe i ważne." },
    { en: "Keeping it. You can come back anytime.", pl: "Przechowuję to. Możesz tu wracać kiedy chcesz." },
    { en: "That memory is sealed in the hive now. 🍯", pl: "To wspomnienie jest teraz zapieczętowane w ulu. 🍯" },
    { en: "Old doesn't mean gone. It's right here.", pl: "Stare nie znaczy zniknięte. Jest tu." },
    { en: "I'm glad you wrote that down.", pl: "Cieszę się, że to zapisałeś." },
    { en: "Home is more than one place. You're allowed that.", pl: "Dom to więcej niż jedno miejsce. Masz do tego prawo." },
    { en: "Stored safely. Nobody takes this from you.", pl: "Przechowane bezpiecznie. Nikt ci tego nie zabierze." },
    { en: "Even far away, it's still yours.", pl: "Nawet daleko — wciąż twoje." },
  ],
  memoryNewWorld: [
    { en: "A new moment in the hive! 🍯", pl: "Nowa chwila w ulu! 🍯" },
    { en: "Look — something new is yours here now.", pl: "Patrz — coś nowego należy tu teraz do ciebie." },
    { en: "The hive is getting real.", pl: "Ul staje się prawdziwy." },
    { en: "One more piece of the new place that's actually yours.", pl: "Jeszcze jeden kawałek nowego miejsca, który jest naprawdę twój." },
    { en: "New things can be good things. This is proof.", pl: "Nowe rzeczy mogą być dobrymi rzeczami. Masz na to dowód." },
    { en: "Building it, one memory at a time.", pl: "Budujesz to, jedno wspomnienie na raz." },
    { en: "That's yours. Nobody can tell you otherwise.", pl: "To twoje. Nikt ci nie powie inaczej." },
    { en: "The new hive has a new cell. I love that.", pl: "Nowy ul ma nową komórkę. Uwielbiam to." },
    { en: "Somewhere, this place just got a little more yours.", pl: "Gdzieś, to miejsce stało się trochę bardziej twoje." },
    { en: "Saved. The hive remembers. 🍯", pl: "Zapisane. Ul pamięta. 🍯" },
  ],
}
```

---

### Step 2 — Create the phrase picker hook

Create file: `src/hooks/useCapyBeePhrase.js`

```js
// src/hooks/useCapyBeePhrase.js
import { useRef, useCallback } from 'react'
import { capybeePhrases } from '../data/capybeePhrases'

export function useCapyBeePhrase(poolKey, locale = 'en') {
  const lastIndexRef = useRef(-1)

  const pick = useCallback(() => {
    const pool = capybeePhrases[poolKey]
    if (!pool || pool.length === 0) return ''
    let index
    do {
      index = Math.floor(Math.random() * pool.length)
    } while (index === lastIndexRef.current && pool.length > 1)
    lastIndexRef.current = index
    return pool[index][locale] ?? pool[index]['en']
  }, [poolKey, locale])

  return pick
}
```

Usage example in any component:

```js
const pickPhrase = useCapyBeePhrase('moodHeavy', currentLocale)
// call pickPhrase() after successful API response to get the string
```

---

### Step 3 — Wire to each trigger

#### SCR-03 Home — mood check-in

In the check-in form component, after `POST /api/check-ins` resolves successfully:

```js
const poolKey = mood === 'heavy' ? 'moodHeavy'
              : mood === 'okay'  ? 'moodOkay'
              : 'moodGood'

const pickPhrase = useCapyBeePhrase(poolKey, locale)

onCheckInSuccess(mood) {
  const phrase = pickPhrase()
  showCapyBeeBubble(phrase)   // triggers the avatar swap + bubble display
}
```

The `showCapyBeeBubble` function updates component state to display the bubble with fade-in. After 4000ms call `hideCapyBeeBubble` which triggers fade-out and then removes the bubble from DOM after 300ms.

#### SCR-06 Missions — mission complete

In the mission list component, after `POST /api/missions/{missionId}/completions` resolves successfully:

```js
const pickPhrase = useCapyBeePhrase('missionComplete', locale)

onMissionCompleted() {
  const phrase = pickPhrase()
  showCompletionBanner(phrase)   // banner at top of screen with capybee-celebrating.png
}
```

#### SCR-08 Friendship tracker — entry created

In the friendship form component, after `POST /api/friendships` resolves successfully:

```js
const pickPhrase = useCapyBeePhrase('friendshipAdded', locale)

onFriendshipCreated() {
  const phrase = pickPhrase()
  showToast(phrase)   // toast at bottom with capybee-celebrating.png
}
```

#### SCR-09 Memories — memory saved

In the memory form component, after `POST /api/memories` resolves successfully:

```js
const poolKey = activeTab === 'old_world' ? 'memoryOldWorld' : 'memoryNewWorld'
const pickPhrase = useCapyBeePhrase(poolKey, locale)

onMemorySaved() {
  const phrase = pickPhrase()
  showToast(phrase)   // toast at bottom with capybee-celebrating.png
}
```

---

### Step 4 — Locale wiring

The `locale` value passed to `useCapyBeePhrase` must come from the same locale state used by the rest of the app (the `en`/`pl` toggle). Do not hardcode it. Pass it down as a prop or read it from context — whichever pattern the app already uses.

---

### Step 5 — Do not add phrases anywhere else

Do not add CapyBee phrase responses to:
- Delete actions (friendship delete, memory delete) — deletion is private and quiet
- Edit/PATCH actions — quiet save is fine, no celebration needed
- Login / logout — handled separately by avatar greeting logic in `05-avatars-adding-instruction.md`
- Loading states — no phrase during loading, only during success

---

## Phrase count summary

| Pool key | Trigger | Phrases |
|---|---|---|
| `moodHeavy` | Check-in saved, mood = heavy | 10 |
| `moodOkay` | Check-in saved, mood = okay | 10 |
| `moodGood` | Check-in saved, mood = good | 10 |
| `missionComplete` | Mission marked complete | 10 |
| `friendshipAdded` | Friendship entry created | 10 |
| `memoryOldWorld` | Memory saved in Old World tab | 10 |
| `memoryNewWorld` | Memory saved in New World tab | 10 |
| **Total** | | **70 phrases** |
