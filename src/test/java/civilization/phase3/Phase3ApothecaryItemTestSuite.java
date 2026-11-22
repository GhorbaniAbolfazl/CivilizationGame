package civilization.phase3;

import civilization.application.factory.UnitFactory;
import civilization.model.building.Apothecary;
import civilization.model.building.HallLevel;
import civilization.model.economy.ResourceBundle;
import civilization.model.item.ItemId;
import civilization.model.item.ItemInstance;
import civilization.model.item.ItemType;
import civilization.model.map.HexCoord;
import civilization.model.map.TerrainType;
import civilization.model.technology.UnitProductionOrder;
import civilization.model.unit.MilitaryUnit;
import civilization.model.unit.Unit;
import civilization.model.unit.UnitType;
import civilization.protocol.BuildRequest;
import civilization.protocol.CraftItemRequest;
import civilization.protocol.EndTurnRequest;
import civilization.protocol.GameStatePush;
import civilization.protocol.HexCoordinate;
import civilization.protocol.MessageMetadata;
import civilization.protocol.PushMessage;
import civilization.protocol.UseItemRequest;
import civilization.rules.DefaultRulesetFactory;
import civilization.server.item.ItemException;
import civilization.server.item.MultiplayerItemService;
import civilization.server.lobby.LobbyServerService;
import civilization.server.match.MatchException;
import civilization.server.match.MultiplayerGameState;
import civilization.server.session.ClientSession;
import civilization.server.session.SessionId;
import civilization.server.session.SessionRegistry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;

// PHASE 3 - Added in Phase 3
public final class Phase3ApothecaryItemTestSuite {
    // PHASE 3 - Added in Phase 3
    private static final Instant NOW = Instant.parse("2032-06-07T08:09:10.123456Z");
    // PHASE 3 - Added in Phase 3
    private Phase3ApothecaryItemTestSuite() { }

    // PHASE 3 - Added in Phase 3
    public static void main(String[] args) throws Exception {
        apothecaryPrerequisitesCostAndIndependentQueue();
        teleportValidationIsFogTerrainOccupancyAndApSafe();
        mobilityIsFreeExceedsCapacityAndExpires();
        combatItemModifiesOnlyServerSideAttacks();
        inventoryAndApothecaryPersistWithPlayerState();
        System.out.println("Phase3ApothecaryItemTestSuite: all checks passed");
    }

