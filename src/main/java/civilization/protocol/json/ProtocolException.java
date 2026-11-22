package civilization.protocol.json;

// PHASE 3 - Added in Phase 3
public final class ProtocolException extends IllegalArgumentException {
    // PHASE 3 - Added in Phase 3
    private static final long serialVersionUID = 1L;
    // PHASE 3 - Added in Phase 3
    private final Code code;

    // PHASE 3 - Added in Phase 3
    public ProtocolException(Code code, String message) {
        super(message);
        this.code = java.util.Objects.requireNonNull(code, "code");
    }

    // PHASE 3 - Added in Phase 3
    public ProtocolException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = java.util.Objects.requireNonNull(code, "code");
    }

    // PHASE 3 - Added in Phase 3
    public Code code() {
        return code;
    }

    // PHASE 3 - Added in Phase 3
    public enum Code {
        // PHASE 3 - Added in Phase 3
        MALFORMED_JSON,
        // PHASE 3 - Added in Phase 3
        UNKNOWN_MESSAGE_TYPE,
        // PHASE 3 - Added in Phase 3
        UNSUPPORTED_PROTOCOL_VERSION,
        // PHASE 3 - Added in Phase 3
        INVALID_MESSAGE
    }
}
