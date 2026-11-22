package civilization.phase3;

import civilization.application.factory.UnitFactory;
import civilization.client.event.ClientMessageRouter;
import civilization.client.event.SwingUiEventDispatcher;
import civilization.client.state.ClientStateStore;
import civilization.client.net.ClientMessageListener;
import civilization.client.net.NetworkConnectionState;
import civilization.client.net.NetworkManager;
import civilization.network.transport.TcpServer;
import civilization.model.map.HexCoord;
import civilization.model.random.DiceSource;
import civilization.model.unit.MilitaryUnit;
import civilization.model.unit.Unit;
import civilization.model.unit.UnitType;
import civilization.protocol.AttackRequest;
import civilization.protocol.BattleReportPush;
import civilization.protocol.EndTurnRequest;
import civilization.protocol.GameStatePush;
import civilization.protocol.MessageMetadata;
import civilization.protocol.PushMessage;
import civilization.protocol.RequestAcceptedResponse;
import civilization.protocol.WelcomeResponse;
import civilization.protocol.HelloRequest;
import civilization.protocol.json.JsonMessageSerializer;
import civilization.protocol.json.ProtocolException;
import civilization.rules.DefaultRulesetFactory;
import civilization.server.combat.PvpCombatResult;
import civilization.server.lobby.LobbyServerService;
import civilization.server.lobby.LobbyServerNetworkAdapter;
import civilization.server.map.PredefinedMapCatalog;
import civilization.server.match.MatchException;
import civilization.server.match.MultiplayerGameState;
import civilization.server.session.ClientSession;
import civilization.server.session.SessionId;
import civilization.server.session.SessionRegistry;
import civilization.view.game.GamePanel;

import javax.swing.SwingUtilities;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

// PHASE 3 - Added in Phase 3
public final class Phase3PvpCombatTestSuite {
    // PHASE 3 - Added in Phase 3
    private static final Instant NOW = Instant.parse("2031-04-05T06:07:08.123456Z");
    // PHASE 3 - Added in Phase 3
    private Phase3PvpCombatTestSuite() {}

    // PHASE 3 - Added in Phase 3
    public static void main(String[] args) throws Exception {
        deterministicMeleeUsesExistingDiceDamageAndDeathRules();
        serverRejectsTurnOwnershipDiplomacyTargetRangeAndApViolations();
        rangedCombatAndExactStructureDamageAreAuthoritative();
        depletedStructureUsesExistingDestructionLifecycle();
        forgedClientResultsAreRejectedByProtocol();
        attackRequestTravelsOverRawTcp();
        defenderReportIsDeliveredOnEdtAtTurnStart();
        System.out.println("Phase3PvpCombatTestSuite: all checks passed");
    }

