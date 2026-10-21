# Local session store

Using the console with `--store-dir <directory>` keeps the complete local session in `session-store.json`: runs, pending starts, command replay records, ordered events, prefetched artifacts, and generation usage. Format v2 also persists command latency/source and per-run prefetch reservations. The document is replaced atomically after each change and the directory is locked while a console is using it.

`session-store.key` contains the local command-MAC key. Both files are private data for the developer's own machine: snapshots, events, and pending input are stored in clear text. Do not share the directory.

Version 1 documents are migrated in memory and are not rewritten until the next store change. Builds from before Spec 27.3 cannot read a v2 document; with their strict JSON decoder they report `STORE_CORRUPT`, so upgrade every console sharing a `--store-dir` together.

If the console reports `STORE_LOCKED`, close the other console or select another store directory. `STORE_CORRUPT` leaves the damaged file untouched; `STORE_VERSION_UNSUPPORTED` means the store was produced by a newer build; and `STORE_LEGACY_LAYOUT` means old per-run files were found—use a new directory instead.

Per-run JSON files from builds before the versioned session store are not imported. Choose a new `--store-dir` when `STORE_LEGACY_LAYOUT` is reported.
