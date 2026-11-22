package civilization.application.generation;

import civilization.model.map.GameMap;
import civilization.model.map.HexCoord;

/** Seeded, UI-independent map creation contract. */
public interface MapGenerator {
    GameMap generate(int radius, long seed, HexCoord townHallCoordinate);
}
