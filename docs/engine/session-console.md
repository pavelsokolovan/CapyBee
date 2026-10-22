# Session console

Use `--session` to run the engine through the resumable local-session host. Without
`--store-dir`, data exists only for the life of the console process. With a store
directory, the current run, command history, outbox events, usage ledger, and
accepted generated content are retained. Each accepted command is committed before
it is reported as applied; completed runs retain their final result for read-only reopen.

Useful session options are `--run <uuid>`, `--new`, `--debug`, `--prefetch on|off`,
`--calls-per-day <0..100000>`, and `--run-calls <1..1000>`. Prefetch defaults to on
for generated runs and off otherwise. `--calls-per-day` applies to both local profile
and global limits, so `0` is useful for rehearsing fallback scenes.

With `--debug`, each diagnostics line reports the most recent scene source and latency,
run calls, prefetch ready/in-flight counts, prefetch calls/hits/misses/wasted upper bound,
and remaining profile/global budgets. Player generation and prefetch have separate
concurrency limits; peak provider concurrency is their sum (defaults: 2 + 1).

Inside a session, use `/text <words>`, `/move <placeId>`, `/status`,
`/events [afterSeq]`, `/debug on|off`, `/abandon`, `/quit` (or `/q`), and `?` or
`/help`. Unknown slash commands are never submitted as player text; use `/text` for
text beginning with a slash. Interactive abandonment asks for confirmation; scripts
do not.

Recoverable errors are printed as `! CODE: hint` and leave the session open. Startup
and storage failures print the code and a hint to stderr and exit with code 3.
Unexpected preparation failures are stored as `START_FAILED`, and preparation wait
timeouts are stored as `START_TIMEOUT`, so reopening does not restart the same failed work.
