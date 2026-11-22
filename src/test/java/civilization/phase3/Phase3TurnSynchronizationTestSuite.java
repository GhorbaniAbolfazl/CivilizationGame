package civilization.phase3;

import civilization.model.economy.ResourceBundle;
import civilization.model.economy.ResourceType;
import civilization.model.technology.UnitProductionOrder;
import civilization.model.unit.UnitType;
import civilization.protocol.EndTurnRequest;
import civilization.protocol.GameStatePush;
import civilization.protocol.MessageMetadata;
import civilization.protocol.PushMessage;
import civilization.protocol.TurnChangedPush;
import civilization.server.lobby.LobbyServerService;
import civilization.server.match.MatchException;
import civilization.server.session.ClientSession;
import civilization.server.session.SessionId;
import civilization.server.session.SessionRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

// PHASE 3 - Added in Phase 3
public final class Phase3TurnSynchronizationTestSuite {
    // PHASE 3 - Added in Phase 3
    private Phase3TurnSynchronizationTestSuite() {}
    // PHASE 3 - Added in Phase 3
    public static void main(String[] args) throws Exception {
        activePlayerAuthorityAndFullPipeline(); simultaneousRequestsCommitOnlyOnce(); actionCannotRaceTurnEnd(); disconnectCannotRaceTurnEnd(); networkDeliveryDoesNotHoldMatchLane();
        System.out.println("Phase3TurnSynchronizationTestSuite: all checks passed");
    }

    // PHASE 3 - Added in Phase 3
    private static void activePlayerAuthorityAndFullPipeline() {
        Fixture f = new Fixture(); Started started = f.start();
        expect("NOT_ACTIVE_PLAYER", () -> f.service.endTurn(f.guest, request(started.matchId(), 1)).join());
        f.service.changeGameState(f.host, started.matchId(), 1, state -> {
            var empire = state.requirePlayer(f.host.playerId()).empire(); empire.units().iterator().next().actionPoints().spend(1);
            empire.townHall().productionQueue().enqueue(new UnitProductionOrder(UnitType.WORKER, ResourceBundle.ZERO, 1));
            state.timedItemEffects().register(f.host.playerId(), "harvest-boost", 1); return null;
        }).join();
        int statePushes = f.count(GameStatePush.class); int turnPushes = f.count(TurnChangedPush.class);
        var result = f.service.endTurn(f.host, request(started.matchId(), 2)).join();
        assert result.stateVersion() == 3; assert result.turnNumber() == 2; assert result.activePlayerId().equals(f.guest.playerId()); assert result.expiredItemEffects().equals(List.of("harvest-boost"));
        assert result.processingTrace().containsAll(List.of("SEASON", "DISASTER_CLEANUP", "NATURAL_DISASTER", "AP_REFRESH", "RESOURCE_PRODUCTION", "TOWN_HALL_QUEUE", "UPKEEP", "STARVATION", "VISIBILITY", "WILDLIFE_COMBAT", "TRIBE_TURN", "HAPPINESS", "ITEM_EXPIRATION"));
        assert f.count(GameStatePush.class) == statePushes + 2; assert f.count(TurnChangedPush.class) == turnPushes + 2;
        var state = f.service.match(started.matchId()).project(value -> value).join(); var host = state.requirePlayer(f.host.playerId()).empire();
        assert host.townHall().stockpile().amountOf(ResourceType.FOOD) < 16; assert host.townHall().productionQueue().isEmpty(); assert state.timedItemEffects().remainingTurns(f.host.playerId(), "harvest-boost") == 0;
        assert host.units().stream().allMatch(unit -> unit.actionPoints().current() == unit.actionPoints().capacity());
        expect("NOT_ACTIVE_PLAYER", () -> f.service.changeGameState(f.host, started.matchId(), 3, ignored -> null).join());
        var next = f.service.endTurn(f.guest, request(started.matchId(), 3)).join(); assert next.activePlayerId().equals(f.host.playerId()); assert next.turnNumber() == 3; f.close();
    }

    // PHASE 3 - Added in Phase 3
    private static void simultaneousRequestsCommitOnlyOnce() {
        Fixture f = new Fixture(); Started started = f.start(); CountDownLatch go = new CountDownLatch(1);
        CompletableFuture<?> first = CompletableFuture.runAsync(() -> { await(go); f.service.endTurn(f.host, request(started.matchId(), 1)).join(); });
        CompletableFuture<?> second = CompletableFuture.runAsync(() -> { await(go); f.service.endTurn(f.host, request(started.matchId(), 1)).join(); });
        go.countDown(); int successes = 0; for (var future : List.of(first, second)) try { future.join(); successes++; } catch (CompletionException ignored) {}
        assert successes == 1; var state = f.service.match(started.matchId()).project(value -> value).join(); assert state.stateVersion() == 2; assert state.activePlayerId().equals(f.guest.playerId()); f.close();
    }

