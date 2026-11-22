package civilization.rules.combat;

import civilization.model.unit.Unit;
import civilization.model.unit.UnitType;
import civilization.model.unit.MilitaryStackLimits;
import java.util.Collection;

public final class MilitaryStackingPolicy {
    public static final int MAX_SWORDSMEN = MilitaryStackLimits.MAX_SWORDSMEN;
    public static final int MAX_ARCHERS = MilitaryStackLimits.MAX_ARCHERS;
    public static final int MAX_CAVALRY = MilitaryStackLimits.MAX_CAVALRY;

    public boolean allows(Collection<? extends Unit> occupants, UnitType incomingType) {
        return MilitaryStackLimits.allows(occupants, incomingType);
    }

    public boolean isLegal(Collection<? extends Unit> occupants) {
        return MilitaryStackLimits.isLegal(occupants);
    }

    public int maximum(UnitType type) {
        return MilitaryStackLimits.maximum(type);
    }
}
