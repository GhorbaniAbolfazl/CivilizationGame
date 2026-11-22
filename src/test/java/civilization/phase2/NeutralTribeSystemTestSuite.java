package civilization.phase2;

import civilization.application.event.SynchronousEventBus;
import civilization.application.event.TribeNotificationEvent;
import civilization.application.factory.UnitFactory;
import civilization.application.generation.SeededProceduralMapGenerator;
import civilization.application.generation.TribeGenerator;
import civilization.application.query.TribeQuery;
import civilization.application.service.CombatService;
import civilization.application.service.TribeDiscoveryService;
import civilization.application.service.TribeInteractionService;
import civilization.application.service.TribeMissionService;
import civilization.application.service.TribeRewardService;
import civilization.application.service.TribeTurnService;
import civilization.application.service.TradeService;
import civilization.application.service.RelationEventPolicy;
import civilization.model.building.BuildingId;
import civilization.model.building.Dock;
import civilization.model.building.TownHall;
import civilization.model.combat.AttackRequest;
import civilization.model.combat.AttackTarget;
import civilization.model.combat.CombatKillRecord;
import civilization.model.diplomacy.AlliedRelationshipState;
import civilization.model.diplomacy.DispleasedRelationshipState;
import civilization.model.diplomacy.EnemyRelationshipState;
import civilization.model.diplomacy.FriendlyRelationshipState;
import civilization.model.diplomacy.NeutralRelationshipState;
import civilization.model.diplomacy.RelationBand;
import civilization.model.diplomacy.RelationshipState;
import civilization.model.economy.ResourceBundle;
import civilization.model.economy.ResourceStockpile;
import civilization.model.economy.ResourceType;
import civilization.model.economy.TurnEconomyReport;
import civilization.model.game.CrisisState;
import civilization.model.game.GameState;
import civilization.model.game.TurnState;
import civilization.model.identity.FactionId;
import civilization.model.identity.TribeId;
import civilization.model.infrastructure.InfrastructureId;
import civilization.model.infrastructure.Road;
import civilization.model.map.HexEdge;
import civilization.model.map.GameMap;
import civilization.model.map.HexCoord;
import civilization.model.map.HexTile;
import civilization.model.map.TerrainType;
import civilization.model.map.VisibilityState;
import civilization.model.mission.MissionStatus;
import civilization.model.player.EmpireBorder;
import civilization.model.player.PlayerState;
import civilization.model.player.UnitCapacity;
import civilization.model.technology.ProductionQueue;
import civilization.model.technology.TechnologyState;
import civilization.model.trade.TribeTradeEndpoint;
import civilization.model.tribe.Tribe;
import civilization.model.tribe.TribeCamp;
import civilization.model.tribe.TribeType;
import civilization.model.unit.UnitId;
import civilization.model.unit.UnitType;
import civilization.rules.DefaultRulesetFactory;
import civilization.rules.GameRules;
import civilization.rules.tribe.MissionCatalog;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Deterministic acceptance suite for every mandatory neutral-tribe subsystem. */
public final class NeutralTribeSystemTestSuite {
    private static final GameRules RULES = DefaultRulesetFactory.create();

    private NeutralTribeSystemTestSuite() { }

    public static void main(String[] args) {
        relationshipStateBoundariesAndClamp();
        ordinaryWorldActionsApplyDocumentedRelationshipDeltas();
        warTransitionsFromEveryNonEnemyState();
        discoveryIsPermanentButCurrentInformationIsFogged();
        giftsUseExactUnitsAndRejectInvalidActions();
        warPeaceAndAllianceTransitionsAreAtomic();
        allianceExclusionsAreEnforced();
        tribeTradeRequiresVisibilityAndHasTurnCooldown();
        supplyMissionLifecycleRewardsFailureCooldownAndCancellation();
        roadDockAndMilitaryObjectivesAreDetectedFromState();
        forbiddenAreaWarningsAndTurnBehaviorAreDeterministic();
        enemyMovementUsesSharedRiverAndSeasonCosts();
        enemyGuardScheduleStopsAtTypeCap();
        campCombatDefeatAndTerritoryTransferAreComplete();
        proceduralPlacementCreatesAllMandatoryTypes();
        System.out.println("NeutralTribeSystemTestSuite: all checks passed");
    }

