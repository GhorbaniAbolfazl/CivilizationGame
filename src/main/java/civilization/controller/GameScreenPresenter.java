package civilization.controller;

import civilization.application.GameEngine;
import civilization.application.action.ActionPreview;
import civilization.application.query.BuildingSnapshot;
import civilization.application.query.GameSnapshot;
import civilization.application.query.UnitDetailSnapshot;
import civilization.model.map.HexCoord;
import civilization.model.technology.UpgradeId;
import civilization.model.technology.TechnologyId;
import civilization.model.building.HallLevel;
import civilization.model.unit.UnitType;
import civilization.view.game.ActionPanel;
import civilization.view.game.GamePanel;
import civilization.view.state.GameUiState;
import civilization.application.query.TradingPostSnapshot;
import civilization.model.building.BuildingType;
import civilization.application.query.TribeSnapshot;
import civilization.application.trade.TradePreview;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Maps immutable application snapshots and previews onto the Swing game screen. */
public final class GameScreenPresenter {
    private static final String PRESENTATION_LOCK_REASON =
            "Wait for movement animation to finish";

    private final GameEngine engine;
    private final GamePanel view;
    private final GameUiState uiState;
    private final BooleanSupplier inputAllowed;
    private final BooleanSupplier autoExploreEnabled;

