package civilization.application.query;

import civilization.model.map.HexCoord;
import civilization.model.world.WildAnimalId;
import java.util.Objects;

public record WildAnimalSnapshot(WildAnimalId id, HexCoord position, int health,
        int maximumHealth, boolean bear) {
    public WildAnimalSnapshot {
        Objects.requireNonNull(id); Objects.requireNonNull(position);
        if (health < 0 || maximumHealth <= 0 || health > maximumHealth) {
            throw new IllegalArgumentException("Invalid animal health");
        }
    }
}
