package civilization.phase3;

import civilization.client.event.ClientMessageRouter;
import civilization.client.event.SwingUiEventDispatcher;
import civilization.client.state.ClientStateStore;
import civilization.protocol.AllianceDecision;
import civilization.protocol.AllianceDecisionRequest;
import civilization.protocol.AllianceRequest;
import civilization.protocol.AllianceRequestPush;
import civilization.protocol.BreakAllianceRequest;
import civilization.protocol.DeclareWarRequest;
import civilization.protocol.DiplomacyStatePush;
import civilization.protocol.EndTurnRequest;
import civilization.protocol.LobbyMemberDto;
import civilization.protocol.MessageMetadata;
import civilization.protocol.NotificationPush;
import civilization.protocol.PushMessage;
import civilization.server.diplomacy.AllianceRequestStatus;
import civilization.server.diplomacy.DiplomacyException;
import civilization.server.diplomacy.PlayerDiplomaticRelation;
import civilization.server.lobby.LobbyServerService;
import civilization.server.match.MatchException;
import civilization.server.session.ClientSession;
import civilization.server.session.SessionId;
import civilization.server.session.SessionRegistry;
import civilization.view.game.DiplomacyPanel;
import javax.swing.SwingUtilities;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

// PHASE 3 - Added in Phase 3
public final class Phase3PlayerDiplomacyTestSuite {
    // PHASE 3 - Added in Phase 3
    private static final Instant NOW = Instant.parse("2033-07-08T09:10:11.123456Z");
    // PHASE 3 - Added in Phase 3
    private Phase3PlayerDiplomacyTestSuite() {}

    // PHASE 3 - Added in Phase 3
    public static void main(String[] args) throws Exception {
        everyPairStartsNeutralAndIsPushed();
        warIsUnilateralGlobalAndCancelsPendingAlliance();
        allianceRequiresTargetAcceptanceAndRejectsUnauthorizedActions();
        alliesShareVisionAndBreakingReturnsToNeutralImmediately();
        diplomaticPushesReachSwingOnTheEdt();
        System.out.println("Phase3PlayerDiplomacyTestSuite: all checks passed");
    }

