package civilization.model.time;

/** Authoritative weather state derived solely from the current global season. */
public enum Weather {
    CLEAR,
    RAIN,
    SNOW;

    public static Weather forSeason(Season season) {
        return switch (season) {
            case AUTUMN -> RAIN;
            case WINTER -> SNOW;
            case SPRING, SUMMER -> CLEAR;
        };
    }
}
