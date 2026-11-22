package civilization.model.combat;

public record CombatRollPair(int attackerRoll, int defenderRoll, PairWinner winner) implements java.io.Serializable {
    public CombatRollPair {
        // PHASE 3 - Added in Phase 3
        if (attackerRoll < 1 || attackerRoll > 7 || defenderRoll < 1 || defenderRoll > 6) {
            throw new IllegalArgumentException("Combat rolls must be d6 values or an item-modified attack value of 7");
        }
        if (winner == null) throw new NullPointerException("winner");
    }
}
