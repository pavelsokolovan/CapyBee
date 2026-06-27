# CapyBee User Stories

## Purpose

This document turns the CapyBee concept into implementation-facing user stories. It focuses on the first release and keeps the scope intentionally small, safe, and buildable.

## Scope For The First Release

The first release should support:
- parent account sign-in
- child profile bootstrap
- a calm first page and authenticated home shell
- daily check-ins
- small missions
- private memory and friendship tracking
- bilingual language support
- mobile-first phone experience for all core flows

The first release should not include:
- user-to-user chat
- public profiles
- social feeds
- ads or analytics tracking aimed at children
- gamified competition between users

## Actors

- Parent - creates the account, manages access, and views progress
- Child - uses the app daily and records feelings, missions, and memories
- System - suggests next steps, stores progress, and keeps data private

## Epic 1 - Account Setup And Safety

### US-001 - Parent can sign in with Google
As a parent, I want to sign in with Google so that I can create and access my family account quickly.

Acceptance criteria:
- A parent can start sign-in from the landing page.
- A successful login creates or reuses a parent account.
- The app shows an error state if sign-in fails.

Priority: Must have

### US-002 - Parent can create a child profile without a real name
As a parent, I want to create a child profile using a nickname so that the app stays private and age-appropriate.

Acceptance criteria:
- The child profile accepts a nickname instead of a real name.
- The profile stores language preference and basic display settings.
- The UI clearly explains that real names are optional and not required.

Priority: Must have

### US-003 - Child lands in a safe authenticated home
As a child, I want to enter a simple home screen after sign-in so that I immediately understand where I am.

Acceptance criteria:
- Authenticated users see a calm home page.
- The home page explains the app in a short, friendly way.
- The home page provides clear entry points to check-ins, missions, and memories.

Priority: Must have

## Epic 2 - Daily Check-In

### US-004 - Child can complete a short daily check-in
As a child, I want to choose how today feels so that I can check in without writing a lot.

Acceptance criteria:
- The check-in takes less than 2 minutes.
- The child can choose from a small set of mood options such as heavy, okay, or good.
- The check-in is saved with a timestamp.

Priority: Must have

### US-005 - CapyBee responds to the check-in
As a child, I want CapyBee to react to my check-in so that the app feels warm and supportive.

Acceptance criteria:
- The response changes based on the selected mood.
- The response validates the feeling before suggesting action.
- The tone stays gentle and never lectures.

Priority: Must have

### US-006 - System can suggest one small next step after check-in
As a child, I want one tiny suggestion after check-in so that I know what to do next.

Acceptance criteria:
- The app offers one mission or reflection suggestion.
- The suggestion is small enough to do in real life.
- The suggestion does not feel mandatory or overwhelming.

Priority: Should have

## Epic 3 - Honeycomb Progress Map

### US-007 - Child can see progress as honeycomb cells
As a child, I want to see my progress as a honeycomb map so that growth feels visible and concrete.

Acceptance criteria:
- The home screen can show a honeycomb-style layout.
- Completed actions add filled cells over time.
- The map starts mostly empty at the beginning.

Priority: Should have

### US-008 - Old World and New World are represented differently
As a child, I want old memories and new progress to look different so that both parts of my life feel respected.

Acceptance criteria:
- Old World items use a distinct visual treatment from New World items.
- The design does not imply that old memories should be deleted or replaced.
- The visual language supports coexistence of both worlds.

Priority: Should have

## Epic 4 - Missions And Encouragement

### US-009 - Child can receive a small mission
As a child, I want a small mission so that I can try one safe step in the real world.

Acceptance criteria:
- A mission appears as a short, clear prompt.
- The mission is realistic for a child in a new school or country.
- The mission can be skipped without punishment.

Priority: Must have

### US-010 - Child can mark a mission complete
As a child, I want to mark a mission complete so that I can see progress.

Acceptance criteria:
- The app stores mission completion history.
- A completed mission updates the UI immediately.
- The app shows positive feedback when a mission is completed.

Priority: Must have

### US-011 - Parent can see mission history
As a parent, I want to see which missions were completed so that I can support my child.

Acceptance criteria:
- Completed missions are visible in the family account.
- The parent view is read-friendly and simple.
- The view does not expose private child notes unnecessarily.

Priority: Should have

## Epic 5 - Friendship Tracker

### US-012 - Child can privately note someone they noticed
As a child, I want to note someone I noticed so that I can remember small social steps.

Acceptance criteria:
- The tracker supports short private entries.
- The child can record a nickname or short description.
- Entries are private and not shared with other users.

Priority: Must have

### US-013 - Child can record friendship progression
As a child, I want to track whether someone was nice, talked to me, or seems like a possible friend so that I can notice gradual progress.

Acceptance criteria:
- The tracker supports a few simple stages.
- The UI shows that every stage counts.
- The app does not rank children or compare them to others.

Priority: Should have

## Epic 6 - Feelings And Memory Tools

### US-014 - Child can save an old-home memory
As a child, I want to save a memory from my old home so that I can keep it with me.

Acceptance criteria:
- The child can add a short text note, drawing, or photo placeholder.
- The memory is stored privately.
- The app treats the memory as something valuable.

Priority: Should have

### US-015 - Child can use a simple calming tool
As a child, I want a calming tool such as breathing or a worry jar so that I can feel better when I am overwhelmed.

Acceptance criteria:
- The tool is presented as a small game or ritual.
- The experience is short and easy to complete.
- The tool never pretends to replace a real support person.

Priority: Could have

## Epic 7 - Language And Tone

### US-016 - Child can switch between English and Polish
As a child, I want to use the app in English or Polish so that I understand it comfortably.

Acceptance criteria:
- The app supports both languages in the UI.
- The chosen language is saved for future sessions.
- Key CapyBee phrases are localized naturally, not word-for-word.

Priority: Must have

### US-017 - Tone stays gentle and child-friendly
As a child, I want the app to sound warm and simple so that it feels safe to use.

Acceptance criteria:
- The text uses short sentences.
- The text avoids therapy jargon and lectures.
- The app validates homesickness instead of minimizing it.

Priority: Must have

## Epic 8 - Privacy And Data Safety

### US-018 - Data stays private within the family account
As a parent, I want the app to keep child data private so that I can trust it.

Acceptance criteria:
- Child data is not public.
- There are no stranger-to-stranger interactions.
- The app stores only the data needed for the feature.

Priority: Must have

### US-019 - App avoids unnecessary child data collection
As a parent, I want the app to collect minimal data so that the product respects child privacy.

Acceptance criteria:
- Real names are optional.
- The app does not require social account linking for the child.
- The app avoids tracking data unrelated to core features.

Priority: Must have

## Epic 9 - Mobile-First Experience

### US-020 - Child can complete core flows comfortably on a phone
As a child, I want the app to be easy to use on a phone so that I can check in and track progress without desktop-only layouts.

Acceptance criteria:
- All MVP screens work at common phone widths (360px and above) without horizontal scrolling.
- Primary actions are visible and reachable on the main viewport.
- Forms and tap targets are sized for touch input.

Priority: Must have

## Open Questions For The Next Spec Docs

- What exact fields should the parent account and child profile store?
- Which check-in moods should be available in the first release?
- Which missions belong in the starter mission set?
- How should the honeycomb map data be modeled so it supports both Old World and New World cells?
- Which endpoints are required for the first UI slice versus later feature work?

## Notes

- These stories are intentionally MVP-focused and leave room for later expansion.
- The wording should remain child-safe, privacy-first, and emotionally grounded.
- This document should be updated as the data model and API contract get more precise.
