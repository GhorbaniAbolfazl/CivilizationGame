package civilization.model.world;

import civilization.model.common.Health;
import civilization.model.map.HexCoord;
import java.util.Objects;

public final class WildAnimal implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final WildAnimalId id;
    private final Health health;
    private final HexCoord nest;
    private final int actionPointCapacity;
    private final int attackStrength;
    private int actionPoints;
    private HexCoord position;

    public WildAnimal(WildAnimalId id, HexCoord position) {
        this(id, position, 1);
    }

    public WildAnimal(WildAnimalId id, HexCoord position, int maximumHealth) {
        this(id, position, maximumHealth, position, 0, 0);
    }

    public static WildAnimal bear(WildAnimalId id, HexCoord position, HexCoord nest) {
        return new WildAnimal(id, position, 120, nest, 2, 35);
    }

    private WildAnimal(WildAnimalId id, HexCoord position, int maximumHealth,
            HexCoord nest, int actionPointCapacity, int attackStrength) {
        this.id = Objects.requireNonNull(id, "id");
        this.position = Objects.requireNonNull(position, "position");
        this.health = new Health(maximumHealth);
        this.nest = Objects.requireNonNull(nest, "nest");
        this.actionPointCapacity = actionPointCapacity;
        this.attackStrength = attackStrength;
        this.actionPoints = actionPointCapacity;
    }

    public WildAnimalId id() { return id; }
    public HexCoord position() { return position; }
    public Health health() { return health; }
    public HexCoord nest() { return nest; }
    public int actionPoints() { return actionPoints; }
    public int attackStrength() { return attackStrength; }
    public boolean isBear() { return attackStrength == 35 && health.maximum() == 120; }
    public void refillActionPoints() { actionPoints = actionPointCapacity; }
    public void spendActionPoint() {
        if (actionPoints <= 0) throw new IllegalStateException("Wild animal has no AP");
        actionPoints--;
    }
    public void moveTo(HexCoord destination) { position = Objects.requireNonNull(destination, "destination"); }
}
