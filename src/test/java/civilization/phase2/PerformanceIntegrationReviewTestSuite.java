package civilization.phase2;

import civilization.application.GameFactory;
import civilization.application.action.ActionPreview;
import civilization.application.event.Subscription;
import civilization.application.event.SynchronousEventBus;
import civilization.application.event.TurnResolutionStartedEvent;
import civilization.application.factory.BuildingFactory;
import civilization.application.factory.UnitFactory;
import civilization.application.generation.SeededProceduralMapGenerator;
import civilization.application.generation.TribeGenerator;
import civilization.application.persistence.SaveGameCodec;
import civilization.application.persistence.GameIdentity;
import civilization.application.persistence.GamePersistenceService;
import civilization.application.persistence.SaveEntry;
import civilization.application.persistence.SaveMetadata;
import civilization.application.persistence.SaveSlot;
import civilization.application.port.SaveGameRepository;
import civilization.application.query.GameSnapshot;
import civilization.application.service.AdjacencyService;
import civilization.application.service.AutoExploreService;
import civilization.application.service.CombatService;
import civilization.application.service.DisasterEligibilityPolicy;
import civilization.application.service.MovementService;
import civilization.application.service.ProductionCalculator;
import civilization.application.service.TribeTurnService;
import civilization.application.service.VisibilityService;
import civilization.application.event.EventBus;
import civilization.bootstrap.GameEngineFactory;
import civilization.controller.GameController;
import civilization.model.building.BuildingType;
import civilization.model.combat.AttackRequest;
import civilization.model.combat.CombatHistory;
import civilization.model.combat.CombatKillRecord;
import civilization.model.disaster.DisasterId;
import civilization.model.disaster.DisasterOccurrence;
import civilization.model.disaster.DisasterState;
import civilization.model.disaster.DisasterType;
import civilization.model.game.GamePhase;
import civilization.model.game.GameState;
import civilization.model.game.TurnState;
import civilization.model.identity.FactionId;
import civilization.model.map.GameMap;
import civilization.model.map.HexCoord;
import civilization.model.map.NaturalResourceType;
import civilization.model.map.TerrainType;
import civilization.model.time.Season;
import civilization.model.time.SeasonState;
import civilization.model.unit.ActionPoints;
import civilization.model.unit.ExplorerUnit;
import civilization.model.unit.SwordsmanUnit;
import civilization.model.unit.UnitId;
import civilization.rules.DefaultRulesetFactory;
import civilization.rules.GameRules;
import civilization.view.animation.AnimationManager;
import civilization.view.animation.VisualEffectManager;
import civilization.view.game.GamePanel;
import civilization.view.render.CameraState;
import civilization.view.render.HexLayout;
import civilization.view.render.MapRenderer;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;

/** Reproducible subsystem profile and regression guard for the full performance review. */
public final class PerformanceIntegrationReviewTestSuite {
    private static final GameRules RULES = DefaultRulesetFactory.create();
    private static final Map<String, Long> METRICS = new LinkedHashMap<>();

    private PerformanceIntegrationReviewTestSuite() { }

    public static void main(String[] args) throws Exception {
        radiusLookupMatchesBruteForce();
        GameState highTurn = highTurnState(30, 10_000, 907_331L);
        GameState dense = denseBuildingState(20, 260, 771_901L);
        profileFog(highTurn);
        profileAdjacency(dense);
        profileProductionProjection(dense);
        profileSnapshots(highTurn);
        profilePathfinding(highTurn);
        profileTribes(highTurn);
        profileCombat(highTurn);
        profileDisasterSelection(highTurn);
        profileSaveLoad(highTurn);
        profileRendering(highTurn);
        profileEventBusAndListenerCleanup();
        profileUiFileIoOffEdt();
        profileHighTurnIntegration(highTurn);
        profileBoundedHighTurnHistory();
        long retained = retainedHeapAfterRepeatedWork(highTurn);
        METRICS.put("retained_heap_delta_bytes", retained);
        var heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        METRICS.put("heap_used_after_gc_bytes", heap.getUsed());
        METRICS.put("heap_committed_bytes", heap.getCommitted());
        METRICS.put("heap_max_bytes", heap.getMax());
        METRICS.forEach((name, value) -> System.out.println("PERF " + name + "=" + value));
        System.out.println("PerformanceIntegrationReviewTestSuite: all checks passed");
    }

