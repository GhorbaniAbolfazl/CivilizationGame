package civilization.client.controller;

import civilization.client.state.ClientStateStore;
import civilization.client.state.ClientGameRenderModel;
import civilization.protocol.GameStartedPush;
import civilization.view.game.GamePanel;
import java.util.Objects;

// PHASE 3 - Added in Phase 3
public final class LobbyToGameCoordinator {
    // PHASE 3 - Added in Phase 3
    private final GameplayNavigator navigator;
    // PHASE 3 - Added in Phase 3
    private final MultiplayerGameplayPresenter presenter;
    // PHASE 3 - Added in Phase 3
    private GameStartedPush pendingStart;
    // PHASE 3 - Added in Phase 3
    private GamePanel gameplay;
    // PHASE 3 - Added in Phase 3
    private final MultiplayerTurnController turnController;
    // PHASE 3 - Added in Phase 3
    private ClientGameRenderModel latestState;
    // PHASE 3 - Added in Phase 3
    private final ChatController chatController;
    // PHASE 3 - Added in Phase 3
    private final MultiplayerTradeController tradeController;
    // PHASE 3 - Added in Phase 3
    private final ClientStateStore store;
    // PHASE 3 - Added in Phase 3
    private final MultiplayerDiplomacyController diplomacyController;
    // PHASE 3 - Added in Phase 3
    private final MultiplayerHallTownController hallTownController;
    // PHASE 3 - Added in Phase 3
    private final MultiplayerItemController itemController;
    // PHASE 3 - Added in Phase 3
    private final MultiplayerCombatController combatController;
    // PHASE 3 - Added in Phase 3
    private final MultiplayerMovementController movementController;
    // PHASE 3 - Added in Phase 3
    private final MultiplayerConstructionController constructionController;
    // PHASE 3 - Added in Phase 3
    private String selectedMovementUnitId;
    // PHASE 3 - Added in Phase 3
    private boolean movementPending;
    // PHASE 3 - Added in Phase 3
    private civilization.model.map.HexCoord selectedMovementCoordinate;
    // PHASE 3 - Added in Phase 3
    private boolean constructionPending;
    // PHASE 3 - Added in Phase 3
    private boolean networkAvailable = true;