    private static void relationshipStateBoundariesAndClamp() {
        RelationshipState relation = new RelationshipState(new TribeId("bands"));
        check(relation.behavior() instanceof NeutralRelationshipState, "Initial state must be Neutral");
        relation.adjustScore(20);
        check(relation.behavior() instanceof FriendlyRelationshipState, "+20 must be Friendly");
        relation.adjustScore(50);
        check(relation.behavior() instanceof AlliedRelationshipState, "+70 must be Allied");
        relation.adjustScore(999);
        check(relation.score() == 100, "Relationship must clamp at +100");
        relation.adjustScore(-120);
        check(relation.score() == -20 && relation.behavior() instanceof DispleasedRelationshipState,
                "-20 must be Displeased");
        relation.adjustScore(-30);
        check(relation.behavior() instanceof EnemyRelationshipState,
                "-50 must be Enemy");
        relation.adjustScore(-999);
        check(relation.score() == -100, "Relationship must clamp at -100");
        check(RelationBand.fromScore(-49) == RelationBand.DISPLEASED
                        && RelationBand.fromScore(-19) == RelationBand.NEUTRAL
                        && RelationBand.fromScore(19) == RelationBand.NEUTRAL
                        && RelationBand.fromScore(69) == RelationBand.FRIENDLY,
                "Relationship boundary table changed");
    }

    private static void ordinaryWorldActionsApplyDocumentedRelationshipDeltas() {
        Scenario scenario = scenario(TribeType.FARMER, 0, true);
        RelationEventPolicy policy = new RelationEventPolicy();
        policy.tribeTrade(scenario.state, scenario.tribe.id());
        check(relationship(scenario).score() == RelationEventPolicy.ORDINARY_TRADE_HELP,
                "An ordinary tribe trade must apply the centralized relationship delta");
        HexCoord neighbor = scenario.camp.neighbors().stream()
                .filter(scenario.state.map()::contains).findFirst().orElseThrow();
        policy.wallBuilt(scenario.state, new HexEdge(scenario.camp, neighbor));
        check(relationship(scenario).score()
                        == RelationEventPolicy.ORDINARY_TRADE_HELP
                        + RelationEventPolicy.ROUTE_BLOCKING_WALL,
                "A route-blocking wall must apply its centralized relationship penalty");
    }

    private static void warTransitionsFromEveryNonEnemyState() {
        for (int score : List.of(-30, 0, 20, 70)) {
            Scenario scenario = scenario(TribeType.FARMER, score, true);
            if (score == 70) new TribeInteractionService().requestAlliance(
                    scenario.state, scenario.tribe.id(), RULES);
            new TribeInteractionService().declareWar(scenario.state, scenario.tribe.id());
            check(relationship(scenario).score() == -100
                            && relationship(scenario).behavior() instanceof EnemyRelationshipState,
                    "War failed to transition score " + score + " to Enemy");
        }
        Scenario invalidAlliance = scenario(TribeType.FARMER, 69, true);
        check(!new TribeInteractionService().previewAlliance(
                        invalidAlliance.state, invalidAlliance.tribe.id()).allowed(),
                "Alliance must remain disabled at relationship 69");
    }

