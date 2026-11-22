package civilization.phase3;

import civilization.client.event.ClientMessageRouter;
import civilization.client.event.SwingUiEventDispatcher;
import civilization.client.state.ClientGameRenderModel;
import civilization.client.state.ClientStateStore;
import civilization.protocol.GameStatePush;
import civilization.protocol.PushMessage;
import civilization.protocol.TurnChangedPush;
import civilization.protocol.EndTurnRequest;
import civilization.protocol.HexCoordinate;
import civilization.protocol.MessageMetadata;
import civilization.protocol.MoveRequest;
import civilization.protocol.BuildRequest;
import civilization.protocol.json.JsonMessageSerializer;
import civilization.server.lobby.LobbyServerService;
import civilization.server.session.ClientSession;
import civilization.server.session.SessionId;
import civilization.server.session.SessionRegistry;
import javax.swing.SwingUtilities;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

// PHASE 3 - Added in Phase 3
public final class Phase3StateBroadcastTestSuite {
    // PHASE 3 - Added in Phase 3
    private Phase3StateBroadcastTestSuite() {}
    // PHASE 3 - Added in Phase 3
    public static void main(String[] args) throws Exception {
        actionBroadcastIsFilteredPerPlayer(); rejectedActionDoesNotBroadcast();
        movementIsAuthoritativeFilteredAndOwnershipChecked();
        standardConstructionUsesServerRulesAndBroadcasts();
        clientModelEmitsMonotonicEdtEventsWithoutBlockingListenerThread();
        System.out.println("Phase3StateBroadcastTestSuite: all checks passed");
    }

    // PHASE 3 - Added in Phase 3
    private static void actionBroadcastIsFilteredPerPlayer() {
        Fixture f = new Fixture(); String matchId = f.start(); int turnsBefore = f.count(TurnChangedPush.class);
        var server = f.service.match(matchId).project(state -> state).join(); var hostEmpire = server.requirePlayer(f.host.playerId()).empire(); var guestEmpire = server.requirePlayer(f.guest.playerId()).empire();
        String hiddenHall = hostEmpire.townHall().id().value().toString(); List<String> hiddenUnits = hostEmpire.units().stream().map(unit -> unit.id().value().toString()).toList();
        String guestHall = guestEmpire.townHall().id().value().toString(); List<String> guestUnits = guestEmpire.units().stream().map(unit -> unit.id().value().toString()).toList();
        var hiddenResourceTile = server.map().tiles().stream().filter(tile -> !guestEmpire.visibility().isCurrentlyVisible(tile.coordinate())).filter(tile -> !tile.resourceDeposits().isEmpty()).findFirst().orElseThrow();
        // PHASE 3 - Added in Phase 3
        f.service.changeGameState(f.host, matchId, 1, state -> {
            state.requirePlayer(f.host.playerId()).empire().units().iterator().next().actionPoints().spend(1);
            state.requirePlayer(f.guest.playerId()).empire().visibility().discover(hiddenResourceTile.coordinate());
            hiddenResourceTile.resourceDeposits().values().iterator().next().extract(1);
            return "moved";
        }).join();
        GameStatePush host = f.latest(f.host.sessionId()); GameStatePush guest = f.latest(f.guest.sessionId());
        assert host.stateVersion() == 2 && guest.stateVersion() == 2; assert f.countVersion(2) == 2; assert f.count(TurnChangedPush.class) == turnsBefore;
        assert host.snapshot().viewerPlayerId().equals(f.host.playerId().toString()); assert guest.snapshot().viewerPlayerId().equals(f.guest.playerId().toString());
        assert host.snapshot().visibleBuildings().stream().anyMatch(building -> building.buildingId().equals(hiddenHall));
        assert host.snapshot().visibleBuildings().stream().noneMatch(building -> building.buildingId().equals(guestHall));
        assert host.snapshot().visibleUnits().stream().noneMatch(unit -> guestUnits.contains(unit.unitId()));
        assert guest.snapshot().visibleBuildings().stream().noneMatch(building -> building.buildingId().equals(hiddenHall));
        assert guest.snapshot().visibleUnits().stream().noneMatch(unit -> hiddenUnits.contains(unit.unitId()));
        var rememberedHiddenHex = guest.snapshot().visibleHexes().stream().filter(hex -> hex.q() == hiddenResourceTile.coordinate().q() && hex.r() == hiddenResourceTile.coordinate().r()).findFirst().orElseThrow();
        assert !rememberedHiddenHex.currentlyVisible(); assert rememberedHiddenHex.resources().isEmpty();
        assert guest.snapshot().visibleHexes().stream().allMatch(hex -> guestEmpire.visibility().isDiscovered(new civilization.model.map.HexCoord(hex.q(), hex.r())));
        assert guest.snapshot().viewerResources().equals(guestEmpire.townHall().stockpile().amounts().asMap().entrySet().stream().collect(java.util.stream.Collectors.toMap(entry -> entry.getKey().name(), java.util.Map.Entry::getValue)));
        assert guest.snapshot().viewerAttributes().keySet().equals(java.util.Set.of("turnPhase", "username"));
        String guestJson = new JsonMessageSerializer().serialize(guest); assert !guestJson.contains(hiddenHall); assert hiddenUnits.stream().noneMatch(guestJson::contains);
        f.close();
    }