    // PHASE 3 - Added in Phase 3
    private static void actionCannotRaceTurnEnd() {
        Fixture f = new Fixture(); Started started = f.start(); CountDownLatch go = new CountDownLatch(1); var runtime = f.service.match(started.matchId());
        CompletableFuture<?> action = CompletableFuture.runAsync(() -> { await(go); f.service.changeGameState(f.host, started.matchId(), 1, state -> "committed").join(); });
        CompletableFuture<?> end = CompletableFuture.runAsync(() -> { await(go); f.service.endTurn(f.host, request(started.matchId(), 1)).join(); });
        go.countDown(); int successes = 0; for (var future : List.of(action, end)) try { future.join(); successes++; } catch (CompletionException ignored) {}
        assert successes == 1; var state = runtime.project(value -> value).join(); assert state.stateVersion() == 2; f.close();
    }

    // PHASE 3 - Added in Phase 3
    private static void disconnectCannotRaceTurnEnd() {
        Fixture f = new Fixture(); Started started = f.start(); CountDownLatch go = new CountDownLatch(1);
        CompletableFuture<?> end = CompletableFuture.runAsync(() -> { await(go); try { f.service.endTurn(f.host, request(started.matchId(), 1)).join(); } catch (CompletionException ignored) {} });
        CompletableFuture<?> disconnect = CompletableFuture.runAsync(() -> { await(go); f.service.disconnect(f.host.sessionId()).join(); });
        go.countDown(); CompletableFuture.allOf(end, disconnect).join(); var state = f.service.match(started.matchId()).project(value -> value).join();
        assert !state.connectedPlayers().contains(f.host.playerId()); assert state.activePlayerId().equals(f.guest.playerId()); assert state.stateVersion() >= 2 && state.stateVersion() <= 3; f.close();
    }

    // PHASE 3 - Added in Phase 3
    private static void networkDeliveryDoesNotHoldMatchLane() throws Exception {
        AtomicBoolean block = new AtomicBoolean(); CountDownLatch entered = new CountDownLatch(1); CountDownLatch release = new CountDownLatch(1);
        Fixture f = new Fixture((session, message) -> { if (block.get() && message instanceof GameStatePush) { entered.countDown(); await(release); } }); Started started = f.start(); block.set(true);
        CompletableFuture<?> ending = f.service.endTurn(f.host, request(started.matchId(), 1)); assert entered.await(2, TimeUnit.SECONDS);
        CompletableFuture<Long> projection = f.service.match(started.matchId()).project(state -> state.stateVersion()); assert projection.get(500, TimeUnit.MILLISECONDS) == 2;
        release.countDown(); ending.get(2, TimeUnit.SECONDS); f.close();
    }

    // PHASE 3 - Added in Phase 3
    private static EndTurnRequest request(String matchId, long version) { return new EndTurnRequest(MessageMetadata.createNow(), matchId, version); }
    // PHASE 3 - Added in Phase 3
    private static void expect(String code, Runnable action) { try { action.run(); throw new AssertionError("Expected " + code); } catch (CompletionException failure) { Throwable cause = failure.getCause(); assert cause instanceof MatchException; assert ((MatchException) cause).code().equals(code); } }
    // PHASE 3 - Added in Phase 3
    private static void await(CountDownLatch latch) { try { latch.await(); } catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new RuntimeException(failure); } }
    // PHASE 3 - Added in Phase 3
    private record Sent(SessionId session, PushMessage message) {}
    // PHASE 3 - Added in Phase 3
    private record Started(String lobbyId, String matchId) {}

    // PHASE 3 - Added in Phase 3
    private static final class Fixture implements AutoCloseable {
        // PHASE 3 - Added in Phase 3
        private final CopyOnWriteArrayList<Sent> messages = new CopyOnWriteArrayList<>();
        // PHASE 3 - Added in Phase 3
        private final SessionRegistry sessions = new SessionRegistry(Clock.fixed(Instant.parse("2026-09-05T10:00:00Z"), ZoneOffset.UTC));
        // PHASE 3 - Added in Phase 3
        private final ClientSession host = sessions.create("Host");
        // PHASE 3 - Added in Phase 3
        private final ClientSession guest = sessions.create("Guest");
        // PHASE 3 - Added in Phase 3
        private final LobbyServerService service;
        // PHASE 3 - Added in Phase 3
        private Fixture() { this(null); }
        // PHASE 3 - Added in Phase 3
        private Fixture(civilization.server.lobby.LobbyPushGateway extra) { service = new LobbyServerService((session, message) -> { messages.add(new Sent(session, message)); if (extra != null) extra.push(session, message); }); }
        // PHASE 3 - Added in Phase 3
        private Started start() { var lobby = service.create(host).join().view(); String id = lobby.lobbyId().toString(); service.join(guest, id).join(); service.selectMap(host, id, "emerald-plains").join(); service.ready(host, id, true).join(); service.ready(guest, id, true).join(); var result = service.start(host, id).join(); return new Started(id, result.launch().matchId()); }
        // PHASE 3 - Added in Phase 3
        private int count(Class<?> type) { return (int) messages.stream().filter(sent -> type.isInstance(sent.message())).count(); }
        // PHASE 3 - Added in Phase 3
        @Override public void close() { service.close(); }
    }
}