    private static void discoveryIsPermanentButCurrentInformationIsFogged() {
        Scenario scenario = scenario(TribeType.FARMER, 0, false);
        TribeDiscoveryService discovery = new TribeDiscoveryService();
        check(discovery.refresh(scenario.state).isEmpty() && !scenario.tribe.isDiscovered(),
                "Fogged camp leaked discovery");
        scenario.state.player().visibility().replaceCurrentlyVisible(Set.of(scenario.camp));
        check(discovery.refresh(scenario.state).equals(List.of(scenario.tribe.id())),
                "Visible camp was not discovered");
        scenario.state.player().visibility().replaceCurrentlyVisible(Set.of());
        check(scenario.tribe.isDiscovered(), "Tribe discovery must be permanent");
        var snapshot = new TribeQuery().discoveredTribes(scenario.state).get(0);
        check(!snapshot.currentlyVisible() && snapshot.relationship().isEmpty()
                        && snapshot.campHealth().isEmpty(),
                "Fogged known tribe exposed current information");
        check(!new TribeInteractionService().previewGift(scenario.state, scenario.tribe.id()).allowed(),
                "Fogged tribe interaction must be disabled");
    }

    private static void giftsUseExactUnitsAndRejectInvalidActions() {
        Scenario scenario = scenario(TribeType.FARMER, 0, true);
        TribeInteractionService service = new TribeInteractionService();
        int foodBefore = stockpile(scenario).amountOf(ResourceType.FOOD);
        var result = service.giveGift(scenario.state, scenario.tribe.id(), ResourceType.FOOD, 20);
        check(result.relationshipIncrease() == 4 && result.resultingRelationship() == 4,
                "Food gift ratio is incorrect");
        check(stockpile(scenario).amountOf(ResourceType.FOOD) == foodBefore - 20,
                "Gift did not debit exact resources");
        service.giveGift(scenario.state, scenario.tribe.id(), ResourceType.IRON, 5);
        check(relationship(scenario).score() == 7, "Iron gift ratio is incorrect");
        check(!service.previewGift(scenario.state, scenario.tribe.id(), ResourceType.WOOD, 5).allowed(),
                "Invalid gift multiple should disable confirmation");
        relationship(scenario).declareWar();
        check(!service.previewGift(scenario.state, scenario.tribe.id()).allowed(),
                "Enemy gift action should be disabled");
        relationship(scenario).establishPeace(99);
        service.giveGift(scenario.state, scenario.tribe.id(), ResourceType.STONE, 10);
        check(relationship(scenario).score() == 100, "Gift must clamp relationship at 100");
    }

    private static void warPeaceAndAllianceTransitionsAreAtomic() {
        Scenario scenario = scenario(TribeType.FARMER, 70, true);
        SynchronousEventBus bus = new SynchronousEventBus();
        List<TribeNotificationEvent> notices = new ArrayList<>();
        bus.subscribe(TribeNotificationEvent.class, notices::add);
        TribeInteractionService service = new TribeInteractionService(
                new TribeDiscoveryService(bus), new TribeRewardService(), bus);
        service.requestAlliance(scenario.state, scenario.tribe.id(), RULES);
        check(relationship(scenario).stance()
                        == civilization.model.diplomacy.DiplomaticStance.ALLIANCE
                        && scenario.tribe.isAllianceBonusActive(),
                "Valid alliance did not activate");
        service.declareWar(scenario.state, scenario.tribe.id());
        check(relationship(scenario).score() == -100
                        && relationship(scenario).stance()
                        == civilization.model.diplomacy.DiplomaticStance.WAR
                        && !scenario.tribe.isAllianceBonusActive(),
                "War must immediately replace alliance with Enemy");
        check(scenario.state.player().happiness().accumulatedValue() == -15,
                "Allied war Happiness penalty is incorrect");
        check(!service.previewPeace(scenario.state, scenario.tribe.id()).allowed(),
                "Peace must require three attack-free turns");
        advanceTurns(scenario.state, 3);
        check(service.previewPeace(scenario.state, scenario.tribe.id()).allowed(),
                "Peace remained disabled after three turns and payment availability");
        service.requestPeace(scenario.state, scenario.tribe.id());
        check(relationship(scenario).score() == -10
                        && relationship(scenario).band() == RelationBand.NEUTRAL,
                "Peace must set exact score -10 under the authoritative bands");
        check(!service.previewTrade(scenario.state, scenario.tribe.id()).allowed(),
                "Peace must not unlock trade at -10");
        check(!notices.isEmpty(), "Diplomacy changes did not publish tribe notifications");
    }

