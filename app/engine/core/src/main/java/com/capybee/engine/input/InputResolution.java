package com.capybee.engine.input;

public sealed interface InputResolution permits InputResolution.SelectOption, InputResolution.ShortReply,
        InputResolution.NoMatch, InputResolution.Blocked {
    record SelectOption(String optionId) implements InputResolution {
        public SelectOption {
            if (optionId == null || !optionId.matches("[a-z][a-z0-9-]*")) {
                throw new IllegalArgumentException("Invalid option id");
            }
        }
    }

    record ShortReply(String text) implements InputResolution {
        @Override
        public String toString() {
            return "ShortReply[redacted]";
        }
    }

    record NoMatch(String reasonCode) implements InputResolution {
        public NoMatch {
            validateReason(reasonCode);
        }
    }

    record Blocked(String reasonCode) implements InputResolution {
        public Blocked {
            validateReason(reasonCode);
        }
    }

    private static void validateReason(String reasonCode) {
        if (reasonCode != null && !reasonCode.matches("[A-Z][A-Z0-9_]{0,47}")) {
            throw new IllegalArgumentException("Invalid input resolution reason code");
        }
    }
}