package civilization.phase3;

import civilization.client.event.ClientMessageRouter;
import civilization.client.controller.LobbyToGameCoordinator;
import civilization.client.state.ClientStateStore;
import civilization.protocol.ChatMessagePush;
import civilization.protocol.GameStartedPush;
import civilization.protocol.GameStatePush;
import civilization.protocol.LobbyStatePush;
import civilization.protocol.PushMessage;
import civilization.server.lobby.LobbyException;
import civilization.server.lobby.LobbyServerService;
import civilization.server.lobby.LobbyView;
import civilization.server.session.ClientSession;
import civilization.server.session.SessionId;
import civilization.server.session.SessionRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletionException;
import civilization.model.economy.ResourceType;
import civilization.model.unit.UnitType;
import civilization.protocol.json.JsonMessageSerializer;
import civilization.protocol.TurnChangedPush;
import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

// PHASE 3 - Added in Phase 3
public final class Phase3LobbyTestSuite {
    // PHASE 3 - Added in Phase 3
    private Phase3LobbyTestSuite() {}

    // PHASE 3 - Added in Phase 3
    public static void main(String[] args) {
        joinHostReadyMapAndPushes(); invalidStartsAreRejected(); disconnectTransfersHost(); chatAndGameTransitionArePushed();
        authoritativeGameInitializationAndFogFiltering(); clientTransitionWaitsForFilteredState();
        System.out.println("Phase3LobbyTestSuite: all checks passed");
    }

    // PHASE 3 - Added in Phase 3
    private static void joinHostReadyMapAndPushes() {
        Fixture f = new Fixture(); LobbyView created = f.service.create(f.host).join().view(); String id = created.lobbyId().toString();
        assert created.hostPlayerId().equals(f.host.playerId()); assert created.members().size() == 1; assert f.count(LobbyStatePush.class) == 1;
        LobbyView joined = f.service.join(f.guest, id).join().view(); assert joined.members().size() == 2; assert f.count(LobbyStatePush.class) == 3;
        LobbyView ready = f.service.ready(f.guest, id, true).join().view(); assert ready.members().stream().filter(m -> m.playerId().equals(f.guest.playerId())).findFirst().orElseThrow().ready();
        expect("HOST_ONLY", () -> f.service.selectMap(f.guest, id, "emerald-plains").join());
        int before = f.count(LobbyStatePush.class); LobbyView selected = f.service.selectMap(f.host, id, "emerald-plains").join().view();
        assert selected.selectedMapId().equals("emerald-plains"); assert f.count(LobbyStatePush.class) == before + 2;
        LobbyStatePush push = (LobbyStatePush) f.messages.get(f.messages.size() - 1).message(); assert push.snapshot().availableMaps().size() == 3;
        f.close();
    }

    // PHASE 3 - Added in Phase 3
    private static void invalidStartsAreRejected() {
        Fixture f = new Fixture(); String id = f.service.create(f.host).join().view().lobbyId().toString(); f.service.join(f.guest, id).join();
        expect("HOST_ONLY", () -> f.service.start(f.guest, id).join()); expect("MAP_REQUIRED", () -> f.service.start(f.host, id).join());
        f.service.selectMap(f.host, id, "emerald-plains").join(); expect("PLAYERS_NOT_READY", () -> f.service.start(f.host, id).join());
        f.service.ready(f.host, id, true).join(); f.service.ready(f.guest, id, true).join(); assert f.service.start(f.host, id).join().view().lifecycle().name().equals("STARTED");
        expect("LOBBY_NOT_OPEN", () -> f.service.ready(f.host, id, false).join()); f.close();
    }

    // PHASE 3 - Added in Phase 3
    private static void disconnectTransfersHost() {
        Fixture f = new Fixture(); String id = f.service.create(f.host).join().view().lobbyId().toString(); f.service.join(f.guest, id).join();
        f.service.disconnect(f.host.sessionId()).join(); LobbyView view = f.service.view(id).join(); assert view.members().size() == 1; assert view.hostPlayerId().equals(f.guest.playerId());
        f.service.disconnect(f.guest.sessionId()).join(); expect("LOBBY_NOT_FOUND", () -> f.service.view(id).join()); f.close();
    }