    private static void allianceExclusionsAreEnforced() {
        Scenario scenario = scenario(TribeType.FARMER, 70, true);
        Tribe mountain = addTribe(scenario.state, TribeType.MOUNTAIN,
                new TribeId("mountain-two"), new HexCoord(-3, 0), 70, true);
        TribeInteractionService service = new TribeInteractionService();
        service.requestAlliance(scenario.state, scenario.tribe.id(), RULES);
        check(!service.previewAlliance(scenario.state, mountain.id()).allowed(),
                "Farmer/Mountain mutual exclusion was not enforced");

        Scenario warriorScenario = scenario(TribeType.WARRIOR, 70, true);
        Tribe trader = addTribe(warriorScenario.state, TribeType.TRADER,
                new TribeId("trader-two"), new HexCoord(-3, 0), 70, true);
        service.requestAlliance(warriorScenario.state, warriorScenario.tribe.id(), RULES);
        check(!service.previewAlliance(warriorScenario.state, trader.id()).allowed(),
                "Warrior alliance exclusivity was not enforced");
    }

    private static void tribeTradeRequiresVisibilityAndHasTurnCooldown() {
        Scenario scenario = scenario(TribeType.TRADER, 20, true);
        TradeService trade = new TradeService();
        check(trade.previewTribe(scenario.state, scenario.tribe.id(),
                ResourceType.WOOD, ResourceType.FOOD, 10).allowed(),
                "Friendly visible tribe trade should be enabled");
        trade.tradeWithTribe(scenario.state, scenario.tribe.id(),
                ResourceType.WOOD, ResourceType.FOOD, 10);
        check(!trade.previewTribe(scenario.state, scenario.tribe.id(),
                ResourceType.WOOD, ResourceType.FOOD, 10).allowed(),
                "Second trade in one turn should be disabled");
        advanceTurns(scenario.state, 1);
        scenario.state.player().visibility().replaceCurrentlyVisible(Set.of());
        check(!trade.previewTribe(scenario.state, scenario.tribe.id(),
                ResourceType.WOOD, ResourceType.FOOD, 10).allowed(),
                "Trade should lock when the known camp returns to fog");
    }

    private static void supplyMissionLifecycleRewardsFailureCooldownAndCancellation() {
        Scenario scenario = scenario(TribeType.FARMER, 20, true);
        TribeMissionService missions = new TribeMissionService();
        var mission = missions.offer(scenario.state, scenario.tribe.id());
        check(mission.status() == MissionStatus.RECEIVABLE, "Mission must begin Receivable");
        missions.accept(scenario.state, mission.id());
        check(missions.evaluateObjective(scenario.state, mission.id())
                        && mission.status() == MissionStatus.READY,
                "Supply objective was not detected from the stockpile");
        int food = stockpile(scenario).amountOf(ResourceType.FOOD);
        missions.deliver(scenario.state, mission.id(), RULES);
        check(mission.status() == MissionStatus.COMPLETED
                        && stockpile(scenario).amountOf(ResourceType.FOOD) == food + 30
                        && relationship(scenario).score() == 35,
                "Farmer reward or relation change is incorrect");

        Scenario failed = scenario(TribeType.FARMER, 20, true);
        TribeMissionService failureService = new TribeMissionService();
        var expiring = failureService.offer(failed.state, failed.tribe.id());
        failureService.accept(failed.state, expiring.id());
        stockpile(failed).debit(ResourceBundle.of(0, 90, 90, 0));
        advanceTurns(failed.state, 6);
        failureService.processDeadlines(failed.state);
        check(expiring.status() == MissionStatus.FAILED && relationship(failed).score() == 10,
                "Expired mission did not fail with -10 relation");
        relationship(failed).adjustScore(10);
        check(!failureService.previewOffer(failed.state, failed.tribe.id()).allowed(),
                "Five-turn failure cooldown was not enforced");
        advanceTurns(failed.state, 5);
        check(failureService.previewOffer(failed.state, failed.tribe.id()).allowed(),
                "Mission cooldown did not expire");

        Scenario cancelled = scenario(TribeType.MOUNTAIN, 20, true);
        var cancelMission = missionsFor(cancelled).offer(cancelled.state, cancelled.tribe.id());
        missionsFor(cancelled).accept(cancelled.state, cancelMission.id());
        new TribeMissionService().cancel(cancelled.state, cancelMission.id());
        check(cancelMission.status() == MissionStatus.CANCELLED
                        && relationship(cancelled).score() == 15,
                "Manual cancellation must apply exactly -5 relation");
    }

