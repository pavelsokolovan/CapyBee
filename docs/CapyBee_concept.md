# CapyBee — App Concept Document

> **"Razem budujemy nowy ul"** / *"Together we build a new hive"*

---

## 1. Problem

Children aged ~12 who relocate to a new country struggle with:
- Social isolation — can't find friends in new school
- Homesickness — miss old home, friends, culture
- Emotional overwhelm — no tools to process the transition
- No option to return — must adapt, but that's painful

The app must **hold both realities**: honoring the old home while actively helping build a new life.

---

## 2. Core Philosophy

- NOT a therapy app — a **companion journey**
- NOT a replacement friend — helps **find real friends**
- Never says "it'll get better" or "you'll love it here"
- Validates feelings first, then offers gentle action
- Homesickness is treated as **normal and healthy**, not a problem to fix
- New memories **coexist** with old ones — never replace them

---

## 3. Character: CapyBee

A hybrid **capybara + bee** character.

### Why this combination:
| Animal | Symbolism |
|---|---|
| Capybara | Calm, gentle, universally liked, everyone's friend |
| Bee | Builds a hive (home/community), social, finds its group |

### Visual direction:
- Round, fluffy capybara body
- Small soft bee wings (cute, not scary)
- Little antennae
- Warm yellow + honey brown colors
- Expression: calm, warm, occasionally silly

### Personality:
- Speaks simply and warmly, never lectures
- Sometimes just says *"That sounds really hard. I'm here."*
- Occasionally silly — diffuses tension
- Never pushes — if kid skips a day: *"Hey, you're back. How was it?"*

---

## 4. Brand Identity

| Element | Value |
|---|---|
| App name | **CapyBee** |
| Tagline (PL) | *"Razem budujemy nowy ul"* |
| Tagline (EN) | *"Together we build a new hive"* |
| Primary colors | Warm yellow, honey gold, soft brown |
| Feel | Gentle, playful, never clinical |
| Target age | ~12 years old |
| Languages | English + Polish |

---

## 5. Two-World Structure

The app holds both worlds simultaneously:

### 🏡 My Old World
A space the child builds and decorates with memories:
- Places they miss
- Friends' names and drawings
- Food, music, traditions from home
- Letters to old friends (even unsent — just to process)

CapyBee message: *"It's okay to miss it. It was real and it mattered."*

Visually: **a golden sealed honeycomb cell** — precious, kept, never deleted.

### 🌱 My New World
Slowly built piece by piece:
- Starts almost empty — honest, not fake-positive
- Each small win adds something to a growing hive map
- Empty honeycomb cells fill in as missions are completed
- Visual proof that something real is being built

CapyBee lives **between both worlds** — carries things from one to the other.

---

## 6. Core Features

### Daily Check-in (2 minutes max)
Not a questionnaire. Visual metaphors:
- *"How's today — heavy, okay, or actually good?"*
- CapyBee reacts and responds based on answer
- Connects to mission suggestion

### Honeycomb Map (main visual)
- Each hexagon cell = a win, memory, person met, or mission completed
- Empty at start, fills over time
- Old World memories in gold cells, New World wins in fresh cells
- Main screen — child sees progress at a glance

### Small Missions (Capy Quests)
Real-world gentle challenges:
- *"Say hi to one person today"*
- *"Sit one table closer to someone at lunch"*
- *"Find one thing you like about your new school"*
- *"Draw your old home and your new home"*
- *"Does your school have an art club? Let's find out."*

Completing missions earns stars → unlocks CapyBee accessories or new hive areas.

### Friendship Tracker (private, no social features)
Simple private list — no social media, no strangers:
- Someone I noticed
- Someone who was nice to me
- Someone I talked to
- Someone I'd like to know better

CapyBee celebrates every single entry, no matter how small.

### Feelings Toolkit
Mini exercises disguised as games:
- **Breathing mini-game** — inflate a balloon slowly
- **Worry jar** — write/draw a worry, seal it, feel lighter
- **Memory box** — store photos/drawings of good things from old home

### "What do kids do here?" Explorer
Simple local activity finder — clubs, sports, hobby groups nearby.
CapyBee frames it: *"Let's find your people — they exist, we just have to find them."*

### Stories
Short illustrated stories about other kids who moved — relatable, hopeful, never preachy.

---

## 7. Language & Tone

### Polish tone rules:
- Use **"ty"** (casual) — never formal
- Short sentences, never lecture-y
- CapyBee speaks like a slightly older friend, not a teacher
- *"paczka"* = friend group/crew — natural for 12-year-olds

### Key phrases (bilingual):

| English | Polish |
|---|---|
| *"How's today — heavy, okay or good?"* | *"Jak dziś było — ciężko, okej, czy całkiem dobrze?"* |
| *"Missing home is not weakness"* | *"Tęsknota to nie słabość"* |
| *"Let's find your people"* | *"Znajdźmy twoją paczkę"* |
| *"You're back! How was it?"* | *"Hej, wróciłeś! Jak było?"* |
| *"That sounds really hard. I'm here."* | *"To brzmi naprawdę ciężko. Jestem tu."* |
| *"Find your hive"* | *"Znajdź swój ul"* |

---

## 8. Daily Flow (5 minutes max)

1. CapyBee asks: *"How's today — heavy, okay, or actually good?"*
2. One small reflection (Old World or New World)
3. One tiny mission suggested by CapyBee
4. Optional: add a memory or update friendship tracker

Light, consistent, never overwhelming.

---

## 9. What the App Quietly Teaches

Without the child realizing it:
- **Emotional vocabulary** — naming feelings
- **Social courage** — small real-world steps
- **Resilience thinking** — "it's hard AND something is being built"
- **Identity stability** — valuing where they came from

---

## 10. Security & Privacy (critical — app for children)

- Parent account linked to child account — parent registers, child uses
- No real names required — kids use nicknames only
- **No social features between users** — fully private, no chat with strangers
- No ads, no tracking, no data selling
- COPPA compliance mindset — minimal data collection
- All data encrypted at rest
- HTTPS only (automatic on Fly.io)
- JWT authentication via Spring Security

---

## 11. Tech Stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3 + Java 21 |
| Frontend | React + Tailwind CSS + Framer Motion |
| Database | PostgreSQL (Fly Postgres) |
| Authentication | Spring Security + JWT |
| Hosting | Fly.io |
| Container | Docker (single container — backend serves React build) |
| CI/CD | GitHub Actions → auto deploy to Fly.io |

### Architecture (single container approach):
```
React build/ → served as static files by Spring Boot
Spring Boot API → handles all backend logic
Fly Postgres → private internal network (not public)
All in one Docker container on Fly.io
```

### Fly.io free tier covers:
- 3 shared-CPU VMs (256MB RAM each)
- 3GB persistent storage
- 160GB/month bandwidth
- Free automatic TLS/HTTPS
- Free custom domain

---

## 12. Next Steps to Build

- [ ] Design CapyBee character visually
- [ ] Wireframe main screens (honeycomb map, check-in, missions, two worlds)
- [ ] Scaffold Spring Boot project structure
- [ ] Set up React + Tailwind + Framer Motion frontend
- [ ] Write Dockerfile (single container)
- [ ] Deploy skeleton app to Fly.io
- [ ] Build daily check-in flow
- [ ] Build honeycomb map component
- [ ] Build Old World / New World spaces
- [ ] Add bilingual support (EN/PL)
- [ ] Build mission system
- [ ] Build friendship tracker
- [ ] Parent account + child account auth flow
- [ ] Beta test with real kids

---

*Document generated from initial concept session. Ready to use as reference for development.*
