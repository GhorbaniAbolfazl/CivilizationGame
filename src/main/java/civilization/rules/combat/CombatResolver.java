package civilization.rules.combat;

import civilization.model.combat.CombatRollPair;
import civilization.model.combat.DiceResolution;
import civilization.model.combat.PairWinner;
import civilization.model.random.DiceSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.IntUnaryOperator;

/** Pure dice resolver: roll, modify, sort descending, pair, and compare. */
public final class CombatResolver {
    public DiceResolution resolve(int attackDice, int defenseDice, DiceSource source,
            IntUnaryOperator defenseModifier) {
        // PHASE 3 - Added in Phase 3
        return resolve(attackDice, defenseDice, source,
                java.util.Collections.nCopies(attackDice, 0), defenseModifier);
    }

    // PHASE 3 - Added in Phase 3
    public DiceResolution resolve(int attackDice, int defenseDice, DiceSource source,
                                  List<Integer> attackerBonuses,
                                  IntUnaryOperator defenseModifier) {
        if (attackDice < 0 || defenseDice < 0) {
            throw new IllegalArgumentException("Dice counts cannot be negative");
        }
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(attackerBonuses, "attackerBonuses");
        Objects.requireNonNull(defenseModifier, "defenseModifier");
        if (attackerBonuses.size() != attackDice
                || attackerBonuses.stream().anyMatch(bonus -> bonus < 0 || bonus > 1)) {
            throw new IllegalArgumentException("Each attacker requires a zero-or-one item bonus");
        }
        List<Integer> attackerRaw = roll(attackDice, source);
        List<Integer> defenderRaw = roll(defenseDice, source);
        // PHASE 3 - Added in Phase 3
        List<Integer> attackerSorted = java.util.stream.IntStream.range(0, attackDice)
                .map(index -> requireItemModifiedD6(attackerRaw.get(index)
                        + attackerBonuses.get(index))).boxed()
                .sorted(Comparator.reverseOrder()).toList();
        List<Integer> defenderSorted = defenderRaw.stream().mapToInt(defenseModifier::applyAsInt)
                .map(CombatResolver::requireD6).boxed().sorted(Comparator.reverseOrder()).toList();
        List<CombatRollPair> pairs = new ArrayList<>();
        int hitsOnAttacker = 0;
        int hitsOnDefender = 0;
        for (int index = 0; index < Math.min(attackerSorted.size(), defenderSorted.size()); index++) {
            int attack = attackerSorted.get(index);
            int defense = defenderSorted.get(index);
            PairWinner winner = attack > defense ? PairWinner.ATTACKER : PairWinner.DEFENDER;
            pairs.add(new CombatRollPair(attack, defense, winner));
            if (winner == PairWinner.ATTACKER) hitsOnDefender++;
            else hitsOnAttacker++;
        }
        return new DiceResolution(attackerRaw, defenderRaw, attackerSorted, defenderSorted,
                pairs, hitsOnAttacker, hitsOnDefender);
    }

    public DiceResolution resolve(int attackDice, int defenseDice, DiceSource source) {
        return resolve(attackDice, defenseDice, source, value -> value);
    }

    private static List<Integer> roll(int count, DiceSource source) {
        List<Integer> rolls = new ArrayList<>(count);
        for (int index = 0; index < count; index++) rolls.add(requireD6(source.rollD6()));
        return List.copyOf(rolls);
    }

    private static List<Integer> descending(List<Integer> values) {
        return values.stream().sorted(Comparator.reverseOrder()).toList();
    }

    private static int requireD6(int value) {
        if (value < 1 || value > 6) throw new IllegalStateException("DiceSource returned " + value);
        return value;
    }

    // PHASE 3 - Added in Phase 3
    private static int requireItemModifiedD6(int value) {
        if (value < 1 || value > 7) {
            throw new IllegalStateException("Item-modified combat die returned " + value);
        }
        return value;
    }
}
