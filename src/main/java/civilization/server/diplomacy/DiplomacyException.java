package civilization.server.diplomacy;

// PHASE 3 - Added in Phase 3
public final class DiplomacyException extends RuntimeException {
    // PHASE 3 - Added in Phase 3
    private static final long serialVersionUID = 1L;
    // PHASE 3 - Added in Phase 3
    private final String code;

    // PHASE 3 - Added in Phase 3
    public DiplomacyException(String code, String message) {
        super(message);
        this.code = code;
    }

    // PHASE 3 - Added in Phase 3
    public String code() { return code; }
}
