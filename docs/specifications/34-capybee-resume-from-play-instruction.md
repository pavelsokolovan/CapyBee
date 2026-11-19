# Spec 34 — CapyBee resume from Play

**Depends on:** 28.2–28.4, 31 and 32

**Baseline:** `3645afe`

Give returning players a one-tap route from **Play** to an active saved
adventure. Keep **Adventures** available for browsing and for checking an
unfinished start.

1. Show **Resume adventure** above the Adventures entry when the signed-in
   player's current run is active and readable. Use its story title and,
   when available, its accepted location. Keep the UI labels in the app
   language and the story labels in the story language.
2. On selection, recheck the current run and open its accepted scene directly.
   Skip catalog loading when the saved run still matches. If a newer run
   replaced the preview or the run ended, open the catalog so the player can
   choose what to play. Give existing start recovery priority.
3. Refresh the shortcut on return to Play, reconnection, and relevant updates
   from another tab. Hide previews on sign-out, owner changes, and offline.
   Keep preview data in component memory and use the existing authenticated,
   no-store reads. A background preview can disappear without asking to
   restore the session. Foreground resume uses the existing restoration flow.
   Opening the shortcut creates no start or story command.
   The gameplay controller keeps its existing recovery for pending actions.
4. Keep the shortcut keyboard-accessible and return focus to its opener when
   leaving play, with Adventures as the fallback when the shortcut disappears.
   Verify a fresh reload into a saved clue, shortcut removal on ending, the
   stale-preview fallback, and preservation of the ordinary catalog resume.