    // PHASE 3 - Added in Phase 3
    private static void everyPairStartsNeutralAndIsPushed() {
        try (Fixture fixture = new Fixture(true)) {
            assert fixture.service.match(fixture.matchId).project(state ->
                    state.diplomacy().relations().size()).join() == 3;
            assert fixture.service.match(fixture.matchId).project(state ->
                    state.diplomacy().relations().stream().allMatch(pair ->
                            pair.relation() == PlayerDiplomaticRelation.NEUTRAL)).join();
            assert fixture.count(DiplomacyStatePush.class, fixture.host.sessionId()) == 3;
            assert fixture.count(DiplomacyStatePush.class, fixture.guest.sessionId()) == 3;
            assert fixture.count(DiplomacyStatePush.class, fixture.observer.sessionId()) == 3;
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void warIsUnilateralGlobalAndCancelsPendingAlliance() {
        try (Fixture fixture = new Fixture(true)) {
            String requestId = fixture.service.requestAlliance(fixture.host,
                    new AllianceRequest(MessageMetadata.createNow(), fixture.matchId,
                            fixture.guest.playerId().toString(), 1L)).join()
                    .allianceRequest().requestId();
            var war = fixture.service.declareWar(fixture.host, new DeclareWarRequest(
                    MessageMetadata.createNow(), fixture.matchId,
                    fixture.guest.playerId().toString(), 2L)).join();
            assert war.relation() == PlayerDiplomaticRelation.ENEMY;
            assert war.allianceRequest().status() == AllianceRequestStatus.CANCELLED;
            assert fixture.relation(fixture.host, fixture.guest) == PlayerDiplomaticRelation.ENEMY;
            assert fixture.latestAlliance(fixture.guest.sessionId(), requestId).status().equals("CANCELLED");
            for (ClientSession player : List.of(fixture.host, fixture.guest, fixture.observer)) {
                assert fixture.messages.stream().anyMatch(sent -> sent.recipient().equals(player.sessionId())
                        && sent.message() instanceof NotificationPush notification
                        && notification.code().equals("WAR_DECLARED"));
                assert fixture.latestRelation(player.sessionId()).relation().equals("ENEMY");
            }
            expectDiplomacy("ALREADY_AT_WAR", () -> fixture.service.declareWar(fixture.host,
                    new DeclareWarRequest(MessageMetadata.createNow(), fixture.matchId,
                            fixture.guest.playerId().toString(), 3L)).join());
            assert fixture.version() == 3L;
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void allianceRequiresTargetAcceptanceAndRejectsUnauthorizedActions() {
        try (Fixture fixture = new Fixture(false)) {
            String requestId = fixture.service.requestAlliance(fixture.host,
                    new AllianceRequest(MessageMetadata.createNow(), fixture.matchId,
                            fixture.guest.playerId().toString(), 1L)).join()
                    .allianceRequest().requestId();
            assert fixture.relation(fixture.host, fixture.guest) == PlayerDiplomaticRelation.NEUTRAL;
            expectDiplomacy("UNAUTHORIZED_DIPLOMACY_ACTION", () ->
                    fixture.service.decideAlliance(fixture.host, new AllianceDecisionRequest(
                            MessageMetadata.createNow(), fixture.matchId, requestId,
                            AllianceDecision.ACCEPT, 2L)).join());
            expectMatch("NOT_ACTIVE_PLAYER", () -> fixture.service.decideAlliance(fixture.guest,
                    new AllianceDecisionRequest(MessageMetadata.createNow(), fixture.matchId,
                            requestId, AllianceDecision.ACCEPT, 2L)).join());
            expectDiplomacy("ALLIANCE_REQUEST_PENDING", () -> fixture.service.requestAlliance(
                    fixture.host, new AllianceRequest(MessageMetadata.createNow(), fixture.matchId,
                            fixture.guest.playerId().toString(), 2L)).join());
            fixture.endHostTurn(2L);
            fixture.service.decideAlliance(fixture.guest, new AllianceDecisionRequest(
                    MessageMetadata.createNow(), fixture.matchId, requestId,
                    AllianceDecision.ACCEPT, 3L)).join();
            assert fixture.relation(fixture.host, fixture.guest) == PlayerDiplomaticRelation.ALLIED;
            expectDiplomacy("ALLIANCE_REQUEST_CLOSED", () -> fixture.service.decideAlliance(
                    fixture.guest, new AllianceDecisionRequest(MessageMetadata.createNow(),
                            fixture.matchId, requestId, AllianceDecision.ACCEPT, 4L)).join());
            assert fixture.version() == 4L;
        }
        try (Fixture fixture = new Fixture(false)) {
            String requestId = fixture.service.requestAlliance(fixture.host,
                    new AllianceRequest(MessageMetadata.createNow(), fixture.matchId,
                            fixture.guest.playerId().toString(), 1L)).join()
                    .allianceRequest().requestId();
            fixture.endHostTurn(2L);
            fixture.service.decideAlliance(fixture.guest, new AllianceDecisionRequest(
                    MessageMetadata.createNow(), fixture.matchId, requestId,
                    AllianceDecision.REJECT, 3L)).join();
            assert fixture.relation(fixture.host, fixture.guest) == PlayerDiplomaticRelation.NEUTRAL;
            assert fixture.latestAlliance(fixture.host.sessionId(), requestId).status().equals("REJECTED");
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void alliesShareVisionAndBreakingReturnsToNeutralImmediately() {
        try (Fixture fixture = new Fixture(true)) {
            var hidden = fixture.service.match(fixture.matchId).project(state -> {
                var hostVisibility = state.requirePlayer(fixture.host.playerId()).empire().visibility();
                var guestVisibility = state.requirePlayer(fixture.guest.playerId()).empire().visibility();
                var observerHall = state.requirePlayer(fixture.observer.playerId()).empire().townHall();
                assert !hostVisibility.isCurrentlyVisible(observerHall.position());
                guestVisibility.replaceCurrentlyVisible(java.util.Set.of(observerHall.position()));
                return observerHall.id().value().toString();
            }).join();
            assert fixture.project(fixture.host).visibleBuildings().stream()
                    .noneMatch(building -> building.buildingId().equals(hidden));
            String requestId = fixture.service.requestAlliance(fixture.host,
                    new AllianceRequest(MessageMetadata.createNow(), fixture.matchId,
                            fixture.guest.playerId().toString(), 1L)).join()
                    .allianceRequest().requestId();
            fixture.endHostTurn(2L);
            fixture.service.decideAlliance(fixture.guest, new AllianceDecisionRequest(
                    MessageMetadata.createNow(), fixture.matchId, requestId,
                    AllianceDecision.ACCEPT, 3L)).join();
            assert fixture.project(fixture.host).visibleBuildings().stream()
                    .anyMatch(building -> building.buildingId().equals(hidden));
            fixture.service.breakAlliance(fixture.guest, new BreakAllianceRequest(
                    MessageMetadata.createNow(), fixture.matchId,
                    fixture.host.playerId().toString(), 4L)).join();
            assert fixture.relation(fixture.host, fixture.guest) == PlayerDiplomaticRelation.NEUTRAL;
            assert fixture.project(fixture.host).visibleBuildings().stream()
                    .noneMatch(building -> building.buildingId().equals(hidden));
            expectDiplomacy("NOT_ALLIED", () -> fixture.service.breakAlliance(fixture.guest,
                    new BreakAllianceRequest(MessageMetadata.createNow(), fixture.matchId,
                            fixture.host.playerId().toString(), 5L)).join());
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void diplomaticPushesReachSwingOnTheEdt() throws Exception {
        ClientStateStore store = new ClientStateStore(new SwingUiEventDispatcher());
        ClientMessageRouter router = new ClientMessageRouter(store, new SwingUiEventDispatcher());
        DiplomacyPanel panel = new DiplomacyPanel();
        panel.setPlayers(List.of(
                new LobbyMemberDto("player-a", "A", NOW, true, true),
                new LobbyMemberDto("player-b", "B", NOW, true, true)), "player-a");
        CountDownLatch delivered = new CountDownLatch(2);
        AtomicBoolean onEdt = new AtomicBoolean(true);
        store.addDiplomacyListener(relation -> {
            onEdt.compareAndSet(true, SwingUtilities.isEventDispatchThread());
            panel.displayRelation(relation); delivered.countDown();
        });
        store.addAllianceRequestListener(request -> {
            onEdt.compareAndSet(true, SwingUtilities.isEventDispatchThread());
            panel.displayAllianceRequest(request); delivered.countDown();
        });
        Thread listener = new Thread(() -> {
            router.onPush(new DiplomacyStatePush(MessageMetadata.createNow(), "match", 2L,
                    "player-a", "player-b", "ENEMY"));
            router.onPush(new AllianceRequestPush(MessageMetadata.createNow(), "match", 3L,
                    "request", "player-b", "player-a", "PENDING", NOW));
        }, "test-diplomacy-listener");
        listener.start(); listener.join(500L);
        assert !listener.isAlive() && delivered.await(2, TimeUnit.SECONDS) && onEdt.get();
        assert panel.relationList().getModel().getElementAt(0).equals("B: ENEMY");
        assert panel.requestList().getModel().getSize() == 1;
        panel.setActiveTurn(true); panel.requestList().setSelectedIndex(0);
        assert panel.acceptButton().isEnabled() && panel.rejectButton().isEnabled();
    }

    // PHASE 3 - Added in Phase 3
    private static void expectDiplomacy(String code, Runnable action) {
        try { action.run(); throw new AssertionError("Expected " + code); }
        catch (CompletionException failure) {
            assert failure.getCause() instanceof DiplomacyException : failure.getCause();
            assert ((DiplomacyException) failure.getCause()).code().equals(code);
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void expectMatch(String code, Runnable action) {
        try { action.run(); throw new AssertionError("Expected " + code); }
        catch (CompletionException failure) {
            assert failure.getCause() instanceof MatchException : failure.getCause();
            assert ((MatchException) failure.getCause()).code().equals(code);
        }
    }

    // PHASE 3 - Added in Phase 3
    private record Sent(SessionId recipient, PushMessage message) {}

    // PHASE 3 - Added in Phase 3
    private static final class Fixture implements AutoCloseable {
        // PHASE 3 - Added in Phase 3
        private final CopyOnWriteArrayList<Sent> messages = new CopyOnWriteArrayList<>();
        // PHASE 3 - Added in Phase 3
        private final SessionRegistry sessions = new SessionRegistry(Clock.fixed(NOW, ZoneOffset.UTC));
        // PHASE 3 - Added in Phase 3
        private final ClientSession host = sessions.create("Player A");
        // PHASE 3 - Added in Phase 3
        private final ClientSession guest = sessions.create("Player B");
        // PHASE 3 - Added in Phase 3
        private final ClientSession observer;
        // PHASE 3 - Added in Phase 3
        private final LobbyServerService service = new LobbyServerService(
                (recipient, message) -> messages.add(new Sent(recipient, message)),
                civilization.server.map.PredefinedMapCatalog.standard(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        // PHASE 3 - Added in Phase 3
        private final String matchId;

        // PHASE 3 - Added in Phase 3
        private Fixture(boolean includeObserver) {
            observer = includeObserver ? sessions.create("Player C") : null;
            String lobby = service.create(host).join().view().lobbyId().toString();
            service.join(guest, lobby).join();
            if (observer != null) service.join(observer, lobby).join();
            service.selectMap(host, lobby, "emerald-plains").join();
            service.ready(host, lobby, true).join(); service.ready(guest, lobby, true).join();
            if (observer != null) service.ready(observer, lobby, true).join();
            matchId = service.start(host, lobby).join().launch().matchId();
        }

        // PHASE 3 - Added in Phase 3
        private void endHostTurn(long version) {
            service.endTurn(host, new EndTurnRequest(MessageMetadata.createNow(), matchId, version)).join();
        }

        // PHASE 3 - Added in Phase 3
        private PlayerDiplomaticRelation relation(ClientSession first, ClientSession second) {
            return service.match(matchId).project(state -> state.diplomacy().relation(
                    first.playerId(), second.playerId())).join();
        }

        // PHASE 3 - Added in Phase 3
        private civilization.protocol.GameSnapshotDto project(ClientSession viewer) {
            return service.match(matchId).project(state ->
                    new civilization.server.match.FilteredGameStateProjector().project(
                            state, viewer.playerId())).join();
        }

        // PHASE 3 - Added in Phase 3
        private long version() { return service.match(matchId).project(state -> state.stateVersion()).join(); }
        // PHASE 3 - Added in Phase 3
        private int count(Class<?> type, SessionId recipient) { return (int) messages.stream().filter(sent -> sent.recipient().equals(recipient) && type.isInstance(sent.message())).count(); }
        // PHASE 3 - Added in Phase 3
        private DiplomacyStatePush latestRelation(SessionId recipient) { return (DiplomacyStatePush) messages.stream().filter(sent -> sent.recipient().equals(recipient) && sent.message() instanceof DiplomacyStatePush).reduce((first, second) -> second).orElseThrow().message(); }
        // PHASE 3 - Added in Phase 3
        private AllianceRequestPush latestAlliance(SessionId recipient, String requestId) { return (AllianceRequestPush) messages.stream().filter(sent -> sent.recipient().equals(recipient) && sent.message() instanceof AllianceRequestPush request && request.requestId().equals(requestId)).reduce((first, second) -> second).orElseThrow().message(); }
        // PHASE 3 - Added in Phase 3
        @Override public void close() { service.close(); }
    }
}