    // PHASE 3 - Added in Phase 3
    private static void chatAndGameTransitionArePushed() {
        Fixture f = new Fixture(); String id = f.service.create(f.host).join().view().lobbyId().toString(); f.service.join(f.guest, id).join();
        ChatMessagePush chat = f.service.chat(f.guest, id, "hello").join(); assert chat.createdAt().equals(Fixture.NOW); assert f.count(ChatMessagePush.class) == 2;
        f.service.selectMap(f.host, id, "iron-crown").join(); f.service.ready(f.host, id, true).join(); f.service.ready(f.guest, id, true).join(); f.service.start(f.host, id).join();
        assert f.count(GameStartedPush.class) == 2; assert f.count(GameStatePush.class) == 2;
        assert f.count(TurnChangedPush.class) == 2;
        ClientStateStore store = new ClientStateStore(Runnable::run); ClientMessageRouter router = new ClientMessageRouter(store, Runnable::run);
        f.messages.stream().filter(sent -> sent.session().equals(f.guest.sessionId())).map(Sent::message).forEach(router::onPush);
        assert store.lobby().orElseThrow().snapshot().lifecycle().equals("STARTED"); assert store.gameStarted().isPresent();
        GameStatePush personalized = store.gameState().orElseThrow(); assert personalized.snapshot().viewerPlayerId().equals(f.guest.playerId().toString());
        assert !personalized.snapshot().viewerAttributes().containsKey("otherPlayerSpawns"); f.close();
    }

    // PHASE 3 - Added in Phase 3
    private static void authoritativeGameInitializationAndFogFiltering() {
        Fixture f = new Fixture(); String id = f.service.create(f.host).join().view().lobbyId().toString(); f.service.join(f.guest, id).join();
        f.service.selectMap(f.host, id, "emerald-plains").join(); f.service.ready(f.host, id, true).join(); f.service.ready(f.guest, id, true).join();
        var start = f.service.start(f.host, id).join(); var game = f.service.match(start.launch().matchId()).project(state -> state).join();
        assert game.turn().turnNumber() == 1; assert game.turn().phase().name().equals("PLAYER_ACTIONS"); assert game.activePlayerId().equals(f.host.playerId()); assert game.players().size() == 2;
        var host = game.requirePlayer(f.host.playerId()).empire(); var guest = game.requirePlayer(f.guest.playerId()).empire();
        assert !host.townHall().position().equals(guest.townHall().position()); assert host.townHall().position().distanceTo(guest.townHall().position()) >= 6;
        // PHASE 3 - Added in Phase 3
        for (var empire : List.of(host, guest)) {
            assert empire.townHall().stockpile().amountOf(ResourceType.FOOD) == 16; assert empire.townHall().stockpile().amountOf(ResourceType.WOOD) == 18;
            assert empire.townHall().stockpile().amountOf(ResourceType.STONE) == 8; assert empire.townHall().stockpile().amountOf(ResourceType.IRON) == 0;
            assert empire.units().size() == 5; assert empire.activeUnitCount(UnitType.BUILDER) == 2; assert empire.activeUnitCount(UnitType.WORKER) == 2; assert empire.activeUnitCount(UnitType.EXPLORER) == 1;
            assert empire.visibility().currentlyVisibleHexes().size() < game.map().size(); assert empire.visibility().currentlyVisibleHexes().contains(empire.townHall().position());
        }
        GameStatePush hostPush = f.gameStateFor(f.host.sessionId()); GameStatePush guestPush = f.gameStateFor(f.guest.sessionId());
        assert hostPush.snapshot().visibleHexes().size() < game.map().size(); assert guestPush.snapshot().visibleHexes().size() < game.map().size();
        assert hostPush.snapshot().viewerPlayerId().equals(f.host.playerId().toString()); assert guestPush.snapshot().viewerPlayerId().equals(f.guest.playerId().toString());
        assert hostPush.snapshot().visibleBuildings().stream().noneMatch(building -> building.ownerPlayerId().equals(f.guest.playerId().toString()));
        assert guestPush.snapshot().visibleBuildings().stream().noneMatch(building -> building.ownerPlayerId().equals(f.host.playerId().toString()));
        assert hostPush.snapshot().visibleUnits().stream().allMatch(unit -> unit.ownerPlayerId().equals(f.host.playerId().toString()));
        assert guestPush.snapshot().visibleUnits().stream().allMatch(unit -> unit.ownerPlayerId().equals(f.guest.playerId().toString()));
        // PHASE 3 - Added in Phase 3
        assert hostPush.snapshot().visibleHexes().stream().allMatch(hex -> host.visibility().isDiscovered(new civilization.model.map.HexCoord(hex.q(), hex.r())));
        assert guestPush.snapshot().visibleHexes().stream().allMatch(hex -> guest.visibility().isDiscovered(new civilization.model.map.HexCoord(hex.q(), hex.r())));
        var serializer = new JsonMessageSerializer(); assert serializer.deserialize(serializer.serialize(guestPush)).equals(guestPush);
        f.close();
    }

