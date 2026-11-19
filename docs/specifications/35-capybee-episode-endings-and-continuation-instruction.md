# Spec 35 — CapyBee episode endings and continuation

**Depends on:** 31 and 34

**Baseline:** `a2b1290`

Give **Tajemnicza wiadomość** a clear finish and a way to gauge interest in a
follow-up during an observed playtest.

1. When the server confirms completion, focus the ending heading and show
   the discovery and the accepted branch's contribution above the final actions.
   Keep the same text and actions in text mode.
2. While this is the only episode, offer **Chcę kolejny odcinek** as an optional,
   reversible response. Confirm the selection on screen. Keep the response in
   memory for this ending so a playtest facilitator can observe it. Closing
   the ending clears it. The response sends no data and creates no obligation.
3. Keep **Wróć do gier** available regardless of the response. An abandoned
   episode has its existing exit and no continuation prompt. Preserve normal
   endings and completion actions for other adventures.
4. When a follow-up ships, offer **Następny odcinek** only where the actual
   next episode can be started. This release exercises the interest option.
5. Verify both clue branches, heading focus, toggle and confirmation, text
   mode, abandonment, and that interest adds no network writes or storage.
