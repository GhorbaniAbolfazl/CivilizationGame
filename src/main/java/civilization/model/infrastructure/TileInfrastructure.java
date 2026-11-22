package civilization.model.infrastructure;

import civilization.model.map.HexCoord;

public non-sealed interface TileInfrastructure extends Infrastructure {
    HexCoord position();
}