    // PHASE 3 - Added in Phase 3
    public LobbyToGameCoordinator(ClientStateStore store, GameplayNavigator navigator) {
        this(store, navigator, new MultiplayerGameplayPresenter(), null, null, null, null, null, null, null);
    }
    // PHASE 3 - Added in Phase 3
    public LobbyToGameCoordinator(ClientStateStore store, GameplayNavigator navigator, MultiplayerGameplayPresenter presenter) {
        this(store, navigator, presenter, null, null, null, null, null, null, null);
    }
    // PHASE 3 - Added in Phase 3
    public LobbyToGameCoordinator(ClientStateStore store, GameplayNavigator navigator,
                                  MultiplayerGameplayPresenter presenter, MultiplayerTurnController turnController) {
        this(store, navigator, presenter, turnController, null, null, null, null, null, null);
    }
    // PHASE 3 - Added in Phase 3
    public LobbyToGameCoordinator(ClientStateStore store, GameplayNavigator navigator,
                                  MultiplayerGameplayPresenter presenter,
                                  MultiplayerTurnController turnController,
                                  ChatController chatController) {
        this(store, navigator, presenter, turnController, chatController, null, null, null, null, null);
    }
    // PHASE 3 - Added in Phase 3
    public LobbyToGameCoordinator(ClientStateStore store, GameplayNavigator navigator,
                                  MultiplayerGameplayPresenter presenter,
                                  MultiplayerTurnController turnController,
                                  ChatController chatController,
                                  MultiplayerTradeController tradeController) {
        this(store, navigator, presenter, turnController, chatController, tradeController, null, null, null, null);
    }
    // PHASE 3 - Added in Phase 3
    public LobbyToGameCoordinator(ClientStateStore store, GameplayNavigator navigator,
                                  MultiplayerGameplayPresenter presenter,
                                  MultiplayerTurnController turnController,
                                  ChatController chatController,
                                  MultiplayerTradeController tradeController,
                                  MultiplayerDiplomacyController diplomacyController) {
        this(store, navigator, presenter, turnController, chatController, tradeController,
                diplomacyController, null, null, null);
    }
    // PHASE 3 - Added in Phase 3
    public LobbyToGameCoordinator(ClientStateStore store, GameplayNavigator navigator,
                                  MultiplayerGameplayPresenter presenter,
                                  MultiplayerTurnController turnController,
                                  ChatController chatController,
                                  MultiplayerTradeController tradeController,
                                  MultiplayerDiplomacyController diplomacyController,
                                  MultiplayerHallTownController hallTownController) {
        this(store, navigator, presenter, turnController, chatController, tradeController,
                diplomacyController, hallTownController, null, null);
    }
    // PHASE 3 - Added in Phase 3
    public LobbyToGameCoordinator(ClientStateStore store, GameplayNavigator navigator,
                                  MultiplayerGameplayPresenter presenter,
                                  MultiplayerTurnController turnController,
                                  ChatController chatController,
                                  MultiplayerTradeController tradeController,
                                  MultiplayerDiplomacyController diplomacyController,
                                  MultiplayerHallTownController hallTownController,
                                  MultiplayerItemController itemController) {
        this(store, navigator, presenter, turnController, chatController, tradeController,
                diplomacyController, hallTownController, itemController, null);
    }
    // PHASE 3 - Added in Phase 3
    public LobbyToGameCoordinator(ClientStateStore store, GameplayNavigator navigator,
                                  MultiplayerGameplayPresenter presenter,
                                  MultiplayerTurnController turnController,
                                  ChatController chatController,
                                  MultiplayerTradeController tradeController,
                                  MultiplayerDiplomacyController diplomacyController,
                                  MultiplayerHallTownController hallTownController,
                                  MultiplayerItemController itemController,
                                  MultiplayerCombatController combatController) {
        this(store, navigator, presenter, turnController, chatController, tradeController,
                diplomacyController, hallTownController, itemController, combatController, null);
    }

    // PHASE 3 - Added in Phase 3
    public LobbyToGameCoordinator(ClientStateStore store, GameplayNavigator navigator,
                                  MultiplayerGameplayPresenter presenter,
                                  MultiplayerTurnController turnController,
                                  ChatController chatController,
                                  MultiplayerTradeController tradeController,
                                  MultiplayerDiplomacyController diplomacyController,
                                  MultiplayerHallTownController hallTownController,
                                  MultiplayerItemController itemController,
                                  MultiplayerCombatController combatController,
                                  MultiplayerMovementController movementController) {
        this(store, navigator, presenter, turnController, chatController, tradeController,
                diplomacyController, hallTownController, itemController, combatController,
                movementController, null);
    }

