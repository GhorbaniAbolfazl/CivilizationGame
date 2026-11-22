package civilization.phase2;

import civilization.application.GameFactory;
import civilization.application.event.EventBus;
import civilization.application.event.GameEvent;
import civilization.application.event.SynchronousEventBus;
import civilization.application.event.TurnResolutionCompletedEvent;
import civilization.application.event.TurnResolutionStartedEvent;
import civilization.application.event.TurnStepCompletedEvent;
import civilization.application.event.TurnStepStartedEvent;
import civilization.application.factory.UnitFactory;
import civilization.application.notification.NotificationCategory;
import civilization.application.notification.NotificationCenter;
import civilization.application.notification.NotificationSeverity;
import civilization.application.service.ActionPointService;
import civilization.application.service.ProductionQueueService;
import civilization.application.service.ResourceProductionService;
import civilization.application.service.ResourceUpkeepService;
import civilization.application.service.StarvationService;
import civilization.application.service.TurnProcessor;
import civilization.application.service.UpgradeService;
import civilization.application.service.VisibilityService;
import civilization.application.validation.MovementValidator;
import civilization.application.validation.ProductionValidator;
import civilization.model.capacity.CapacityKey;
import civilization.model.capacity.CapacityState;
import civilization.model.common.Health;
import civilization.model.diplomacy.DiplomaticStance;
import civilization.model.diplomacy.RelationBand;
import civilization.model.diplomacy.RelationshipState;
import civilization.model.disaster.DisasterId;
import civilization.model.disaster.DisasterOccurrence;
import civilization.model.disaster.DisasterState;
import civilization.model.disaster.DisasterType;
import civilization.model.economy.ResourceBundle;
import civilization.model.economy.ResourceStockpile;
import civilization.model.economy.ResourceType;
import civilization.model.game.GameState;
import civilization.model.identity.FactionId;
import civilization.model.identity.TribeId;
import civilization.model.infrastructure.InfrastructureId;
import civilization.model.infrastructure.InfrastructureState;
import civilization.model.infrastructure.Road;
import civilization.model.infrastructure.Wall;
import civilization.model.map.GameMap;
import civilization.model.map.HexCoord;
import civilization.model.map.HexEdge;
import civilization.model.map.HexTile;
import civilization.model.map.NaturalResourceType;
import civilization.model.map.ResourceDeposit;
import civilization.model.map.TerrainType;
import civilization.model.mission.Mission;
import civilization.model.mission.MissionId;
import civilization.model.mission.MissionRegistry;
import civilization.model.mission.MissionStatus;
import civilization.model.mission.MissionType;
import civilization.model.player.EmpireBorder;
import civilization.model.random.GameplayRandomSnapshot;
import civilization.model.random.SplitMix64GameplayRandom;
import civilization.model.technology.TechnologyCatalog;
import civilization.model.technology.TechnologyDefinition;
import civilization.model.technology.TechnologyId;
import civilization.model.technology.TechnologyState;
import civilization.model.time.Season;
import civilization.model.time.SeasonState;
import civilization.rules.DefaultRulesetFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Deterministic dependency-free checks for the Phase 2 shared core. */
public final class Phase2CoreSystemsTestSuite {
    private Phase2CoreSystemsTestSuite() { }

    public static void main(String[] args) {
        edgeFeaturesAreCanonicalAndCanCoexist();
        newTerrainRulesAreSafeBeforeFeatureUnlocks();
        randomSnapshotsReplayExactly();
        eventBusIsOrderedAndFailureIsolated();
        turnLifecyclePublishesExactBoundaries();
        technologyGraphAndProgressEnforcePrerequisites();
        relationshipsUseExactBandsAndLegalStances();
        missionsEnforceTheirLifecycleAndRegistryInvariant();
        seasonsAndDisasterDurationsAreDeterministic();
        resourcesAndCapacitiesChangeAtomically();
        notificationInfrastructureIsUiIndependent();
        System.out.println("Phase2CoreSystemsTestSuite: all checks passed");
    }

