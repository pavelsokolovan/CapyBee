# Installed-app updates and missing game illustrations

A successful deployment replaces server assets, not JavaScript already running
in an open installed app. The previous entry point only registered the worker;
although Workbox used skipWaiting and clientsClaim, the mounted React renderer
received no update notice or reload handling. Starting another adventure in that
same window could therefore continue using an older renderer.

The app now listens for controller changes and offers an English/Polish update
notice. Reload is explicit and warns that unsent text is cleared. Later dismisses
the notice so it cannot obstruct ongoing play. Initial installation is quiet.
Returning to the foreground or reconnecting checks the worker for updates;
o timers, game commands, storage clearing or automatic page reload are added.

Already-open clients from before this fix must load the new entry point once
before they can receive this notice. Do not delete site data or abandon an
adventure to update the renderer. A worker update can require a subsequent page
reload after its download finishes.

## Diagnostic boundary

The reported new-start screenshots rule out assuming an old save. They do not
identify the client bundle or the authenticated run's presentation payload.
The public live index served the expected current asset filenames, and local
Spring/PostgreSQL browser acceptance rendered the current Pack with its board,
portraits and backdrop. The affected phone's state still needs confirmation;
the worker handling defect is verified, but is not yet proven to be the only
cause of those screenshots.

If a fresh renderer still shows text only, inspect the actual run's Pack version,
view schema and whether presentation exists before/after client decoding.
Old saved Packs 1.0/1.1 intentionally retain text-only behavior. Current Packs
must expose illustrations; absent or rejected metadata on a new current run is
an additional defect to trace, not an expected text-mode setting.

## Verification

Frontend unit/coverage tests and official production build/static sync passed.
The integrated Spring/PostgreSQL browser gate passed with a controller-change
simulation against the real service-worker container: notice appears without
focus theft, reload or game writes; explicit reload loads the app normally.
Existing illustrated-game, text-mode, offline/recovery and PWA checks also passed.
No production account or game was modified during investigation.