    // PHASE 3 - Added in Phase 3
    public LobbyToGameCoordinator(ClientStateStore store, GameplayNavigator navigator,
                                  MultiplayerGameplayPresenter presenter,
                                  MultiplayerTurnController turnController,
                                  ChatController chatController,
                                  MultiplayerTradeController tradeController,
                                  MultiplayerDiplomacyController diplomacyController,
                                  MultiplayerHallTownController hallTownController,
                                  MultiplayerItemController itemController,
                                  MultiplayerCombatController combatController,
                                  MultiplayerMovementController movementController,
                                  MultiplayerConstructionController constructionController) {
        this.navigator = Objects.requireNonNull(navigator); this.presenter = Objects.requireNonNull(presenter); this.store = Objects.requireNonNull(store);
        this.turnController = turnController;
        this.chatController = chatController;
        this.tradeController = tradeController;
        this.diplomacyController = diplomacyController;
        this.hallTownController = hallTownController;
        this.itemController = itemController;
        this.combatController = combatController;
        this.movementController = movementController;
        this.constructionController = constructionController;
        store.addGameStartedListener(this::onStarted); store.addGameModelListener(this::onState);
        // PHASE 3 - Added in Phase 3
        store.addChatListener(message -> {
            if (gameplay != null && pendingStart != null
                    && pendingStart.matchId().equals(message.scopeId())) gameplay.chat().append(message);
        });
        // PHASE 3 - Added in Phase 3
        store.addBattleReportListener(report -> {
            if (gameplay != null && pendingStart != null && latestState != null
                    && pendingStart.matchId().equals(latestState.snapshot().matchId())) {
                gameplay.showBattleReport(report);
            }
        });
        // PHASE 3 - Added in Phase 3
        store.addNotificationListener(notification -> { if (gameplay != null) gameplay.notifyUser(notification.message()); });
        // PHASE 3 - Added in Phase 3
        store.addTradeOfferListener(offer -> {
            if (gameplay != null && pendingStart != null
                    && pendingStart.matchId().equals(offer.matchId())) gameplay.playerTrade().displayOffer(offer);
        });
        // PHASE 3 - Added in Phase 3
        store.addDiplomacyListener(relation -> {
            if (gameplay != null && pendingStart != null
                    && pendingStart.matchId().equals(relation.matchId())) gameplay.diplomacy().displayRelation(relation);
        });
        // PHASE 3 - Added in Phase 3
        store.addAllianceRequestListener(request -> {
            if (gameplay != null && pendingStart != null
                    && pendingStart.matchId().equals(request.matchId())) gameplay.diplomacy().displayAllianceRequest(request);
        });
        // PHASE 3 - Added in Phase 3
        store.addConnectionStatusListener(status -> {
            if (gameplay != null) gameplay.notifyUser("Player "
                    + status.playerId().substring(0, Math.min(8, status.playerId().length()))
                    + " is " + status.status());
        });
        store.gameStarted().ifPresent(this::onStarted); store.gameModel().ifPresent(this::onState);
    }
    // PHASE 3 - Added in Phase 3
    private void onStarted(GameStartedPush start) { pendingStart = start; }
    // PHASE 3 - Added in Phase 3
    private void onState(ClientGameRenderModel state) {
        if (pendingStart == null || !pendingStart.matchId().equals(state.snapshot().matchId())) return;
        latestState = state;
        // PHASE 3 - Added in Phase 3
        if (gameplay == null) { gameplay = new GamePanel(); installTurnAction(); installChatAction(); installTradeActions(); installDiplomacyActions(); installHallTownAction(); installItemActions(); installCombatAction(); installMovementAction(); installConstructionActions(); presenter.present(gameplay, state); gameplay.hud().setConnectionState(networkAvailable ? "CONNECTED" : "DISCONNECTED", !networkAvailable); navigator.openGameplay(gameplay); }
        else presenter.present(gameplay, state);
        // PHASE 3 - Added in Phase 3
        store.lobby().filter(lobby -> pendingStart.lobbyId().equals(lobby.snapshot().lobbyId()))
                .ifPresent(lobby -> gameplay.playerTrade().setPlayers(
                        lobby.snapshot().members(), state.snapshot().viewerPlayerId()));
        // PHASE 3 - Added in Phase 3
        store.lobby().filter(lobby -> pendingStart.lobbyId().equals(lobby.snapshot().lobbyId()))
                .ifPresent(lobby -> gameplay.diplomacy().setPlayers(
                        lobby.snapshot().members(), state.snapshot().viewerPlayerId()));
        // PHASE 3 - Added in Phase 3
        store.tradeOffers().stream().filter(offer -> offer.matchId().equals(state.snapshot().matchId()))
                .forEach(gameplay.playerTrade()::displayOffer);
        // PHASE 3 - Added in Phase 3
        store.diplomacyRelations().stream().filter(relation -> relation.matchId().equals(state.snapshot().matchId()))
                .forEach(gameplay.diplomacy()::displayRelation);
        // PHASE 3 - Added in Phase 3
        store.allianceRequests().stream().filter(request -> request.matchId().equals(state.snapshot().matchId()))
                .forEach(gameplay.diplomacy()::displayAllianceRequest);
        // PHASE 3 - Added in Phase 3
        gameplay.diplomacy().setActiveTurn(state.snapshot().viewerPlayerId().equals(
                state.snapshot().activePlayerId()) && state.snapshot().matchStatus().equals("ACTIVE")
                && networkAvailable);
        // PHASE 3 - Added in Phase 3
        gameplay.playerTrade().setActiveTurn(tradeController != null && networkAvailable
                && state.snapshot().matchStatus().equals("ACTIVE")
                && state.snapshot().viewerPlayerId().equals(state.snapshot().activePlayerId()));
        // PHASE 3 - Added in Phase 3
        gameplay.multiplayerHallTown().setBuilders(state.snapshot().visibleUnits(),
                state.snapshot().viewerPlayerId());
        gameplay.multiplayerHallTown().setActionAvailable(hallTownController != null
                && state.snapshot().matchStatus().equals("ACTIVE")
                && networkAvailable
                && state.snapshot().viewerPlayerId().equals(state.snapshot().activePlayerId()));
        // PHASE 3 - Added in Phase 3
        gameplay.multiplayerHallTown().setActiveHallCount(state.snapshot().visibleBuildings().stream()
                .filter(building -> building.ownerPlayerId().equals(state.snapshot().viewerPlayerId()))
                .filter(building -> building.buildingType().equals("TOWN_HALL"))
                .filter(building -> building.status().equals("ACTIVE")).count());
        // PHASE 3 - Added in Phase 3
        gameplay.multiplayerItems().display(state.snapshot());
        gameplay.multiplayerItems().setActionsAvailable(itemController != null
                && state.snapshot().matchStatus().equals("ACTIVE")
                && networkAvailable
                && state.snapshot().viewerPlayerId().equals(state.snapshot().activePlayerId()));
        // PHASE 3 - Added in Phase 3
        gameplay.combat().displayMultiplayerTargets(state.snapshot());
        gameplay.combat().setMultiplayerActionAvailable(combatController != null
                && networkAvailable && state.snapshot().matchStatus().equals("ACTIVE")
                && state.snapshot().viewerPlayerId().equals(state.snapshot().activePlayerId()));
        // PHASE 3 - Added in Phase 3
        gameplay.hud().endTurnButton().setEnabled(turnController != null
                && state.snapshot().matchStatus().equals("ACTIVE")
                && networkAvailable
                && state.snapshot().viewerPlayerId().equals(state.snapshot().activePlayerId()));
        gameplay.hud().endTurnButton().setToolTipText(gameplay.hud().endTurnButton().isEnabled()
                ? "Ask the authoritative server to process and advance the turn"
                : !networkAvailable ? "Reconnect before ending your turn"
                : !state.snapshot().matchStatus().equals("ACTIVE") ? "The match is not active"
                : "Only the active player may end the turn");
        // PHASE 3 - Added in Phase 3
        boolean movementAvailable = movementController != null && networkAvailable
                && !movementPending && !constructionPending && state.snapshot().matchStatus().equals("ACTIVE")
                && state.snapshot().viewerPlayerId().equals(state.snapshot().activePlayerId());
        gameplay.setActionInputGate(() -> movementAvailable);
        if (selectedMovementUnitId != null && state.snapshot().visibleUnits().stream()
                .noneMatch(unit -> unit.unitId().equals(selectedMovementUnitId)
                        && unit.ownerPlayerId().equals(state.snapshot().viewerPlayerId()))) {
            selectedMovementUnitId = null;
            selectedMovementCoordinate = null;
        }
        // PHASE 3 - Added in Phase 3
        boolean builderSelected = selectedMovementUnitId != null
                && state.snapshot().visibleUnits().stream().anyMatch(unit ->
                unit.unitId().equals(selectedMovementUnitId)
                        && unit.ownerPlayerId().equals(state.snapshot().viewerPlayerId())
                        && unit.unitType().equals("BUILDER"));
        gameplay.actions().buildButtons().forEach((type, button) -> {
            boolean standardType = type != civilization.model.building.BuildingType.APOTHECARY;
            boolean enabled = standardType && constructionController != null && movementAvailable
                    && !constructionPending && builderSelected;
            button.setEnabled(enabled);
            // PHASE 3 - Added in Phase 3
            button.setToolTipText(!standardType
                    ? "Use the Items tab to build an Apothecary"
                    : !movementAvailable ? "Only the connected active player may build"
                    : !builderSelected ? "Select an owned Builder on the map first"
                    : "Send this construction request to the authoritative server");
        });
        // PHASE 3 - Added in Phase 3
        gameplay.chat().setNetworkAvailable(networkAvailable && chatController != null);
    }
    // PHASE 3 - Added in Phase 3
    private void installTurnAction() {
        if (turnController == null) return;
        gameplay.hud().endTurnButton().addActionListener(event -> {
            ClientGameRenderModel submitted = latestState; gameplay.hud().endTurnButton().setEnabled(false);
            // PHASE 3 - Added in Phase 3
            turnController.endTurn(submitted).whenComplete((response, failure) -> javax.swing.SwingUtilities.invokeLater(() -> {
                if (failure != null) gameplay.notifyUser("End Turn failed: " + failure.getMessage());
                else if (response instanceof civilization.protocol.ErrorResponse error) gameplay.notifyUser(error.message());
            }));
        });
    }
    // PHASE 3 - Added in Phase 3
    private void installChatAction() {
        if (chatController == null) return;
        gameplay.chat().setSendAction(content -> chatController.send(
                latestState.snapshot().matchId(), content).whenComplete((response, failure) ->
                javax.swing.SwingUtilities.invokeLater(() -> {
                    if (failure != null) gameplay.notifyUser(
                            "Chat failed: " + failure.getMessage());
                    else if (response instanceof civilization.protocol.ErrorResponse error) {
                        gameplay.notifyUser(error.message());
                    }
                })));
    }
    // PHASE 3 - Added in Phase 3
    private void installTradeActions() {
        if (tradeController == null) return;
        gameplay.playerTrade().setCreateAction(() -> submitTrade(tradeController.create(
                latestState, gameplay.playerTrade().selectedTargetPlayerId(),
                gameplay.playerTrade().offeredBundle(), gameplay.playerTrade().requestedBundle())));
        gameplay.playerTrade().setAcceptAction(offer -> submitTrade(
                tradeController.accept(latestState, offer.offerId())));
        gameplay.playerTrade().setRejectAction(offer -> submitTrade(
                tradeController.reject(latestState, offer.offerId())));
        gameplay.playerTrade().setCancelAction(offer -> submitTrade(
                tradeController.cancel(latestState, offer.offerId())));
    }

