package civilization.model.combat;

import java.util.List;

public record DiceResolution(
        List<Integer> attackerRawRolls,
        List<Integer> defenderRawRolls,
        List<Integer> attackerSortedRolls,
        List<Integer> defenderSortedRolls,
        List<CombatRollPair> pairs,
        int hitsOnAttacker,
        int hitsOnDefender) implements java.io.Serializable {
    public DiceResolution {
        attackerRawRolls = List.copyOf(attackerRawRolls);
        defenderRawRolls = List.copyOf(defenderRawRolls);
        attackerSortedRolls = List.copyOf(attackerSortedRolls);
        defenderSortedRolls = List.copyOf(defenderSortedRolls);
        pairs = List.copyOf(pairs);
        if (hitsOnAttacker < 0 || hitsOnDefender < 0) {
            throw new IllegalArgumentException("Hit counts cannot be negative");
        }
    }
}
