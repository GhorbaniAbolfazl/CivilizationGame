package civilization.model.infrastructure;

import civilization.model.map.HexEdge;

public non-sealed interface EdgeInfrastructure extends Infrastructure {
    HexEdge edge();
}
