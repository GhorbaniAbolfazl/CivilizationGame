package civilization.client.controller;

import civilization.application.query.BuildingSnapshot;
import civilization.application.query.HexSnapshot;
import civilization.application.query.MapSnapshot;
import civilization.application.query.UnitSnapshot;
import civilization.model.building.BuildingId;
import civilization.model.building.BuildingStatus;
import civilization.model.building.BuildingType;
import civilization.model.identity.FactionId;
import civilization.model.map.HexCoord;
import civilization.model.map.NaturalResourceType;
import civilization.model.map.TerrainType;
import civilization.model.unit.UnitId;
import civilization.model.unit.UnitType;
import civilization.client.state.ClientGameRenderModel;
import civilization.view.game.GamePanel;
import java.util.EnumMap;
import java.util.UUID;

// PHASE 3 - Added in Phase 3
public final class MultiplayerGameplayPresenter {
    // PHASE 3 - Added in Phase 3
    public void present(GamePanel panel, ClientGameRenderModel update) {
        var snapshot = update.snapshot();
        var hexes = snapshot.visibleHexes().stream().map(hex -> {
            EnumMap<NaturalResourceType,Integer> resources = new EnumMap<>(NaturalResourceType.class);
            // PHASE 3 - Added in Phase 3
            hex.resources().forEach((type, amount) -> resources.put(NaturalResourceType.valueOf(type), amount));
            return new HexSnapshot(new HexCoord(hex.q(), hex.r()), true, hex.currentlyVisible(), hex.owned(), TerrainType.valueOf(hex.terrain()), resources);
        }).toList();
        var units = snapshot.visibleUnits().stream().map(unit -> new UnitSnapshot(
                new UnitId(UUID.fromString(unit.unitId())), UnitType.valueOf(unit.unitType()), new HexCoord(unit.q(), unit.r()), unit.remainingActionPoints(),
                new FactionId("player:" + unit.ownerPlayerId()), 0, 0, false)).toList();
        var buildings = snapshot.visibleBuildings().stream().map(building -> new BuildingSnapshot(
                new BuildingId(UUID.fromString(building.buildingId())), BuildingType.valueOf(building.buildingType()), new HexCoord(building.q(), building.r()), BuildingStatus.valueOf(building.status()), 0, 0)).toList();
        MapSnapshot map = new MapSnapshot(update.stateVersion(), hexes, units, buildings, java.util.List.of());
        panel.mapCanvas().setSnapshot(map); panel.minimap().setSnapshot(map);
        // PHASE 3 - Added in Phase 3
        int ownUnits = (int) snapshot.visibleUnits().stream().filter(unit -> unit.ownerPlayerId().equals(snapshot.viewerPlayerId())).count();
        panel.hud().displayMultiplayer(Math.toIntExact(snapshot.turnNumber()),
                snapshot.activePlayerId(), snapshot.viewerPlayerId(), snapshot.viewerResources(), ownUnits);
        // PHASE 3 - Added in Phase 3
        if (snapshot.matchStatus().equals("FINISHED")) {
            panel.notifyUser("Match finished — winner: "
                    + snapshot.viewerAttributes().getOrDefault("winnerPlayerId", "unknown"));
        } else {
            panel.notifyUser("Multiplayer Turn " + snapshot.turnNumber()
                    + (snapshot.viewerPlayerId().equals(snapshot.activePlayerId())
                    ? " — your turn" : " — waiting for active player"));
        }
    }
}