    private static void radiusLookupMatchesBruteForce() {
        GameMap map = new SeededProceduralMapGenerator().generate(12, 4L, new HexCoord(0, 0));
        for (HexCoord center : List.of(new HexCoord(0, 0), new HexCoord(11, -2),
                new HexCoord(-10, 4))) {
            for (int radius = 0; radius <= 4; radius++) {
                int testedRadius = radius;
                Set<HexCoord> expected = map.tiles().stream().map(tile -> tile.coordinate())
                        .filter(coord -> center.distanceTo(coord) <= testedRadius)
                        .collect(java.util.stream.Collectors.toSet());
                check(Set.copyOf(map.coordinatesWithin(center, radius)).equals(expected),
                        "Indexed radius lookup changed Fog/effect membership");
            }
        }
    }

    private static void profileFog(GameState state) {
        VisibilityService visibility = new VisibilityService();
        long elapsed = measure(() -> {
            for (int i = 0; i < 2_000; i++) visibility.refresh(state, RULES);
        });
        recordMillis("fog_refresh_2000_ms", elapsed, 2_000);
    }

    private static void profileAdjacency(GameState state) {
        AdjacencyService adjacency = new AdjacencyService();
        Map<?, ?> expected = adjacency.productionBonuses(state);
        long elapsed = measure(() -> {
            for (int i = 0; i < 1_000; i++) {
                check(adjacency.productionBonuses(state).equals(expected),
                        "Adjacency result changed across identical reads");
            }
        });
        recordMillis("adjacency_1000_ms", elapsed, 3_000);
    }

    private static void profileProductionProjection(GameState state) {
        ProductionCalculator calculator = new ProductionCalculator();
        var expected = calculator.calculate(state, RULES);
        long elapsed = measure(() -> {
            for (int i = 0; i < 1_000; i++) {
                check(calculator.calculate(state, RULES).equals(expected),
                        "Repeated production projection changed authoritative output");
            }
        });
        recordMillis("production_projection_1000_ms", elapsed, 4_000);
    }

    private static void profileSnapshots(GameState state) {
        var engine = new GameEngineFactory().createGame(state);
        GameSnapshot expected = engine.snapshot();
        long elapsed = measure(() -> {
            for (int i = 0; i < 500; i++) {
                check(engine.snapshot().equals(expected),
                        "Repeated immutable snapshot changed without model mutation");
            }
        });
        recordMillis("full_snapshot_500_ms", elapsed, 5_000);
    }

    private static void profilePathfinding(GameState state) {
        state.player().visibility().discoverAll(state.map().tiles().stream()
                .map(tile -> tile.coordinate()).collect(java.util.stream.Collectors.toSet()));
        UnitId explorer = state.player().units().stream().filter(ExplorerUnit.class::isInstance)
                .findFirst().orElseThrow().id();
        AutoExploreService service = new AutoExploreService(new MovementService(new VisibilityService()));
        long elapsed = measure(() -> {
            for (int i = 0; i < 100; i++) {
                check(service.planNext(state, explorer, RULES).isEmpty(),
                        "Fully discovered map unexpectedly has an exploration frontier");
            }
        });
        recordMillis("pathfinding_full_map_100_ms", elapsed, 4_000);
    }

    private static void profileTribes(GameState state) {
        TribeTurnService tribes = new TribeTurnService(EventBus.noOp());
        long elapsed = measure(() -> {
            for (int i = 0; i < 100; i++) tribes.process(state, RULES);
        });
        recordMillis("tribe_behavior_100_ms", elapsed, 3_000);
    }

    private static void profileCombat(GameState state) {
        HexCoord center = state.player().townHall().position();
        HexCoord target = center.neighbors().stream().filter(state.map()::contains)
                .filter(coord -> state.map().requireTile(coord).terrain() != TerrainType.MOUNTAIN_RANGE)
                .findFirst().orElseThrow();
        SwordsmanUnit attacker = new SwordsmanUnit(UnitId.random(), center, new ActionPoints(2),
                FactionId.PLAYER, 100, 1);
        state.player().addUnit(attacker);
        state.world().units().add(new SwordsmanUnit(UnitId.random(), target, new ActionPoints(2),
                FactionId.BARBARIANS, 100, 1), state.map());
        new VisibilityService().refresh(state, RULES);
        CombatService combat = new CombatService();
        AttackRequest request = new AttackRequest(center, target);
        ActionPreview expected = combat.previewPlayerAttack(state, request);
        check(expected.allowed(), "Combat profile setup is invalid: " + expected.reason());
        long elapsed = measure(() -> {
            for (int i = 0; i < 20_000; i++) {
                check(combat.previewPlayerAttack(state, request).equals(expected),
                        "Read-only combat preview is nondeterministic");
            }
        });
        recordMillis("combat_preview_20000_ms", elapsed, 4_000);
    }

