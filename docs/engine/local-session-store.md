# Local session store

Using the console with `--store-dir <directory>` keeps the complete local session in `session-store.json`: runs, pending starts, command replay records, ordered events, and generation usage. Format v4 stores every accepted authored or generated snapshot through the engine's canonical `SnapshotCodec`, including provenance and quality stamps. Prefetched artifacts remain disposable cache data and are rebuilt when needed. The document is replaced atomically after each change and the directory is locked while a console is using it.

`session-store.key` contains the local command-MAC key. Both files are private data for the developer's own machine: snapshots, events, and pending input are stored in clear text. Do not share the directory.

Version 1 and 2 documents and checksummed version 3 authored documents are migrated in memory and are not rewritten until the next store change. The first real mutation writes version 4 and retains the previous valid document as the backup. Older builds cannot read a v4 document, so upgrade every console sharing a `--store-dir` together.

If the console reports `STORE_LOCKED`, close the other console or select another store directory. `STORE_CORRUPT` leaves the damaged file untouched; `STORE_VERSION_UNSUPPORTED` means the store was produced by a newer build; and `STORE_LEGACY_LAYOUT` means old per-run files were found—use a new directory instead.

Per-run JSON files from builds before the versioned session store are not imported. Choose a new `--store-dir` when `STORE_LEGACY_LAYOUT` is reported.