    // PHASE 3 - Added in Phase 3
    private static void apothecaryPrerequisitesCostAndIndependentQueue() {
        try (Fixture fixture = new Fixture(() -> 6)) {
            BuildSetup setup = fixture.prepareBuild(TerrainType.PLAINS, false, true);
            expectItem("HALL_LEVEL_REQUIRED", () -> fixture.service.build(fixture.host,
                    build(fixture.matchId, setup, 1L)).join());
        }
        try (Fixture fixture = new Fixture(() -> 6)) {
            BuildSetup setup = fixture.prepareBuild(TerrainType.GRASSLAND, true, true);
            expectItem("APOTHECARY_REQUIRES_PLAINS", () -> fixture.service.build(fixture.host,
                    build(fixture.matchId, setup, 1L)).join());
        }
        try (Fixture fixture = new Fixture(() -> 6)) {
            BuildSetup setup = fixture.prepareBuild(TerrainType.PLAINS, true, false);
            expectItem("INVALID_APOTHECARY_BUILD", () -> fixture.service.build(fixture.host,
                    build(fixture.matchId, setup, 1L)).join());
        }
        try (Fixture fixture = new Fixture(() -> 6)) {
            BuildSetup setup = fixture.prepareBuild(TerrainType.PLAINS, true, true);
            fixture.service.build(fixture.host, build(fixture.matchId, setup, 1L)).join();
            String apothecaryId = fixture.apothecaryId();
            fixture.project(state -> {
                state.requirePlayer(fixture.host.playerId()).empire().townHall()
                        .productionQueue().enqueue(new UnitProductionOrder(
                                UnitType.WORKER, ResourceBundle.ZERO, 2));
                return null;
            });
            ResourceBundle before = fixture.resources(fixture.host);
            fixture.service.craftItem(fixture.host, craft(fixture.matchId, apothecaryId,
                    ItemType.TELEPORT, 2L)).join();
            ResourceBundle after = fixture.resources(fixture.host);
            MultiplayerItemService.cost(ItemType.TELEPORT).asMap().forEach((type, amount) ->
                    { assert after.amountOf(type) == before.amountOf(type) - amount; });
            expectItem("APOTHECARY_QUEUE_OCCUPIED", () -> fixture.service.craftItem(
                    fixture.host, craft(fixture.matchId, apothecaryId,
                            ItemType.MOBILITY, 3L)).join());
            fixture.service.endTurn(fixture.host, end(fixture.matchId, 3L)).join();
            fixture.project(state -> {
                var empire = state.requirePlayer(fixture.host.playerId()).empire();
                Apothecary apothecary = (Apothecary) empire.requireBuilding(
                        new civilization.model.building.BuildingId(UUID.fromString(apothecaryId)));
                assert apothecary.craftingQueue().isEmpty();
                assert empire.itemInventory().items().size() == 1;
                assert empire.itemInventory().items().iterator().next().createdAt().equals(NOW);
                assert empire.townHall().productionQueue().currentOrder().orElseThrow()
                        .remainingTurns() == 1;
                return null;
            });
            GameStatePush hostState = fixture.latestState(fixture.host.sessionId());
            GameStatePush guestState = fixture.latestState(fixture.guest.sessionId());
            assert hostState.snapshot().viewerItems().size() == 1;
            assert guestState.snapshot().viewerItems().isEmpty();
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void teleportValidationIsFogTerrainOccupancyAndApSafe() {
        try (Fixture fixture = new Fixture(() -> 6)) {
            ItemInstance teleport = fixture.give(fixture.host, ItemType.TELEPORT);
            Unit unit = fixture.unit(fixture.host, UnitType.EXPLORER);
            HexCoord valid = fixture.visibleEmptyLand(unit.position());
            int ap = unit.actionPoints().current();
            fixture.service.useItem(fixture.host, use(fixture.matchId, teleport, unit, valid, 1L)).join();
            assert unit.position().equals(valid);
            assert unit.actionPoints().current() == ap;
            assert fixture.inventorySize(fixture.host) == 0;

            ItemInstance second = fixture.give(fixture.host, ItemType.MOBILITY);
            expectItem("ITEM_ALREADY_USED_ON_UNIT", () -> fixture.service.useItem(fixture.host,
                    use(fixture.matchId, second, unit, null, 2L)).join());
            assert fixture.inventorySize(fixture.host) == 1;

            ItemInstance guestItem = fixture.give(fixture.guest, ItemType.MOBILITY);
            expectItem("ITEM_NOT_OWNED", () -> fixture.service.useItem(fixture.host,
                    use(fixture.matchId, guestItem, fixture.unit(fixture.host, UnitType.BUILDER),
                            null, 2L)).join());
            expectMatch("NOT_ACTIVE_PLAYER", () -> fixture.service.useItem(fixture.guest,
                    use(fixture.matchId, guestItem, fixture.unit(fixture.guest, UnitType.BUILDER),
                            null, 2L)).join());
        }
        try (Fixture fixture = new Fixture(() -> 6)) {
            Unit target = fixture.unit(fixture.host, UnitType.BUILDER);
            ItemInstance fogItem = fixture.give(fixture.host, ItemType.TELEPORT);
            HexCoord fogged = fixture.foggedLand();
            expectItem("TELEPORT_DESTINATION_FOGGED", () -> fixture.service.useItem(fixture.host,
                    use(fixture.matchId, fogItem, target, fogged, 1L)).join());
            assert fixture.inventorySize(fixture.host) == 1;

            HexCoord impassable = fixture.makeVisibleTerrain(TerrainType.MOUNTAIN_RANGE);
            expectItem("IMPASSABLE_TELEPORT_DESTINATION", () -> fixture.service.useItem(fixture.host,
                    use(fixture.matchId, fogItem, target, impassable, 1L)).join());
            assert fixture.inventorySize(fixture.host) == 1;

            Unit occupant = fixture.unit(fixture.host, UnitType.WORKER);
            fixture.makeVisible(occupant.position());
            expectItem("TELEPORT_DESTINATION_OCCUPIED", () -> fixture.service.useItem(fixture.host,
                    use(fixture.matchId, fogItem, target, occupant.position(), 1L)).join());
            assert fixture.inventorySize(fixture.host) == 1;
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void mobilityIsFreeExceedsCapacityAndExpires() {
        try (Fixture fixture = new Fixture(() -> 6)) {
            Unit unit = fixture.unit(fixture.host, UnitType.BUILDER);
            ItemInstance mobility = fixture.give(fixture.host, ItemType.MOBILITY);
            int capacity = unit.actionPoints().capacity();
            fixture.service.useItem(fixture.host,
                    use(fixture.matchId, mobility, unit, null, 1L)).join();
            assert unit.actionPoints().current() == capacity + 2;
            assert unit.actionPoints().temporaryBonusRemaining() == 2;
            var ended = fixture.service.endTurn(fixture.host, end(fixture.matchId, 2L)).join();
            assert ended.expiredItemEffects().contains("PLAYER_ITEM_EFFECTS");
            assert unit.actionPoints().current() == capacity;
            assert unit.actionPoints().temporaryBonusRemaining() == 0;
            assert !fixture.project(state -> state.itemEffects().hasUsedItem(
                    fixture.host.playerId(), unit.id()));
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void combatItemModifiesOnlyServerSideAttacks() {
        try (Fixture fixture = new Fixture(() -> 6)) {
            CombatSetup setup = fixture.prepareUnitCombat();
            ItemInstance combat = fixture.give(fixture.host, ItemType.COMBAT);
            int ap = setup.attacker().actionPoints().current();
            fixture.service.useItem(fixture.host,
                    use(fixture.matchId, combat, setup.attacker(), null, 1L)).join();
            assert setup.attacker().actionPoints().current() == ap;
            var result = fixture.service.attack(fixture.host, new civilization.protocol.AttackRequest(
                    MessageMetadata.createNow(), fixture.matchId,
                    List.of(setup.attacker().id().value().toString()),
                    setup.defender().id().value().toString(), 2L)).join().combat();
            assert result.dice().attackerRawRolls().equals(List.of(6));
            assert result.dice().attackerSortedRolls().equals(List.of(7));
            assert result.dice().defenderSortedRolls().equals(List.of(6));
            assert result.dice().hitsOnDefender() == 1;
            fixture.service.endTurn(fixture.host, end(fixture.matchId, 3L)).join();
            assert !fixture.project(state -> state.itemEffects().hasCombatBuff(
                    fixture.host.playerId(), setup.attacker().id()));
        }
        try (Fixture fixture = new Fixture(() -> 6)) {
            CombatSetup setup = fixture.prepareStructureCombat();
            ItemInstance combat = fixture.give(fixture.host, ItemType.COMBAT);
            fixture.service.useItem(fixture.host,
                    use(fixture.matchId, combat, setup.attacker(), null, 1L)).join();
            var result = fixture.service.attack(fixture.host, new civilization.protocol.AttackRequest(
                    MessageMetadata.createNow(), fixture.matchId,
                    List.of(setup.attacker().id().value().toString()),
                    setup.targetId(), 2L)).join().combat();
            assert result.report().structureDamage() == 15;
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void inventoryAndApothecaryPersistWithPlayerState() throws Exception {
        try (Fixture fixture = new Fixture(() -> 6)) {
            BuildSetup setup = fixture.prepareBuild(TerrainType.PLAINS, true, true);
            fixture.service.build(fixture.host, build(fixture.matchId, setup, 1L)).join();
            fixture.give(fixture.host, ItemType.COMBAT);
            var empire = fixture.project(state -> state.requirePlayer(fixture.host.playerId()).empire());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            // PHASE 3 - Added in Phase 3
            try (ObjectOutputStream output = new ObjectOutputStream(bytes)) { output.writeObject(empire); }
            civilization.model.player.PlayerState restored;
            // PHASE 3 - Added in Phase 3
            try (ObjectInputStream input = new ObjectInputStream(
                    new ByteArrayInputStream(bytes.toByteArray()))) {
                restored = (civilization.model.player.PlayerState) input.readObject();
            }
            assert restored.itemInventory().items().size() == 1;
            assert restored.constructedBuildings().stream().anyMatch(Apothecary.class::isInstance);
        }
    }

    // PHASE 3 - Added in Phase 3
    private static BuildRequest build(String matchId, BuildSetup setup, long version) {
        return new BuildRequest(MessageMetadata.createNow(), matchId, setup.builderId(),
                "APOTHECARY", new HexCoordinate(setup.target().q(), setup.target().r()), version);
    }

    // PHASE 3 - Added in Phase 3
    private static CraftItemRequest craft(String matchId, String apothecaryId,
                                          ItemType type, long version) {
        return new CraftItemRequest(MessageMetadata.createNow(), matchId, apothecaryId,
                type.name(), 1, version);
    }

    // PHASE 3 - Added in Phase 3
    private static UseItemRequest use(String matchId, ItemInstance item, Unit unit,
                                      HexCoord destination, long version) {
        return new UseItemRequest(MessageMetadata.createNow(), matchId,
                item.id().value().toString(), unit.id().value().toString(),
                destination == null ? null : new HexCoordinate(destination.q(), destination.r()),
                version);
    }

    // PHASE 3 - Added in Phase 3
    private static EndTurnRequest end(String matchId, long version) {
        return new EndTurnRequest(MessageMetadata.createNow(), matchId, version);
    }

    // PHASE 3 - Added in Phase 3
    private static void expectItem(String code, Runnable operation) {
        try { operation.run(); throw new AssertionError("Expected " + code); }
        catch (CompletionException failure) {
            assert failure.getCause() instanceof ItemException : failure.getCause();
            assert ((ItemException) failure.getCause()).code().equals(code)
                    : ((ItemException) failure.getCause()).code();
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void expectMatch(String code, Runnable operation) {
        try { operation.run(); throw new AssertionError("Expected " + code); }
        catch (CompletionException failure) {
            assert failure.getCause() instanceof MatchException;
            assert ((MatchException) failure.getCause()).code().equals(code);
        }
    }

    // PHASE 3 - Added in Phase 3
    private record BuildSetup(String builderId, HexCoord target) { }
    // PHASE 3 - Added in Phase 3
    private record CombatSetup(MilitaryUnit attacker, MilitaryUnit defender, String targetId) { }
    // PHASE 3 - Added in Phase 3
    private record Sent(SessionId recipient, PushMessage message) { }

    // PHASE 3 - Added in Phase 3
    private static final class Fixture implements AutoCloseable {
        // PHASE 3 - Added in Phase 3
        private final SessionRegistry sessions = new SessionRegistry(Clock.fixed(NOW, ZoneOffset.UTC));
        // PHASE 3 - Added in Phase 3
        private final ClientSession host = sessions.create("Host");
        // PHASE 3 - Added in Phase 3
        private final ClientSession guest = sessions.create("Guest");
        // PHASE 3 - Added in Phase 3
        private final CopyOnWriteArrayList<Sent> sent = new CopyOnWriteArrayList<>();
        // PHASE 3 - Added in Phase 3
        private final LobbyServerService service;
        // PHASE 3 - Added in Phase 3
        private final String matchId;

        // PHASE 3 - Added in Phase 3
        private Fixture(civilization.model.random.DiceSource dice) {
            service = new LobbyServerService((recipient, message) ->
                    sent.add(new Sent(recipient, message)),
                    civilization.server.map.PredefinedMapCatalog.standard(),
                    Clock.fixed(NOW, ZoneOffset.UTC), dice);
            var lobby = service.create(host).join().view();
            String lobbyId = lobby.lobbyId().toString();
            service.join(guest, lobbyId).join();
            service.selectMap(host, lobbyId, "emerald-plains").join();
            service.ready(host, lobbyId, true).join();
            service.ready(guest, lobbyId, true).join();
            matchId = service.start(host, lobbyId).join().launch().matchId();
        }

        // PHASE 3 - Added in Phase 3
        private BuildSetup prepareBuild(TerrainType terrain, boolean levelTwo, boolean fund) {
            return project(state -> {
                var empire = state.requirePlayer(host.playerId()).empire();
                if (levelTwo) empire.townHall().upgradeTo(HallLevel.SETTLEMENT);
                Unit builder = empire.units().stream()
                        .filter(candidate -> candidate.type() == UnitType.BUILDER)
                        .findFirst().orElseThrow();
                HexCoord target = findTerrain(state, terrain, builder.position());
                empire.border().claimAll(Set.of(target), state.map());
                empire.visibility().discover(target);
                builder.moveTo(target);
                if (fund) empire.townHall().stockpile().credit(
                        empire.townHall().stockpile().remainingCapacity());
                return new BuildSetup(builder.id().value().toString(), target);
            });
        }

        // PHASE 3 - Added in Phase 3
        private HexCoord findTerrain(MultiplayerGameState state, TerrainType terrain,
                                     HexCoord excluded) {
            return state.map().tiles().stream().map(tile -> tile.coordinate())
                    .filter(coordinate -> !coordinate.equals(excluded))
                    .filter(coordinate -> state.map().requireTile(coordinate).terrain() == terrain)
                    .filter(coordinate -> state.world().territories().ownerAt(coordinate).isEmpty())
                    .filter(coordinate -> !occupied(state, coordinate))
                    .findFirst().orElseThrow();
        }

        // PHASE 3 - Added in Phase 3
        private HexCoord visibleEmptyLand(HexCoord excluded) {
            return project(state -> state.requirePlayer(host.playerId()).empire().visibility()
                    .currentlyVisibleHexes().stream().filter(coordinate -> !coordinate.equals(excluded))
                    .filter(coordinate -> state.map().requireTile(coordinate).terrain() != TerrainType.SEA)
                    .filter(coordinate -> state.map().requireTile(coordinate).terrain() != TerrainType.MOUNTAIN_RANGE)
                    .filter(coordinate -> !occupied(state, coordinate)).findFirst().orElseThrow());
        }

        // PHASE 3 - Added in Phase 3
        private HexCoord foggedLand() {
            return project(state -> state.map().tiles().stream().map(tile -> tile.coordinate())
                    .filter(coordinate -> state.map().requireTile(coordinate).terrain() == TerrainType.PLAINS)
                    .filter(coordinate -> !state.isCurrentlyVisibleTo(host.playerId(), coordinate))
                    .filter(coordinate -> !occupied(state, coordinate)).findFirst().orElseThrow());
        }

        // PHASE 3 - Added in Phase 3
        private HexCoord makeVisibleTerrain(TerrainType terrain) {
            return project(state -> {
                HexCoord coordinate = findTerrain(state, terrain,
                        state.requirePlayer(host.playerId()).empire().townHall().position());
                // PHASE 3 - Added in Phase 3
                var visibility = state.requirePlayer(host.playerId()).empire().visibility();
                java.util.HashSet<HexCoord> visible = new java.util.HashSet<>(
                        visibility.currentlyVisibleHexes());
                visible.add(coordinate); visibility.replaceCurrentlyVisible(visible);
                return coordinate;
            });
        }

        // PHASE 3 - Added in Phase 3
        private void makeVisible(HexCoord coordinate) {
            project(state -> {
                var visibility = state.requirePlayer(host.playerId()).empire().visibility();
                java.util.HashSet<HexCoord> visible = new java.util.HashSet<>(
                        visibility.currentlyVisibleHexes());
                visible.add(coordinate); visibility.replaceCurrentlyVisible(visible); return null;
            });
        }

        // PHASE 3 - Added in Phase 3
        private String apothecaryId() {
            return project(state -> state.requirePlayer(host.playerId()).empire()
                    .constructedBuildings().stream().filter(Apothecary.class::isInstance)
                    .findFirst().orElseThrow().id().value().toString());
        }

        // PHASE 3 - Added in Phase 3
        private ItemInstance give(ClientSession player, ItemType type) {
            return project(state -> {
                ItemInstance item = new ItemInstance(new ItemId(UUID.randomUUID()), type, NOW);
                state.requirePlayer(player.playerId()).empire().itemInventory().add(item);
                return item;
            });
        }

        // PHASE 3 - Added in Phase 3
        private Unit unit(ClientSession player, UnitType type) {
            return project(state -> state.requirePlayer(player.playerId()).empire().units().stream()
                    .filter(candidate -> candidate.type() == type).findFirst().orElseThrow());
        }

        // PHASE 3 - Added in Phase 3
        private CombatSetup prepareUnitCombat() {
            return project(state -> {
                var hostEmpire = state.requirePlayer(host.playerId()).empire();
                var guestEmpire = state.requirePlayer(guest.playerId()).empire();
                clearUnits(hostEmpire); clearUnits(guestEmpire);
                HexCoord defenderPosition = guestEmpire.townHall().position().neighbors().stream()
                        .filter(state.map()::contains)
                        .filter(coordinate -> state.map().requireTile(coordinate).terrain()
                                != TerrainType.MOUNTAIN_RANGE).findFirst().orElseThrow();
                HexCoord attackerPosition = defenderPosition.neighbors().stream()
                        .filter(state.map()::contains)
                        .filter(coordinate -> !coordinate.equals(guestEmpire.townHall().position()))
                        .filter(coordinate -> state.map().requireTile(coordinate).terrain()
                                != TerrainType.MOUNTAIN_RANGE).findFirst().orElseThrow();
                MilitaryUnit attacker = military(hostEmpire, UnitType.SWORDSMAN, attackerPosition);
                MilitaryUnit defender = military(guestEmpire, UnitType.SWORDSMAN, defenderPosition);
                reveal(hostEmpire, attackerPosition, defenderPosition);
                state.diplomacy().declareWar(host.playerId(), guest.playerId());
                // PHASE 3 - Added in Phase 3
                state.itemEffects().markUsed(guest.playerId(), defender.id(), ItemType.COMBAT);
                return new CombatSetup(attacker, defender, defender.id().value().toString());
            });
        }

        // PHASE 3 - Added in Phase 3
        private CombatSetup prepareStructureCombat() {
            return project(state -> {
                var hostEmpire = state.requirePlayer(host.playerId()).empire();
                var guestEmpire = state.requirePlayer(guest.playerId()).empire();
                clearUnits(hostEmpire); clearUnits(guestEmpire);
                HexCoord hall = guestEmpire.townHall().position();
                HexCoord attackerPosition = hall.neighbors().stream().filter(state.map()::contains)
                        .filter(coordinate -> state.map().requireTile(coordinate).terrain()
                                != TerrainType.MOUNTAIN_RANGE).findFirst().orElseThrow();
                MilitaryUnit attacker = military(hostEmpire, UnitType.SWORDSMAN, attackerPosition);
                reveal(hostEmpire, attackerPosition, hall);
                state.diplomacy().declareWar(host.playerId(), guest.playerId());
                return new CombatSetup(attacker, null,
                        guestEmpire.townHall().id().value().toString());
            });
        }

        // PHASE 3 - Added in Phase 3
        private MilitaryUnit military(civilization.model.player.PlayerState empire,
                                      UnitType type, HexCoord position) {
            MilitaryUnit unit = (MilitaryUnit) new UnitFactory().create(type, position,
                    DefaultRulesetFactory.create(), empire.factionId());
            empire.addUnit(unit); return unit;
        }

        // PHASE 3 - Added in Phase 3
        private static void clearUnits(civilization.model.player.PlayerState empire) {
            List.copyOf(empire.units()).forEach(unit -> empire.removeUnit(unit.id()));
        }

        // PHASE 3 - Added in Phase 3
        private static void reveal(civilization.model.player.PlayerState empire,
                                   HexCoord... coordinates) {
            java.util.HashSet<HexCoord> visible = new java.util.HashSet<>(
                    empire.visibility().currentlyVisibleHexes());
            visible.addAll(List.of(coordinates)); empire.visibility().replaceCurrentlyVisible(visible);
        }

        // PHASE 3 - Added in Phase 3
        private static boolean occupied(MultiplayerGameState state, HexCoord coordinate) {
            return state.players().values().stream().flatMap(player -> player.empire().units().stream())
                    .anyMatch(unit -> unit.position().equals(coordinate))
                    || state.players().values().stream().flatMap(player -> player.empire().allBuildings().stream())
                    .anyMatch(building -> building.position().equals(coordinate))
                    || !state.world().units().at(coordinate).isEmpty()
                    || state.world().structures().at(coordinate).isPresent()
                    || state.world().wildAnimals().at(coordinate).isPresent()
                    || state.world().tribes().atCamp(coordinate).isPresent()
                    || state.world().tradingPosts().all().stream()
                    .anyMatch(post -> post.position().equals(coordinate));
        }

        // PHASE 3 - Added in Phase 3
        private ResourceBundle resources(ClientSession player) {
            return project(state -> state.requirePlayer(player.playerId()).empire()
                    .townHall().stockpile().amounts());
        }

        // PHASE 3 - Added in Phase 3
        private int inventorySize(ClientSession player) {
            return project(state -> state.requirePlayer(player.playerId()).empire()
                    .itemInventory().items().size());
        }

        // PHASE 3 - Added in Phase 3
        private GameStatePush latestState(SessionId recipient) {
            return sent.stream().filter(value -> value.recipient().equals(recipient))
                    .map(Sent::message).filter(GameStatePush.class::isInstance)
                    .map(GameStatePush.class::cast).reduce((first, second) -> second).orElseThrow();
        }

        // PHASE 3 - Added in Phase 3
        private <T> T project(java.util.function.Function<MultiplayerGameState, T> operation) {
            return service.match(matchId).project(operation).join();
        }

        // PHASE 3 - Added in Phase 3
        @Override public void close() { service.close(); }
    }
}
