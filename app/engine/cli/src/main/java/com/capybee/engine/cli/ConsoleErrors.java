package com.capybee.engine.cli;

import com.capybee.engine.session.StoreException;

final class ConsoleErrors {
    private ConsoleErrors() {}

    static String sessionHint(String code) {
        return switch (code) {
            case "START_FAILED" -> "The game could not be prepared. Start a new game or check the saved-game status.";
            case "REVISION_CONFLICT" -> "The run moved on. The current scene is shown again.";
            case "RUN_BUSY" -> "The run is still locked by an earlier session. Wait up to 30 seconds and try again.";
            case "GENERATION_BUSY" -> "Too many generation jobs are running. Try again in a moment.";
            case "RATE_LIMITED" -> "The call budget for today is used up. Fallback scenes will be used.";
            case "GENERATION_UNAVAILABLE" -> "The text provider is not available. Check GENERATION_MODEL_ID and ANTHROPIC_API_KEY.";
            case "SERVICE_UNAVAILABLE" -> "The session could not save the change. Nothing was applied. Try again.";
            case "REQUEST_INVALID" -> "The input was not accepted. Type ? for help.";
            case "PAYLOAD_TOO_LARGE" -> "That text is too long. Shorten it.";
            case "RUN_NOT_ACTIVE" -> "This run is not active any more.";
            case "RUN_UNRECOVERABLE" -> "The saved run cannot be continued. Start a new run with --new.";
            case "RUN_NOT_FOUND" -> "No such run for the local player. Omit --run to continue the current run.";
            case "RUN_ALREADY_ACTIVE" -> "A run is already open. Continue it or use --new.";
            case "START_REJECTED" -> "The pack or inputs were rejected. Check --pack-dir, --locale and --input.";
            case "START_TIMEOUT" -> "Starting took too long. Run again with --new.";
            case "GAME_DISABLED" -> "Game sessions are switched off.";
            default -> "Unexpected internal error. Nothing was changed.";
        };
    }

    static String storeHint(StoreException.Code code) {
        return switch (code) {
            case STORE_LOCKED -> "This store is open in another console. Close it or use another --store-dir.";
            case STORE_CORRUPT -> "The store file is damaged and was left untouched.";
            case STORE_VERSION_UNSUPPORTED -> "The store was written by a newer version.";
            case STORE_LEGACY_LAYOUT -> "Old run files found. Use a new --store-dir.";
            default -> "Could not read or write the store; nothing was changed.";
        };
    }
}