    // PHASE 3 - Added in Phase 3
    private static void rejectedActionDoesNotBroadcast() {
        Fixture f = new Fixture(); String matchId = f.start(); int before = f.count(GameStatePush.class);
        try {
            f.service.changeGameState(f.guest, matchId, 1, state -> "illegal").join();
            throw new AssertionError("Non-active player action should be rejected");
        } catch (java.util.concurrent.CompletionException expected) {
            assert expected.getCause() instanceof civilization.server.match.MatchException;
        }
        assert f.count(GameStatePush.class) == before;
        assert f.service.match(matchId).project(state -> state.stateVersion()).join() == 1;
        f.close();
    }

    // PHASE 3 - Added in Phase 3
    private static void movementIsAuthoritativeFilteredAndOwnershipChecked() {
        Fixture f = new Fixture();
        String matchId = f.start();
        var game = f.service.match(matchId).project(state -> state).join();
        var hostEmpire = game.requirePlayer(f.host.playerId()).empire();
        var unit = hostEmpire.units().iterator().next();
        // PHASE 3 - Added in Phase 3
        assert unit.createdAt() != null;
        assert hostEmpire.townHall().createdAt() != null;
        var destination = unit.position().neighbors().stream()
                .filter(game.map()::contains)
                .filter(candidate -> game.players().values().stream()
                        .flatMap(player -> player.empire().units().stream())
                        .noneMatch(other -> other.position().equals(candidate)))
                .filter(candidate -> game.map().requireTile(candidate).terrain()
                        != civilization.model.map.TerrainType.MOUNTAIN_RANGE)
                .findFirst().orElseThrow();
        String unitId = unit.id().value().toString();
        // PHASE 3 - Added in Phase 3
        f.service.move(f.host, new MoveRequest(MessageMetadata.createNow(), matchId, unitId,
                new HexCoordinate(destination.q(), destination.r()), 1L)).join();
        GameStatePush host = f.latest(f.host.sessionId());
        GameStatePush guest = f.latest(f.guest.sessionId());
        assert host.stateVersion() == 2L;
        assert host.snapshot().visibleUnits().stream().anyMatch(value -> value.unitId().equals(unitId)
                && value.q() == destination.q() && value.r() == destination.r());
        assert guest.snapshot().visibleUnits().stream().noneMatch(value -> value.unitId().equals(unitId))
                : "Movement broadcast leaked a hidden opposing unit";
        // PHASE 3 - Added in Phase 3
        f.service.endTurn(f.host, new EndTurnRequest(MessageMetadata.createNow(), matchId, 2L)).join();
        long guestVersion = f.service.match(matchId).project(state -> state.stateVersion()).join();
        try {
            f.service.move(f.guest, new MoveRequest(MessageMetadata.createNow(), matchId, unitId,
                    new HexCoordinate(destination.q(), destination.r()), guestVersion)).join();
            throw new AssertionError("A player moved another player's unit");
        } catch (java.util.concurrent.CompletionException expected) {
            assert expected.getCause() instanceof civilization.server.match.MatchException failure
                    && failure.code().equals("UNIT_NOT_OWNED");
        }
        f.close();
    }

    // PHASE 3 - Added in Phase 3
    private static void standardConstructionUsesServerRulesAndBroadcasts() {
        Fixture f = new Fixture();
        String matchId = f.start();
        // PHASE 3 - Added in Phase 3
        record Prepared(String builderId, civilization.model.map.HexCoord target) { }
        // PHASE 3 - Added in Phase 3
        Prepared prepared = f.service.changeGameState(f.host, matchId, 1L, state -> {
            var empire = state.requirePlayer(f.host.playerId()).empire();
            var builder = empire.units().stream()
                    .filter(unit -> unit.type() == civilization.model.unit.UnitType.BUILDER)
                    .findFirst().orElseThrow();
            var target = state.map().tiles().stream()
                    .filter(tile -> tile.hasAvailableNaturalResource(
                            civilization.model.map.NaturalResourceType.WHEAT)
                            || tile.hasAvailableNaturalResource(
                            civilization.model.map.NaturalResourceType.RICE))
                    .filter(tile -> state.players().values().stream()
                            .flatMap(player -> player.empire().allBuildings().stream())
                            .noneMatch(building -> building.position().equals(tile.coordinate())))
                    .findFirst().orElseThrow().coordinate();
            builder.moveTo(target);
            empire.border().claimAll(java.util.Set.of(target), state.map());
            empire.visibility().discover(target);
            return new Prepared(builder.id().value().toString(), target);
        }).join();
        // PHASE 3 - Added in Phase 3
        f.service.build(f.host, new BuildRequest(MessageMetadata.createNow(), matchId,
                prepared.builderId(), "FARM",
                new HexCoordinate(prepared.target().q(), prepared.target().r()), 2L)).join();
        GameStatePush host = f.latest(f.host.sessionId());
        assert host.stateVersion() == 3L;
        assert host.snapshot().visibleBuildings().stream().anyMatch(building ->
                building.ownerPlayerId().equals(f.host.playerId().toString())
                        && building.buildingType().equals("FARM")
                        && building.q() == prepared.target().q()
                        && building.r() == prepared.target().r());
        f.close();
    }

