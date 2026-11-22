package civilization.phase3;

import civilization.model.building.BuildingId;
import civilization.model.building.TownHall;
import civilization.model.economy.ResourceBundle;
import civilization.model.map.HexCoord;
import civilization.model.map.TerrainType;
import civilization.model.technology.ProductionQueue;
import civilization.model.unit.BuilderUnit;
import civilization.model.unit.UnitType;
import civilization.protocol.BuildRequest;
import civilization.protocol.GameStatePush;
import civilization.protocol.HexCoordinate;
import civilization.protocol.MessageMetadata;
import civilization.protocol.PushMessage;
import civilization.server.hall.HallTownException;
import civilization.server.hall.MultiplayerHallTownService;
import civilization.server.lobby.LobbyServerService;
import civilization.server.match.MatchException;
import civilization.server.match.MultiplayerGameState;
import civilization.server.match.MultiplayerMatchStatus;
import civilization.server.session.ClientSession;
import civilization.server.session.SessionId;
import civilization.server.session.SessionRegistry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;

// PHASE 3 - Added in Phase 3
public final class Phase3MultipleHallTownTestSuite {
    // PHASE 3 - Added in Phase 3
    private Phase3MultipleHallTownTestSuite() { }

    // PHASE 3 - Added in Phase 3
    public static void main(String[] args) {
        remoteHallCreatesASeparateControlledRegionAndFilteredPush();
        authorityRejectsOwnershipBuilderResourcePlacementApAndTurnViolations();
        lastHallControlsEliminationAndVictory();
        System.out.println("Phase3MultipleHallTownTestSuite: all checks passed");
    }