    private static void edgeFeaturesAreCanonicalAndCanCoexist() {
        HexCoord origin = new HexCoord(0, 0);
        HexCoord east = new HexCoord(1, 0);
        HexEdge forward = new HexEdge(origin, east);
        check(forward.equals(new HexEdge(east, origin)), "Hex edge must be undirected");
        expectFailure(() -> new HexEdge(origin, new HexCoord(2, 0)), "Non-adjacent edge accepted");

        GameMap map = mapOf(new HexTile(origin, TerrainType.GRASSLAND),
                new HexTile(east, TerrainType.PLAINS));
        map.addRiver(origin, east);
        InfrastructureState infrastructure = new InfrastructureState();
        Wall wall = new Wall(InfrastructureId.random(), forward, FactionId.PLAYER, new Health(20));
        infrastructure.add(wall, map);
        check(map.hasRiver(east, origin), "River lookup must be direction independent");
        check(infrastructure.on(forward).orElseThrow() == wall, "Wall was not registered on edge");
        check(map.riverEdges().contains(forward), "River and Wall must coexist on the same edge");
    }

    private static void newTerrainRulesAreSafeBeforeFeatureUnlocks() {
        HexCoord center = new HexCoord(0, 0);
        HexCoord sea = new HexCoord(1, 0);
        GameMap seaMap = mapOf(
                new HexTile(center, TerrainType.GRASSLAND),
                new HexTile(sea, TerrainType.SEA,
                        new ResourceDeposit(NaturalResourceType.FISH, 10)));
        GameState state = createState(seaMap, center, 17L);
        var unit = state.player().units().iterator().next();
        var preview = new MovementValidator().validate(
                state, unit.id(), sea, DefaultRulesetFactory.create());
        check(!preview.allowed() && preview.reason().contains("Navigation"),
                "Sea must be blocked before Navigation is implemented");

        HexCoord range = new HexCoord(1, 0);
        GameMap rangeMap = mapOf(new HexTile(center, TerrainType.GRASSLAND),
                new HexTile(range, TerrainType.MOUNTAIN_RANGE));
        expectFailure(() -> new EmpireBorder(Set.of(center, range), rangeMap),
                "Mountain range entered territory");
        expectFailure(() -> new InfrastructureState().add(
                new Road(InfrastructureId.random(), range), rangeMap),
                "Road was accepted on a mountain range");
        expectFailure(() -> new InfrastructureState().add(
                new Wall(InfrastructureId.random(), new HexEdge(center, range),
                        FactionId.PLAYER, new Health(10)), rangeMap),
                "Wall was accepted beside a mountain range");
    }

    private static void randomSnapshotsReplayExactly() {
        SplitMix64GameplayRandom random = new SplitMix64GameplayRandom(992341L);
        random.nextLong();
        GameplayRandomSnapshot snapshot = random.snapshot();
        long expectedLong = random.nextLong();
        int expectedInt = random.nextInt(37);
        double expectedDouble = random.nextDouble();
        SplitMix64GameplayRandom restored = new SplitMix64GameplayRandom(snapshot);
        check(restored.nextLong() == expectedLong, "Restored long diverged");
        check(restored.nextInt(37) == expectedInt, "Restored int diverged");
        check(Double.compare(restored.nextDouble(), expectedDouble) == 0, "Restored double diverged");
    }

    private static void eventBusIsOrderedAndFailureIsolated() {
        SynchronousEventBus bus = new SynchronousEventBus();
        List<String> calls = new ArrayList<>();
        var first = bus.subscribe(TurnResolutionStartedEvent.class, event -> calls.add("first"));
        bus.subscribe(GameEvent.class, event -> { throw new IllegalStateException("observer failure"); });
        bus.subscribe(TurnResolutionStartedEvent.class, event -> calls.add("last"));
        var result = bus.publish(new TurnResolutionStartedEvent(2));
        check(calls.equals(List.of("first", "last")), "Event listener order or isolation failed");
        check(result.listenersInvoked() == 3 && result.failures().size() == 1,
                "Dispatch failures were not reported");
        first.close();
        calls.clear();
        bus.publish(new TurnResolutionStartedEvent(3));
        check(calls.equals(List.of("last")), "Unsubscription failed");
    }