    // PHASE 3 - Added in Phase 3
    private static void clientModelEmitsMonotonicEdtEventsWithoutBlockingListenerThread() throws Exception {
        Fixture f = new Fixture(); String matchId = f.start(); f.service.changeGameState(f.host, matchId, 1, state -> "changed").join();
        GameStatePush versionTwo = f.latest(f.guest.sessionId()); GameStatePush stale = f.version(f.guest.sessionId(), 1);
        ClientStateStore store = new ClientStateStore(new SwingUiEventDispatcher()); ClientMessageRouter router = new ClientMessageRouter(store, new SwingUiEventDispatcher());
        CountDownLatch event = new CountDownLatch(1); AtomicBoolean onEdt = new AtomicBoolean(); AtomicReference<ClientGameRenderModel> received = new AtomicReference<>(); AtomicInteger count = new AtomicInteger();
        store.addGameModelListener(model -> { onEdt.set(SwingUtilities.isEventDispatchThread()); received.set(model); count.incrementAndGet(); event.countDown(); });
        long started = System.nanoTime(); Thread listener = new Thread(() -> router.onPush(versionTwo), "test-client-listener"); listener.start(); listener.join(500);
        assert !listener.isAlive(); assert TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 500; assert event.await(2, TimeUnit.SECONDS);
        assert onEdt.get(); assert received.get().stateVersion() == 2; assert store.gameModel().orElseThrow().snapshot().viewerPlayerId().equals(f.guest.playerId().toString());
        router.onPush(stale); SwingUtilities.invokeAndWait(() -> {}); assert store.gameModel().orElseThrow().stateVersion() == 2; assert count.get() == 1; f.close();
    }

    // PHASE 3 - Added in Phase 3
    private record Sent(SessionId sessionId, PushMessage message) {}
    // PHASE 3 - Added in Phase 3
    private static final class Fixture implements AutoCloseable {
        // PHASE 3 - Added in Phase 3
        private final CopyOnWriteArrayList<Sent> messages = new CopyOnWriteArrayList<>();
        // PHASE 3 - Added in Phase 3
        private final SessionRegistry sessions = new SessionRegistry(Clock.fixed(Instant.parse("2026-09-05T12:00:00Z"), ZoneOffset.UTC));
        // PHASE 3 - Added in Phase 3
        private final ClientSession host = sessions.create("Player A");
        // PHASE 3 - Added in Phase 3
        private final ClientSession guest = sessions.create("Player B");
        // PHASE 3 - Added in Phase 3
        private final LobbyServerService service = new LobbyServerService((session, message) -> messages.add(new Sent(session, message)));
        // PHASE 3 - Added in Phase 3
        private String start() { String lobby = service.create(host).join().view().lobbyId().toString(); service.join(guest, lobby).join(); service.selectMap(host, lobby, "iron-crown").join(); service.ready(host, lobby, true).join(); service.ready(guest, lobby, true).join(); return service.start(host, lobby).join().launch().matchId(); }
        // PHASE 3 - Added in Phase 3
        private GameStatePush latest(SessionId session) { return (GameStatePush) messages.stream().filter(sent -> sent.sessionId().equals(session) && sent.message() instanceof GameStatePush).reduce((first, second) -> second).orElseThrow().message(); }
        // PHASE 3 - Added in Phase 3
        private GameStatePush version(SessionId session, long version) { return (GameStatePush) messages.stream().filter(sent -> sent.sessionId().equals(session) && sent.message() instanceof GameStatePush push && push.stateVersion() == version).findFirst().orElseThrow().message(); }
        // PHASE 3 - Added in Phase 3
        private int count(Class<?> type) { return (int) messages.stream().filter(sent -> type.isInstance(sent.message())).count(); }
        // PHASE 3 - Added in Phase 3
        private int countVersion(long version) { return (int) messages.stream().filter(sent -> sent.message() instanceof GameStatePush push && push.stateVersion() == version).count(); }
        // PHASE 3 - Added in Phase 3
        @Override public void close() { service.close(); }
    }
}
