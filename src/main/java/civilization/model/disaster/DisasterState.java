package civilization.model.disaster;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import civilization.model.map.HexCoord;

/** Tracks occurrences and persistent effects; selection and damage are separate policies. */
public final class DisasterState implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<DisasterId, DisasterOccurrence> occurrences = new LinkedHashMap<>();
    private final Map<DisasterId, Integer> remainingEffectTurns = new LinkedHashMap<>();

    public void register(DisasterOccurrence occurrence) {
        Objects.requireNonNull(occurrence, "occurrence");
        if (occurrences.putIfAbsent(occurrence.id(), occurrence) != null) {
            throw new IllegalStateException("Duplicate disaster: " + occurrence.id());
        }
        if (occurrence.effectTurns() > 0) remainingEffectTurns.put(occurrence.id(), occurrence.effectTurns());
    }

    public int remainingEffectTurns(DisasterId id) {
        if (!occurrences.containsKey(Objects.requireNonNull(id, "id"))) {
            throw new IllegalArgumentException("Unknown disaster: " + id);
        }
        return remainingEffectTurns.getOrDefault(id, 0);
    }

    public void advancePersistentEffects() {
        remainingEffectTurns.replaceAll((id, turns) -> turns - 1);
        remainingEffectTurns.values().removeIf(turns -> turns <= 0);
    }

    /**
     * Advances timed effects and removes history that can no longer affect rules.
     * Bear occurrences remain through their exact five-turn cooldown window.
     */
    public void advancePersistentEffectsAndPrune(int currentTurn) {
        if (currentTurn < 1) throw new IllegalArgumentException("Turn must be positive");
        advancePersistentEffects();
        occurrences.entrySet().removeIf(entry -> {
            DisasterOccurrence occurrence = entry.getValue();
            if (remainingEffectTurns.containsKey(entry.getKey())) return false;
            return occurrence.type() != DisasterType.BEAR_ATTACK
                    || currentTurn - occurrence.occurredOnTurn() > 5;
        });
    }

    /** Flooded production buildings remain disabled while their occurrence is active. */
    public boolean blocksProductionAt(HexCoord coordinate) {
        Objects.requireNonNull(coordinate, "coordinate");
        return remainingEffectTurns.entrySet().stream().anyMatch(entry -> entry.getValue() > 0
                && occurrences.get(entry.getKey()).type() == DisasterType.FLOOD
                && occurrences.get(entry.getKey()).affectedHexes().contains(coordinate));
    }

    public boolean bearAttackOnCooldown(HexCoord nest, int turnNumber) {
        return occurrences.values().stream()
                .filter(item -> item.type() == DisasterType.BEAR_ATTACK)
                .filter(item -> item.center().distanceTo(nest) <= 3)
                .anyMatch(item -> turnNumber - item.occurredOnTurn() <= 5);
    }

    public Collection<DisasterOccurrence> occurrences() {
        return Collections.unmodifiableCollection(occurrences.values());
    }
}
