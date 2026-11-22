package civilization.model.building;

/** Ordered Town Hall progression and the selected military-cap balance. */
public enum HallLevel {
    BASE_CAMP(1, 5),
    SETTLEMENT(2, 10),
    CAPITAL(3, 15);

    private final int number;
    private final int militaryUnitCap;

    HallLevel(int number, int militaryUnitCap) {
        this.number = number;
        this.militaryUnitCap = militaryUnitCap;
    }

    public int number() { return number; }
    public int militaryUnitCap() { return militaryUnitCap; }

    public HallLevel next() {
        return switch (this) {
            case BASE_CAMP -> SETTLEMENT;
            case SETTLEMENT -> CAPITAL;
            case CAPITAL -> throw new IllegalStateException("The Town Hall is already at maximum level");
        };
    }
}
