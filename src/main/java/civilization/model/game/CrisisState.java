package civilization.model.game;

import civilization.model.economy.ResourceBundle;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class CrisisState implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final EnumSet<CrisisType> activeCrises = EnumSet.noneOf(CrisisType.class);
    private ResourceBundle shortage = ResourceBundle.ZERO;

    public boolean isActive(CrisisType type) {
        return activeCrises.contains(Objects.requireNonNull(type, "type"));
    }

    public boolean hasAnyCrisis() {
        return !activeCrises.isEmpty();
    }

    public Set<CrisisType> activeCrises() {
        return Collections.unmodifiableSet(activeCrises);
    }

    public ResourceBundle shortage() {
        return shortage;
    }

    public boolean isPopulationGrowthAllowed() {
        return !isActive(CrisisType.STARVATION);
    }

    public void activate(CrisisType type) {
        activeCrises.add(Objects.requireNonNull(type, "type"));
    }

    public void resolve(CrisisType type) {
        activeCrises.remove(Objects.requireNonNull(type, "type"));
    }

    public void setShortage(ResourceBundle shortage) {
        this.shortage = Objects.requireNonNull(shortage, "shortage");
        if (shortage.isZero()) {
            resolve(CrisisType.RESOURCE_SHORTAGE);
        } else {
            activate(CrisisType.RESOURCE_SHORTAGE);
        }
    }
}