    private static void roadDockAndMilitaryObjectivesAreDetectedFromState() {
        Scenario road = scenario(TribeType.TRADER, 20, true);
        TribeMissionService service = new TribeMissionService();
        var roadMission = service.offer(road.state, road.tribe.id());
        service.accept(road.state, roadMission.id());
        road.state.world().infrastructure().add(new Road(InfrastructureId.random(), new HexCoord(1, 0)), road.state.map());
        road.state.world().infrastructure().add(new Road(InfrastructureId.random(), new HexCoord(2, 0)), road.state.map());
        check(service.evaluateObjective(road.state, roadMission.id()),
                "Continuous road objective was not detected");
        service.deliver(road.state, roadMission.id(), RULES);
        check(road.state.world().tribeTrades().require(road.tribe.id()).rateBonusPercent() == 10,
                "Trader mission did not persist +10 rate points");

        Scenario dock = scenario(TribeType.COASTAL, 20, true);
        var dockMission = service.offer(dock.state, dock.tribe.id());
        service.accept(dock.state, dockMission.id());
        dock.state.player().addBuilding(new Dock(BuildingId.random(), new HexCoord(1, 0),
                FactionId.PLAYER, 120));
        check(service.evaluateObjective(dock.state, dockMission.id()),
                "Dock distance objective was not detected");
        service.deliver(dock.state, dockMission.id(), RULES);
        check(dock.tribe.hasNextDockDiscount(), "Coastal reward did not arm one-use Dock discount");

        Scenario military = scenario(TribeType.WARRIOR, 20, true);
        var bounty = service.offer(military.state, military.tribe.id());
        service.accept(military.state, bounty.id());
        military.state.world().combatHistory().recordKill(new CombatKillRecord(UnitId.random(),
                FactionId.BARBARIANS, new HexCoord(2, 0), military.state.turn().turnNumber()));
        military.state.world().combatHistory().recordKill(new CombatKillRecord(UnitId.random(),
                FactionId.BARBARIANS, new HexCoord(3, 1), military.state.turn().turnNumber()));
        check(service.evaluateObjective(military.state, bounty.id()),
                "Military objective ignored qualifying combat history");
        service.deliver(military.state, bounty.id(), RULES);
        check(military.state.player().activeUnitCount(UnitType.SWORDSMAN) == 3,
                "Warrior mission did not grant three cap-checked Swordsmen");
    }

    private static void forbiddenAreaWarningsAndTurnBehaviorAreDeterministic() {
        Scenario scenario = scenario(TribeType.FARMER, 0, true);
        var intruder = new UnitFactory().create(UnitType.SWORDSMAN, new HexCoord(2, 0),
                RULES, FactionId.PLAYER);
        scenario.state.player().addUnit(intruder);
        SynchronousEventBus bus = new SynchronousEventBus();
        List<TribeNotificationEvent> notices = new ArrayList<>();
        bus.subscribe(TribeNotificationEvent.class, notices::add);
        TribeTurnService turns = new TribeTurnService(bus);
        turns.process(scenario.state, RULES);
        check(relationship(scenario).score() == 0, "First intrusion must only warn");
        advanceTurns(scenario.state, 1);
        turns.process(scenario.state, RULES);
        check(relationship(scenario).score() == -5,
                "Continued Neutral intrusion must use the documented balance penalty");
        check(notices.size() >= 2, "Intrusion warning/change notifications were not emitted");
    }

