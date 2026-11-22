package civilization.view.state;

import civilization.model.building.BuildingId;
import civilization.model.map.HexCoord;
import civilization.model.unit.UnitId;
import civilization.model.trade.TradingPostId;
import civilization.model.identity.TribeId;

import java.util.Optional;

/** Controller-owned presentation state; never serialized into the game model. */
public final class GameUiState {
    private UnitId selectedUnit;
    private BuildingId selectedBuilding;
    private HexCoord selectedHex;
    private TradingPostId selectedTradingPost;
    private TribeId selectedTribe;
    private boolean townHallPanelOpen;

    public Optional<UnitId> selectedUnit() {
        return Optional.ofNullable(selectedUnit);
    }

    public Optional<BuildingId> selectedBuilding() {
        return Optional.ofNullable(selectedBuilding);
    }

    public Optional<HexCoord> selectedHex() {
        return Optional.ofNullable(selectedHex);
    }

    public Optional<TradingPostId> selectedTradingPost() {
        return Optional.ofNullable(selectedTradingPost);
    }
    public Optional<TribeId> selectedTribe() { return Optional.ofNullable(selectedTribe); }

    public boolean isTownHallPanelOpen() {
        return townHallPanelOpen;
    }

    public void selectUnit(UnitId unitId, HexCoord coordinate) {
        selectedUnit = unitId;
        selectedBuilding = null;
        selectedTradingPost = null;
        selectedTribe = null;
        selectedHex = coordinate;
        townHallPanelOpen = false;
    }

    public void selectBuilding(BuildingId buildingId, HexCoord coordinate, boolean townHall) {
        selectedUnit = null;
        selectedTradingPost = null;
        selectedTribe = null;
        selectedBuilding = buildingId;
        selectedHex = coordinate;
        townHallPanelOpen = townHall;
    }

    public void selectTradingPost(TradingPostId postId, HexCoord coordinate) {
        selectedUnit = null;
        selectedBuilding = null;
        selectedTribe = null;
        selectedTradingPost = postId;
        selectedHex = coordinate;
        townHallPanelOpen = false;
    }

    public void selectTribe(TribeId tribeId, HexCoord coordinate) {
        selectedUnit = null;
        selectedBuilding = null;
        selectedTradingPost = null;
        selectedTribe = tribeId;
        selectedHex = coordinate;
        townHallPanelOpen = false;
    }

    public void selectHex(HexCoord coordinate) {
        selectedUnit = null;
        selectedBuilding = null;
        selectedTradingPost = null;
        selectedTribe = null;
        selectedHex = coordinate;
        townHallPanelOpen = false;
    }

    public void clearSelection() {
        selectedUnit = null;
        selectedBuilding = null;
        selectedTradingPost = null;
        selectedTribe = null;
        selectedHex = null;
        townHallPanelOpen = false;
    }
}
