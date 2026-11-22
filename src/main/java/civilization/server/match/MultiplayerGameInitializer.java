package civilization.server.match;

import civilization.application.factory.UnitFactory;
import civilization.application.generation.TribeGenerator;
import civilization.model.building.BuildingId;
import civilization.model.building.TownHall;
import civilization.model.economy.ResourceStockpile;
import civilization.model.identity.FactionId;
import civilization.model.map.GameMap;
import civilization.model.map.HexCoord;
import civilization.model.map.VisibilityState;
import civilization.model.player.EmpireBorder;
import civilization.model.player.PlayerState;
import civilization.model.player.UnitCapacity;
import civilization.model.technology.ProductionQueue;
import civilization.model.technology.TechnologyState;
import civilization.model.unit.UnitType;
import civilization.rules.GameRules;
import civilization.server.lobby.LobbyMember;
import civilization.server.map.PredefinedMapDefinition;
import civilization.server.map.PredefinedMapLoader;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

// PHASE 3 - Added in Phase 3
public final class MultiplayerGameInitializer {
    // PHASE 3 - Added in Phase 3
    private final PredefinedMapLoader mapLoader;
    // PHASE 3 - Added in Phase 3
    private final GameRules rules;
    // PHASE 3 - Added in Phase 3
    private final UnitFactory unitFactory;
    // PHASE 3 - Added in Phase 3
    private final Clock clock;

    // PHASE 3 - Added in Phase 3
    public MultiplayerGameInitializer(PredefinedMapLoader mapLoader, GameRules rules, UnitFactory unitFactory, Clock clock) {
        this.mapLoader = Objects.requireNonNull(mapLoader); this.rules = Objects.requireNonNull(rules); this.unitFactory = Objects.requireNonNull(unitFactory); this.clock = Objects.requireNonNull(clock);
    }

    // PHASE 3 - Added in Phase 3
    public MultiplayerGameState initialize(String matchId, PredefinedMapDefinition definition, List<LobbyMember> members) {
        if (!definition.supports(members.size())) throw new IllegalArgumentException("map does not support player count");
        GameMap map = mapLoader.load(definition); ArrayList<MultiplayerPlayerState> players = new ArrayList<>();
        List<civilization.server.map.SpawnSlot> spawnSlots = definition.spawnSlotsFor(members.size());
        // PHASE 3 - Added in Phase 3
        for (int index = 0; index < members.size(); index++) {
            LobbyMember member = members.get(index); HexCoord spawn = spawnSlots.get(index).coordinate();
            FactionId faction = new FactionId("player:" + member.playerId());
            TownHall hall = new TownHall(BuildingId.random(), spawn,
                    new ResourceStockpile(rules.initialResources(), rules.initialStorageCapacity()), new ProductionQueue(), faction);
            HashSet<HexCoord> territory = new HashSet<>(); territory.add(spawn);
            // PHASE 3 - Added in Phase 3
            spawn.neighbors().stream().filter(map::contains).forEach(territory::add);
            PlayerState empire = new PlayerState(faction, hall, new UnitCapacity(rules.initialUnitCap()),
                    new EmpireBorder(territory, map), new VisibilityState(), new TechnologyState());
            // PHASE 3 - Added in Phase 3
            for (UnitType type : List.of(UnitType.BUILDER, UnitType.BUILDER, UnitType.WORKER, UnitType.WORKER, UnitType.EXPLORER)) {
                empire.addUnit(unitFactory.create(type, spawn, rules, faction));
            }
            refreshVisibility(map, empire); players.add(new MultiplayerPlayerState(member.sessionId(), member.playerId(), member.username(), empire));
        }
        MultiplayerGameState game = new MultiplayerGameState(matchId, definition.id(), map, players, Instant.now(clock));
        // PHASE 3 - Added in Phase 3
        new TribeGenerator().generate(game.activePlayerView(game.activePlayerId()), rules);
        return game;
    }

    // PHASE 3 - Added in Phase 3
    private void refreshVisibility(GameMap map, PlayerState player) {
        HashSet<HexCoord> visible = new HashSet<>(); HashSet<HexCoord> explorerVisible = new HashSet<>();
        // PHASE 3 - Added in Phase 3
        player.units().stream().filter(civilization.model.unit.Unit::isActive).forEach(unit -> {
            var footprint = map.coordinatesWithin(unit.position(), rules.unit(unit.type()).visionRadius()); visible.addAll(footprint);
            if (unit.type() == UnitType.EXPLORER) explorerVisible.addAll(footprint);
        });
        // PHASE 3 - Added in Phase 3
        player.allBuildings().stream().filter(civilization.model.building.Building::isActive)
                .forEach(building -> visible.addAll(map.coordinatesWithin(building.position(), rules.building(building.type()).visionRadius())));
        player.visibility().replaceCurrentlyVisible(visible); player.visibility().discoverAllByExplorer(explorerVisible);
    }
}
