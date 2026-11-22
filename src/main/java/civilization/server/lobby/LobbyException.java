package civilization.server.lobby;

// PHASE 3 - Added in Phase 3
public final class LobbyException extends RuntimeException {
    // PHASE 3 - Added in Phase 3
    private static final long serialVersionUID = 1L;
    // PHASE 3 - Added in Phase 3
    private final String code;
    // PHASE 3 - Added in Phase 3
    public LobbyException(String code, String message) { super(message); this.code = code; }
    // PHASE 3 - Added in Phase 3
    public String code() { return code; }
}