    public GameScreenPresenter(
            GameEngine engine,
            GamePanel view,
            GameUiState uiState,
            BooleanSupplier inputAllowed,
            BooleanSupplier autoExploreEnabled) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.view = Objects.requireNonNull(view, "view");
        this.uiState = Objects.requireNonNull(uiState, "uiState");
        this.inputAllowed = Objects.requireNonNull(inputAllowed, "inputAllowed");
        this.autoExploreEnabled = Objects.requireNonNull(
                autoExploreEnabled, "autoExploreEnabled");
    }

    public void presentAll(GameSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        view.mapCanvas().setSnapshot(snapshot.map());
        view.mapCanvas().effects().setSeason(snapshot.hud().season());
        view.minimap().setSnapshot(snapshot.map());
        presentControls(snapshot);
    }

    public void presentControls(GameSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        view.hud().display(snapshot.hud());
        view.townHall().display(snapshot.townHall());
        view.setTownHallOpen(uiState.isTownHallPanelOpen());
        presentSelectionAndActions(snapshot);
        presentTrade(snapshot);
        presentTribe(snapshot);
        presentInfrastructure(snapshot);
        UnitDetailSnapshot combatUnit = selectedUnit(snapshot);
        ActionPreview combatPreview = combatUnit == null || !combatUnit.type().isMilitary()
                ? ActionPreview.reject("Select a military unit")
                : engine.previewAttack(new civilization.model.combat.AttackRequest(
                        combatUnit.position(),view.combat().targetHex(),view.combat().selectedTarget()));
        view.combat().setAttackPreview(gate(combatPreview));
        boolean allowed = inputAllowed.getAsBoolean();
        view.hud().endTurnButton().setEnabled(allowed);
        view.actions().openTownHallButton().setEnabled(allowed);
        view.actions().displayAutoExplore(autoExploreEnabled.getAsBoolean());
        view.revalidate();
        view.repaint();
    }

    public UnitDetailSnapshot selectedUnit(GameSnapshot snapshot) {
        return uiState.selectedUnit().flatMap(id -> snapshot.units().stream()
                .filter(unit -> unit.id().equals(id)).findFirst()).orElse(null);
    }

    public BuildingSnapshot selectedBuilding(GameSnapshot snapshot) {
        return uiState.selectedBuilding().flatMap(id -> snapshot.buildings().stream()
                .filter(building -> building.id().equals(id)).findFirst()).orElse(null);
    }

    public TradingPostSnapshot selectedTradingPost(GameSnapshot snapshot) {
        return uiState.selectedTradingPost().flatMap(id -> snapshot.map().tradingPosts().stream()
                .filter(post -> post.id().equals(id)).findFirst()).orElse(null);
    }

    public TribeSnapshot selectedTribe(GameSnapshot snapshot) {
        return uiState.selectedTribe().flatMap(id -> snapshot.map().tribes().stream()
                .filter(tribe -> tribe.id().equals(id) && tribe.currentlyVisible())
                .findFirst()).orElse(null);
    }

    public BuildingSnapshot productionBuildingAt(GameSnapshot snapshot, HexCoord coordinate) {
        return snapshot.buildings().stream()
                .filter(building -> building.position().equals(coordinate))
                .filter(building -> building.workerCapacity() > 0)
                .findFirst().orElse(null);
    }

    private void presentSelectionAndActions(GameSnapshot snapshot) {
        UnitDetailSnapshot unit = selectedUnit(snapshot);
        BuildingSnapshot building = selectedBuilding(snapshot);
        view.actions().displaySelection(unit, building);
        presentMovementPreviews(snapshot, unit);
        for (var entry : view.actions().buildButtons().entrySet()) {
            ActionPreview preview = unit == null || uiState.selectedHex().isEmpty()
                    ? ActionPreview.reject("Select a Builder on the target hex")
                    : engine.previewConstruction(
                            unit.id(), entry.getKey(), uiState.selectedHex().orElseThrow());
            ActionPanel.applyPreview(entry.getValue(), gate(preview));
        }
        BuildingSnapshot facility = unit == null
                ? null : productionBuildingAt(snapshot, unit.position());
        ActionPreview stationPreview = unit == null || facility == null
                ? ActionPreview.reject("Select a Worker on a production building")
                : engine.previewStation(unit.id(), facility.id());
        ActionPanel.applyPreview(view.actions().stationButton(), gate(stationPreview));

        boolean canUnstation = unit != null
                && unit.type() == UnitType.WORKER
                && unit.station().isPresent();
        ActionPreview unstationPreview = canUnstation
                ? ActionPreview.allow()
                : ActionPreview.reject("Selected Worker is not stationed");
        ActionPanel.applyPreview(view.actions().unstationButton(), gate(unstationPreview));

        ActionPreview expandPreview = unit == null
                ? ActionPreview.reject("Select a Border Expander")
                : engine.previewBorderExpansion(unit.id(), unit.position());
        ActionPanel.applyPreview(view.actions().expandBorderButton(), gate(expandPreview));

        if (autoExploreEnabled.getAsBoolean()) {
            ActionPanel.applyPreview(
                    view.actions().autoExploreButton(), ActionPreview.allow());
        } else {
            ActionPreview autoPreview = unit != null && unit.type() == UnitType.EXPLORER
                    ? ActionPreview.allow()
                    : ActionPreview.reject("Select an Explorer");
            ActionPanel.applyPreview(view.actions().autoExploreButton(), gate(autoPreview));
        }

        for (UnitType type : UnitType.values()) {
            view.townHall().setUnitPreview(type, gate(engine.previewQueueUnit(type)));
        }
        for (UpgradeId id : UpgradeId.values()) {
            view.townHall().setUpgradePreview(id, gate(engine.previewQueueUpgrade(id)));
        }
        for (HallLevel level : view.townHall().hallButtons().keySet()) {
            view.townHall().setHallPreview(level, gate(engine.previewHallUpgrade(level)));
        }
        for (TechnologyId technology : view.townHall().technologyButtons().keySet()) {
            view.townHall().setTechnologyPreview(
                    technology, gate(engine.previewTechnology(technology)));
        }
        view.townHall().setCancelPreview(gate(engine.previewCancelHallProduction()));
    }

    private void presentMovementPreviews(GameSnapshot snapshot, UnitDetailSnapshot unit) {
        if (unit == null || !inputAllowed.getAsBoolean()) {
            view.mapCanvas().setMovementPreviews(Map.of());
            return;
        }
        Map<HexCoord, ActionPreview> previews = new LinkedHashMap<>();
        unit.position().neighbors().stream()
                .filter(coordinate -> snapshot.map().hexes().stream()
                        .anyMatch(hex -> hex.coordinate().equals(coordinate)))
                .forEach(coordinate -> previews.put(
                        coordinate, engine.previewMove(unit.id(), coordinate)));
        view.mapCanvas().setMovementPreviews(previews);
    }

    private void presentTrade(GameSnapshot snapshot) {
        BuildingSnapshot building = selectedBuilding(snapshot);
        TradingPostSnapshot post = selectedTradingPost(snapshot);
        boolean bazaarSelected = building != null && building.type() == BuildingType.BAZAAR;
        boolean postSelected = post != null;
        view.setTradeOpen(bazaarSelected || postSelected);
        if (!bazaarSelected && !postSelected) return;

        var panel = view.trade();
        TradePreview preview;
        if (!inputAllowed.getAsBoolean()) {
            preview = TradePreview.reject(PRESENTATION_LOCK_REASON);
        } else if (bazaarSelected) {
            panel.displayEndpoint("Bazaar", true);
            preview = engine.previewBazaarTrade(building.id(), panel.selectedLevel(),
                    panel.selectedSource(), panel.selectedTarget());
        } else {
            panel.displayEndpoint(post.controlled()
                    ? "Trading Center - controlled" : "Trading Center - outside empire", false);
            preview = engine.previewTradingPostTrade(post.id(), panel.selectedSource(),
                    panel.selectedTarget(), panel.selectedAmount());
        }
        panel.displayPreview(preview);
    }

    private void presentTribe(GameSnapshot snapshot) {
        TribeSnapshot tribe = selectedTribe(snapshot);
        view.setTribeOpen(tribe != null);
        if (tribe == null) return;
        var panel = view.tribe();
        var actions = engine.tribeActions(tribe.id());
        ActionPreview gift = engine.previewTribeGift(tribe.id(), panel.giftResource(),
                panel.giftAmount());
        TradePreview trade = engine.previewTribeTrade(tribe.id(), panel.tradeSource(),
                panel.tradeTarget(), panel.tradeAmount());
        ActionPreview delivery = tribe.missionId().isPresent()
                ? engine.previewMissionDelivery(tribe.missionId().orElseThrow())
                : ActionPreview.reject("No mission to deliver");
        panel.display(tribe, actions, gate(gift),
                inputAllowed.getAsBoolean() ? trade
                        : TradePreview.reject(PRESENTATION_LOCK_REASON),
                gate(delivery));
    }

    private void presentInfrastructure(GameSnapshot snapshot) {
        var panel=view.infrastructure();
        UnitDetailSnapshot unit=selectedUnit(snapshot);
        if(unit==null || unit.type()!=UnitType.BUILDER) {
            ActionPreview rejected=ActionPreview.reject("Select an active Builder");
            panel.roadPreview(rejected); panel.wallPreview(rejected);
            panel.demolishRoadPreview(rejected); panel.demolishWallPreview(rejected);
            panel.demolishBuildingPreview(rejected);
        } else {
            panel.roadPreview(gate(engine.previewRoad(unit.id(),panel.tile())));
            try { panel.wallPreview(gate(engine.previewWall(unit.id(),panel.edge()))); }
            catch(IllegalArgumentException invalid){panel.wallPreview(ActionPreview.reject(invalid.getMessage()));}
            panel.demolishRoadPreview(gate(engine.previewDemolishRoad(unit.id(),panel.tile())));
            try { panel.demolishWallPreview(gate(engine.previewDemolishWall(unit.id(),panel.edge()))); }
            catch(IllegalArgumentException invalid){panel.demolishWallPreview(ActionPreview.reject(invalid.getMessage()));}
            panel.demolishBuildingPreview(gate(engine.previewDemolishBuilding(unit.id(),panel.tile())));
        }
        var hex=snapshot.map().hexes().stream().filter(item->item.coordinate().equals(panel.tile())).findFirst();
        if(hex.isEmpty()) panel.showInspection("Outside map");
        else if(!hex.orElseThrow().discovered()) panel.showInspection("Undiscovered: terrain, resources and legality remain hidden");
        else {
            var item=hex.orElseThrow();
            String movement=item.terrain()==civilization.model.map.TerrainType.MOUNTAIN_RANGE
                    ? "impassable; no ownership, Road, Wall, unit or building"
                    : item.terrain()==civilization.model.map.TerrainType.MOUNTAIN
                    ? "passable high-cost land; Stone/Iron mines allowed with deposit"
                    : item.terrain()==civilization.model.map.TerrainType.SEA
                    ? "Sea; Navigation required for embarked land units" : "ordinary passable land";
            panel.showInspection("Terrain: "+item.terrain()+"<br>Movement/build rule: "+movement
                    +"<br>Owned: "+item.owned()+" | Visible: "+item.currentlyVisible()
                    +"<br>Resources: "+(item.remainingResources().isEmpty()?"none":item.remainingResources()));
        }
    }

    private ActionPreview gate(ActionPreview preview) {
        return inputAllowed.getAsBoolean()
                ? preview
                : ActionPreview.reject(PRESENTATION_LOCK_REASON);
    }
}