    private static void turnLifecyclePublishesExactBoundaries() {
        GameState state = createState(plainRadiusOne(), new HexCoord(0, 0), 23L);
        SynchronousEventBus bus = new SynchronousEventBus();
        List<String> events = new ArrayList<>();
        bus.subscribe(GameEvent.class, event -> {
            if (event instanceof TurnResolutionStartedEvent) events.add("turn:start");
            if (event instanceof TurnStepStartedEvent step) events.add("start:" + step.stepId());
            if (event instanceof TurnStepCompletedEvent step) events.add("complete:" + step.stepId());
            if (event instanceof TurnResolutionCompletedEvent) events.add("turn:complete");
        });
        TurnProcessor processor = turnProcessor(bus);
        var resolution = processor.endTurn(state, DefaultRulesetFactory.create());
        List<String> expected = new ArrayList<>();
        expected.add("turn:start");
        for (String step : resolution.phaseTrace()) {
            expected.add("start:" + step);
            expected.add("complete:" + step);
        }
        expected.add("turn:complete");
        check(events.equals(expected), "Turn lifecycle event ordering diverged: " + events);
        check(state.turn().turnNumber() == 2 && state.stateVersion() == 1,
                "Turn completion did not commit expected state");
    }

    private static void technologyGraphAndProgressEnforcePrerequisites() {
        TechnologyId root = new TechnologyId("root");
        TechnologyId child = new TechnologyId("child");
        TechnologyCatalog catalog = new TechnologyCatalog(List.of(
                new TechnologyDefinition(root, Set.of()),
                new TechnologyDefinition(child, Set.of(root))));
        TechnologyState state = new TechnologyState();
        expectFailure(() -> state.research(child, catalog), "Technology bypassed prerequisite");
        state.research(root, catalog);
        state.research(child, catalog);
        check(state.isResearched(child), "Researched technology not stored");
        expectFailure(() -> new TechnologyCatalog(List.of(
                new TechnologyDefinition(root, Set.of(child)),
                new TechnologyDefinition(child, Set.of(root)))), "Technology cycle accepted");
    }

    private static void relationshipsUseExactBandsAndLegalStances() {
        check(RelationBand.fromScore(-50) == RelationBand.ENEMY, "Enemy boundary incorrect");
        check(RelationBand.fromScore(-49) == RelationBand.DISPLEASED, "Displeased boundary incorrect");
        check(RelationBand.fromScore(-19) == RelationBand.NEUTRAL, "Neutral boundary incorrect");
        check(RelationBand.fromScore(20) == RelationBand.FRIENDLY, "Friendly boundary incorrect");
        check(RelationBand.fromScore(70) == RelationBand.ALLIED, "Allied boundary incorrect");
        RelationshipState relation = new RelationshipState(new TribeId("river-clan"));
        relation.adjustScore(70);
        relation.formAlliance();
        check(relation.stance() == DiplomaticStance.ALLIANCE, "Alliance was not established");
        relation.adjustScore(-1);
        check(relation.stance() == DiplomaticStance.PEACE, "Invalid alliance was retained");
        relation.declareWar();
        check(relation.score() == -100 && relation.stance() == DiplomaticStance.WAR,
                "War transition is inconsistent");
    }

    private static void missionsEnforceTheirLifecycleAndRegistryInvariant() {
        TribeId tribe = new TribeId("forest-clan");
        Mission mission = new Mission(new MissionId("m-1"), tribe,
                MissionType.FARMER_SUPPLY, 4, 10);
        MissionRegistry registry = new MissionRegistry();
        registry.add(mission);
        Mission second = new Mission(new MissionId("m-2"), tribe,
                MissionType.TRADER_ROAD, 4, 12);
        registry.add(second);
        registry.accept(mission.id());
        expectFailure(() -> registry.accept(second.id()), "Multiple active missions accepted for one tribe");
        mission.markReady();
        mission.complete();
        check(mission.status() == MissionStatus.COMPLETED, "Mission completion not retained");
        expectFailure(mission::cancel, "Completed mission was cancelled");
        Mission restored = new Mission(new MissionId("m-restore"), tribe,
                MissionType.COASTAL_DOCK, 8, 18, MissionStatus.READY);
        restored.complete();
        check(restored.status() == MissionStatus.COMPLETED, "Exact mission status did not restore");
    }