    private static void enemyGuardScheduleStopsAtTypeCap() {
        Scenario scenario = scenario(TribeType.FARMER, -100, true);
        TribeTurnService turns = new TribeTurnService(
                civilization.application.event.EventBus.noOp());
        advanceTurns(scenario.state, 2); // turn 3
        for (int scheduled = 0; scheduled < 5; scheduled++) {
            turns.process(scenario.state, RULES);
            if (scheduled < 4) advanceTurns(scenario.state, 3);
        }
        long guards = scenario.state.world().units().all().stream()
                .filter(unit -> unit.ownerId().equals(scenario.tribe.id().asFactionId())).count();
        check(guards == 3, "Normal tribe guard production exceeded or missed cap 3: " + guards);
    }

    private static void enemyMovementUsesSharedRiverAndSeasonCosts() {
        Scenario scenario = scenario(TribeType.FARMER, -100, true);
        HexCoord targetHex = new HexCoord(0, 0);
        var target = new UnitFactory().create(UnitType.SWORDSMAN, targetHex,
                RULES, FactionId.PLAYER);
        scenario.state.player().addUnit(target);
        var guard = new UnitFactory().create(UnitType.SWORDSMAN, scenario.camp,
                RULES, scenario.tribe.id().asFactionId());
        scenario.state.world().units().add(guard, scenario.state.map());
        HexCoord destination = new HexCoord(2, 0);
        scenario.state.map().addRiver(scenario.camp, destination);
        scenario.tribe.recordPlayerAttack(scenario.state.turn().turnNumber(), targetHex);

        new TribeTurnService(civilization.application.event.EventBus.noOp())
                .process(scenario.state, RULES);

        check(guard.position().equals(destination),
                "Enemy guard did not make the deterministic response move");
        check(guard.actionPoints().current() == 0,
                "Enemy movement bypassed the shared +1 River crossing cost");
    }

    private static void campCombatDefeatAndTerritoryTransferAreComplete() {
        Scenario scenario = scenario(TribeType.FARMER, 0, true);
        var attacker = new UnitFactory().create(UnitType.SWORDSMAN, new HexCoord(2, 0),
                RULES, FactionId.PLAYER);
        scenario.state.player().addUnit(attacker);
        CombatService combat = new CombatService(() -> 6);
        for (int hit = 0; hit < 4; hit++) {
            attacker.actionPoints().refill();
            combat.playerAttack(scenario.state,
                    new AttackRequest(attacker.position(), scenario.camp, AttackTarget.STRUCTURE));
        }
        check(scenario.tribe.isDefeated() && scenario.tribe.hasOutpost(),
                "Zero-HP camp did not defeat tribe/create Outpost state");
        check(scenario.tribe.territory().stream().allMatch(scenario.state.player().border()::owns),
                "Defeated tribe territory did not transfer to player");
        check(scenario.state.world().units().all().stream().noneMatch(unit ->
                        unit.ownerId().equals(scenario.tribe.id().asFactionId())),
                "Defeated tribe units remained in world registry");
    }

    private static void proceduralPlacementCreatesAllMandatoryTypes() {
        for (long seed = 0; seed < 64; seed++) {
            GameMap map = new SeededProceduralMapGenerator().generate(8, seed, new HexCoord(0, 0));
            var base = new civilization.application.GameFactory(
                    new civilization.application.service.VisibilityService())
                    .create(map, new HexCoord(0, 0), seed, RULES);
            List<Tribe> tribes = new TribeGenerator().generate(base, RULES);
            check(tribes.size() == 6 && tribes.stream().map(Tribe::type).collect(
                            java.util.stream.Collectors.toSet()).containsAll(Set.of(TribeType.values())),
                    "Generator omitted a mandatory tribe type for seed " + seed);
            check(tribes.stream().allMatch(tribe -> tribe.camp().position()
                            .distanceTo(base.player().townHall().position()) >= 6),
                    "A generated camp is too close to Town Hall");
        }
    }

