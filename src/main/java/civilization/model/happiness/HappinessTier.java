package civilization.model.happiness;

public enum HappinessTier {
    GOLDEN_AGE,
    NORMAL,
    DISPLEASED,
    REVOLT;

    public static HappinessTier fromValue(int value) {
        if (value >= 3) return GOLDEN_AGE;
        if (value >= -2) return NORMAL;
        if (value >= -4) return DISPLEASED;
        return REVOLT;
    }
}
