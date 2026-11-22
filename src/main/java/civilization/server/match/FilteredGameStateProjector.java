package civilization.server.match;

import civilization.model.economy.ResourceType;
import civilization.protocol.GameSnapshotDto;
import civilization.protocol.VisibleBuildingDto;
import civilization.protocol.VisibleHexDto;
import civilization.protocol.VisibleUnitDto;
import civilization.server.session.PlayerId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import civilization.server.session.SessionId;
import civilization.model.building.Apothecary;
import civilization.protocol.ApothecaryQueueDto;
import civilization.protocol.InventoryItemDto;
import civilization.model.map.HexTile;

// PHASE 3 - Added in Phase 3
public final class FilteredGameStateProjector {
    // PHASE 3 - Added in Phase 3
    private static final Comparator<HexTile> TILE_ORDER =
            Comparator.comparingInt((HexTile tile) -> tile.coordinate().q())
                    .thenComparingInt(tile -> tile.coordinate().r());

    // PHASE 3 - Added in Phase 3
    public GameSnapshotDto project(MultiplayerGameState state, PlayerId viewerId) {
        return project(state, viewerId, sortedTiles(state));
    }

    // PHASE 3 - Added in Phase 3
    private GameSnapshotDto project(MultiplayerGameState state, PlayerId viewerId,
                                    List<HexTile> sortedTiles) {
        MultiplayerPlayerState viewer = state.requirePlayer(viewerId);
        // PHASE 3 - Added in Phase 3
        var discovered = state.discoveredFor(viewerId);
        var currentlyVisible = state.currentlyVisibleFor(viewerId);
        List<VisibleHexDto> hexes = sortedTiles.stream()
                .filter(tile -> discovered.contains(tile.coordinate()))
                .map(tile -> new VisibleHexDto(tile.coordinate().q(), tile.coordinate().r(), currentlyVisible.contains(tile.coordinate()),
                        viewer.empire().border().owns(tile.coordinate()), tile.terrain().name(),
                        // PHASE 3 - Added in Phase 3
                        currentlyVisible.contains(tile.coordinate())
                                ? tile.resourceDeposits().entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(entry -> entry.getKey().name(), entry -> entry.getValue().remainingAmount()))
                                : Map.of())).toList();
        ArrayList<VisibleUnitDto> units = new ArrayList<>(); ArrayList<VisibleBuildingDto> buildings = new ArrayList<>();
        // PHASE 3 - Added in Phase 3
        for (MultiplayerPlayerState owner : state.players().values()) {
            boolean own = owner.playerId().equals(viewerId);
            // PHASE 3 - Added in Phase 3
            owner.empire().units().stream().filter(unit -> own || currentlyVisible.contains(unit.position()))
                    .map(unit -> new VisibleUnitDto(unit.id().value().toString(), unit.type().name(), owner.playerId().toString(), unit.position().q(), unit.position().r(), unit.actionPoints().current())).forEach(units::add);
            // PHASE 3 - Added in Phase 3
            owner.empire().allBuildings().stream().filter(building -> own || currentlyVisible.contains(building.position()))
                    .map(building -> new VisibleBuildingDto(building.id().value().toString(), building.type().name(), owner.playerId().toString(), building.position().q(), building.position().r(), building.status().name())).forEach(buildings::add);
        }
        units.sort(Comparator.comparing(VisibleUnitDto::unitId)); buildings.sort(Comparator.comparing(VisibleBuildingDto::buildingId));
        LinkedHashMap<String,Integer> resources = new LinkedHashMap<>();
        // PHASE 3 - Added in Phase 3
        // PHASE 3 - Added in Phase 3
        for (ResourceType type : ResourceType.values()) resources.put(type.name(), viewer.empire().townHall().stockpile().availableAmountOf(type));
        Map<String,String> attributes;
        // PHASE 3 - Added in Phase 3
        if (state.matchStatus() == MultiplayerMatchStatus.FINISHED) {
            attributes = Map.of("turnPhase", state.turn().phase().name(), "username", viewer.username(),
                    "winnerPlayerId", state.winnerPlayerId().orElseThrow().toString());
        } else attributes = Map.of("turnPhase", state.turn().phase().name(), "username", viewer.username());
        // PHASE 3 - Added in Phase 3
        List<InventoryItemDto> items = viewer.empire().itemInventory().items().stream()
                .map(item -> new InventoryItemDto(item.id().value().toString(),
                        item.type().name(), item.createdAt())).toList();
        // PHASE 3 - Added in Phase 3
        List<ApothecaryQueueDto> queues = viewer.empire().constructedBuildings().stream()
                .filter(Apothecary.class::isInstance).map(Apothecary.class::cast)
                .flatMap(apothecary -> apothecary.craftingQueue().currentOrder().stream()
                        .map(order -> new ApothecaryQueueDto(
                                apothecary.id().value().toString(), order.itemType().name(),
                                order.remainingTurns()))).toList();
        return new GameSnapshotDto(state.matchId(), state.turn().turnNumber(), state.activePlayerId().toString(), state.matchStatus().name(),
                state.mapId(), viewerId.toString(), resources, hexes, List.copyOf(units),
                List.copyOf(buildings), items, queues, attributes);
    }

    // PHASE 3 - Added in Phase 3
    public MultiplayerStateUpdate projectConnected(MultiplayerGameState state) {
        LinkedHashMap<SessionId,GameSnapshotDto> snapshots = new LinkedHashMap<>();
        List<HexTile> sortedTiles = sortedTiles(state);
        // PHASE 3 - Added in Phase 3
        for (PlayerId playerId : state.connectedPlayers()) {
            MultiplayerPlayerState player = state.requirePlayer(playerId);
            snapshots.put(player.sessionId(), project(state, playerId, sortedTiles));
        }
        return new MultiplayerStateUpdate(state.matchId(), state.stateVersion(), state.turn().turnNumber(), state.activePlayerId(), state.turn().phase().name(), snapshots);
    }

    // PHASE 3 - Added in Phase 3
    private static List<HexTile> sortedTiles(MultiplayerGameState state) {
        return state.map().tiles().stream().sorted(TILE_ORDER).toList();
    }
}