    // PHASE 3 - Added in Phase 3
    private void submitTrade(java.util.concurrent.CompletableFuture<civilization.protocol.ResponseMessage> operation) {
        operation.whenComplete((response, failure) -> javax.swing.SwingUtilities.invokeLater(() -> {
            if (failure != null) gameplay.notifyUser("Trade failed: " + failure.getMessage());
            else if (response instanceof civilization.protocol.ErrorResponse error) gameplay.notifyUser(error.message());
        }));
    }
    // PHASE 3 - Added in Phase 3
    private void installDiplomacyActions() {
        if (diplomacyController == null) return;
        gameplay.diplomacy().setDeclareWarAction(target -> submitDiplomacy(
                diplomacyController.declareWar(latestState, target)));
        gameplay.diplomacy().setRequestAllianceAction(target -> submitDiplomacy(
                diplomacyController.requestAlliance(latestState, target)));
        gameplay.diplomacy().setBreakAllianceAction(target -> submitDiplomacy(
                diplomacyController.breakAlliance(latestState, target)));
        gameplay.diplomacy().setAcceptAction(request -> submitDiplomacy(
                diplomacyController.decideAlliance(latestState, request.requestId(),
                        civilization.protocol.AllianceDecision.ACCEPT)));
        gameplay.diplomacy().setRejectAction(request -> submitDiplomacy(
                diplomacyController.decideAlliance(latestState, request.requestId(),
                        civilization.protocol.AllianceDecision.REJECT)));
    }