    // PHASE 3 - Added in Phase 3
    private static void remoteHallCreatesASeparateControlledRegionAndFilteredPush() {
        try (Fixture fixture = new Fixture(2)) {
            Prepared prepared = fixture.prepareRemoteBuild(fixture.host, true);
            ResourceBundle before = fixture.project(state -> state.requirePlayer(
                    fixture.host.playerId()).empire().townHall().stockpile().amounts());
            int pushes = fixture.count(GameStatePush.class);
            var result = fixture.service.buildHallTown(fixture.host,
                    request(fixture.matchId, prepared, 1L)).join();
            assert result.position().equals(prepared.target());
            assert result.controlledRegion().contains(prepared.target());
            assert fixture.count(GameStatePush.class) == pushes + 2;
            fixture.project(state -> {
                var empire = state.requirePlayer(fixture.host.playerId()).empire();
                assert empire.activeHallTowns().size() == 2;
                assert empire.border().owns(prepared.target());
                assert empire.hallRegion(result.hallId()).equals(result.controlledRegion());
                assert empire.hallRegion(empire.hallTowns().iterator().next().id()).stream()
                        .noneMatch(result.controlledRegion()::contains);
                ResourceBundle after = empire.townHall().stockpile().amounts();
                MultiplayerHallTownService.HALL_TOWN_COST.asMap().forEach((type, amount) ->
                        { assert after.amountOf(type) == before.amountOf(type) - amount; });
                return null;
            });
            GameStatePush hostPush = fixture.latestState(fixture.host.sessionId());
            assert hostPush.snapshot().visibleBuildings().stream()
                    .filter(building -> building.ownerPlayerId().equals(
                            fixture.host.playerId().toString()))
                    .filter(building -> building.buildingType().equals("TOWN_HALL")).count() == 2;
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void authorityRejectsOwnershipBuilderResourcePlacementApAndTurnViolations() {
        try (Fixture fixture = new Fixture(2)) {
            Prepared host = fixture.prepareRemoteBuild(fixture.host, true);
            Prepared guest = fixture.prepareRemoteBuild(fixture.guest, true);
            expectMatch("NOT_ACTIVE_PLAYER", () -> fixture.service.buildHallTown(fixture.guest,
                    request(fixture.matchId, guest, 1L)).join());
            expectHall("BUILDER_NOT_OWNED", () -> fixture.service.buildHallTown(fixture.host,
                    request(fixture.matchId, new Prepared(guest.builderId(), host.target()), 1L)).join());
        }
        try (Fixture fixture = new Fixture(2)) {
            Prepared prepared = fixture.prepareRemoteBuild(fixture.host, false);
            expectHall("INSUFFICIENT_HALL_RESOURCES", () -> fixture.service.buildHallTown(
                    fixture.host, request(fixture.matchId, prepared, 1L)).join());
        }
        try (Fixture fixture = new Fixture(2)) {
            String builder = fixture.builderId(fixture.host);
            HexCoord remote = fixture.project(state -> fixture.findRemote(state,
                    fixture.host, state.requirePlayer(fixture.host.playerId()).empire().townHall().position()));
            expectHall("BUILDER_NOT_AT_LOCATION", () -> fixture.service.buildHallTown(fixture.host,
                    request(fixture.matchId, new Prepared(builder, remote), 1L)).join());
        }
        try (Fixture fixture = new Fixture(2)) {
            Prepared occupied = fixture.project(state -> {
                var empire = state.requirePlayer(fixture.host.playerId()).empire();
                BuilderUnit builder = fixture.builder(state, fixture.host);
                HexCoord hall = empire.townHall().position();
                builder.moveTo(hall);
                empire.townHall().stockpile().credit(
                        empire.townHall().stockpile().remainingCapacity());
                return new Prepared(builder.id().value().toString(), hall);
            });
            expectHall("HALL_LOCATION_OCCUPIED", () -> fixture.service.buildHallTown(
                    fixture.host, request(fixture.matchId, occupied, 1L)).join());
        }
        try (Fixture fixture = new Fixture(2)) {
            Prepared prepared = fixture.prepareRemoteBuild(fixture.host, true);
            fixture.project(state -> {
                BuilderUnit builder = fixture.builder(state, fixture.host);
                builder.actionPoints().spend(builder.actionPoints().current());
                return null;
            });
            expectHall("INSUFFICIENT_BUILDER_AP", () -> fixture.service.buildHallTown(
                    fixture.host, request(fixture.matchId, prepared, 1L)).join());
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void lastHallControlsEliminationAndVictory() {
        try (Fixture fixture = new Fixture(3)) {
            fixture.project(state -> {
                var guest = state.requirePlayer(fixture.guest.playerId()).empire();
                TownHall originalGuest = guest.townHall();
                HexCoord remote = fixture.findRemote(state, fixture.guest, originalGuest.position());
                TownHall second = new TownHall(new BuildingId(UUID.randomUUID()), remote,
                        originalGuest.stockpile(), new ProductionQueue(), guest.factionId());
                guest.addTownHall(second, Set.of(remote), state.map());

                Set<HexCoord> originalRegion = guest.hallRegion(originalGuest.id());
                destroy(guest, originalGuest);
                var firstLoss = state.evaluateHallSurvival(fixture.guest.playerId());
                assert !firstLoss.eliminated();
                assert !state.isEliminated(fixture.guest.playerId());
                assert guest.activeHallTowns().size() == 1;
                assert originalRegion.stream().noneMatch(guest.border()::owns);

                destroy(guest, second);
                var eliminated = state.evaluateHallSurvival(fixture.guest.playerId());
                assert eliminated.eliminated();
                assert guest.units().isEmpty();
                assert guest.hallTowns().size() == 2;
                assert guest.hallTowns().stream().noneMatch(TownHall::isActive);
                assert state.matchStatus() == MultiplayerMatchStatus.ACTIVE;

                var third = state.requirePlayer(fixture.third.playerId()).empire();
                TownHall thirdHall = third.townHall();
                destroy(third, thirdHall);
                var won = state.evaluateHallSurvival(fixture.third.playerId());
                assert won.matchFinished();
                assert won.winnerPlayerId().equals(fixture.host.playerId());
                assert state.matchStatus() == MultiplayerMatchStatus.FINISHED;
                assert state.winnerPlayerId().orElseThrow().equals(fixture.host.playerId());
                return null;
            });
            Prepared host = new Prepared(fixture.builderId(fixture.host), new HexCoord(0, 0));
            expectMatch("MATCH_FINISHED", () -> fixture.service.buildHallTown(fixture.host,
                    request(fixture.matchId, host, 1L)).join());
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void destroy(civilization.model.player.PlayerState empire, TownHall hall) {
        hall.health().damage(hall.health().current());
        empire.destroyBuilding(hall.id());
    }

    // PHASE 3 - Added in Phase 3
    private static BuildRequest request(String matchId, Prepared prepared, long version) {
        return new BuildRequest(MessageMetadata.createNow(), matchId, prepared.builderId(),
                "HALL_TOWN", new HexCoordinate(prepared.target().q(), prepared.target().r()), version);
    }

    // PHASE 3 - Added in Phase 3
    private static void expectHall(String code, Runnable action) {
        try { action.run(); throw new AssertionError("Expected " + code); }
        catch (CompletionException failure) {
            assert failure.getCause() instanceof HallTownException;
            assert ((HallTownException) failure.getCause()).code().equals(code);
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void expectMatch(String code, Runnable action) {
        try { action.run(); throw new AssertionError("Expected " + code); }
        catch (CompletionException failure) {
            assert failure.getCause() instanceof MatchException;
            assert ((MatchException) failure.getCause()).code().equals(code);
        }
    }

    // PHASE 3 - Added in Phase 3
    private record Prepared(String builderId, HexCoord target) { }
    // PHASE 3 - Added in Phase 3
    private record Sent(SessionId recipient, PushMessage message) { }

    // PHASE 3 - Added in Phase 3
    private static final class Fixture implements AutoCloseable {
        // PHASE 3 - Added in Phase 3
        private final SessionRegistry sessions = new SessionRegistry();
        // PHASE 3 - Added in Phase 3
        private final ClientSession host = sessions.create("Host");
        // PHASE 3 - Added in Phase 3
        private final ClientSession guest = sessions.create("Guest");
        // PHASE 3 - Added in Phase 3
        private final ClientSession third = sessions.create("Third");
        // PHASE 3 - Added in Phase 3
        private final CopyOnWriteArrayList<Sent> sent = new CopyOnWriteArrayList<>();
        // PHASE 3 - Added in Phase 3
        private final LobbyServerService service = new LobbyServerService(
                (recipient, message) -> sent.add(new Sent(recipient, message)));
        // PHASE 3 - Added in Phase 3
        private final String matchId;

        // PHASE 3 - Added in Phase 3
        private Fixture(int players) {
            var lobby = service.create(host).join().view();
            String lobbyId = lobby.lobbyId().toString();
            service.join(guest, lobbyId).join();
            if (players == 3) service.join(third, lobbyId).join();
            service.selectMap(host, lobbyId, "emerald-plains").join();
            service.ready(host, lobbyId, true).join();
            service.ready(guest, lobbyId, true).join();
            if (players == 3) service.ready(third, lobbyId, true).join();
            matchId = service.start(host, lobbyId).join().launch().matchId();
        }

        // PHASE 3 - Added in Phase 3
        private Prepared prepareRemoteBuild(ClientSession player, boolean fund) {
            return project(state -> {
                var empire = state.requirePlayer(player.playerId()).empire();
                BuilderUnit builder = builder(state, player);
                HexCoord target = findRemote(state, player, empire.townHall().position());
                builder.moveTo(target);
                empire.visibility().discover(target);
                if (fund) empire.townHall().stockpile().credit(
                        empire.townHall().stockpile().remainingCapacity());
                return new Prepared(builder.id().value().toString(), target);
            });
        }

        // PHASE 3 - Added in Phase 3
        private BuilderUnit builder(MultiplayerGameState state, ClientSession player) {
            return state.requirePlayer(player.playerId()).empire().units().stream()
                    .filter(unit -> unit.type() == UnitType.BUILDER).map(BuilderUnit.class::cast)
                    .findFirst().orElseThrow();
        }

        // PHASE 3 - Added in Phase 3
        private String builderId(ClientSession player) {
            return project(state -> builder(state, player).id().value().toString());
        }

        // PHASE 3 - Added in Phase 3
        private HexCoord findRemote(MultiplayerGameState state, ClientSession player,
                                    HexCoord origin) {
            return state.map().tiles().stream().map(tile -> tile.coordinate())
                    .filter(coordinate -> coordinate.distanceTo(origin) >= 4)
                    .filter(coordinate -> state.map().requireTile(coordinate).terrain() != TerrainType.SEA)
                    .filter(coordinate -> state.map().requireTile(coordinate).terrain() != TerrainType.MOUNTAIN_RANGE)
                    .filter(coordinate -> state.players().values().stream()
                            .noneMatch(owner -> owner.empire().border().owns(coordinate)))
                    .filter(coordinate -> state.players().values().stream()
                            .flatMap(owner -> owner.empire().allBuildings().stream())
                            .noneMatch(building -> building.position().equals(coordinate)))
                    .filter(coordinate -> state.world().territories().ownerAt(coordinate).isEmpty())
                    .filter(coordinate -> state.world().units().at(coordinate).isEmpty())
                    .filter(coordinate -> state.world().structures().at(coordinate).isEmpty())
                    .filter(coordinate -> state.world().wildAnimals().at(coordinate).isEmpty())
                    .filter(coordinate -> state.world().tribes().atCamp(coordinate).isEmpty())
                    .filter(coordinate -> state.world().tradingPosts().all().stream()
                            .noneMatch(post -> post.position().equals(coordinate)))
                    .findFirst().orElseThrow();
        }

        // PHASE 3 - Added in Phase 3
        private <T> T project(java.util.function.Function<MultiplayerGameState, T> operation) {
            return service.match(matchId).project(operation).join();
        }

        // PHASE 3 - Added in Phase 3
        private int count(Class<?> type) {
            return (int) sent.stream().filter(value -> type.isInstance(value.message())).count();
        }

        // PHASE 3 - Added in Phase 3
        private GameStatePush latestState(SessionId recipient) {
            return sent.stream().filter(value -> value.recipient().equals(recipient))
                    .map(Sent::message).filter(GameStatePush.class::isInstance)
                    .map(GameStatePush.class::cast).reduce((first, second) -> second).orElseThrow();
        }

        // PHASE 3 - Added in Phase 3
        @Override public void close() { service.close(); }
    }
}
