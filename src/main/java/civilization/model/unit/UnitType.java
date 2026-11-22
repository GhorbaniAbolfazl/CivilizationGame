package civilization.model.unit;

public enum UnitType {
    EXPLORER,
    BUILDER,
    WORKER,
    BORDER_EXPANDER,
    SWORDSMAN,
    ARCHER,
    CAVALRY;

    public boolean isMilitary() {
        return this == SWORDSMAN || this == ARCHER || this == CAVALRY;
    }
}