    // PHASE 3 - Added in Phase 3
    private void submitDiplomacy(java.util.concurrent.CompletableFuture<civilization.protocol.ResponseMessage> operation) {
        operation.whenComplete((response, failure) -> javax.swing.SwingUtilities.invokeLater(() -> {
            if (failure != null) gameplay.notifyUser("Diplomacy failed: " + failure.getMessage());
            else if (response instanceof civilization.protocol.ErrorResponse error) gameplay.notifyUser(error.message());
        }));
    }
    // PHASE 3 - Added in Phase 3
    private void installHallTownAction() {
        if (hallTownController == null) return;
        gameplay.multiplayerHallTown().setEstablishAction(selection -> {
            ClientGameRenderModel submitted = latestState;
            gameplay.multiplayerHallTown().setActionAvailable(false);
            // PHASE 3 - Added in Phase 3
            hallTownController.establish(submitted, selection.builderId(), selection.q(), selection.r())
                    .whenComplete((response, failure) -> javax.swing.SwingUtilities.invokeLater(() -> {
                        if (failure != null) gameplay.notifyUser(
                                "Hall Town failed: " + failure.getMessage());
                        else if (response instanceof civilization.protocol.ErrorResponse error) {
                            gameplay.notifyUser(error.message());
                        }
                        // PHASE 3 - Added in Phase 3
                        ClientGameRenderModel current = latestState;
                        gameplay.multiplayerHallTown().setActionAvailable(current != null
                                && current.snapshot().matchStatus().equals("ACTIVE")
                                && current.snapshot().viewerPlayerId().equals(
                                current.snapshot().activePlayerId()));
                    }));
        });
    }
    // PHASE 3 - Added in Phase 3
    private void installItemActions() {
        if (itemController == null) return;
        gameplay.multiplayerItems().setBuildAction(selection -> submitItem(
                itemController.buildApothecary(latestState, selection.builderId(),
                        selection.q(), selection.r())));
        gameplay.multiplayerItems().setCraftAction(selection -> submitItem(
                itemController.craft(latestState, selection.apothecaryId(),
                        selection.itemType())));
        gameplay.multiplayerItems().setUseAction(selection -> submitItem(
                itemController.use(latestState, selection.itemId(), selection.unitId(),
                        selection.q(), selection.r())));
    }

