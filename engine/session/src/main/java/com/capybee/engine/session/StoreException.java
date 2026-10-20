package com.capybee.engine.session;

import java.util.Map;

/** A stable, non-sensitive failure when opening or updating a local session store. */
public final class StoreException extends RuntimeException {
    public enum Code { STORE_LOCKED, STORE_CORRUPT, STORE_VERSION_UNSUPPORTED, STORE_LEGACY_LAYOUT,
        STORE_WRITE_FAILED, STORE_READ_FAILED, STORE_TOO_LARGE, STORE_CLOSED }

    private final Code code;
    private final Map<String, Object> details;

    public StoreException(Code code) { this(code, Map.of(), null); }
    public StoreException(Code code, Throwable cause) { this(code, Map.of(), cause); }
    public StoreException(Code code, Map<String, Object> details) { this(code, details, null); }
    public StoreException(Code code, Map<String, Object> details, Throwable cause) {
        super(code.name(), cause);
        this.code = code;
        this.details = Map.copyOf(details);
    }
    public Code code() { return code; }
    public Map<String, Object> details() { return details; }
}
