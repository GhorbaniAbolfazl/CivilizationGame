package civilization.application;

import civilization.application.service.VisibilityService;
import civilization.application.factory.UnitFactory;
import civilization.model.building.BuildingId;
import civilization.model.building.TownHall;
import civilization.model.economy.ResourceStockpile;
import civilization.model.game.CrisisState;
import civilization.model.game.GameState;
import civilization.model.game.TurnState;
import civilization.model.map.GameMap;
import civilization.model.map.HexCoord;
import civilization.model.map.VisibilityState;
import civilization.model.player.EmpireBorder;
import civilization.model.player.PlayerState;
import civilization.model.player.UnitCapacity;
import civilization.model.technology.ProductionQueue;
import civilization.model.technology.TechnologyState;
import civilization.model.unit.UnitType;
import civilization.model.trade.TradingPost;
import civilization.model.trade.TradingPostId;
import civilization.model.map.TerrainType;
import civilization.rules.GameRules;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class GameFactory {
    private final VisibilityService visibilityService;
    private final UnitFactory unitFactory;

    public GameFactory(VisibilityService visibilityService) {
        this(visibilityService, new UnitFactory());
    }

    public GameFactory(VisibilityService visibilityService, UnitFactory unitFactory) {
        this.visibilityService = Objects.requireNonNull(visibilityService, "visibilityService");
        this.unitFactory = Objects.requireNonNull(unitFactory, "unitFactory");
    }

    public GameState create(GameMap map, HexCoord center, long mapSeed, GameRules rules) {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(rules, "rules");
        map.requireTile(center);

        ResourceStockpile stockpile = new ResourceStockpile(
                rules.initialResources(), rules.initialStorageCapacity());
        TownHall townHall = new TownHall(
                BuildingId.random(), center, stockpile, new ProductionQueue(),
                civilization.model.identity.FactionId.PLAYER);
        Set<HexCoord> initialTerritory = new HashSet<>();
        initialTerritory.add(center);
        center.neighbors().stream().filter(map::contains).forEach(initialTerritory::add);
        PlayerState player = new PlayerState(
                townHall,
                new UnitCapacity(rules.initialUnitCap()),
                new EmpireBorder(initialTerritory, map),
                new VisibilityState(),
                new TechnologyState());

        addInitialUnit(player, UnitType.BUILDER, center, rules);
        addInitialUnit(player, UnitType.BUILDER, center, rules);
        addInitialUnit(player, UnitType.WORKER, center, rules);
        addInitialUnit(player, UnitType.WORKER, center, rules);
        addInitialUnit(player, UnitType.EXPLORER, center, rules);

        GameState state = new GameState(map, player, new TurnState(), new CrisisState(), mapSeed);
        placeInitialTradingPost(state, center, mapSeed);
        visibilityService.refresh(state, rules);
        state.validateSpatialReferences();
        return state;
    }

    private static void placeInitialTradingPost(GameState state, HexCoord center, long seed) {
        var candidates = state.map().tiles().stream()
                .filter(tile -> !tile.coordinate().equals(center))
                .filter(tile -> tile.terrain() != TerrainType.SEA
                        && tile.terrain() != TerrainType.MOUNTAIN_RANGE)
                .sorted(java.util.Comparator
                        .comparingInt((civilization.model.map.HexTile tile) -> tile.coordinate().q())
                        .thenComparingInt(tile -> tile.coordinate().r()))
                .toList();
        if (candidates.isEmpty()) return;
        int preferredDistance = candidates.stream()
                .mapToInt(tile -> tile.coordinate().distanceTo(center))
                .filter(distance -> distance >= 3)
                .min().orElseGet(() -> candidates.stream()
                        .mapToInt(tile -> tile.coordinate().distanceTo(center)).min().orElseThrow());
        var preferred = candidates.stream()
                .filter(tile -> tile.coordinate().distanceTo(center) == preferredDistance)
                .toList();
        var position = preferred.get(Math.floorMod(seed, preferred.size())).coordinate();
        state.world().tradingPosts().add(
                new TradingPost(TradingPostId.random(), position), state.map());
    }

    private void addInitialUnit(
            PlayerState player, UnitType type, HexCoord center, GameRules rules) {
        if (type == UnitType.BORDER_EXPANDER) {
            throw new IllegalArgumentException("Border Expander is not an initial unit");
        }
        player.addUnit(unitFactory.create(type, center, rules, player.factionId()));
    }
}