    private static void profileDisasterSelection(GameState state) {
        DisasterEligibilityPolicy eligibility = new DisasterEligibilityPolicy();
        EnumMap<DisasterType, Set<HexCoord>> expected = new EnumMap<>(DisasterType.class);
        for (DisasterType type : DisasterType.values()) {
            expected.put(type, eligibility.eligibleCenters(state, type));
        }
        long elapsed = measure(() -> {
            for (int i = 0; i < 100; i++) {
                for (DisasterType type : DisasterType.values()) {
                    check(eligibility.eligibleCenters(state, type).equals(expected.get(type)),
                            "Disaster eligibility changed without state mutation");
                }
            }
        });
        recordMillis("disaster_eligibility_all_types_100_ms", elapsed, 4_000);
    }

    private static void profileSaveLoad(GameState state) {
        SaveGameCodec codec = new SaveGameCodec();
        SaveMetadata metadata = metadata(state);
        byte[] first = codec.encode(metadata, state);
        long elapsed = measure(() -> {
            for (int i = 0; i < 10; i++) {
                byte[] bytes = codec.encode(metadata, state);
                var decoded = codec.decode(bytes);
                check(decoded.state().turn().turnNumber() == state.turn().turnNumber()
                                && decoded.state().map().size() == state.map().size(),
                        "Large save codec round trip changed state dimensions");
            }
        });
        METRICS.put("large_save_bytes", (long) first.length);
        recordMillis("save_encode_decode_10_ms", elapsed, 8_000);
    }

    private static void profileRendering(GameState state) {
        GameSnapshot snapshot = new GameEngineFactory().createGame(state).snapshot();
        BufferedImage image = new BufferedImage(1280, 800, BufferedImage.TYPE_INT_ARGB);
        HexLayout layout = new HexLayout(42);
        CameraState camera = new CameraState();
        AnimationManager animations = new AnimationManager(layout, () -> { });
        VisualEffectManager effects = new VisualEffectManager(() -> { });
        MapRenderer renderer = new MapRenderer();
        long elapsed = measure(() -> {
            for (int i = 0; i < 300; i++) {
                Graphics2D graphics = image.createGraphics();
                try {
                    renderer.render(graphics, snapshot.map(), layout, camera, animations, effects,
                            Map.of(), image.getWidth(), image.getHeight());
                } finally {
                    graphics.dispose();
                }
            }
        });
        animations.cancelAll();
        effects.cancelAll();
        recordMillis("map_render_300_frames_ms", elapsed, 8_000);
    }

    private static void profileEventBusAndListenerCleanup() throws Exception {
        SynchronousEventBus bus = new SynchronousEventBus();
        AtomicInteger delivered = new AtomicInteger();
        List<Subscription> subscriptions = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            subscriptions.add(bus.subscribe(TurnResolutionStartedEvent.class,
                    ignored -> delivered.incrementAndGet()));
        }
        long elapsed = measure(() -> {
            for (int i = 0; i < 100_000; i++) bus.publish(new TurnResolutionStartedEvent(i));
        });
        check(delivered.get() == 3_200_000 && bus.subscriberCount() == 32,
                "Event Bus lost or duplicated ordered listeners");
        subscriptions.forEach(Subscription::close);
        check(bus.subscriberCount() == 0, "Closed Event Bus subscriptions leaked");
        recordMillis("event_bus_100000_x32_ms", elapsed, 5_000);

