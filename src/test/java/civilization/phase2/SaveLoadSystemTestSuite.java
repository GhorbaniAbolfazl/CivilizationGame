package civilization.phase2;

import civilization.application.event.SynchronousEventBus;
import civilization.application.event.TurnResolutionCompletedEvent;
import civilization.application.persistence.AutosaveCoordinator;
import civilization.application.persistence.CorruptedSaveException;
import civilization.application.persistence.GameIdentity;
import civilization.application.persistence.GamePersistenceService;
import civilization.application.persistence.GameSession;
import civilization.application.persistence.IncompatibleSaveException;
import civilization.application.persistence.SaveGameCodec;
import civilization.application.persistence.SaveResult;
import civilization.application.persistence.SaveSlot;
import civilization.application.service.CombatService;
import civilization.bootstrap.GameEngineFactory;
import civilization.infrastructure.persistence.AtomicFileSaveRepository;
import civilization.model.building.BuildingId;
import civilization.model.building.BuildingType;
import civilization.model.building.ProductionBuilding;
import civilization.model.building.TownHall;
import civilization.model.combat.CombatKillRecord;
import civilization.model.combat.AttackRequest;
import civilization.model.diplomacy.RelationshipState;
import civilization.model.disaster.DisasterId;
import civilization.model.disaster.DisasterOccurrence;
import civilization.model.disaster.DisasterType;
import civilization.model.economy.ResourceBundle;
import civilization.model.economy.ResourceStockpile;
import civilization.model.economy.ResourceType;
import civilization.model.game.CrisisState;
import civilization.model.game.GamePhase;
import civilization.model.game.GameState;
import civilization.model.game.TurnState;
import civilization.model.identity.FactionId;
import civilization.model.identity.TribeId;
import civilization.model.infrastructure.InfrastructureId;
import civilization.model.infrastructure.Road;
import civilization.model.infrastructure.Wall;
import civilization.model.map.GameMap;
import civilization.model.map.HexCoord;
import civilization.model.map.HexEdge;
import civilization.model.map.HexTile;
import civilization.model.map.VisibilityState;
import civilization.model.map.NaturalResourceType;
import civilization.model.map.ResourceDeposit;
import civilization.model.mission.Mission;
import civilization.model.mission.MissionId;
import civilization.model.mission.MissionType;
import civilization.model.player.EmpireBorder;
import civilization.model.player.PlayerState;
import civilization.model.player.UnitCapacity;
import civilization.model.random.SplitMix64GameplayRandom;
import civilization.model.technology.ProductionQueue;
import civilization.model.technology.TechnologyId;
import civilization.model.technology.TechnologyState;
import civilization.model.technology.UnitProductionOrder;
import civilization.model.technology.UpgradeId;
import civilization.model.time.Season;
import civilization.model.time.SeasonState;
import civilization.model.trade.TradeCooldown;
import civilization.model.trade.TradingPost;
import civilization.model.trade.TradingPostId;
import civilization.model.trade.TribeTradeEndpoint;
import civilization.model.trade.TribeTradeProfile;
import civilization.model.tribe.Tribe;
import civilization.model.tribe.TribeCamp;
import civilization.model.tribe.TribeType;
import civilization.model.unit.ActionPoints;
import civilization.model.unit.BuilderUnit;
import civilization.model.unit.SwordsmanUnit;
import civilization.model.unit.UnitId;
import civilization.model.unit.WorkerUnit;
import civilization.model.world.WildAnimal;
import civilization.model.world.WildAnimalId;
import civilization.model.world.WorldState;
import civilization.rules.DefaultRulesetFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** End-to-end persistence acceptance tests, including process-independent file reopening. */
public final class SaveLoadSystemTestSuite {
    private static final Instant STARTED = Instant.parse("2026-08-20T10:15:30Z");
    private static final Instant SAVED = Instant.parse("2026-08-21T11:20:00Z");
    private static final GameIdentity IDENTITY = new GameIdentity(
            UUID.fromString("c61a8568-d7cd-48ee-b6dd-b80711260653"), STARTED);

