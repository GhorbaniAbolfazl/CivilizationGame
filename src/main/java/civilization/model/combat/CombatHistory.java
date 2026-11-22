package civilization.model.combat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import civilization.model.unit.UnitId;

public final class CombatHistory implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final List<CombatKillRecord> kills = new ArrayList<>();
    private transient Set<UnitId> recordedUnitIds = new HashSet<>();

    public void recordKill(CombatKillRecord kill) {
        CombatKillRecord checked = Objects.requireNonNull(kill, "kill");
        ensureIndex();
        if (!recordedUnitIds.add(checked.unitId())) {
            throw new IllegalStateException("Duplicate kill record: " + checked.unitId());
        }
        kills.add(checked);
    }

    public List<CombatKillRecord> kills() { return List.copyOf(kills); }

    /** Drops evidence older than the earliest active bounty mission can observe. */
    public void pruneBefore(int earliestTurnToKeep) {
        kills.removeIf(kill -> kill.turnNumber() < earliestTurnToKeep);
        rebuildIndex();
    }

    /** No resolved or unaccepted mission can consume historical kill evidence. */
    public void clear() {
        kills.clear();
        ensureIndex();
        recordedUnitIds.clear();
    }

    private void ensureIndex() {
        if (recordedUnitIds == null) rebuildIndex();
    }

    private void rebuildIndex() {
        recordedUnitIds = new HashSet<>();
        for (CombatKillRecord kill : kills) recordedUnitIds.add(kill.unitId());
    }

    private void readObject(java.io.ObjectInputStream input)
            throws java.io.IOException, ClassNotFoundException {
        input.defaultReadObject();
        rebuildIndex();
    }
}
