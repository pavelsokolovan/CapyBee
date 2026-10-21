package com.capybee.engine.session.adapter;

import java.util.Map;

final class StoreMigrations {
    private StoreMigrations() {}

    static LocalSessionStore.Document toCurrent(LocalSessionStore.Document document) {
        return new LocalSessionStore.Document(document.format(), 2, document.runs(),
                document.reservations() == null ? Map.of() : document.reservations(),
                document.runReservations() == null ? Map.of() : document.runReservations(),
                document.usage() == null ? Map.of() : document.usage(),
                document.runPrefetchCalls() == null ? Map.of() : document.runPrefetchCalls());
    }
}