    private static void seasonsAndDisasterDurationsAreDeterministic() {
        SeasonState season = new SeasonState();
        for (int i = 0; i < 9; i++) season.advanceOneTurn();
        check(season.season() == Season.SPRING && season.turnWithinSeason() == 10,
                "Spring duration is not ten turns");
        season.advanceOneTurn();
        check(season.season() == Season.SUMMER && season.turnWithinSeason() == 1,
                "Season boundary is incorrect");
        for (int i = 0; i < 30; i++) season.advanceOneTurn();
        check(season.season() == Season.SPRING && season.turnWithinSeason() == 1,
                "Four-season wrap is incorrect");

        DisasterState disasters = new DisasterState();
        DisasterId id = new DisasterId("d-1");
        HexCoord center = new HexCoord(0, 0);
        disasters.register(new DisasterOccurrence(id, DisasterType.FLOOD,
                center, Set.of(center), 7, 2));
        disasters.advancePersistentEffects();
        check(disasters.remainingEffectTurns(id) == 1, "Disaster duration did not decrement");
        disasters.advancePersistentEffects();
        check(disasters.remainingEffectTurns(id) == 0, "Disaster effect did not expire");
    }

    private static void resourcesAndCapacitiesChangeAtomically() {
        ResourceStockpile stockpile = new ResourceStockpile(
                ResourceBundle.of(2, 4, 1, 0), ResourceBundle.of(5, 5, 5, 5));
        check(!stockpile.creditExact(ResourceBundle.of(4, 0, 0, 0)), "Overflowing exact credit succeeded");
        check(stockpile.amountOf(ResourceType.FOOD) == 2, "Failed exact credit mutated storage");
        check(!stockpile.tryDebit(ResourceBundle.of(0, 8, 0, 0)), "Unaffordable debit succeeded");
        check(stockpile.remainingCapacity().amountOf(ResourceType.WOOD) == 1,
                "Remaining capacity is incorrect");
        var overflow = stockpile.resizeCapacity(ResourceBundle.of(5, 2, 5, 5));
        check(stockpile.amountOf(ResourceType.WOOD) == 2 && overflow.amountOf(ResourceType.WOOD) == 2,
                "Capacity shrink did not report and clamp overflow");

        CapacityState capacity = new CapacityState();
        capacity.define(CapacityKey.MILITARY_UNITS, 3);
        capacity.setModifier(CapacityKey.MILITARY_UNITS, "technology:steel", 2);
        check(capacity.limit(CapacityKey.MILITARY_UNITS) == 5
                && capacity.allows(CapacityKey.MILITARY_UNITS, 4, 1), "Capacity modifier failed");
    }

    private static void notificationInfrastructureIsUiIndependent() {
        EventBus bus = new SynchronousEventBus();
        try (NotificationCenter center = new NotificationCenter(bus)) {
            var notification = center.publish(3, NotificationSeverity.WARNING,
                    NotificationCategory.DISASTER, "Flood risk", new HexCoord(0, 0));
            check(center.history().equals(List.of(notification)), "Notification was not observed");
            center.clear();
            check(center.history().isEmpty(), "Notification history did not clear");
        }
    }

    private static TurnProcessor turnProcessor(EventBus bus) {
        VisibilityService visibility = new VisibilityService();
        ActionPointService actionPoints = new ActionPointService();
        UpgradeService upgrades = new UpgradeService();
        ProductionQueueService queue = new ProductionQueueService(
                upgrades, new ProductionValidator(upgrades), new UnitFactory());
        return new TurnProcessor(actionPoints, new ResourceProductionService(), queue,
                new ResourceUpkeepService(), new StarvationService(actionPoints), visibility, bus);
    }

    private static GameState createState(GameMap map, HexCoord center, long seed) {
        return new GameFactory(new VisibilityService(), new UnitFactory())
                .create(map, center, seed, DefaultRulesetFactory.create());
    }

    private static GameMap plainRadiusOne() {
        HexCoord center = new HexCoord(0, 0);
        List<HexTile> tiles = new ArrayList<>();
        tiles.add(new HexTile(center, TerrainType.GRASSLAND));
        center.neighbors().forEach(coord -> tiles.add(new HexTile(coord, TerrainType.PLAINS)));
        return new GameMap(tiles);
    }

    private static GameMap mapOf(HexTile... tiles) { return new GameMap(List.of(tiles)); }

    private static void expectFailure(Runnable action, String message) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
