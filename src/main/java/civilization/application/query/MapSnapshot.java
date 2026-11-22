package civilization.application.query;

import java.util.List;

public record MapSnapshot(
        long stateVersion,
        List<HexSnapshot> hexes,
        List<UnitSnapshot> units,
        List<BuildingSnapshot> buildings,
        List<TradingPostSnapshot> tradingPosts,
        List<TribeSnapshot> tribes,
        java.util.Set<civilization.model.map.HexCoord> roads,
        java.util.Set<civilization.model.map.HexEdge> rivers,
        List<WallSnapshot> walls,
        List<WildAnimalSnapshot> wildAnimals) {
    public MapSnapshot(long stateVersion, List<HexSnapshot> hexes, List<UnitSnapshot> units,
            List<BuildingSnapshot> buildings, List<TradingPostSnapshot> tradingPosts) {
        this(stateVersion, hexes, units, buildings, tradingPosts, List.of(),
                java.util.Set.of(), java.util.Set.of(), List.of(), List.of());
    }

    public MapSnapshot(long stateVersion, List<HexSnapshot> hexes, List<UnitSnapshot> units,
            List<BuildingSnapshot> buildings, List<TradingPostSnapshot> tradingPosts,
            List<TribeSnapshot> tribes) {
        this(stateVersion, hexes, units, buildings, tradingPosts, tribes,
                java.util.Set.of(), java.util.Set.of(), List.of(), List.of());
    }

    public MapSnapshot {
        if (stateVersion < 0) {
            throw new IllegalArgumentException("State version cannot be negative");
        }
        hexes = List.copyOf(hexes);
        units = List.copyOf(units);
        buildings = List.copyOf(buildings);
        tradingPosts = List.copyOf(tradingPosts);
        tribes = List.copyOf(tribes);
        roads = java.util.Set.copyOf(roads);
        rivers = java.util.Set.copyOf(rivers);
        walls = List.copyOf(walls);
        wildAnimals = List.copyOf(wildAnimals);
    }
}
