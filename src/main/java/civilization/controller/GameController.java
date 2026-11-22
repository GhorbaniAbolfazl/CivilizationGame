package civilization.controller;

import civilization.application.ActionFailure;
import civilization.application.GameEngine;
import civilization.application.action.ActionPreview;
import civilization.application.query.BuildingSnapshot;
import civilization.application.query.GameSnapshot;
import civilization.application.query.UnitDetailSnapshot;
import civilization.application.query.TradingPostSnapshot;
import civilization.application.query.TribeSnapshot;
import civilization.model.mission.MissionStatus;
import civilization.application.event.Subscription;
import civilization.application.event.TribeNotificationEvent;
import civilization.application.event.DisasterViewEvent;
import civilization.controller.port.ConfirmationPrompt;
import civilization.model.building.BuildingType;
import civilization.model.map.HexCoord;
import civilization.model.technology.UpgradeId;
import civilization.model.technology.TechnologyId;
import civilization.model.building.HallLevel;
import civilization.model.unit.UnitType;
import civilization.model.combat.AttackRequest;
import civilization.model.building.BuildingId;
import civilization.model.unit.UnitId;
import civilization.model.infrastructure.InfrastructureId;
import civilization.view.game.GamePanel;
import civilization.view.state.GameUiState;
import civilization.view.dialog.SwingConfirmationPrompt;
import civilization.application.persistence.GamePersistenceService;
import civilization.application.persistence.GameIdentity;
import civilization.application.persistence.SaveSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;

/** EDT controller: translates view intent into GameEngine commands and immutable snapshots. */
public final class GameController implements AutoCloseable {
    private final GameEngine engine;
    private final GamePanel view;
    private final ConfirmationPrompt confirmations;
    private final GameUiState uiState = new GameUiState();
    private final GameScreenPresenter presenter;
    private GameSnapshot snapshot;
    private UnitDetailSnapshot autoExplorer;
    private final Timer autoExploreTimer = new Timer(330, event -> runAutoExploreStep());
    private final Subscription tribeNotifications;
    private final Subscription disasterNotifications;
    private final GamePersistenceService persistence;
    private GameIdentity gameIdentity;
    private boolean commandInProgress;
    private List<civilization.application.persistence.SaveEntry> saveEntries =
            java.util.Arrays.stream(SaveSlot.values())
                    .map(civilization.application.persistence.SaveEntry::empty).toList();
    private boolean saveSlotScanInProgress;
    private boolean saveSlotScanRequested;
    private SwingWorker<?, ?> saveSlotScanWorker;
    private boolean closed;

    public GameController(GameEngine engine, GamePanel view) {
        this(engine, view, new SwingConfirmationPrompt(view));
    }

    public GameController(
            GameEngine engine, GamePanel view, ConfirmationPrompt confirmations) {
        this(engine, view, confirmations, null, null);
    }