    // PHASE 3 - Added in Phase 3
    private static void clientTransitionWaitsForFilteredState() {
        Fixture f = new Fixture(); String id = f.service.create(f.host).join().view().lobbyId().toString(); f.service.join(f.guest, id).join();
        f.service.selectMap(f.host, id, "twin-rivers").join(); f.service.ready(f.host, id, true).join(); f.service.ready(f.guest, id, true).join(); f.service.start(f.host, id).join();
        ClientStateStore store = new ClientStateStore(task -> {
            // PHASE 3 - Added in Phase 3
            if (SwingUtilities.isEventDispatchThread()) task.run(); else try { SwingUtilities.invokeAndWait(task); } catch (Exception failure) { throw new RuntimeException(failure); }
        });
        ClientMessageRouter router = new ClientMessageRouter(store, task -> SwingUtilities.invokeLater(task)); AtomicInteger opened = new AtomicInteger(); AtomicBoolean onEdt = new AtomicBoolean();
        new LobbyToGameCoordinator(store, panel -> { opened.incrementAndGet(); onEdt.set(SwingUtilities.isEventDispatchThread()); });
        GameStartedPush started = (GameStartedPush) f.messages.stream().filter(sent -> sent.session().equals(f.guest.sessionId()) && sent.message() instanceof GameStartedPush).findFirst().orElseThrow().message();
        GameStatePush state = f.gameStateFor(f.guest.sessionId()); router.onPush(started); assert opened.get() == 0; router.onPush(state);
        assert opened.get() == 1; assert onEdt.get(); f.close();
    }

    // PHASE 3 - Added in Phase 3
    private static void expect(String code, Runnable operation) {
        try { operation.run(); throw new AssertionError("Expected " + code); }
        catch (RuntimeException failure) {
            // PHASE 3 - Added in Phase 3
            Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
            assert cause instanceof LobbyException : cause; assert ((LobbyException) cause).code().equals(code) : cause;
        }
    }

    // PHASE 3 - Added in Phase 3
    private record Sent(SessionId session, PushMessage message) {}

    // PHASE 3 - Added in Phase 3
    private static final class Fixture implements AutoCloseable {
        // PHASE 3 - Added in Phase 3
        private static final Instant NOW = Instant.parse("2026-09-01T10:15:30Z");
        // PHASE 3 - Added in Phase 3
        private final CopyOnWriteArrayList<Sent> messages = new CopyOnWriteArrayList<>();
        // PHASE 3 - Added in Phase 3
        private final SessionRegistry sessions = new SessionRegistry(Clock.fixed(NOW, ZoneOffset.UTC));
        // PHASE 3 - Added in Phase 3
        private final ClientSession host = sessions.create("Host");
        // PHASE 3 - Added in Phase 3
        private final ClientSession guest = sessions.create("Guest");
        // PHASE 3 - Added in Phase 3
        private final LobbyServerService service = new LobbyServerService((session, message) -> messages.add(new Sent(session, message)), civilization.server.map.PredefinedMapCatalog.standard(), Clock.fixed(NOW, ZoneOffset.UTC));
        // PHASE 3 - Added in Phase 3
        private int count(Class<?> type) { return (int) messages.stream().filter(sent -> type.isInstance(sent.message())).count(); }
        // PHASE 3 - Added in Phase 3
        private GameStatePush gameStateFor(SessionId session) { return (GameStatePush) messages.stream().filter(sent -> sent.session().equals(session) && sent.message() instanceof GameStatePush).findFirst().orElseThrow().message(); }
        // PHASE 3 - Added in Phase 3
        @Override public void close() { service.close(); }
    }
}
