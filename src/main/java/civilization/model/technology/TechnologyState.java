package civilization.model.technology;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

public final class TechnologyState implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final EnumSet<UpgradeId> completed = EnumSet.noneOf(UpgradeId.class);
    private final Set<TechnologyId> researched = new HashSet<>();

    public TechnologyState() {
    }

    public TechnologyState(Set<UpgradeId> completed) {
        Objects.requireNonNull(completed, "completed");
        this.completed.addAll(completed);
        validatePrerequisites();
    }

    public boolean isCompleted(UpgradeId upgradeId) {
        return completed.contains(Objects.requireNonNull(upgradeId, "upgradeId"));
    }

    public void complete(UpgradeId upgradeId) {
        Objects.requireNonNull(upgradeId, "upgradeId");
        if (isCompleted(upgradeId)) {
            throw new IllegalStateException("Upgrade is already completed: " + upgradeId);
        }
        completed.add(upgradeId);
        try {
            validatePrerequisites();
        } catch (RuntimeException invalidState) {
            completed.remove(upgradeId);
            throw invalidState;
        }
    }

    public Set<UpgradeId> completedUpgrades() {
        return Collections.unmodifiableSet(completed);
    }

    public int storageUpgradeLevel() {
        if (completed.contains(UpgradeId.STORAGE_2)) {
            return 2;
        }
        return completed.contains(UpgradeId.STORAGE_1) ? 1 : 0;
    }

    public boolean isResearched(TechnologyId id) {
        return researched.contains(Objects.requireNonNull(id, "id"));
    }

    public void research(TechnologyId id, TechnologyCatalog catalog) {
        Objects.requireNonNull(catalog, "catalog");
        if (!catalog.canResearch(id, researched)) {
            throw new IllegalStateException("Technology prerequisites are not satisfied: " + id);
        }
        researched.add(id);
    }

    public Set<TechnologyId> researchedTechnologies() {
        return Collections.unmodifiableSet(researched);
    }

    private void validatePrerequisites() {
        if (completed.contains(UpgradeId.STORAGE_2) && !completed.contains(UpgradeId.STORAGE_1)) {
            throw new IllegalArgumentException("Storage upgrade 2 requires storage upgrade 1");
        }
        if (completed.contains(UpgradeId.IRON_MINING) && !completed.contains(UpgradeId.STONE_MINING)) {
            throw new IllegalArgumentException("Iron mining requires stone mining");
        }
        if (completed.contains(UpgradeId.PROFESSIONAL_TOOLS)
                && (!completed.contains(UpgradeId.STONE_MINING)
                || !completed.contains(UpgradeId.IRON_MINING))) {
            throw new IllegalArgumentException(
                    "Professional tools requires both stone and iron mining");
        }
    }
}
