package civilization.server.combat;

// PHASE 3 - Added in Phase 3
public record ServerAttackResult(long stateVersion, PvpCombatResult combat) {
    // PHASE 3 - Added in Phase 3
    public ServerAttackResult {
        if (stateVersion < 0) throw new IllegalArgumentException("stateVersion cannot be negative");
        if (combat == null) throw new NullPointerException("combat");
    }
}