    private static TribeMissionService missionsFor(Scenario ignored) {
        return new TribeMissionService(new MissionCatalog(), new TribeDiscoveryService(),
                new TribeRewardService(), civilization.application.event.EventBus.noOp());
    }

    private static Scenario scenario(TribeType type, int relation, boolean visible) {
        GameMap map = landMap(6);
        HexCoord hall = new HexCoord(0, 0);
        ResourceStockpile resources = new ResourceStockpile(ResourceBundle.of(100, 100, 100, 100),
                ResourceBundle.of(500, 500, 500, 500));
        TownHall townHall = new TownHall(BuildingId.random(), hall, resources,
                new ProductionQueue(), FactionId.PLAYER);
        Set<HexCoord> border = new HashSet<>();
        border.add(hall); border.addAll(hall.neighbors());
        PlayerState player = new PlayerState(townHall, new UnitCapacity(30),
                new EmpireBorder(border, map), new VisibilityState(), new TechnologyState());
        GameState state = new GameState(map, player, new TurnState(), new CrisisState(), 17L);
        Tribe tribe = addTribe(state, type, new TribeId(type.name().toLowerCase()),
                new HexCoord(3, 0), relation, visible);
        return new Scenario(state, tribe, tribe.camp().position());
    }

    private static Tribe addTribe(GameState state, TribeType type, TribeId id,
            HexCoord campHex, int score, boolean visible) {
        Set<HexCoord> territory = new HashSet<>();
        territory.add(campHex);
        campHex.neighbors().stream().filter(state.map()::contains)
                .filter(coord -> !state.player().border().owns(coord)).forEach(territory::add);
        Tribe tribe = new Tribe(id, type + " Test Tribe", type,
                new TribeCamp(id, campHex, type.campHealth()), territory);
        state.world().tribes().add(tribe);
        RelationshipState relationship = new RelationshipState(id);
        relationship.adjustScore(score);
        if (score > -50) relationship.establishPeace(score);
        state.world().relationships().add(relationship);
        type.tradeProfile().ifPresent(profile -> state.world().tribeTrades()
                .add(new TribeTradeEndpoint(id, profile)));
        state.world().structures().add(tribe.camp().structure(), state.map());
        territory.forEach(coord -> state.world().territories().claim(coord, id.asFactionId(), state.map()));
        if (visible) {
            Set<HexCoord> now = new HashSet<>(state.player().visibility().currentlyVisibleHexes());
            now.add(campHex);
            state.player().visibility().replaceCurrentlyVisible(now);
            new TribeDiscoveryService().refresh(state);
        }
        return tribe;
    }

    private static GameMap landMap(int radius) {
        List<HexTile> tiles = new ArrayList<>();
        for (int q = -radius; q <= radius; q++) {
            int minR = Math.max(-radius, -q - radius);
            int maxR = Math.min(radius, -q + radius);
            for (int r = minR; r <= maxR; r++) {
                tiles.add(new HexTile(new HexCoord(q, r), TerrainType.GRASSLAND));
            }
        }
        return new GameMap(tiles);
    }

    private static ResourceStockpile stockpile(Scenario scenario) {
        return scenario.state.player().townHall().stockpile();
    }

    private static RelationshipState relationship(Scenario scenario) {
        return scenario.state.world().relationships().require(scenario.tribe.id());
    }

    private static void advanceTurns(GameState state, int turns) {
        for (int index = 0; index < turns; index++) {
            state.turn().beginNextTurnResolution();
            state.turn().finishResolution(TurnEconomyReport.EMPTY);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record Scenario(GameState state, Tribe tribe, HexCoord camp) { }
}