    // PHASE 3 - Added in Phase 3
    private void submitItem(java.util.concurrent.CompletableFuture<civilization.protocol.ResponseMessage> operation) {
        gameplay.multiplayerItems().setActionsAvailable(false);
        operation.whenComplete((response, failure) -> javax.swing.SwingUtilities.invokeLater(() -> {
            if (failure != null) gameplay.notifyUser("Item action failed: " + failure.getMessage());
            else if (response instanceof civilization.protocol.ErrorResponse error) {
                gameplay.notifyUser(error.message());
            }
            // PHASE 3 - Added in Phase 3
            ClientGameRenderModel current = latestState;
            gameplay.multiplayerItems().setActionsAvailable(current != null
                    && current.snapshot().matchStatus().equals("ACTIVE")
                    && current.snapshot().viewerPlayerId().equals(
                    current.snapshot().activePlayerId()));
        }));
    }
    // PHASE 3 - Added in Phase 3
    private void installCombatAction() {
        if (combatController == null) return;
        gameplay.combat().setMultiplayerAction(selection -> {
            gameplay.combat().setMultiplayerActionAvailable(false);
            // PHASE 3 - Added in Phase 3
            combatController.attack(latestState, selection.attackerUnitIds(), selection.targetId())
                    .whenComplete((response, failure) -> javax.swing.SwingUtilities.invokeLater(() -> {
                        if (failure != null) gameplay.notifyUser("Combat failed: " + rootMessage(failure));
                        else if (response instanceof civilization.protocol.ErrorResponse error) {
                            gameplay.notifyUser(error.message());
                        }
                        refreshAvailability();
                    }));
        });
    }

