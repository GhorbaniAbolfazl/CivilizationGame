package civilization.model.time;

import java.util.Objects;

/** Deterministic four-season calendar; every season lasts exactly ten turns. */
public final class SeasonState implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    public static final int TURNS_PER_SEASON = 10;

    private Season season;
    private int turnWithinSeason;

    public SeasonState() {
        this(Season.SPRING, 1);
    }

    public SeasonState(Season season, int turnWithinSeason) {
        this.season = Objects.requireNonNull(season, "season");
        if (turnWithinSeason < 1 || turnWithinSeason > TURNS_PER_SEASON) {
            throw new IllegalArgumentException("Turn within season must be from 1 to 10");
        }
        this.turnWithinSeason = turnWithinSeason;
    }

    public Season season() {
        return season;
    }

    public int turnWithinSeason() {
        return turnWithinSeason;
    }

    public Weather weather() { return Weather.forSeason(season); }

    /** Advances one completed turn and returns true when the season changes. */
    public boolean advanceOneTurn() {
        if (turnWithinSeason < TURNS_PER_SEASON) {
            turnWithinSeason++;
            return false;
        }
        season = season.next();
        turnWithinSeason = 1;
        return true;
    }
}
