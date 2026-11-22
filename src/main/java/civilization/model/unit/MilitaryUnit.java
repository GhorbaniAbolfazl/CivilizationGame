package civilization.model.unit;

import civilization.model.common.Health;
import civilization.model.identity.FactionId;
import civilization.model.map.HexCoord;
import java.util.Objects;

public abstract sealed class MilitaryUnit extends Unit
        permits SwordsmanUnit, ArcherUnit, CavalryUnit {
    private static final long serialVersionUID = 1L;
    private final Health health;
    private final int attackRange;

    protected MilitaryUnit(UnitId id, UnitType type, HexCoord position, ActionPoints actionPoints,
            FactionId ownerId, int maximumHealth, int attackRange) {
        super(id, type, position, actionPoints, ownerId);
        if (!type.isMilitary() || attackRange <= 0) {
            throw new IllegalArgumentException("Invalid military unit definition");
        }
        this.health = new Health(maximumHealth);
        this.attackRange = attackRange;
    }

    public final Health health() { return health; }
    public final int attackRange() { return attackRange; }

    public final boolean canAttack(HexCoord target) {
        int distance = position().distanceTo(Objects.requireNonNull(target, "target"));
        return distance >= 1 && distance <= attackRange;
    }
}