        GameController[] controller = new GameController[1];
        SynchronousEventBus[] engineBus = new SynchronousEventBus[1];
        SwingUtilities.invokeAndWait(() -> {
            var engine = new GameEngineFactory().createGame(40L);
            engineBus[0] = (SynchronousEventBus) engine.eventBus();
            controller[0] = new GameController(engine, new GamePanel(), (title, message) -> true);
            check(engineBus[0].subscriberCount() == 2,
                    "Gameplay controller registered an unexpected listener count");
            controller[0].close();
        });
        check(engineBus[0].subscriberCount() == 0,
                "Closing GameController leaked Event Bus listeners");
    }

    private static void profileHighTurnIntegration(GameState state) {
        var engine = new GameEngineFactory().createGame(state);
        int startingTurn = state.turn().turnNumber();
        long elapsed = measure(() -> {
            for (int i = 0; i < 50 && !state.isGameOver(); i++) engine.endTurn();
        });
        check(state.turn().turnNumber() > startingTurn,
                "High-turn integration did not advance the game");
        recordMillis("high_turn_full_pipeline_50_ms", elapsed, 8_000);
    }

    private static void profileBoundedHighTurnHistory() {
        long elapsed = measure(() -> {
            CombatHistory combat = new CombatHistory();
            for (int turn = 1; turn <= 10_000; turn++) {
                combat.recordKill(new CombatKillRecord(UnitId.random(), FactionId.BARBARIANS,
                        new HexCoord(0, 0), turn));
            }
            combat.pruneBefore(9_991);
            check(combat.kills().size() == 10,
                    "Combat mission evidence was not bounded by its observable turn window");

            DisasterState disasters = new DisasterState();
            for (int turn = 1; turn <= 10_000; turn++) {
                disasters.advancePersistentEffectsAndPrune(turn);
                HexCoord center = new HexCoord(0, 0);
                disasters.register(new DisasterOccurrence(new DisasterId("quake:" + turn),
                        DisasterType.EARTHQUAKE, center, Set.of(center), turn, 0));
            }
            check(disasters.occurrences().size() == 1,
                    "Expired disaster history grew with the Turn number");
            HexCoord center = new HexCoord(1, 0);
            disasters.register(new DisasterOccurrence(new DisasterId("bear:10000"),
                    DisasterType.BEAR_ATTACK, center, Set.of(center), 10_000, 0));
            disasters.advancePersistentEffectsAndPrune(10_005);
            check(disasters.bearAttackOnCooldown(center, 10_005),
                    "Disaster pruning shortened the Bear Attack cooldown");
            disasters.advancePersistentEffectsAndPrune(10_006);
            check(!disasters.bearAttackOnCooldown(center, 10_006),
                    "Expired Bear Attack cooldown was retained");
        });
        recordMillis("bounded_history_10000_events_ms", elapsed, 3_000);
    }

    private static void profileUiFileIoOffEdt() throws Exception {
        CountDownLatch scanned = new CountDownLatch(1);
        AtomicBoolean scanRanOnEdt = new AtomicBoolean();
        SaveGameRepository slow = new SaveGameRepository() {
            @Override public List<SaveEntry> list() {
                scanRanOnEdt.set(SwingUtilities.isEventDispatchThread());
                try { Thread.sleep(350); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
                finally { scanned.countDown(); }
                return java.util.Arrays.stream(SaveSlot.values()).map(SaveEntry::empty).toList();
            }
            @Override public void write(SaveSlot slot, SaveMetadata metadata, GameState state) {
                throw new UnsupportedOperationException();
            }
            @Override public LoadedGame read(SaveSlot slot) { throw new UnsupportedOperationException(); }
            @Override public java.util.Optional<SaveMetadata> metadata(SaveSlot slot) {
                return java.util.Optional.empty();
            }
            @Override public boolean exists(SaveSlot slot) { return false; }
            @Override public void delete(SaveSlot slot) { }
        };
        GameController[] controller = new GameController[1];
        GamePanel[] panel = new GamePanel[1];
        long[] dispatchMs = new long[1];
        SwingUtilities.invokeAndWait(() -> {
            long started = System.nanoTime();
            panel[0] = new GamePanel();
            controller[0] = new GameController(new GameEngineFactory().createGame(98L),
                    panel[0], (title, message) -> true,
                    new GamePersistenceService(slow), GameIdentity.newGame());
            dispatchMs[0] = (System.nanoTime() - started) / 1_000_000L;
            check(!panel[0].pause().saveButton(SaveSlot.MANUAL_1).isEnabled()
                            && panel[0].pause().saveButton(SaveSlot.MANUAL_1)
                            .getToolTipText().contains("Checking save slots"),
                    "Manual save was enabled before occupied-slot validation completed");
        });
        check(dispatchMs[0] < 250, "Slow save-slot I/O blocked controller construction on EDT");
        check(scanned.await(2, TimeUnit.SECONDS) && !scanRanOnEdt.get(),
                "Save-slot scanning did not execute on a background worker");
        SwingUtilities.invokeAndWait(controller[0]::close);
        METRICS.put("edt_dispatch_with_350ms_slot_io_ms", dispatchMs[0]);
    }

    private static long retainedHeapAfterRepeatedWork(GameState state) throws InterruptedException {
        SaveGameCodec codec = new SaveGameCodec();
        SaveMetadata metadata = metadata(state);
        forceGc();
        long before = usedHeap();
        for (int i = 0; i < 250; i++) new GameEngineFactory().createGame(state).snapshot();
        for (int i = 0; i < 20; i++) codec.decode(codec.encode(metadata, state));
        forceGc();
        long delta = Math.max(0, usedHeap() - before);
        check(delta < 96L * 1024 * 1024,
                "Repeated snapshot/save work retained excessive heap: " + delta);
        return delta;
    }

    private static GameState highTurnState(int radius, int turn, long seed) {
        VisibilityService visibility = new VisibilityService();
        GameMap map = new SeededProceduralMapGenerator().generate(radius, seed, new HexCoord(0, 0));
        GameState base = new GameFactory(visibility, new UnitFactory()).create(
                map, new HexCoord(0, 0), seed, RULES);
        GameState state = new GameState(base.world(), base.player(),
                new TurnState(turn, GamePhase.PLAYER_ACTIONS,
                        civilization.model.economy.TurnEconomyReport.EMPTY),
                base.crises(), seed, new SeasonState(Season.AUTUMN, 7), base.gameplayRandom());
        new TribeGenerator().generate(state, RULES);
        return state;
    }

    private static GameState denseBuildingState(int radius, int maximumBuildings, long seed) {
        VisibilityService visibility = new VisibilityService();
        GameMap map = new SeededProceduralMapGenerator().generate(radius, seed, new HexCoord(0, 0));
        GameState state = new GameFactory(visibility, new UnitFactory()).create(
                map, new HexCoord(0, 0), seed, RULES);
        Set<HexCoord> land = map.tiles().stream()
                .filter(tile -> tile.terrain() != TerrainType.MOUNTAIN_RANGE)
                .map(tile -> tile.coordinate()).collect(java.util.stream.Collectors.toSet());
        state.player().border().claimAll(land, map);
        Set<HexCoord> posts = state.world().tradingPosts().all().stream()
                .map(post -> post.position()).collect(java.util.stream.Collectors.toSet());
        BuildingFactory factory = new BuildingFactory();
        int added = 0;
        for (var tile : map.tiles()) {
            if (added >= maximumBuildings || tile.coordinate().equals(new HexCoord(0, 0))
                    || posts.contains(tile.coordinate())) continue;
            BuildingType type = buildingFor(tile.resourceDeposits().keySet());
            if (type == null) continue;
            state.player().addBuilding(factory.create(type, tile.coordinate(), RULES));
            added++;
        }
        check(added >= 100, "Dense adjacency fixture did not create enough buildings: " + added);
        state.validateSpatialReferences();
        return state;
    }

    private static BuildingType buildingFor(Set<NaturalResourceType> resources) {
        if (resources.contains(NaturalResourceType.WOOD)) return BuildingType.LUMBER_MILL;
        if (resources.contains(NaturalResourceType.IRON)) return BuildingType.IRON_MINE;
        if (resources.contains(NaturalResourceType.STONE)) return BuildingType.STONE_MINE;
        if (resources.contains(NaturalResourceType.WHEAT)
                || resources.contains(NaturalResourceType.RICE)) return BuildingType.FARM;
        if (resources.contains(NaturalResourceType.CATTLE)
                || resources.contains(NaturalResourceType.SHEEP)) return BuildingType.STABLE;
        return null;
    }

    private static SaveMetadata metadata(GameState state) {
        Instant instant = Instant.parse("2026-08-23T00:00:00Z");
        return new SaveMetadata("Performance", UUID.fromString(
                "fd5edb3b-4023-47a2-9295-72cbe910916f"), instant, instant,
                state.turn().turnNumber(), state.season().season(),
                state.season().turnWithinSeason(), state.season().weather(),
                state.player().townHall().level(), "large-map performance fixture");
    }

    private static long measure(Runnable action) {
        long started = System.nanoTime();
        action.run();
        return System.nanoTime() - started;
    }

    private static void recordMillis(String name, long nanos, long budgetMs) {
        long millis = nanos / 1_000_000L;
        METRICS.put(name, millis);
        check(millis < budgetMs, name + " exceeded " + budgetMs + " ms: " + millis);
    }

    private static long usedHeap() {
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    private static void forceGc() throws InterruptedException {
        System.gc();
        Thread.sleep(80);
        System.runFinalization();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