    // PHASE 3 - Added in Phase 3
    private void installMovementAction() {
        if (movementController == null) return;
        gameplay.mapCanvas().setPrimaryHexIntent(coordinate -> {
            var selected = latestState.snapshot().visibleUnits().stream()
                    .filter(unit -> unit.ownerPlayerId().equals(
                            latestState.snapshot().viewerPlayerId()))
                    .filter(unit -> unit.q() == coordinate.q() && unit.r() == coordinate.r())
                    .findFirst().orElse(null);
            // PHASE 3 - Added in Phase 3
            if (selected == null) {
                selectedMovementUnitId = null;
                selectedMovementCoordinate = null;
                gameplay.notifyUser("Select one of your visible units before moving.");
            } else {
                selectedMovementUnitId = selected.unitId();
                selectedMovementCoordinate = coordinate;
                gameplay.notifyUser("Selected " + selected.unitType()
                        + ". Right-click an adjacent destination hex.");
            }
        });
        gameplay.mapCanvas().setSecondaryHexIntent(coordinate -> {
            if (selectedMovementUnitId == null) {
                gameplay.notifyUser("Select one of your units first, then right-click a destination.");
                return;
            }
            ClientGameRenderModel submitted = latestState;
            String unitId = selectedMovementUnitId;
            movementPending = true;
            gameplay.setActionInputGate(() -> false);
            // PHASE 3 - Added in Phase 3
            movementController.move(submitted, unitId, coordinate.q(), coordinate.r())
                    .whenComplete((response, failure) -> javax.swing.SwingUtilities.invokeLater(() -> {
                        movementPending = false;
                        if (failure != null) gameplay.notifyUser(
                                "Movement failed: " + rootMessage(failure));
                        else if (response instanceof civilization.protocol.ErrorResponse error) {
                            gameplay.notifyUser(error.message());
                        } else {
                            selectedMovementUnitId = null;
                            selectedMovementCoordinate = null;
                        }
                        refreshAvailability();
                    }));
        });
    }

    // PHASE 3 - Added in Phase 3
    private void installConstructionActions() {
        if (constructionController == null) return;
        gameplay.actions().buildButtons().forEach((type, button) ->
                button.addActionListener(event -> {
                    if (selectedMovementUnitId == null || selectedMovementCoordinate == null) {
                        gameplay.notifyUser("Select an owned Builder on the map first.");
                        return;
                    }
                    ClientGameRenderModel submitted = latestState;
                    constructionPending = true;
                    button.setEnabled(false);
                    // PHASE 3 - Added in Phase 3
                    constructionController.build(submitted, selectedMovementUnitId, type,
                                    selectedMovementCoordinate.q(), selectedMovementCoordinate.r())
                            .whenComplete((response, failure) -> javax.swing.SwingUtilities.invokeLater(() -> {
                                constructionPending = false;
                                if (failure != null) gameplay.notifyUser(
                                        "Construction failed: " + rootMessage(failure));
                                else if (response instanceof civilization.protocol.ErrorResponse error) {
                                    gameplay.notifyUser(error.message());
                                }
                                refreshAvailability();
                            }));
                }));
    }

    // PHASE 3 - Added in Phase 3
    public void setNetworkAvailable(boolean available, String label, Runnable reconnectAction) {
        networkAvailable = available;
        if (gameplay == null) return;
        gameplay.hud().setConnectionState(label, !available && reconnectAction != null);
        gameplay.chat().setNetworkAvailable(available && chatController != null);
        // PHASE 3 - Added in Phase 3
        if (reconnectAction != null) {
            for (var listener : gameplay.hud().reconnectButton().getActionListeners()) {
                gameplay.hud().reconnectButton().removeActionListener(listener);
            }
            gameplay.hud().reconnectButton().addActionListener(event -> reconnectAction.run());
        }
        refreshAvailability();
    }

    // PHASE 3 - Added in Phase 3
    private void refreshAvailability() {
        if (gameplay == null || latestState == null) return;
        onState(latestState);
    }

    // PHASE 3 - Added in Phase 3
    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
    // PHASE 3 - Added in Phase 3
    public boolean transitioned() { return gameplay != null; }
}