    private SaveLoadSystemTestSuite() { }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            processMode(args);
            return;
        }
        roundTripRestoresTheCompleteModelWithoutGameplay();
        corruptedSaveIsRejectedWithoutSessionReplacement();
        incompatibleSaveIsRejectedWithoutSessionReplacement();
        supportedVersionIsMigratedWithoutGameplay();
        invalidSlotsCanBeDeletedSafely();
        overwriteRequiresConfirmation();
        autosaveFailureKeepsPreviousAutosave();
        manualAndAutosaveSlotsAreIndependent();
        repeatedLoadsAreEquivalentAndIndependent();
        loadedRngProducesTheSameActualCombatOutcome();
        loadedCopiesHaveIdenticalMultiTurnContinuation();
        separateJvmProcessCanReopenEquivalentState();
        completedTurnEventTriggersOneAutosaveAttempt();
        System.out.println("SaveLoadSystemTestSuite: all checks passed");
    }

    private static void roundTripRestoresTheCompleteModelWithoutGameplay() throws Exception {
        Path directory = Files.createTempDirectory("civ-save-round-trip-");
        GameState original = richState();
        String before = fingerprint(original);
        var repository = new AtomicFileSaveRepository(directory);
        GamePersistenceService persistence = persistence(repository);
        GameSession session = new GameSession(original, IDENTITY, persistence);
        SaveResult saved = session.saveManual(SaveSlot.MANUAL_1, "Rich kingdom",
                (slot, oldMetadata, replacement) -> true);
        check(saved.status() == SaveResult.Status.SAVED, "Manual save failed");
        byte[] bytes = Files.readAllBytes(repository.slotPath(SaveSlot.MANUAL_1));
        String raw = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        check(!raw.contains("javax.swing") && !raw.contains("civilization.view")
                && !raw.contains("civilization.controller"), "Presentation object entered save bytes");

        original = null; // model root is deliberately closed/dropped before reopening the repository
        var reopenedRepository = new AtomicFileSaveRepository(directory);
        GameSession reopened = new GameSession(minimalState(), persistence(reopenedRepository));
        reopened.load(SaveSlot.MANUAL_1);
        GameState loaded = reopened.state();
        String after = fingerprint(loaded);
        check(before.equals(after), "Loaded model is not equivalent to saved model: "
                + firstDifference(before, after));
        check(loaded.turn().turnNumber() == 27 && loaded.season().season() == Season.AUTUMN
                && loaded.season().turnWithinSeason() == 7, "Load advanced turn or season");
        check(loaded.player().units().stream().filter(WorkerUnit.class::isInstance)
                .map(WorkerUnit.class::cast).findFirst().orElseThrow().stationedAt().isPresent(),
                "Worker-to-building reference was not restored");
        Tribe loadedTribe = loaded.world().tribes().all().iterator().next();
        check(loaded.world().structures().at(loadedTribe.camp().position()).orElseThrow()
                        == loadedTribe.camp().structure(),
                "Camp registry did not reconnect to the Tribe's structure instance");

        long expectedNext = new SplitMix64GameplayRandom(
                loaded.gameplayRandom().snapshot()).nextLong();
        GameState loadedAgain = persistence(reopenedRepository).load(SaveSlot.MANUAL_1).state();
        check(loadedAgain.gameplayRandom().nextLong() == expectedNext,
                "RNG continuation changed across load");
    }

    private static void corruptedSaveIsRejectedWithoutSessionReplacement() throws Exception {
        Path directory = Files.createTempDirectory("civ-save-corrupt-");
        var repository = new AtomicFileSaveRepository(directory);
        GameSession writer = new GameSession(richState(), IDENTITY, persistence(repository));
        writer.saveManual(SaveSlot.MANUAL_1, "Good", (slot, old, replacement) -> true);
        Path path = repository.slotPath(SaveSlot.MANUAL_1);
        byte[] bytes = Files.readAllBytes(path);
        bytes[bytes.length / 2] ^= 0x5a;
        Files.write(path, bytes);

        GameState active = minimalState();
        GameSession session = new GameSession(active, persistence(repository));
        expect(CorruptedSaveException.class, () -> session.load(SaveSlot.MANUAL_1));
        check(session.state() == active, "Corrupted load replaced the active model");
        check(repository.list().stream().filter(entry -> entry.slot() == SaveSlot.MANUAL_1)
                .findFirst().orElseThrow().status()
                == civilization.application.persistence.SaveEntry.Status.CORRUPTED,
                "Corrupted slot was not isolated in listing");
    }

    private static void incompatibleSaveIsRejectedWithoutSessionReplacement() throws Exception {
        Path directory = Files.createTempDirectory("civ-save-version-");
        var repository = new AtomicFileSaveRepository(directory);
        GameSession writer = new GameSession(richState(), IDENTITY, persistence(repository));
        writer.saveManual(SaveSlot.MANUAL_1, "Future", (slot, old, replacement) -> true);
        Path path = repository.slotPath(SaveSlot.MANUAL_1);
        byte[] bytes = Files.readAllBytes(path);
        bytes[4] = 0; bytes[5] = 0; bytes[6] = 0; bytes[7] = 99;
        replaceChecksum(bytes);
        Files.write(path, bytes);

        GameState active = minimalState();
        GameSession session = new GameSession(active, persistence(repository));
        expect(IncompatibleSaveException.class, () -> session.load(SaveSlot.MANUAL_1));
        check(session.state() == active, "Incompatible load replaced the active model");
    }

    private static void supportedVersionIsMigratedWithoutGameplay() throws Exception {
        Path directory = Files.createTempDirectory("civ-save-migrate-");
        var repository = new AtomicFileSaveRepository(directory);
        GameState original = richState();
        String before = fingerprint(original);
        persistence(repository).saveManual(SaveSlot.MANUAL_1, "Version one", IDENTITY,
                original, (slot, old, replacement) -> true);
        Path path = repository.slotPath(SaveSlot.MANUAL_1);
        byte[] bytes = Files.readAllBytes(path);
        bytes[4] = 0; bytes[5] = 0; bytes[6] = 0; bytes[7] = 1;
        replaceChecksum(bytes);
        Files.write(path, bytes);
        GameState migrated = persistence(repository).load(SaveSlot.MANUAL_1).state();
        check(before.equals(fingerprint(migrated)),
                "Supported-version migration mutated model state or executed gameplay");
    }

    private static void invalidSlotsCanBeDeletedSafely() throws Exception {
        Path directory = Files.createTempDirectory("civ-save-delete-");
        var repository = new AtomicFileSaveRepository(directory);
        var service = persistence(repository);
        service.saveManual(SaveSlot.MANUAL_2, "Delete me", IDENTITY, minimalState(),
                (slot, old, replacement) -> true);
        byte[] bytes = Files.readAllBytes(repository.slotPath(SaveSlot.MANUAL_2));
        bytes[bytes.length / 2] ^= 1;
        Files.write(repository.slotPath(SaveSlot.MANUAL_2), bytes);
        check(service.listSlots().stream().filter(entry -> entry.slot() == SaveSlot.MANUAL_2)
                        .findFirst().orElseThrow().status()
                        == civilization.application.persistence.SaveEntry.Status.CORRUPTED,
                "Test setup did not produce a corrupted slot");
        service.delete(SaveSlot.MANUAL_2);
        check(!repository.exists(SaveSlot.MANUAL_2)
                        && service.listSlots().stream()
                        .filter(entry -> entry.slot() == SaveSlot.MANUAL_2)
                        .findFirst().orElseThrow().status()
                        == civilization.application.persistence.SaveEntry.Status.EMPTY,
                "Deleting a corrupted slot did not isolate and clear that exact slot");
    }

    private static void overwriteRequiresConfirmation() throws Exception {
        Path directory = Files.createTempDirectory("civ-save-overwrite-");
        var repository = new AtomicFileSaveRepository(directory);
        GameSession session = new GameSession(richState(), IDENTITY, persistence(repository));
        session.saveManual(SaveSlot.MANUAL_1, "Original", (slot, old, replacement) -> true);
        byte[] originalBytes = Files.readAllBytes(repository.slotPath(SaveSlot.MANUAL_1));
        SaveResult declined = session.saveManual(SaveSlot.MANUAL_1, "Replacement",
                (slot, old, replacement) -> false);
        check(declined.status() == SaveResult.Status.DECLINED
                        && Arrays.equals(originalBytes,
                        Files.readAllBytes(repository.slotPath(SaveSlot.MANUAL_1))),
                "Declined overwrite changed slot bytes");
        session.saveManual(SaveSlot.MANUAL_1, "Replacement", (slot, old, replacement) -> true);
        check(repository.metadata(SaveSlot.MANUAL_1).orElseThrow().saveName().equals("Replacement"),
                "Confirmed overwrite did not replace metadata");
    }

    private static void autosaveFailureKeepsPreviousAutosave() throws Exception {
        Path directory = Files.createTempDirectory("civ-save-auto-failure-");
        var healthy = new AtomicFileSaveRepository(directory);
        GamePersistenceService healthyService = persistence(healthy);
        GameState originalAutosave = richState();
        String originalFingerprint = fingerprint(originalAutosave);
        check(healthyService.autosave(IDENTITY, originalAutosave).status() == SaveResult.Status.SAVED,
                "Initial autosave failed");
        byte[] valid = Files.readAllBytes(healthy.slotPath(SaveSlot.AUTOSAVE));

        var failing = new AtomicFileSaveRepository(directory, new SaveGameCodec(),
                (slot, temporary, target) -> { throw new IOException("injected before replace"); });
        SaveResult failed = persistence(failing).autosave(IDENTITY, minimalState());
        check(failed.status() == SaveResult.Status.FAILED, "Autosave failure was not reported");
        check(Arrays.equals(valid, Files.readAllBytes(healthy.slotPath(SaveSlot.AUTOSAVE))),
                "Failed autosave damaged the previous valid autosave");
        check(fingerprint(healthyService.load(SaveSlot.AUTOSAVE).state())
                .equals(originalFingerprint), "Previous autosave is no longer loadable");
    }

    private static void manualAndAutosaveSlotsAreIndependent() throws Exception {
        Path directory = Files.createTempDirectory("civ-save-slots-");
        var repository = new AtomicFileSaveRepository(directory);
        GameSession session = new GameSession(richState(), IDENTITY, persistence(repository));
        for (SaveSlot slot : SaveSlot.manualSlots()) {
            session.saveManual(slot, slot.name(), (candidate, old, replacement) -> true);
        }
        session.autosave();
        check(SaveSlot.manualSlots().size() == 3 && repository.list().size() == 4
                && Arrays.stream(SaveSlot.values()).allMatch(repository::exists),
                "Repository does not expose three manual slots plus autosave");
        byte[] manual = Files.readAllBytes(repository.slotPath(SaveSlot.MANUAL_1));
        session.autosave();
        check(Arrays.equals(manual, Files.readAllBytes(repository.slotPath(SaveSlot.MANUAL_1))),
                "Autosave replaced a manual slot");
    }

    private static void repeatedLoadsAreEquivalentAndIndependent() throws Exception {
        Path directory = Files.createTempDirectory("civ-save-repeat-");
        var repository = new AtomicFileSaveRepository(directory);
        GameSession writer = new GameSession(richState(), IDENTITY, persistence(repository));
        writer.saveManual(SaveSlot.MANUAL_1, "Repeat", (slot, old, replacement) -> true);
        GameState first = persistence(repository).load(SaveSlot.MANUAL_1).state();
        GameState second = persistence(repository).load(SaveSlot.MANUAL_1).state();
        check(first != second && fingerprint(first).equals(fingerprint(second)),
                "Repeated loading did not create equivalent independent roots");
        first.player().townHall().stockpile().debit(ResourceBundle.of(1, 0, 0, 0));
        check(!fingerprint(first).equals(fingerprint(second)),
                "Two loads unexpectedly share mutable model objects");
    }

    private static void loadedRngProducesTheSameActualCombatOutcome() throws Exception {
        Path directory = Files.createTempDirectory("civ-save-outcome-");
        var repository = new AtomicFileSaveRepository(directory);
        persistence(repository).saveManual(SaveSlot.MANUAL_1, "Outcome", IDENTITY, richState(),
                (slot, old, replacement) -> true);
        GameState first = persistence(repository).load(SaveSlot.MANUAL_1).state();
        GameState second = persistence(repository).load(SaveSlot.MANUAL_1).state();
        AttackRequest request = new AttackRequest(new HexCoord(0, 1), new HexCoord(1, 1));
        var firstResult = new CombatService().playerAttack(first, request);
        var secondResult = new CombatService().playerAttack(second, request);
        check(firstResult.equals(secondResult) && fingerprint(first).equals(fingerprint(second)),
                "Restored RNG did not reproduce the same committed combat dice and outcome");
    }

    private static void loadedCopiesHaveIdenticalMultiTurnContinuation() throws Exception {
        Path directory = Files.createTempDirectory("civ-save-continuation-");
        var repository = new AtomicFileSaveRepository(directory);
        persistence(repository).saveManual(SaveSlot.MANUAL_1, "Continuation", IDENTITY,
                richState(), (slot, old, replacement) -> true);
        var first = new GameEngineFactory().createGame(
                persistence(repository).load(SaveSlot.MANUAL_1).state());
        var second = new GameEngineFactory().createGame(
                persistence(repository).load(SaveSlot.MANUAL_1).state());

        first.endTurn();
        second.endTurn();
        first.endTurn(); // completes the saved Worker order and creates a new authoritative ID
        second.endTurn();

        check(fingerprint(first.persistenceState()).equals(fingerprint(second.persistenceState())),
                "Equal loaded state/actions diverged through runtime IDs, tribes, or disasters");
    }

    private static void separateJvmProcessCanReopenEquivalentState() throws Exception {
        Path directory = Files.createTempDirectory("civ-save-process-");
        Path expected = directory.resolve("expected.txt");
        runChild("process-write", directory, expected);
        runChild("process-read", directory, expected);
    }

    private static void runChild(String mode, Path directory, Path expected) throws Exception {
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(javaExecutable, "-cp", System.getProperty("java.class.path"),
                SaveLoadSystemTestSuite.class.getName(), mode, directory.toString(),
                expected.toString()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        int exit = process.waitFor();
        check(exit == 0, "Separate JVM " + mode + " failed: " + output);
    }

    private static void processMode(String[] args) throws Exception {
        Path directory = Path.of(args[1]);
        Path expected = Path.of(args[2]);
        var repository = new AtomicFileSaveRepository(directory);
        if (args[0].equals("process-write")) {
            GameState state = richState();
            persistence(repository).saveManual(SaveSlot.MANUAL_3, "Restart", IDENTITY, state,
                    (slot, old, replacement) -> true);
            Files.writeString(expected, fingerprint(state));
            return;
        }
        if (args[0].equals("process-read")) {
            String actual = fingerprint(persistence(repository).load(SaveSlot.MANUAL_3).state());
            String wanted = Files.readString(expected);
            check(actual.equals(wanted), "Fresh JVM loaded a non-equivalent state: "
                    + firstDifference(wanted, actual));
            return;
        }
        throw new IllegalArgumentException("Unknown process test mode: " + args[0]);
    }

    private static void completedTurnEventTriggersOneAutosaveAttempt() throws Exception {
        Path directory = Files.createTempDirectory("civ-save-coordinator-");
        var repository = new AtomicFileSaveRepository(directory);
        GameSession session = new GameSession(richState(), IDENTITY, persistence(repository));
        SynchronousEventBus events = new SynchronousEventBus();
        try (AutosaveCoordinator coordinator = new AutosaveCoordinator(events, session)) {
            events.publish(new TurnResolutionCompletedEvent(27, session.state().stateVersion(), List.of()));
            check(coordinator.lastResult().orElseThrow().status() == SaveResult.Status.SAVED
                            && repository.exists(SaveSlot.AUTOSAVE),
                    "Successful turn completion did not autosave final state");
        }
    }

    private static GamePersistenceService persistence(AtomicFileSaveRepository repository) {
        return new GamePersistenceService(repository,
                new civilization.application.persistence.SaveValidator(),
                new civilization.application.persistence.DerivedStateRebuilder(),
                Clock.fixed(SAVED, ZoneOffset.UTC));
    }

    private static GameState richState() {
        GameMap map = landMap(3);
        HexCoord hallHex = new HexCoord(0, 0);
        map.addRiver(hallHex, new HexCoord(0, 1));
        ProductionQueue queue = new ProductionQueue();
        UnitProductionOrder order = new UnitProductionOrder(civilization.model.unit.UnitType.WORKER,
                ResourceBundle.of(4, 3, 0, 0), 3);
        order.advanceOneTurn();
        queue.enqueue(order);
        TownHall hall = new TownHall(BuildingId.random(), hallHex,
                new ResourceStockpile(ResourceBundle.of(70, 80, 60, 20),
                        ResourceBundle.of(150, 150, 150, 150)), queue);
        hall.upgradeTo(civilization.model.building.HallLevel.SETTLEMENT);
        hall.health().damage(17);
        Set<HexCoord> playerLand = map.tiles().stream().map(HexTile::coordinate)
                .filter(coord -> coord.distanceTo(hallHex) <= 2)
                .collect(java.util.stream.Collectors.toSet());
        VisibilityState visibility = new VisibilityState(
                Set.of(hallHex, new HexCoord(1, 0), new HexCoord(2, 0), new HexCoord(1, 1)),
                Set.of(hallHex, new HexCoord(1, 0), new HexCoord(1, 1)),
                Set.of(new HexCoord(2, 0)));
        TechnologyState technology = new TechnologyState();
        technology.complete(UpgradeId.STORAGE_1);
        technology.research(TechnologyId.NAVIGATION,
                DefaultRulesetFactory.create().technologyCatalog());
        PlayerState player = new PlayerState(hall, new UnitCapacity(12),
                new EmpireBorder(playerLand, map), visibility, technology);
        player.happiness().applyOnce("saved-event", 9);

        HexCoord farmHex = new HexCoord(1, 0);
        ProductionBuilding farm = new ProductionBuilding(BuildingId.random(),
                BuildingType.FARM, farmHex, 2);
        farm.health().damage(11);
        player.addBuilding(farm);
        WorkerUnit worker = new WorkerUnit(UnitId.random(), farmHex, new ActionPoints(4, 2));
        worker.healthState().orElseThrow().damage(27);
        worker.stationAt(farm.id());
        player.addUnit(worker);
        BuilderUnit builder = new BuilderUnit(UnitId.random(), hallHex, new ActionPoints(5, 3), 2);
        builder.markIdle();
        player.addUnit(builder);
        SwordsmanUnit soldier = new SwordsmanUnit(UnitId.random(), new HexCoord(0, 1),
                new ActionPoints(2, 1), FactionId.PLAYER, 100, 1);
        soldier.health().damage(23);
        soldier.setEmbarked(false);
        player.addUnit(soldier);

        WorldState world = new WorldState(map);
        world.infrastructure().add(new Road(InfrastructureId.random(), farmHex), map);
        Wall wall = new Wall(InfrastructureId.random(), new HexEdge(hallHex, new HexCoord(0, 1)),
                FactionId.PLAYER, new civilization.model.common.Health(100, 64));
        world.infrastructure().add(wall, map);
        var flood = new DisasterOccurrence(new DisasterId("saved-flood"), DisasterType.FLOOD,
                new HexCoord(1, -1), Set.of(new HexCoord(1, -1), hallHex), 26, 3);
        world.disasters().register(flood);

        TradingPost post = new TradingPost(new TradingPostId("post-save"), new HexCoord(2, -2),
                new TradeCooldown(26));
        world.tradingPosts().add(post, map);
        TribeId tribeId = new TribeId("saved-tribe");
        HexCoord campHex = new HexCoord(3, 0);
        TribeCamp camp = new TribeCamp(tribeId, campHex, 180);
        Tribe tribe = new Tribe(tribeId, "Saved Clan", TribeType.TRADER, camp, Set.of(campHex));
        tribe.discover();
        tribe.setAllianceBonusActive(true);
        tribe.markAllianceRewardGranted();
        tribe.grantNextDockDiscount();
        MissionId missionId = new MissionId("saved-mission");
        tribe.recordMissionOffer(20, missionId);
        world.tribes().add(tribe);
        world.structures().add(camp.structure(), map);
        world.territories().claim(campHex, tribeId.asFactionId(), map);
        RelationshipState relationship = new RelationshipState(tribeId);
        relationship.adjustScore(75);
        relationship.formAlliance();
        world.relationships().add(relationship);
        Mission mission = new Mission(missionId, tribeId, MissionType.TRADER_ROAD, 20, 30);
        world.missions().add(mission);
        world.missions().accept(missionId, 21);
        TribeTradeEndpoint trade = new TribeTradeEndpoint(tribeId, TribeTradeProfile.TRADER,
                new TradeCooldown(25), 10);
        world.tribeTrades().add(trade);
        SwordsmanUnit guard = new SwordsmanUnit(UnitId.random(), campHex, new ActionPoints(2, 1),
                tribeId.asFactionId(), 90, 1);
        guard.health().damage(12);
        world.units().add(guard, map);
        world.units().add(new SwordsmanUnit(UnitId.random(), new HexCoord(1, 1),
                new ActionPoints(2), FactionId.BARBARIANS, 100, 1), map);
        WildAnimal bear = WildAnimal.bear(WildAnimalId.random(), new HexCoord(-3, 0),
                new HexCoord(-3, 0));
        bear.spendActionPoint();
        bear.health().damage(7);
        world.wildAnimals().add(bear, map);
        world.combatHistory().recordKill(new CombatKillRecord(UnitId.random(),
                tribeId.asFactionId(), new HexCoord(2, 0), 24));

        CrisisState crises = new CrisisState();
        crises.setShortage(ResourceBundle.of(0, 1, 0, 0));
        SplitMix64GameplayRandom random = new SplitMix64GameplayRandom(88991L);
        random.nextLong();
        random.nextInt(17);
        GameState state = new GameState(world, player,
                new TurnState(27, GamePhase.PLAYER_ACTIONS,
                        civilization.model.economy.TurnEconomyReport.EMPTY), crises, 712381L,
                new SeasonState(Season.AUTUMN, 7), random);
        state.incrementVersion();
        state.incrementVersion();
        return state;
    }

    private static GameState minimalState() {
        GameMap map = landMap(1);
        HexCoord center = new HexCoord(0, 0);
        TownHall hall = new TownHall(BuildingId.random(), center,
                new ResourceStockpile(ResourceBundle.of(1, 1, 0, 0),
                        ResourceBundle.of(100, 100, 100, 100)), new ProductionQueue());
        PlayerState player = new PlayerState(hall, new UnitCapacity(5),
                new EmpireBorder(map.tiles().stream().map(HexTile::coordinate)
                        .collect(java.util.stream.Collectors.toSet()), map),
                new VisibilityState(Set.of(center), Set.of(center)), new TechnologyState());
        return new GameState(map, player, new TurnState(), new CrisisState(), 2L);
    }

    private static GameMap landMap(int radius) {
        List<HexTile> tiles = new ArrayList<>();
        for (int q = -radius; q <= radius; q++) {
            for (int r = -radius; r <= radius; r++) {
                HexCoord coord = new HexCoord(q, r);
                if (new HexCoord(0, 0).distanceTo(coord) <= radius) {
                    tiles.add(coord.equals(new HexCoord(1, 0))
                            ? new HexTile(coord, civilization.model.map.TerrainType.GRASSLAND,
                            new ResourceDeposit(NaturalResourceType.WHEAT, 100))
                            : new HexTile(coord, civilization.model.map.TerrainType.GRASSLAND));
                }
            }
        }
        return new GameMap(tiles);
    }

    /** A deterministic exhaustive field projection; identity-bearing references are included by ID. */
    private static String fingerprint(GameState state) {
        StringBuilder out = new StringBuilder();
        var report = state.turn().lastEconomyReport();
        out.append(state.mapSeed()).append('|').append(state.stateVersion()).append('|')
                .append(state.turn().turnNumber()).append('|').append(state.turn().phase()).append('|')
                .append(resources(report.produced())).append('/').append(resources(report.consumed()))
                .append('/').append(resources(report.accepted())).append('/')
                .append(resources(report.overflow())).append('/').append(resources(report.shortage()))
                .append('|').append(state.season().season())
                .append('|').append(state.season().turnWithinSeason()).append('|')
                .append(state.season().weather()).append('|').append(state.gameplayRandom().snapshot())
                .append('|').append(state.crises().activeCrises().stream().sorted().toList()).append('|')
                .append(resources(state.crises().shortage())).append('\n');
        state.map().tiles().stream().sorted(java.util.Comparator.comparing(HexTile::coordinate,
                        java.util.Comparator.comparingInt(HexCoord::q).thenComparingInt(HexCoord::r)))
                .forEach(tile -> out.append("tile:").append(tile.coordinate()).append(':')
                        .append(tile.terrain()).append(':').append(tile.resourceDeposits().entrySet().stream()
                                .sorted(java.util.Map.Entry.comparingByKey())
                                .map(entry -> entry.getKey() + "=" + entry.getValue().remainingAmount()
                                        + "/" + entry.getValue().originalAmount()).toList()).append('\n'));
        out.append("rivers:").append(state.map().riverEdges().stream().sorted(java.util.Comparator.comparing(Object::toString)).toList()).append('\n')
                .append("border:").append(sorted(state.player().border().ownedHexes())).append('\n')
                .append("discovered:").append(sorted(state.player().visibility().discoveredHexes())).append('\n')
                .append("visible:").append(sorted(state.player().visibility().currentlyVisibleHexes())).append('\n')
                .append("explorerFog:").append(sorted(state.player().visibility().explorerDiscoveredHexes())).append('\n');
        var hall = state.player().townHall();
        out.append("hall:").append(hall.id()).append(':').append(hall.position()).append(':')
                .append(hall.ownerId()).append(':').append(hall.level()).append(':').append(hall.defense())
                .append(':').append(hall.health().current()).append('/').append(hall.health().maximum())
                .append(':').append(resources(hall.stockpile().amounts())).append(':')
                .append(resources(hall.stockpile().capacities()))
                .append(':').append(order(hall.productionQueue())).append('\n');
        state.player().constructedBuildings().stream().sorted(java.util.Comparator.comparing(b -> b.id().toString()))
                .forEach(b -> out.append("building:").append(b.id()).append(':').append(b.type()).append(':')
                        .append(b.ownerId()).append(':').append(b.position()).append(':').append(b.status())
                        .append(':').append(b.health().current()).append('/').append(b.health().maximum())
                        .append(':').append(b.consecutiveUnpaidUpkeepTurns()).append('\n'));
        state.player().units().stream().sorted(java.util.Comparator.comparing(u -> u.id().toString()))
                .forEach(u -> appendUnit(out, "unit", u));
        out.append("unitCap:").append(state.player().unitCapacity().baseCapacity()).append(':')
                .append(state.player().totalUnitCapacity()).append('\n')
                .append("technology:").append(state.player().technologies().completedUpgrades().stream()
                        .sorted().toList()).append(':')
                .append(state.player().technologies().researchedTechnologies().stream()
                        .sorted().toList()).append('\n')
                .append("happiness:").append(state.player().happiness().accumulatedValue()).append(':')
                .append(state.player().happiness().appliedEvents().entrySet().stream()
                        .sorted(java.util.Map.Entry.comparingByKey()).toList()).append('\n');
        state.world().infrastructure().all().stream().sorted(java.util.Comparator.comparing(i -> i.id().toString()))
                .forEach(i -> {
                    out.append("infra:").append(i.id());
                    if (i instanceof Road road) out.append(":ROAD:").append(road.ownerId())
                            .append(':').append(road.position());
                    if (i instanceof Wall savedWall) out.append(":WALL:").append(savedWall.ownerId())
                            .append(':').append(savedWall.edge()).append(':')
                            .append(savedWall.health().current()).append('/')
                            .append(savedWall.health().maximum());
                    out.append('\n');
                });
        state.world().disasters().occurrences().stream().sorted(java.util.Comparator.comparing(d -> d.id().value()))
                .forEach(d -> out.append("disaster:").append(d.id()).append(':').append(d.type())
                        .append(':').append(d.center()).append(':').append(sorted(d.affectedHexes()))
                        .append(':').append(d.occurredOnTurn()).append(':').append(d.effectTurns()).append(':')
                        .append(state.world().disasters().remainingEffectTurns(d.id())).append('\n'));
        state.world().tradingPosts().all().stream().sorted(java.util.Comparator.comparing(p -> p.id().value()))
                .forEach(p -> out.append("post:").append(p.id()).append(':').append(p.position()).append(':')
                        .append(p.tradeCooldown().lastUsedTurn()).append('\n'));
        state.world().tribeTrades().all().stream().sorted(java.util.Comparator.comparing(t -> t.tribeId().value()))
                .forEach(t -> out.append("tribeTrade:").append(t.tribeId()).append(':').append(t.profile())
                        .append(':').append(t.tradeCooldown().lastUsedTurn()).append(':')
                        .append(t.rateBonusPercent()).append('\n'));
        state.world().relationships().all().stream().sorted(java.util.Comparator.comparing(r -> r.tribeId().value()))
                .forEach(r -> out.append("relation:").append(r.tribeId()).append(':').append(r.score())
                        .append(':').append(r.stance()).append(':').append(r.band()).append(':')
                        .append(r.behavior().getClass().getSimpleName()).append('\n'));
        state.world().missions().all().stream().sorted(java.util.Comparator.comparing(m -> m.id().value()))
                .forEach(m -> out.append("mission:").append(m.id()).append(':').append(m.tribeId())
                        .append(':').append(m.type()).append(':').append(m.issuedTurn()).append(':')
                        .append(m.deadlineTurn()).append(':').append(m.status()).append(':')
                        .append(m.acceptedTurn()).append(':').append(m.resolvedTurn()).append('\n'));
        state.world().tribes().all().stream().sorted(java.util.Comparator.comparing(t -> t.id().value()))
                .forEach(t -> out.append("tribe:").append(t.id()).append(':').append(t.name()).append(':')
                        .append(t.type()).append(':').append(t.camp().structure().id()).append(':')
                        .append(t.camp().position()).append(':').append(t.camp().health().current()).append(':')
                        .append(sorted(t.territory())).append(':').append(t.isDiscovered()).append(':')
                        .append(t.isDefeated()).append(':').append(t.hasOutpost()).append(':')
                        .append(t.isAllianceBonusActive()).append(':').append(t.isAllianceRewardGranted())
                        .append(':').append(t.hasNextDockDiscount()).append(':').append(t.lastMissionOfferTurn())
                        .append(':').append(t.lastMissionFailureTurn()).append(':').append(t.lastPlayerAttackTurn())
                        .append(':').append(t.lastAttackerHex()).append(':').append(t.currentMissionId())
                        .append(':').append(t.warnedIntruders()).append('\n'));
        state.world().units().all().stream().sorted(java.util.Comparator.comparing(u -> u.id().toString()))
                .forEach(u -> appendUnit(out, "worldUnit", u));
        state.world().structures().all().stream().sorted(java.util.Comparator.comparing(s -> s.id().toString()))
                .forEach(s -> out.append("structure:").append(s.id()).append(':').append(s.type()).append(':')
                        .append(s.ownerId()).append(':').append(s.position()).append(':')
                        .append(s.health().current()).append('/').append(s.health().maximum()).append('\n'));
        state.world().wildAnimals().all().stream().sorted(java.util.Comparator.comparing(a -> a.id().toString()))
                .forEach(a -> out.append("animal:").append(a.id()).append(':').append(a.position()).append(':')
                        .append(a.nest()).append(':').append(a.health().current()).append('/')
                        .append(a.health().maximum()).append(':').append(a.actionPoints()).append(':')
                        .append(a.attackStrength()).append('\n'));
        out.append("territories:").append(state.world().territories().ownership().entrySet().stream()
                        .sorted(java.util.Comparator.comparing(entry -> entry.getKey(),
                                java.util.Comparator.comparingInt(HexCoord::q)
                                        .thenComparingInt(HexCoord::r))).toList()).append('\n')
                .append("kills:").append(state.world().combatHistory().kills()).append('\n');
        return out.toString();
    }

    private static void appendUnit(StringBuilder out, String label,
            civilization.model.unit.Unit unit) {
        out.append(label).append(':').append(unit.id()).append(':').append(unit.type()).append(':')
                .append(unit.ownerId()).append(':').append(unit.position()).append(':')
                .append(unit.actionPoints().current()).append('/').append(unit.actionPoints().capacity())
                .append(':').append(unit.status()).append(':').append(unit.isEmbarked());
        unit.healthState().ifPresent(health -> out.append(":hp=")
                .append(health.current()).append('/').append(health.maximum()));
        if (unit instanceof civilization.model.unit.MilitaryUnit military) out.append(':')
                .append("range=").append(military.attackRange());
        if (unit instanceof BuilderUnit builder) out.append(":charges=").append(builder.remainingCharges());
        if (unit instanceof WorkerUnit worker) out.append(":station=").append(worker.stationedAt());
        out.append('\n');
    }

    private static String order(ProductionQueue queue) {
        return queue.currentOrder().map(value -> value.getClass().getSimpleName() + ':'
                + resources(value.paidCost()) + ':' + value.totalTurns() + ':' + value.remainingTurns()
                + (value instanceof UnitProductionOrder unit ? ':' + unit.unitType().name() : ""))
                .orElse("empty");
    }

    private static String resources(ResourceBundle bundle) {
        return Arrays.stream(ResourceType.values())
                .map(type -> type.name() + '=' + bundle.amountOf(type))
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }

    private static List<HexCoord> sorted(java.util.Collection<HexCoord> coordinates) {
        return coordinates.stream().sorted(java.util.Comparator.comparingInt(HexCoord::q)
                .thenComparingInt(HexCoord::r)).toList();
    }

    private static void replaceChecksum(byte[] bytes) throws Exception {
        byte[] checksum = MessageDigest.getInstance("SHA-256")
                .digest(Arrays.copyOf(bytes, bytes.length - 32));
        System.arraycopy(checksum, 0, bytes, bytes.length - 32, 32);
    }

    private static String firstDifference(String before, String after) {
        String[] left = before.split("\\n");
        String[] right = after.split("\\n");
        int count = Math.min(left.length, right.length);
        for (int index = 0; index < count; index++) {
            if (!left[index].equals(right[index])) {
                return "before=[" + left[index] + "] after=[" + right[index] + "]";
            }
        }
        return "line counts " + left.length + " and " + right.length;
    }

    private static void expect(Class<? extends Throwable> expected, ThrowingRunnable action) {
        try { action.run(); }
        catch (Throwable failure) {
            if (expected.isInstance(failure)) return;
            throw new AssertionError("Expected " + expected.getSimpleName() + " but got " + failure, failure);
        }
        throw new AssertionError("Expected " + expected.getSimpleName());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @FunctionalInterface private interface ThrowingRunnable { void run() throws Exception; }
}
