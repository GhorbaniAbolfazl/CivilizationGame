package civilization.application.persistence;

import civilization.model.building.HallLevel;
import civilization.model.time.Season;
import civilization.model.time.Weather;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SaveMetadata(String saveName, UUID gameId, Instant gameStartedAt,
        Instant savedAt, int turnNumber, Season season, int turnWithinSeason,
        Weather weather, HallLevel hallLevel, String summary) {
    public SaveMetadata {
        saveName = Objects.requireNonNull(saveName, "saveName").trim();
        Objects.requireNonNull(gameId, "gameId");
        Objects.requireNonNull(gameStartedAt, "gameStartedAt");
        Objects.requireNonNull(savedAt, "savedAt");
        Objects.requireNonNull(season, "season");
        Objects.requireNonNull(weather, "weather");
        Objects.requireNonNull(hallLevel, "hallLevel");
        summary = Objects.requireNonNull(summary, "summary").trim();
        if (saveName.isEmpty() || summary.isEmpty() || turnNumber < 1
                || turnWithinSeason < 1 || turnWithinSeason > 10) {
            throw new IllegalArgumentException("Invalid save metadata");
        }
    }
}