    // PHASE 3 - Added in Phase 3
    private static void deterministicMeleeUsesExistingDiceDamageAndDeathRules() {
        try (Fixture fixture = new Fixture(6, 1, 5, 1)) {
            Setup setup = fixture.setupMilitary(1,
                    List.of(UnitType.SWORDSMAN, UnitType.CAVALRY),
                    List.of(UnitType.SWORDSMAN, UnitType.CAVALRY));
            int statePushes = fixture.count(GameStatePush.class);
            var accepted = fixture.service.attack(fixture.host, new AttackRequest(
                    MessageMetadata.createNow(), fixture.matchId, setup.attackerIds(),
                    setup.targetId(), 1L)).join();
            PvpCombatResult result = accepted.combat();
            assert accepted.stateVersion() == 2L;
            assert result.dice().attackerRawRolls().equals(List.of(6, 1));
            assert result.dice().defenderRawRolls().equals(List.of(5, 1));
            assert result.dice().attackerSortedRolls().equals(List.of(6, 1));
            assert result.dice().defenderSortedRolls().equals(List.of(5, 1));
            assert result.dice().hitsOnAttacker() == 1;
            assert result.dice().hitsOnDefender() == 1;
            assert result.damage().stream().filter(record -> record.removed()
                    && record.entityKind().equals("SWORDSMAN")).count() == 2;
            assert result.report().lostUnitIds().size() == 2;
            assert fixture.count(BattleReportPush.class, fixture.host.sessionId()) == 1;
            assert fixture.count(BattleReportPush.class, fixture.guest.sessionId()) == 0;
            assert fixture.count(GameStatePush.class) == statePushes + 2;

            fixture.service.match(fixture.matchId).project(state -> {
                var host = state.requirePlayer(fixture.host.playerId()).empire();
                var guest = state.requirePlayer(fixture.guest.playerId()).empire();
                assert host.units().stream().noneMatch(unit -> id(unit).equals(setup.attackerIds().get(0)));
                assert guest.units().stream().noneMatch(unit -> id(unit).equals(setup.targetId()));
                assert host.units().stream().map(MilitaryUnit.class::cast)
                        .allMatch(unit -> unit.actionPoints().current()
                                == unit.actionPoints().capacity() - 1);
                assert guest.units().stream().map(MilitaryUnit.class::cast)
                        .allMatch(unit -> unit.actionPoints().current()
                                == unit.actionPoints().capacity());
                return null;
            }).join();

            fixture.service.endTurn(fixture.host, new EndTurnRequest(
                    MessageMetadata.createNow(), fixture.matchId, 2L)).join();
            BattleReportPush defenderReport = fixture.latestReport(fixture.guest.sessionId());
            assert defenderReport.attackerRolls().equals(List.of(6, 1));
            assert defenderReport.defenderRolls().equals(List.of(5, 1));
            assert defenderReport.lostUnitIds().size() == 2;
            assert defenderReport.createdAt().equals(NOW);
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void serverRejectsTurnOwnershipDiplomacyTargetRangeAndApViolations() {
        try (Fixture fixture = new Fixture(6, 1)) {
            Setup setup = fixture.setupMilitary(1, List.of(UnitType.SWORDSMAN),
                    List.of(UnitType.SWORDSMAN), false);
            expect("NOT_AT_WAR", () -> fixture.attack(fixture.host, setup, 1L));
            assert fixture.version() == 1L;
            fixture.service.match(fixture.matchId).project(state -> {
                state.diplomacy().setAllied(fixture.host.playerId(), fixture.guest.playerId());
                return null;
            }).join();
            expect("NOT_AT_WAR", () -> fixture.attack(fixture.host, setup, 1L));
            fixture.declareWar();
            expect("NOT_ACTIVE_PLAYER", () -> fixture.service.attack(fixture.guest,
                    new AttackRequest(MessageMetadata.createNow(), fixture.matchId,
                            List.of(setup.targetId()), setup.attackerIds().get(0), 1L)).join());
            expect("ATTACKER_NOT_OWNED", () -> fixture.service.attack(fixture.host,
                    new AttackRequest(MessageMetadata.createNow(), fixture.matchId,
                            List.of(setup.targetId()), setup.targetId(), 1L)).join());
            expect("TARGET_UNAVAILABLE", () -> fixture.service.attack(fixture.host,
                    new AttackRequest(MessageMetadata.createNow(), fixture.matchId,
                            setup.attackerIds(), "00000000-0000-0000-0000-000000000099", 1L)).join());
            expect("DUPLICATE_ATTACKER", () -> fixture.service.attack(fixture.host,
                    new AttackRequest(MessageMetadata.createNow(), fixture.matchId,
                            List.of(setup.attackerIds().get(0), setup.attackerIds().get(0)),
                            setup.targetId(), 1L)).join());
            assert fixture.version() == 1L;
        }

        try (Fixture fixture = new Fixture(6, 1)) {
            Setup setup = fixture.setupMilitary(2, List.of(UnitType.SWORDSMAN),
                    List.of(UnitType.SWORDSMAN));
            expect("TARGET_OUT_OF_RANGE", () -> fixture.attack(fixture.host, setup, 1L));
            assert fixture.version() == 1L;
        }

        CountingDice rejectedDice = new CountingDice();
        try (Fixture fixture = new Fixture(rejectedDice)) {
            Setup setup = fixture.setupMilitary(1,
                    List.of(UnitType.SWORDSMAN, UnitType.CAVALRY),
                    List.of(UnitType.SWORDSMAN));
            fixture.service.match(fixture.matchId).project(state -> {
                MilitaryUnit unit = state.requirePlayer(fixture.host.playerId()).empire()
                        .units().stream().filter(candidate -> id(candidate)
                                .equals(setup.attackerIds().get(1)))
                        .map(MilitaryUnit.class::cast).findFirst().orElseThrow();
                unit.actionPoints().spend(unit.actionPoints().current());
                return null;
            }).join();
            expect("INSUFFICIENT_AP", () -> fixture.attack(fixture.host, setup, 1L));
            assert fixture.version() == 1L;
            assert rejectedDice.rolls == 0;
            assert fixture.service.match(fixture.matchId).project(state ->
                    state.requirePlayer(fixture.host.playerId()).empire().units().stream()
                            .filter(unit -> id(unit).equals(setup.attackerIds().get(0)))
                            .findFirst().orElseThrow().actionPoints().current()).join() == 2;
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void rangedCombatAndExactStructureDamageAreAuthoritative() {
        try (Fixture fixture = new Fixture(6, 1)) {
            Setup ranged = fixture.setupMilitary(2, List.of(UnitType.ARCHER),
                    List.of(UnitType.ARCHER));
            PvpCombatResult result = fixture.attack(fixture.host, ranged, 1L).combat();
            assert result.dice().attackerRawRolls().equals(List.of(6));
            assert result.dice().defenderRawRolls().equals(List.of(1));
            assert result.participatingAttackers().size() == 1;
        }
        assertStructureDamage(UnitType.SWORDSMAN, 1, 10);
        assertStructureDamage(UnitType.ARCHER, 2, 6);
        assertStructureDamage(UnitType.CAVALRY, 1, 9);
    }

    // PHASE 3 - Added in Phase 3
    private static void assertStructureDamage(UnitType type, int distance, int expected) {
        CountingDice dice = new CountingDice();
        try (Fixture fixture = new Fixture(dice)) {
            Setup setup = fixture.setupStructure(distance, type);
            int before = fixture.service.match(fixture.matchId).project(state ->
                    state.requirePlayer(fixture.guest.playerId()).empire().townHall()
                            .health().current()).join();
            PvpCombatResult result = fixture.attack(fixture.host, setup, 1L).combat();
            assert result.report().structureDamage() == expected;
            assert result.dice().attackerRawRolls().isEmpty();
            assert dice.rolls == 0;
            int after = fixture.service.match(fixture.matchId).project(state ->
                    state.requirePlayer(fixture.guest.playerId()).empire().townHall()
                            .health().current()).join();
            assert before - after == expected;
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void depletedStructureUsesExistingDestructionLifecycle() {
        try (Fixture fixture = new Fixture(new CountingDice())) {
            Setup setup = fixture.setupStructure(1, UnitType.SWORDSMAN);
            fixture.service.match(fixture.matchId).project(state -> {
                var hall = state.requirePlayer(fixture.guest.playerId()).empire().townHall();
                hall.health().damage(hall.health().current() - 5);
                return null;
            }).join();
            PvpCombatResult result = fixture.attack(fixture.host, setup, 1L).combat();
            assert result.report().structureDamage() == 5;
            assert result.damage().get(0).removed();
            assert fixture.service.match(fixture.matchId).project(state ->
                    state.requirePlayer(fixture.guest.playerId()).empire().townHall()
                            .status().name()).join().equals("DESTROYED");
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void forgedClientResultsAreRejectedByProtocol() {
        JsonMessageSerializer serializer = new JsonMessageSerializer();
        String valid = serializer.serialize(new AttackRequest(MessageMetadata.createNow(),
                "match-1", List.of("unit-1"), "target-1", 1L));
        String forged = valid.substring(0, valid.length() - 1)
                + ",\"attackerRolls\":[6],\"claimedDamage\":999}";
        try {
            serializer.deserialize(forged);
            throw new AssertionError("Forged combat result fields were accepted");
        } catch (ProtocolException expected) {
            assert expected.code() == ProtocolException.Code.INVALID_MESSAGE;
        }
        assert !valid.contains("Roll") && !valid.contains("damage") && !valid.contains("result");
    }

    // PHASE 3 - Added in Phase 3
    private static void attackRequestTravelsOverRawTcp() throws Exception {
        JsonMessageSerializer serializer = new JsonMessageSerializer();
        SessionRegistry sessions = new SessionRegistry();
        try (LobbyServerNetworkAdapter adapter = new LobbyServerNetworkAdapter(sessions, serializer);
             TcpServer server = new TcpServer(serializer, adapter);
             NetworkManager hostNetwork = new NetworkManager(serializer);
             NetworkManager guestNetwork = new NetworkManager(serializer)) {
            NetworkCapture hostPushes = new NetworkCapture();
            NetworkCapture guestPushes = new NetworkCapture();
            hostNetwork.addListener(hostPushes);
            guestNetwork.addListener(guestPushes);
            server.start(0);
            hostNetwork.connect("127.0.0.1", server.localPort()).get(2, TimeUnit.SECONDS);
            guestNetwork.connect("127.0.0.1", server.localPort()).get(2, TimeUnit.SECONDS);
            WelcomeResponse hostWelcome = (WelcomeResponse) hostNetwork.send(new HelloRequest(
                    MessageMetadata.createNow(), "TCP Attacker")).get(2, TimeUnit.SECONDS);
            WelcomeResponse guestWelcome = (WelcomeResponse) guestNetwork.send(new HelloRequest(
                    MessageMetadata.createNow(), "TCP Defender")).get(2, TimeUnit.SECONDS);
            ClientSession host = sessions.find(SessionId.parse(hostWelcome.sessionId())).orElseThrow();
            ClientSession guest = sessions.find(SessionId.parse(guestWelcome.sessionId())).orElseThrow();
            String lobby = adapter.lobbyService().create(host).join().view().lobbyId().toString();
            adapter.lobbyService().join(guest, lobby).join();
            adapter.lobbyService().selectMap(host, lobby, "emerald-plains").join();
            adapter.lobbyService().ready(host, lobby, true).join();
            adapter.lobbyService().ready(guest, lobby, true).join();
            String match = adapter.lobbyService().start(host, lobby).join().launch().matchId();
            Setup structure = adapter.lobbyService().match(match).project(state -> {
                Fixture.clearUnits(state);
                var attackingEmpire = state.requirePlayer(host.playerId()).empire();
                var hall = state.requirePlayer(guest.playerId()).empire().townHall();
                HexCoord origin = Fixture.coordinateAtDistance(state, hall.position(), 1);
                Unit sword = new UnitFactory().create(UnitType.SWORDSMAN, origin,
                        DefaultRulesetFactory.create(), attackingEmpire.factionId());
                attackingEmpire.addUnit(sword);
                attackingEmpire.visibility().replaceCurrentlyVisible(
                        java.util.Set.of(origin, hall.position()));
                state.diplomacy().declareWar(host.playerId(), guest.playerId());
                return new Setup(List.of(id(sword)), hall.id().value().toString());
            }).join();
            var response = hostNetwork.send(new AttackRequest(MessageMetadata.createNow(), match,
                    structure.attackerIds(), structure.targetId(), 1L)).get(2, TimeUnit.SECONDS);
            assert response instanceof RequestAcceptedResponse accepted
                    && accepted.committedStateVersion() == 2L;
            await(() -> hostPushes.hasStateVersion(2L) && guestPushes.hasStateVersion(2L)
                    && hostPushes.reportCount() == 1, "TCP combat broadcast");
            assert guestPushes.reportCount() == 0;
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void defenderReportIsDeliveredOnEdtAtTurnStart() throws Exception {
        ClientStateStore store = new ClientStateStore(new SwingUiEventDispatcher());
        ClientMessageRouter router = new ClientMessageRouter(store, new SwingUiEventDispatcher());
        GamePanel panel = new GamePanel();
        CountDownLatch displayed = new CountDownLatch(1);
        AtomicBoolean onEdt = new AtomicBoolean();
        // PHASE 3 - Added in Phase 3
        store.addBattleReportListener(report -> {
            onEdt.set(SwingUtilities.isEventDispatchThread());
            panel.showBattleReport(report);
            displayed.countDown();
        });
        BattleReportPush report = new BattleReportPush(MessageMetadata.createNow(), 3L,
                "battle-1", "player-a", "player-b", "target-1", "STALEMATE",
                java.util.Map.of("target-1", 1), List.of(6, 2), List.of(5, 2),
                List.of(), 0, NOW);
        Thread listener = new Thread(() -> router.onPush(report), "test-combat-listener");
        listener.start();
        listener.join(500L);
        assert !listener.isAlive();
        assert displayed.await(2, TimeUnit.SECONDS) && onEdt.get();
        assert panel.combat().displayedTrace().contains("battle-1");
        assert panel.combat().diceCanvas().pairedComparisons()
                .equals(List.of("6 vs 5 → ATTACKER", "2 vs 2 → DEFENDER"));
    }

    // PHASE 3 - Added in Phase 3
    private static void expect(String code, Runnable action) {
        try { action.run(); throw new AssertionError("Expected " + code); }
        catch (CompletionException failure) {
            assert failure.getCause() instanceof MatchException : failure.getCause();
            assert ((MatchException) failure.getCause()).code().equals(code) : failure.getCause();
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void await(java.util.function.BooleanSupplier condition,
                              String description) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        // PHASE 3 - Added in Phase 3
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) throw new AssertionError("Timed out: " + description);
            Thread.sleep(10L);
        }
    }

    // PHASE 3 - Added in Phase 3
    private static String id(Unit unit) { return unit.id().value().toString(); }

    // PHASE 3 - Added in Phase 3
    private record Setup(List<String> attackerIds, String targetId) {}
    // PHASE 3 - Added in Phase 3
    private record Sent(SessionId session, PushMessage message) {}

    // PHASE 3 - Added in Phase 3
    private static final class NetworkCapture implements ClientMessageListener {
        // PHASE 3 - Added in Phase 3
        private final CopyOnWriteArrayList<PushMessage> pushes = new CopyOnWriteArrayList<>();
        // PHASE 3 - Added in Phase 3
        @Override public void onPush(PushMessage message) { pushes.add(message); }
        // PHASE 3 - Added in Phase 3
        @Override public void onConnectionStateChanged(NetworkConnectionState state, Throwable cause) {}
        // PHASE 3 - Added in Phase 3
        private boolean hasStateVersion(long version) { return pushes.stream().anyMatch(push -> push instanceof GameStatePush state && state.stateVersion() == version); }
        // PHASE 3 - Added in Phase 3
        private long reportCount() { return pushes.stream().filter(BattleReportPush.class::isInstance).count(); }
    }

    // PHASE 3 - Added in Phase 3
    private static final class Fixture implements AutoCloseable {
        // PHASE 3 - Added in Phase 3
        private final CopyOnWriteArrayList<Sent> messages = new CopyOnWriteArrayList<>();
        // PHASE 3 - Added in Phase 3
        private final SessionRegistry sessions = new SessionRegistry(Clock.fixed(NOW, ZoneOffset.UTC));
        // PHASE 3 - Added in Phase 3
        private final ClientSession host = sessions.create("Attacker");
        // PHASE 3 - Added in Phase 3
        private final ClientSession guest = sessions.create("Defender");
        // PHASE 3 - Added in Phase 3
        private final LobbyServerService service;
        // PHASE 3 - Added in Phase 3
        private final String matchId;

        // PHASE 3 - Added in Phase 3
        private Fixture(int... rolls) { this(new SequenceDice(rolls)); }
        // PHASE 3 - Added in Phase 3
        private Fixture(DiceSource dice) {
            service = new LobbyServerService((session, message) -> messages.add(new Sent(session, message)),
                    PredefinedMapCatalog.standard(), Clock.fixed(NOW, ZoneOffset.UTC), dice);
            String lobby = service.create(host).join().view().lobbyId().toString();
            service.join(guest, lobby).join();
            service.selectMap(host, lobby, "emerald-plains").join();
            service.ready(host, lobby, true).join();
            service.ready(guest, lobby, true).join();
            matchId = service.start(host, lobby).join().launch().matchId();
        }

        // PHASE 3 - Added in Phase 3
        private Setup setupMilitary(int distance, List<UnitType> attackerTypes,
                                    List<UnitType> defenderTypes) {
            return setupMilitary(distance, attackerTypes, defenderTypes, true);
        }

        // PHASE 3 - Added in Phase 3
        private Setup setupMilitary(int distance, List<UnitType> attackerTypes,
                                    List<UnitType> defenderTypes, boolean war) {
            return service.match(matchId).project(state -> {
                clearUnits(state);
                HexCoord origin = state.requirePlayer(host.playerId()).empire().townHall().position();
                HexCoord target = coordinateAtDistance(state, origin, distance);
                UnitFactory factory = new UnitFactory();
                var rules = DefaultRulesetFactory.create();
                var attacker = state.requirePlayer(host.playerId()).empire();
                var defender = state.requirePlayer(guest.playerId()).empire();
                List<String> attackerIds = new ArrayList<>();
                // PHASE 3 - Added in Phase 3
                for (UnitType type : attackerTypes) {
                    Unit unit = factory.create(type, origin, rules, attacker.factionId());
                    attacker.addUnit(unit); attackerIds.add(id(unit));
                }
                String targetId = null;
                // PHASE 3 - Added in Phase 3
                for (UnitType type : defenderTypes) {
                    Unit unit = factory.create(type, target, rules, defender.factionId());
                    defender.addUnit(unit); if (targetId == null) targetId = id(unit);
                }
                attacker.visibility().replaceCurrentlyVisible(java.util.Set.of(origin, target));
                if (war) state.diplomacy().declareWar(host.playerId(), guest.playerId());
                return new Setup(List.copyOf(attackerIds), targetId);
            }).join();
        }

        // PHASE 3 - Added in Phase 3
        private Setup setupStructure(int distance, UnitType attackerType) {
            return service.match(matchId).project(state -> {
                clearUnits(state);
                var attacker = state.requirePlayer(host.playerId()).empire();
                var target = state.requirePlayer(guest.playerId()).empire().townHall();
                HexCoord origin = coordinateAtDistance(state, target.position(), distance);
                Unit unit = new UnitFactory().create(attackerType, origin,
                        DefaultRulesetFactory.create(), attacker.factionId());
                attacker.addUnit(unit);
                attacker.visibility().replaceCurrentlyVisible(java.util.Set.of(origin, target.position()));
                state.diplomacy().declareWar(host.playerId(), guest.playerId());
                return new Setup(List.of(id(unit)), target.id().value().toString());
            }).join();
        }

        // PHASE 3 - Added in Phase 3
        private static void clearUnits(MultiplayerGameState state) {
            // PHASE 3 - Added in Phase 3
            for (var player : state.players().values()) {
                List<Unit> units = List.copyOf(player.empire().units());
                units.forEach(unit -> player.empire().removeUnit(unit.id()));
            }
        }

        // PHASE 3 - Added in Phase 3
        private static HexCoord coordinateAtDistance(MultiplayerGameState state,
                                                     HexCoord origin, int distance) {
            return state.map().tiles().stream().map(tile -> tile.coordinate())
                    .filter(coordinate -> coordinate.distanceTo(origin) == distance)
                    .findFirst().orElseThrow();
        }

        // PHASE 3 - Added in Phase 3
        private void declareWar() {
            service.match(matchId).project(state -> {
                state.diplomacy().declareWar(host.playerId(), guest.playerId()); return null;
            }).join();
        }

        // PHASE 3 - Added in Phase 3
        private civilization.server.combat.ServerAttackResult attack(
                ClientSession session, Setup setup, long version) {
            return service.attack(session, new AttackRequest(MessageMetadata.createNow(),
                    matchId, setup.attackerIds(), setup.targetId(), version)).join();
        }

        // PHASE 3 - Added in Phase 3
        private long version() { return service.match(matchId).project(MultiplayerGameState::stateVersion).join(); }
        // PHASE 3 - Added in Phase 3
        private int count(Class<?> type) { return (int) messages.stream().filter(sent -> type.isInstance(sent.message())).count(); }
        // PHASE 3 - Added in Phase 3
        private int count(Class<?> type, SessionId recipient) { return (int) messages.stream().filter(sent -> sent.session().equals(recipient) && type.isInstance(sent.message())).count(); }
        // PHASE 3 - Added in Phase 3
        private BattleReportPush latestReport(SessionId recipient) { return (BattleReportPush) messages.stream().filter(sent -> sent.session().equals(recipient) && sent.message() instanceof BattleReportPush).reduce((first, second) -> second).orElseThrow().message(); }
        // PHASE 3 - Added in Phase 3
        @Override public void close() { service.close(); }
    }

    // PHASE 3 - Added in Phase 3
    private static final class SequenceDice implements DiceSource {
        // PHASE 3 - Added in Phase 3
        private final ArrayDeque<Integer> values = new ArrayDeque<>();
        // PHASE 3 - Added in Phase 3
        private SequenceDice(int... rolls) { for (int roll : rolls) values.addLast(roll); }
        // PHASE 3 - Added in Phase 3
        @Override public int rollD6() { if (values.isEmpty()) throw new AssertionError("Unexpected dice roll"); return values.removeFirst(); }
    }

    // PHASE 3 - Added in Phase 3
    private static final class CountingDice implements DiceSource {
        // PHASE 3 - Added in Phase 3
        private int rolls;
        // PHASE 3 - Added in Phase 3
        @Override public int rollD6() { rolls++; return 1; }
    }
}
