package civilization.model.unit;

import java.util.Collection;

/** Domain occupancy invariant shared by player/world aggregates and action policies. */
public final class MilitaryStackLimits implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    public static final int MAX_SWORDSMEN = 2;
    public static final int MAX_ARCHERS = 2;
    public static final int MAX_CAVALRY = 1;

    private MilitaryStackLimits() { }

    public static boolean allows(Collection<? extends Unit> occupants, UnitType incomingType) {
        if (!incomingType.isMilitary()) return true;
        return occupants.stream().filter(Unit::isActive)
                .filter(unit -> unit.type() == incomingType).count() < maximum(incomingType);
    }

    public static boolean isLegal(Collection<? extends Unit> occupants) {
        return count(occupants, UnitType.SWORDSMAN) <= MAX_SWORDSMEN
                && count(occupants, UnitType.ARCHER) <= MAX_ARCHERS
                && count(occupants, UnitType.CAVALRY) <= MAX_CAVALRY;
    }

    public static int maximum(UnitType type) {
        return switch (type) {
            case SWORDSMAN -> MAX_SWORDSMEN;
            case ARCHER -> MAX_ARCHERS;
            case CAVALRY -> MAX_CAVALRY;
            default -> Integer.MAX_VALUE;
        };
    }

    private static long count(Collection<? extends Unit> units, UnitType type) {
        return units.stream().filter(Unit::isActive).filter(unit -> unit.type() == type).count();
    }
}
