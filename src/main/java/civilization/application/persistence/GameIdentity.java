package civilization.application.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record GameIdentity(UUID gameId, Instant startedAt) {
    public GameIdentity {
        Objects.requireNonNull(gameId, "gameId");
        Objects.requireNonNull(startedAt, "startedAt");
    }
    public static GameIdentity newGame() { return new GameIdentity(UUID.randomUUID(), Instant.now()); }
}
