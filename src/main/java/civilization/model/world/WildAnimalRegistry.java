package civilization.model.world;

import civilization.model.map.GameMap;
import civilization.model.map.HexCoord;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class WildAnimalRegistry implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<WildAnimalId, WildAnimal> animals = new LinkedHashMap<>();

    public void add(WildAnimal animal, GameMap map) {
        Objects.requireNonNull(animal, "animal");
        var tile = Objects.requireNonNull(map, "map").requireTile(animal.position());
        if (tile.terrain() == civilization.model.map.TerrainType.MOUNTAIN_RANGE) {
            throw new IllegalArgumentException("Wild animal cannot occupy Mountain Range");
        }
        if (animals.putIfAbsent(animal.id(), animal) != null) {
            throw new IllegalArgumentException("Duplicate wild animal ID: " + animal.id());
        }
    }

    public Optional<WildAnimal> at(HexCoord position) {
        return animals.values().stream().filter(a -> a.position().equals(position)).findFirst();
    }

    public WildAnimal require(WildAnimalId id) {
        WildAnimal animal = animals.get(Objects.requireNonNull(id, "id"));
        if (animal == null) throw new IllegalArgumentException("Unknown wild animal: " + id);
        return animal;
    }

    public void remove(WildAnimalId id) {
        if (animals.remove(Objects.requireNonNull(id, "id")) == null) {
            throw new IllegalArgumentException("Unknown wild animal: " + id);
        }
    }

    public Collection<WildAnimal> all() { return Collections.unmodifiableCollection(animals.values()); }
}
