# Spec 40 — CapyBee short authored text

Shorten the Polish story and its reviewed generation fallbacks for **Nowy ul, dwa
światła**. Use at most 20 words per narration block. Split longer beats into
successive blocks where needed. Keep choice labels to two to five words.

Preserve scene and option IDs, map travel, hub actions, conditions, effects, and
reviewed authored endings. Spec 41 owns generation limits. The fallback schema
has one narration string, so use a short complete version of the same beat when
its authored scene has several blocks. Keep fallback labels aligned with the story.

The original intent named 1.3.0. That version and 1.5.0 are already assigned, so
publish the reconciled pack as 1.6.0. Retain existing published pack bytes for saves.