    public GameController(GameEngine engine, GamePanel view, ConfirmationPrompt confirmations,
            GamePersistenceService persistence, GameIdentity gameIdentity) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.view = Objects.requireNonNull(view, "view");
        this.confirmations = Objects.requireNonNull(confirmations, "confirmations");
        this.persistence = persistence;
        this.gameIdentity = gameIdentity;
        this.presenter = new GameScreenPresenter(
                engine, view, uiState, this::isGameplayInputAllowed,
                () -> autoExplorer != null);
        this.tribeNotifications = engine.eventBus().subscribe(TribeNotificationEvent.class,
                event -> onEdt(() -> { if (!closed) view.notifyUser(event.message()); }));
        this.disasterNotifications = engine.eventBus().subscribe(DisasterViewEvent.class, event -> {
            onEdt(() -> {
                if (closed) return;
                view.notifyUser(event.message());
                if (event.visible()) view.mapCanvas().effects().showDisaster(
                        event.type().orElseThrow(), event.affectedHexes());
            });
        });
        view.setActionInputGate(this::isGameplayInputAllowed);
        view.mapCanvas().animations().setAnimationStateChanged(() -> {
            if (snapshot != null) {
                refreshExceptMap();
            }
        });
        view.mapCanvas().effects().setEffectStateChanged(() -> {
            if (snapshot != null && !closed) {
                refreshExceptMap();
                refreshSaveSlots();
            }
        });
        bindIntents();
        refreshSaveSlots();
        autoExploreTimer.setCoalesce(true);
        refresh();
        var townHall = snapshot.buildings().stream()
                .filter(building -> building.type() == BuildingType.TOWN_HALL)
                .findFirst().orElseThrow();
        view.mapCanvas().camera().centerOn(
                new civilization.view.render.HexLayout(42.0).center(townHall.position()));
    }

    public GameSnapshot snapshot() {
        return snapshot;
    }

    public void selectHex(HexCoord coordinate) {
        if (autoExplorer != null) {
            disableAutoExplore();
            view.notifyUser("Auto-Explore disabled. Manual control restored.");
        }
        List<Object> entities = new ArrayList<>();
        snapshot.units().stream().filter(unit -> unit.position().equals(coordinate)).forEach(entities::add);
        snapshot.buildings().stream().filter(building -> building.position().equals(coordinate))
                .forEach(entities::add);
        snapshot.map().tradingPosts().stream().filter(post -> post.position().equals(coordinate))
                .forEach(entities::add);
        snapshot.map().tribes().stream().filter(tribe -> tribe.currentlyVisible()
                && tribe.camp().equals(coordinate)).forEach(entities::add);
        if (entities.isEmpty()) {
            uiState.selectHex(coordinate);
        } else {
            int current = currentEntityIndex(entities);
            Object next = entities.get((current + 1) % entities.size());
            if (next instanceof UnitDetailSnapshot unit) {
                uiState.selectUnit(unit.id(), coordinate);
            } else if (next instanceof BuildingSnapshot building) {
                uiState.selectBuilding(
                        building.id(), coordinate, building.type() == BuildingType.TOWN_HALL);
            } else if (next instanceof TradingPostSnapshot post) {
                uiState.selectTradingPost(post.id(), coordinate);
            } else if (next instanceof TribeSnapshot tribe) {
                uiState.selectTribe(tribe.id(), coordinate);
            }
        }
        refreshExceptMap();
    }

    public void selectTownHall() {
        BuildingSnapshot hall = snapshot.buildings().stream()
                .filter(building -> building.type() == BuildingType.TOWN_HALL)
                .findFirst().orElseThrow();
        uiState.selectBuilding(hall.id(), hall.position(), true);
        refreshExceptMap();
        view.setTownHallOpen(true);
        view.revalidate();
        view.repaint();
    }

    public void requestMove(HexCoord destination) {
        disableAutoExplore();
        if (view.mapCanvas().animations().hasActiveAnimations()) {
            view.notifyUser("Wait for the current movement animation to finish.");
            return;
        }
        var selected = uiState.selectedUnit();
        if (selected.isEmpty()) {
            view.notifyUser("Select a unit, then right-click an adjacent hex.");
            return;
        }
        ActionPreview preview = engine.previewMove(selected.orElseThrow(), destination);
        if (!preview.allowed()) {
            view.notifyUser(preview.reason());
            return;
        }
        execute("Movement failed", () -> {
            var event = engine.move(selected.orElseThrow(), destination);
            snapshot = engine.snapshot();
            uiState.selectUnit(event.unitId(), destination);
            view.mapCanvas().displayCommittedMovement(snapshot.map(), event);
            refreshExceptMap();
        });
    }

    public void construct(BuildingType type) {
        var unitId = uiState.selectedUnit().orElse(null);
        var target = uiState.selectedHex().orElse(null);
        if (unitId == null || target == null) {
            return;
        }
        execute("Construction failed", () -> {
            engine.construct(unitId, type, target);
            view.mapCanvas().effects().showBuild(target);
            uiState.clearSelection();
            refresh();
        });
    }

    /** UI adapters may call this after selecting a Builder and an owned building target. */
    public void requestBuildingDemolition(UnitId builderId, BuildingId buildingId) {
        if (!confirmations.confirm("Demolish Building",
                "Demolition is irreversible. Workers will be released and no resources refunded. Continue?")) {
            return;
        }
        execute("Demolition failed", () -> {
            engine.demolishBuilding(builderId, buildingId);
            uiState.clearSelection();
            refresh();
        });
    }

    /** UI edge/tile adapters use the same irreversible confirmation for Roads and Walls. */
    public void requestInfrastructureDemolition(UnitId builderId, InfrastructureId targetId) {
        if (!confirmations.confirm("Demolish Infrastructure",
                "Demolition is permanent, gives no refund, and costs 1 Builder AP. Continue?")) {
            return;
        }
        execute("Demolition failed", () -> {
            engine.demolishInfrastructure(builderId, targetId);
            uiState.clearSelection();
            refresh();
        });
    }

    private UnitId selectedBuilderId() {
        UnitDetailSnapshot unit=selectedUnit();
        return unit != null && unit.type()==UnitType.BUILDER ? unit.id() : null;
    }

    private void buildSelectedRoad() {
        UnitId builder=selectedBuilderId(); if(builder==null)return;
        execute("Road construction failed",()->{engine.buildRoad(builder,view.infrastructure().tile());refresh();});
    }
    private void buildSelectedWall() {
        UnitId builder=selectedBuilderId(); if(builder==null)return;
        execute("Wall construction failed",()->{engine.buildWall(builder,view.infrastructure().edge());refresh();});
    }
    private void demolishSelectedRoad() {
        UnitId builder=selectedBuilderId(); if(builder==null)return;
        if(!confirmations.confirm("Demolish Road","Permanently remove this exact Road? Workers are unaffected, no resources are refunded, and 1 Builder AP is spent."))return;
        execute("Road demolition failed",()->{engine.demolishRoadAt(builder,view.infrastructure().tile());refresh();});
    }
    private void demolishSelectedWall() {
        UnitId builder=selectedBuilderId(); if(builder==null)return;
        if(!confirmations.confirm("Demolish Wall","Permanently remove this exact Wall edge? No resources are refunded and 1 Builder AP is spent."))return;
        execute("Wall demolition failed",()->{engine.demolishWallAt(builder,view.infrastructure().edge());refresh();});
    }
    private void demolishSelectedBuilding() {
        UnitId builder=selectedBuilderId(); if(builder==null)return;
        if(!confirmations.confirm("Demolish Building","Permanently remove this building? No resources are refunded; stationed Workers relocate to the nearest legal land and 1 Builder AP is spent."))return;
        execute("Building demolition failed",()->{engine.demolishBuildingAt(builder,view.infrastructure().tile());refresh();});
    }

    public void stationSelectedWorker() {
        UnitDetailSnapshot unit = selectedUnit();
        if (unit == null) {
            return;
        }
        BuildingSnapshot building = productionBuildingAt(unit.position());
        if (building == null) {
            view.notifyUser("No production building is on the Worker's hex.");
            return;
        }
        execute("Stationing failed", () -> {
            engine.stationWorker(unit.id(), building.id());
            refresh();
        });
    }

    public void unstationSelectedWorker() {
        UnitDetailSnapshot unit = selectedUnit();
        if (unit == null) {
            return;
        }
        execute("Leaving station failed", () -> {
            engine.unstationWorker(unit.id());
            refresh();
        });
    }

    public void expandSelectedBorder() {
        UnitDetailSnapshot unit = selectedUnit();
        if (unit == null) {
            return;
        }
        execute("Border expansion failed", () -> {
            engine.expandBorder(unit.id(), unit.position());
            uiState.clearSelection();
            refresh();
        });
    }

    public void queueUnit(UnitType type) {
        execute("Unit production failed", () -> {
            engine.queueUnit(type);
            refresh();
        });
    }

    public void queueUpgrade(UpgradeId id) {
        execute("Upgrade failed", () -> {
            engine.queueUpgrade(id);
            refresh();
        });
    }

    public void upgradeHall(HallLevel level) {
        execute("Town Hall upgrade failed", () -> { engine.upgradeHall(level); refresh(); });
    }

    public void researchTechnology(TechnologyId technology) {
        execute("Research failed", () -> { engine.researchTechnology(technology); refresh(); });
    }

    public void cancelProduction() {
        if (!confirmations.confirm("Cancel Production",
                "Cancel the active Town Hall order? All paid resources remain spent and this cannot be undone.")) {
            return;
        }
        execute("Production cancellation failed", () -> {
            var cancelled = engine.cancelHallProduction();
            view.notifyUser("Cancelled " + cancelled.getClass().getSimpleName() + ".");
            refresh();
        });
    }

    public void requestTrade() {
        var building = presenter.selectedBuilding(snapshot);
        var post = presenter.selectedTradingPost(snapshot);
        var panel = view.trade();
        execute("Trade failed", () -> {
            var result = building != null && building.type() == BuildingType.BAZAAR
                    ? engine.tradeAtBazaar(building.id(), panel.selectedLevel(),
                    panel.selectedSource(), panel.selectedTarget())
                    : engine.tradeAtTradingPost(
                    java.util.Objects.requireNonNull(post, "Select a Bazaar or Trading Center").id(),
                    panel.selectedSource(), panel.selectedTarget(), panel.selectedAmount());
            view.notifyUser("Trade complete: spent %d %s, received %d %s."
                    .formatted(result.sourceAmount(), result.source(),
                            result.targetAmount(), result.target()));
            refresh();
        });
    }

    /** Presenter-facing combat intent; target selection controls can call this without owning rules. */
    public void requestAttack(AttackRequest request) {
        execute("Attack failed", () -> {
            var result = engine.attack(request);
            refresh();
            view.showCombatResult(result);
            view.notifyUser("Combat resolved: " + result.outcome());
        });
    }

    private void requestSelectedAttack() {
        UnitDetailSnapshot attacker = selectedUnit();
        if (attacker == null) return;
        requestAttack(new AttackRequest(attacker.position(), view.combat().targetHex(),
                view.combat().selectedTarget()));
    }

    public void sendTribeGift() {
        TribeSnapshot tribe = presenter.selectedTribe(snapshot);
        if (tribe == null) return;
        execute("Gift failed", () -> {
            var result = engine.giveTribeGift(tribe.id(), view.tribe().giftResource(),
                    view.tribe().giftAmount());
            view.notifyUser("Gift accepted; relationship is " + result.resultingRelationship());
            refresh();
        });
    }

    public void tradeWithSelectedTribe() {
        TribeSnapshot tribe = presenter.selectedTribe(snapshot);
        if (tribe == null) return;
        execute("Tribe trade failed", () -> {
            var result = engine.tradeWithTribe(tribe.id(), view.tribe().tradeSource(),
                    view.tribe().tradeTarget(), view.tribe().tradeAmount());
            view.notifyUser("Tribe trade complete: received " + result.targetAmount()
                    + " " + result.target());
            refresh();
        });
    }

    public void requestOrAcceptMission() {
        TribeSnapshot tribe = presenter.selectedTribe(snapshot);
        if (tribe == null) return;
        execute("Mission action failed", () -> {
            if (tribe.missionStatus().orElse(null) == MissionStatus.RECEIVABLE) {
                engine.acceptTribeMission(tribe.missionId().orElseThrow());
            } else if (tribe.missionId().isEmpty()) {
                engine.requestTribeMission(tribe.id());
            }
            refresh();
        });
    }

    public void deliverMission() {
        TribeSnapshot tribe = presenter.selectedTribe(snapshot);
        if (tribe == null || tribe.missionId().isEmpty()) return;
        execute("Mission delivery failed", () -> {
            engine.deliverTribeMission(tribe.missionId().orElseThrow());
            view.notifyUser("Mission completed and reward granted.");
            refresh();
        });
    }

    public void cancelMission() {
        TribeSnapshot tribe = presenter.selectedTribe(snapshot);
        if (tribe == null || tribe.missionId().isEmpty()) return;
        execute("Mission cancellation failed", () -> {
            engine.cancelTribeMission(tribe.missionId().orElseThrow());
            refresh();
        });
    }

    public void declareWarOnTribe() {
        TribeSnapshot tribe = presenter.selectedTribe(snapshot);
        if (tribe == null) return;
        int happiness = tribe.stance().orElseThrow()
                == civilization.model.diplomacy.DiplomaticStance.ALLIANCE ? -15
                : tribe.relationshipState().orElseThrow()
                == civilization.model.diplomacy.RelationBand.FRIENDLY ? -5 : 0;
        if (!confirmations.confirm("Declare War",
                "Declare war on " + tribe.name() + "? Relation becomes -100, any mission "
                        + "is cancelled, and Happiness changes by " + happiness + ".")) return;
        execute("War declaration failed", () -> { engine.declareTribeWar(tribe.id()); refresh(); });
    }

    public void requestPeaceWithTribe() {
        TribeSnapshot tribe = presenter.selectedTribe(snapshot);
        if (tribe == null) return;
        execute("Peace request failed", () -> { engine.requestTribePeace(tribe.id()); refresh(); });
    }

    public void requestAllianceWithTribe() {
        TribeSnapshot tribe = presenter.selectedTribe(snapshot);
        if (tribe == null) return;
        execute("Alliance request failed", () -> {
            engine.requestTribeAlliance(tribe.id()); refresh();
        });
    }

    public void requestEndTurn() {
        disableAutoExplore();
        if (view.mapCanvas().animations().hasActiveAnimations()) {
            view.notifyUser("End Turn is locked while a unit is moving visually.");
            return;
        }
        if (snapshot.hud().idleUnitsWithAp() > 0) {
            if (!confirmations.confirm(
                    "Unused Action Points",
                    snapshot.hud().idleUnitsWithAp() + " idle unit(s) still have AP. End turn?")) {
                return;
            }
        }
        List<HexCoord> productiveBuildings = snapshot.buildings().stream()
                .filter(building -> building.stationedWorkers() > 0)
                .filter(building -> building.status()
                        == civilization.model.building.BuildingStatus.ACTIVE)
                .map(BuildingSnapshot::position).toList();
        runBackground("End Turn failed", () -> {
            var resolution = engine.endTurn();
            var autosaveResult = persistence == null ? null
                    : persistence.autosave(gameIdentity, engine.persistenceState());
            return new EndTurnWork(resolution, autosaveResult);
        }, work -> {
            refresh();
            view.notifyUser("Turn " + snapshot.hud().turnNumber() + " is ready ("
                    + snapshot.hud().season() + " " + snapshot.hud().turnWithinSeason() + ").");
            if (!work.resolution().economyReport().accepted().isZero()) {
                var townHall = snapshot.buildings().stream()
                        .filter(building -> building.type() == BuildingType.TOWN_HALL)
                        .findFirst().orElseThrow();
                view.mapCanvas().effects().showProduction(townHall.position());
                productiveBuildings.forEach(view.mapCanvas().effects()::showProduction);
            }
            displayAutosave(work.autosave());
        });
    }

    public void refresh() {
        snapshot = engine.snapshot();
        presenter.presentAll(snapshot);
    }

    private void refreshExceptMap() {
        presenter.presentControls(snapshot);
    }

    private void bindIntents() {
        view.mapCanvas().setPrimaryHexIntent(this::selectHex);
        view.mapCanvas().setSecondaryHexIntent(this::requestMove);
        view.hud().endTurnButton().addActionListener(event -> requestEndTurn());
        view.hud().pauseButton().addActionListener(event -> {
            refreshSaveSlots(); view.showPause(true);
        });
        view.pause().resumeButton().addActionListener(event -> view.showPause(false));
        view.actions().buildButtons().forEach(
                (type, button) -> button.addActionListener(event -> construct(type)));
        view.actions().stationButton().addActionListener(event -> stationSelectedWorker());
        view.actions().unstationButton().addActionListener(event -> unstationSelectedWorker());
        view.actions().expandBorderButton().addActionListener(event -> expandSelectedBorder());
        view.actions().autoExploreButton().addActionListener(event -> toggleAutoExplore());
        view.actions().openTownHallButton().addActionListener(event -> selectTownHall());
        view.townHall().unitButtons().forEach(
                (type, button) -> button.addActionListener(event -> queueUnit(type)));
        view.townHall().upgradeButtons().forEach(
                (id, button) -> button.addActionListener(event -> queueUpgrade(id)));
        view.townHall().hallButtons().forEach(
                (level, button) -> button.addActionListener(event -> upgradeHall(level)));
        view.townHall().technologyButtons().forEach(
                (technology, button) -> button.addActionListener(
                        event -> researchTechnology(technology)));
        view.townHall().cancelButton().addActionListener(event -> cancelProduction());
        view.trade().tradeButton().addActionListener(event -> requestTrade());
        view.trade().sourceSelector().addActionListener(event -> refreshExceptMap());
        view.trade().targetSelector().addActionListener(event -> refreshExceptMap());
        view.trade().levelSelector().addActionListener(event -> refreshExceptMap());
        view.trade().amountSpinner().addChangeListener(event -> refreshExceptMap());
        view.combat().attackButton().addActionListener(event -> requestSelectedAttack());
        view.combat().qSpinner().addChangeListener(event -> refreshExceptMap());
        view.combat().rSpinner().addChangeListener(event -> refreshExceptMap());
        view.combat().targetSelector().addActionListener(event -> refreshExceptMap());
        view.infrastructure().buildRoadButton().addActionListener(event->buildSelectedRoad());
        view.infrastructure().buildWallButton().addActionListener(event->buildSelectedWall());
        view.infrastructure().demolishRoadButton().addActionListener(event->demolishSelectedRoad());
        view.infrastructure().demolishWallButton().addActionListener(event->demolishSelectedWall());
        view.infrastructure().demolishBuildingButton().addActionListener(event->demolishSelectedBuilding());
        view.infrastructure().coordinateSpinners().forEach(spinner->spinner.addChangeListener(event->refreshExceptMap()));
        view.tribe().giftButton().addActionListener(event -> sendTribeGift());
        view.tribe().tradeButton().addActionListener(event -> tradeWithSelectedTribe());
        view.tribe().missionButton().addActionListener(event -> requestOrAcceptMission());
        view.tribe().deliverButton().addActionListener(event -> deliverMission());
        view.tribe().cancelMissionButton().addActionListener(event -> cancelMission());
        view.tribe().warButton().addActionListener(event -> declareWarOnTribe());
        view.tribe().peaceButton().addActionListener(event -> requestPeaceWithTribe());
        view.tribe().allianceButton().addActionListener(event -> requestAllianceWithTribe());
        view.tribe().giftResourceSelector().addActionListener(event -> refreshExceptMap());
        view.tribe().giftAmountSpinner().addChangeListener(event -> refreshExceptMap());
        view.tribe().tradeSourceSelector().addActionListener(event -> refreshExceptMap());
        view.tribe().tradeTargetSelector().addActionListener(event -> refreshExceptMap());
        view.tribe().tradeAmountSpinner().addChangeListener(event -> refreshExceptMap());
        if (persistence != null) {
            for (SaveSlot slot : SaveSlot.manualSlots()) {
                view.pause().saveButton(slot).addActionListener(event -> save(slot));
            }
            for (SaveSlot slot : SaveSlot.values()) {
                view.pause().loadButton(slot).addActionListener(event -> load(slot));
                view.pause().deleteButton(slot).addActionListener(event -> delete(slot));
            }
        }
    }

    private UnitDetailSnapshot selectedUnit() {
        return presenter.selectedUnit(snapshot);
    }

    private BuildingSnapshot productionBuildingAt(HexCoord coordinate) {
        return presenter.productionBuildingAt(snapshot, coordinate);
    }

    private int currentEntityIndex(List<Object> entities) {
        for (int i = 0; i < entities.size(); i++) {
            Object entity = entities.get(i);
            if (entity instanceof UnitDetailSnapshot unit
                    && uiState.selectedUnit().filter(unit.id()::equals).isPresent()) {
                return i;
            }
            if (entity instanceof BuildingSnapshot building
                    && uiState.selectedBuilding().filter(building.id()::equals).isPresent()) {
                return i;
            }
            if (entity instanceof TradingPostSnapshot post
                    && uiState.selectedTradingPost().filter(post.id()::equals).isPresent()) {
                return i;
            }
            if (entity instanceof TribeSnapshot tribe
                    && uiState.selectedTribe().filter(tribe.id()::equals).isPresent()) return i;
        }
        return -1;
    }

    private void execute(String prefix, Runnable command) {
        try {
            commandInProgress = true;
            view.notifyUser(" ");
            command.run();
        } catch (ActionFailure | IllegalArgumentException | IllegalStateException failure) {
            disableAutoExplore();
            view.notifyUser(prefix + ": " + failure.getMessage());
        } finally {
            commandInProgress = false;
            if (snapshot != null) {
                refreshExceptMap();
                refreshSaveSlots();
            }
        }
    }

    private void save(SaveSlot slot) {
        if (saveSlotScanInProgress) {
            view.notifyUser("Wait for save-slot validation to finish before saving.");
            refreshSaveSlots();
            return;
        }
        if (!isStableForSave()) { refreshSaveSlots(); return; }
        var existing = saveEntries.stream().filter(entry -> entry.slot() == slot)
                .findFirst().flatMap(civilization.application.persistence.SaveEntry::metadata);
        if (existing.isPresent() && !confirmations.confirm("Overwrite Save",
                "Overwrite " + existing.orElseThrow().saveName() + " from turn "
                        + existing.orElseThrow().turnNumber() + "?")) return;
        runBackground("Save failed", () -> persistence.saveManual(slot,
                slot.name().replace('_', ' '), gameIdentity, engine.persistenceState(),
                (requested, old, replacement) -> true), result ->
                view.notifyUser(result.status()
                        == civilization.application.persistence.SaveResult.Status.SAVED
                        ? "Saved " + slot + "." : "Save cancelled."));
    }

    private void delete(SaveSlot slot) {
        if (!confirmations.confirm("Delete Save",
                "Permanently delete " + slot + "? This cannot be undone.")) return;
        runBackground("Delete failed", () -> {
            persistence.delete(slot);
            return slot;
        }, deleted -> view.notifyUser("Deleted " + deleted + "."));
    }

    private void load(SaveSlot slot) {
        if (!confirmations.confirm("Load Game",
                "Replace the current game state with " + slot
                        + "? Choose Cancel to keep the current game unchanged.")) return;
        boolean saveFirst = confirmations.confirm("Save Current Game First",
                "Save current progress to MANUAL 1 before loading? Choose No to load without saving.");
        if (saveFirst) {
            var manualOne = saveEntries.stream()
                    .filter(entry -> entry.slot() == SaveSlot.MANUAL_1)
                    .findFirst().flatMap(civilization.application.persistence.SaveEntry::metadata);
            if (manualOne.isPresent() && !confirmations.confirm("Overwrite Save",
                    "Save-first will overwrite " + manualOne.orElseThrow().saveName()
                            + " from turn " + manualOne.orElseThrow().turnNumber() + ". Continue?")) {
                view.notifyUser("Load cancelled; current game was not changed.");
                return;
            }
        }
        runBackground("Load failed; current game was not changed", () -> {
            if (saveFirst) persistence.saveManual(SaveSlot.MANUAL_1, "Before load",
                    gameIdentity, engine.persistenceState(), (requested, old, replacement) -> true);
            return persistence.load(slot);
        }, candidate -> {
            disableAutoExplore();
            view.mapCanvas().animations().cancelAll();
            view.mapCanvas().effects().cancelAll();
            engine.restoreLoadedState(candidate.state());
            gameIdentity = candidate.identity();
            uiState.clearSelection();
            refresh();
            view.notifyUser("Loaded " + candidate.metadata().saveName()
                    + " without advancing the turn.");
        });
    }

    private <T> void runBackground(String prefix, Supplier<T> work, Consumer<T> success) {
        if (commandInProgress) return;
        commandInProgress = true;
        view.notifyUser("Working…");
        refreshExceptMap();
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() { return work.get(); }
            @Override protected void done() {
                if (closed) return;
                try {
                    success.accept(get());
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    view.notifyUser(prefix + ": interrupted");
                } catch (ExecutionException failure) {
                    Throwable cause = failure.getCause();
                    view.notifyUser(prefix + ": " + (cause.getMessage() == null
                            ? cause.getClass().getSimpleName() : cause.getMessage()));
                } catch (RuntimeException failure) {
                    view.notifyUser(prefix + ": " + failure.getMessage());
                } finally {
                    commandInProgress = false;
                    if (snapshot != null) refreshExceptMap();
                    refreshSaveSlots();
                }
            }
        }.execute();
    }

    private static void onEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) task.run();
        else SwingUtilities.invokeLater(task);
    }

    private record EndTurnWork(civilization.application.service.TurnResolution resolution,
            civilization.application.persistence.SaveResult autosave) { }

    private void displayAutosave(civilization.application.persistence.SaveResult result) {
        if (result == null) return;
        boolean saved = result.status()
                == civilization.application.persistence.SaveResult.Status.SAVED;
        view.pause().displayAutosave(saved, saved
                ? "saved turn " + snapshot.hud().turnNumber()
                : result.error().orElse("failed; previous autosave retained"));
    }

    private void refreshSaveSlots() {
        if (closed) return;
        if (persistence == null) {
            view.pause().display(saveEntries, false,
                    "Save storage is unavailable");
            return;
        }
        if (saveSlotScanInProgress) {
            view.pause().display(saveEntries, false,
                    "Checking save slots before saving or overwriting");
            saveSlotScanRequested = true;
            return;
        }
        saveSlotScanInProgress = true;
        view.pause().display(saveEntries, false,
                "Checking save slots before saving or overwriting");
        saveSlotScanWorker = new SwingWorker<List<civilization.application.persistence.SaveEntry>, Void>() {
            @Override protected List<civilization.application.persistence.SaveEntry> doInBackground() {
                return persistence.listSlots();
            }
            @Override protected void done() {
                if (closed) return;
                boolean loaded = false;
                try {
                    saveEntries = get();
                    loaded = true;
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    view.pause().displayAutosave(false, "slot scan interrupted");
                } catch (ExecutionException failure) {
                    view.pause().displayAutosave(false,
                            "slot scan failed: " + failure.getCause().getMessage());
                } catch (java.util.concurrent.CancellationException ignored) {
                    // Controller closure deliberately cancels presentation-only slot refresh.
                } finally {
                    saveSlotScanInProgress = false;
                    saveSlotScanWorker = null;
                    if (loaded) {
                        view.pause().display(saveEntries, isStableForSave(),
                                "Saving is disabled while a command, movement, combat, or event effect is active");
                    }
                    if (saveSlotScanRequested) {
                        saveSlotScanRequested = false;
                        refreshSaveSlots();
                    }
                }
            }
        };
        saveSlotScanWorker.execute();
    }

    public boolean isStableForSave() {
        return !commandInProgress && !view.mapCanvas().animations().hasActiveAnimations()
                && !view.mapCanvas().effects().hasActiveEffects();
    }

    public void toggleAutoExplore() {
        if (autoExplorer != null) {
            disableAutoExplore();
            view.notifyUser("Auto-Explore disabled. Manual control restored.");
            refreshExceptMap();
            return;
        }
        UnitDetailSnapshot selected = selectedUnit();
        if (selected == null || selected.type() != UnitType.EXPLORER) {
            view.notifyUser("Select an Explorer to enable Auto-Explore.");
            return;
        }
        autoExplorer = selected;
        view.notifyUser("Auto-Explore enabled. Disable it at any time to restore manual control.");
        refreshExceptMap();
        runAutoExploreStep();
    }

    private void runAutoExploreStep() {
        if (autoExplorer == null || view.mapCanvas().animations().hasActiveAnimations()) {
            return;
        }
        var explorerId = autoExplorer.id();
        execute("Auto-Explore stopped", () -> {
            var moved = engine.autoExploreStep(explorerId);
            if (moved.isEmpty()) {
                disableAutoExplore();
                view.notifyUser("Auto-Explore paused: no safe unexplored step is affordable.");
                refreshExceptMap();
                return;
            }
            var event = moved.orElseThrow();
            snapshot = engine.snapshot();
            autoExplorer = snapshot.units().stream().filter(unit -> unit.id().equals(explorerId))
                    .findFirst().orElse(null);
            uiState.selectUnit(explorerId, event.to());
            view.mapCanvas().displayCommittedMovement(snapshot.map(), event);
            view.minimap().setSnapshot(snapshot.map());
            refreshExceptMap();
            autoExploreTimer.restart();
        });
    }

    private void disableAutoExplore() {
        autoExploreTimer.stop();
        autoExplorer = null;
    }

    private boolean isGameplayInputAllowed() {
        return !commandInProgress && !view.mapCanvas().animations().hasActiveAnimations();
    }

    @Override
    public void close() {
        closed = true;
        if (saveSlotScanWorker != null) {
            saveSlotScanWorker.cancel(true);
            saveSlotScanWorker = null;
        }
        disableAutoExplore();
        view.mapCanvas().animations().setAnimationStateChanged(() -> { });
        view.mapCanvas().effects().setEffectStateChanged(() -> { });
        view.mapCanvas().animations().cancelAll();
        view.mapCanvas().effects().cancelAll();
        tribeNotifications.close();
        disasterNotifications.close();
    }
}
