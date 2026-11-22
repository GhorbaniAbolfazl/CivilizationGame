package civilization.phase2;

import civilization.application.GameFactory;
import civilization.application.event.SynchronousEventBus;
import civilization.application.event.TradeCompletedEvent;
import civilization.application.service.ConstructionService;
import civilization.application.service.TradeService;
import civilization.application.service.VisibilityService;
import civilization.application.validation.ConstructionValidator;
import civilization.model.building.Bazaar;
import civilization.model.building.BuildingId;
import civilization.model.building.BuildingType;
import civilization.model.building.HallLevel;
import civilization.model.building.TownHall;
import civilization.model.diplomacy.RelationshipState;
import civilization.model.economy.ResourceBundle;
import civilization.model.economy.ResourceStockpile;
import civilization.model.economy.ResourceType;
import civilization.model.economy.TurnEconomyReport;
import civilization.model.game.CrisisState;
import civilization.model.game.GameState;
import civilization.model.game.TurnState;
import civilization.model.identity.TribeId;
import civilization.model.map.GameMap;
import civilization.model.map.HexCoord;
import civilization.model.map.HexTile;
import civilization.model.map.TerrainType;
import civilization.model.map.VisibilityState;
import civilization.model.player.EmpireBorder;
import civilization.model.player.PlayerState;
import civilization.model.player.UnitCapacity;
import civilization.model.random.SplitMix64GameplayRandom;
import civilization.model.technology.ProductionQueue;
import civilization.model.technology.TechnologyState;
import civilization.model.time.SeasonState;
import civilization.model.trade.BazaarTradeLevel;
import civilization.model.trade.TradeEndpointKind;
import civilization.model.trade.TradeRequest;
import civilization.model.trade.TradingPost;
import civilization.model.trade.TradingPostId;
import civilization.model.trade.TribeTradeEndpoint;
import civilization.model.trade.TribeTradeProfile;
import civilization.model.unit.ActionPoints;
import civilization.model.unit.BuilderUnit;
import civilization.model.unit.UnitId;
import civilization.model.world.WorldState;
import civilization.rules.DefaultRulesetFactory;
import civilization.rules.GameRules;
import civilization.rules.trade.BazaarExchangeRateStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic acceptance tests for the complete mandatory trade slice. */
public final class TradeSystemTestSuite {
    private static final HexCoord CENTER = new HexCoord(0, 0);
    private static final HexCoord BAZAAR_HEX = new HexCoord(1, 0);
    private static final HexCoord POST_HEX = new HexCoord(-1, 0);

    private TradeSystemTestSuite() { }

    public static void main(String[] args) {
        bazaarConstructionRequiresSettlement();
        everyBazaarLevelAndResourcePairUsesExactRate();
        bazaarValidationIsAtomicAtStorageBoundaries();
        bazaarCooldownIsPerBuildingAndPerTurn();
        tradingPostRequiresTerritoryAndUsesFlooredEightyPercent();
        tradingPostValidationAndCooldownAreAtomic();
        tribeStrategiesValidateRelationProfileRateAndCooldown();
        successfulTradePublishesIntegrationEvent();
        defaultGamesContainANeutralTradingPost();
        System.out.println("TradeSystemTestSuite: all checks passed");
    }

    private static void bazaarConstructionRequiresSettlement() {
        Scenario scenario = scenario(false, richAmounts(), richCapacity(), false, true);
        BuilderUnit builder = new BuilderUnit(UnitId.random(), BAZAAR_HEX,
                new ActionPoints(5), 3);
        scenario.state.player().addUnit(builder);
        ConstructionValidator validator = new ConstructionValidator();
        check(!validator.validate(scenario.state, builder.id(), BuildingType.BAZAAR,
                BAZAAR_HEX, scenario.rules).allowed(), "Bazaar was buildable at Hall level 1");
        scenario.hall().upgradeTo(HallLevel.SETTLEMENT);
        check(validator.validate(scenario.state, builder.id(), BuildingType.BAZAAR,
                BAZAAR_HEX, scenario.rules).allowed(), "Valid Hall level 2 Bazaar was rejected");

        int wood = scenario.stockpile().amountOf(ResourceType.WOOD);
        int stone = scenario.stockpile().amountOf(ResourceType.STONE);
        int iron = scenario.stockpile().amountOf(ResourceType.IRON);
        var built = new ConstructionService(new VisibilityService()).construct(
                scenario.state, builder.id(), BuildingType.BAZAAR, BAZAAR_HEX, scenario.rules);
        check(built instanceof Bazaar, "BuildingFactory did not create a Bazaar");
        check(scenario.stockpile().amountOf(ResourceType.WOOD) == wood - 30
                        && scenario.stockpile().amountOf(ResourceType.STONE) == stone - 20
                        && scenario.stockpile().amountOf(ResourceType.IRON) == iron - 5,
                "Bazaar construction cost is inconsistent with documented balance");
        check(builder.actionPoints().current() == 3, "Bazaar construction AP cost is incorrect");
        check(((Bazaar) built).health().maximum() == 120, "Bazaar HP balance is incorrect");
    }

    private static void everyBazaarLevelAndResourcePairUsesExactRate() {
        assertBazaarTrade(BazaarTradeLevel.LEVEL_1,
                ResourceType.FOOD, ResourceType.WOOD, 10, 5, 50);
        assertBazaarTrade(BazaarTradeLevel.LEVEL_2,
                ResourceType.WOOD, ResourceType.STONE, 100, 60, 60);
        assertBazaarTrade(BazaarTradeLevel.LEVEL_3,
                ResourceType.STONE, ResourceType.IRON, 500, 350, 70);

        for (ResourceType source : ResourceType.values()) {
            for (ResourceType target : ResourceType.values()) {
                if (source == target) continue;
                Scenario scenario = readyBazaar();
                int sourceBefore = scenario.stockpile().amountOf(source);
                int targetBefore = scenario.stockpile().amountOf(target);
                scenario.service.tradeAtBazaar(scenario.state, scenario.bazaar.id(),
                        BazaarTradeLevel.LEVEL_1, source, target);
                check(scenario.stockpile().amountOf(source) == sourceBefore - 10
                                && scenario.stockpile().amountOf(target) == targetBefore + 5,
                        "Bazaar rejected or miscalculated pair " + source + " -> " + target);
            }
        }

        expectFailure(() -> new BazaarExchangeRateStrategy(BazaarTradeLevel.LEVEL_1)
                        .quote(new TradeRequest(ResourceType.FOOD, ResourceType.WOOD, 100)),
                "Bazaar strategy accepted a non-tier source amount");
    }

    private static void bazaarValidationIsAtomicAtStorageBoundaries() {
        Scenario insufficient = scenario(true,
                ResourceBundle.of(9, 20, 20, 20), richCapacity(), true, true);
        ResourceBundle before = insufficient.stockpile().amounts();
        check(!insufficient.service.previewBazaar(insufficient.state, insufficient.bazaar.id(),
                BazaarTradeLevel.LEVEL_1, ResourceType.FOOD, ResourceType.WOOD).allowed(),
                "Insufficient Bazaar source was previewed as valid");
        expectFailure(() -> insufficient.service.tradeAtBazaar(insufficient.state,
                        insufficient.bazaar.id(), BazaarTradeLevel.LEVEL_1,
                        ResourceType.FOOD, ResourceType.WOOD),
                "Insufficient Bazaar trade succeeded");
        check(insufficient.stockpile().amounts().equals(before),
                "Failed source validation partially mutated resources");

        Scenario noSpace = scenario(true,
                ResourceBundle.of(100, 98, 0, 0), ResourceBundle.of(100, 100, 100, 100),
                true, true);
        before = noSpace.stockpile().amounts();
        expectFailure(() -> noSpace.service.tradeAtBazaar(noSpace.state, noSpace.bazaar.id(),
                        BazaarTradeLevel.LEVEL_1, ResourceType.FOOD, ResourceType.WOOD),
                "Trade exceeding destination capacity succeeded");
        check(noSpace.stockpile().amounts().equals(before),
                "Storage rejection debited the source or credited overflow");

        Scenario exact = scenario(true,
                ResourceBundle.of(100, 95, 0, 0), ResourceBundle.of(100, 100, 100, 100),
                true, true);
        exact.service.tradeAtBazaar(exact.state, exact.bazaar.id(), BazaarTradeLevel.LEVEL_1,
                ResourceType.FOOD, ResourceType.WOOD);
        check(exact.stockpile().amountOf(ResourceType.WOOD) == 100,
                "Trade ending exactly at capacity was rejected or clamped");

        Scenario sameType = readyBazaar();
        before = sameType.stockpile().amounts();
        check(!sameType.service.previewBazaar(sameType.state, sameType.bazaar.id(),
                BazaarTradeLevel.LEVEL_1, ResourceType.IRON, ResourceType.IRON).allowed(),
                "Same-resource trade was accepted");
        check(sameType.stockpile().amounts().equals(before)
                        && sameType.bazaar.tradeCooldown().isAvailable(1),
                "Invalid resource selection mutated state or consumed cooldown");
    }

    private static void bazaarCooldownIsPerBuildingAndPerTurn() {
        Scenario scenario = readyBazaar();
        scenario.service.tradeAtBazaar(scenario.state, scenario.bazaar.id(),
                BazaarTradeLevel.LEVEL_1, ResourceType.FOOD, ResourceType.WOOD);
        expectFailure(() -> scenario.service.tradeAtBazaar(scenario.state, scenario.bazaar.id(),
                        BazaarTradeLevel.LEVEL_1, ResourceType.STONE, ResourceType.IRON),
                "Bazaar accepted a second same-turn trade");

        Bazaar second = new Bazaar(BuildingId.random(), new HexCoord(0, 1), 120);
        scenario.state.player().addBuilding(second);
        scenario.service.tradeAtBazaar(scenario.state, second.id(),
                BazaarTradeLevel.LEVEL_1, ResourceType.STONE, ResourceType.IRON);
        nextTurn(scenario.state);
        scenario.service.tradeAtBazaar(scenario.state, scenario.bazaar.id(),
                BazaarTradeLevel.LEVEL_1, ResourceType.WOOD, ResourceType.FOOD);
    }

    private static void tradingPostRequiresTerritoryAndUsesFlooredEightyPercent() {
        Scenario scenario = scenario(true, richAmounts(), richCapacity(), true, false);
        check(!scenario.service.previewTradingPost(scenario.state, scenario.post.id(),
                ResourceType.WOOD, ResourceType.FOOD, 7).allowed(),
                "Uncontrolled Trading Post was usable");
        scenario.state.player().border().claimAll(Set.of(POST_HEX), scenario.state.map());
        var result = scenario.service.tradeAtTradingPost(scenario.state, scenario.post.id(),
                ResourceType.WOOD, ResourceType.FOOD, 7);
        check(result.endpointKind() == TradeEndpointKind.TRADING_POST
                        && result.sourceAmount() == 7 && result.targetAmount() == 5
                        && result.ratePercent() == 80,
                "Trading Post did not apply floored 80% conversion");
    }

    private static void tradingPostValidationAndCooldownAreAtomic() {
        Scenario tiny = scenario(true, richAmounts(), richCapacity(), true, true);
        ResourceBundle before = tiny.stockpile().amounts();
        check(!tiny.service.previewTradingPost(tiny.state, tiny.post.id(),
                ResourceType.FOOD, ResourceType.WOOD, 1).allowed(),
                "Zero-result floor trade was accepted");
        check(tiny.stockpile().amounts().equals(before)
                        && tiny.post.tradeCooldown().isAvailable(1),
                "Rejected zero-result trade mutated resources or cooldown");

        var result = tiny.service.tradeAtTradingPost(tiny.state, tiny.post.id(),
                ResourceType.FOOD, ResourceType.WOOD, 2);
        check(result.targetAmount() == 1, "Trading Post floor rounding boundary is wrong");
        expectFailure(() -> tiny.service.tradeAtTradingPost(tiny.state, tiny.post.id(),
                        ResourceType.STONE, ResourceType.IRON, 10),
                "Trading Post accepted a second same-turn trade");
        nextTurn(tiny.state);
        tiny.service.tradeAtTradingPost(tiny.state, tiny.post.id(),
                ResourceType.STONE, ResourceType.IRON, 10);

        Scenario noSpace = scenario(true,
                ResourceBundle.of(100, 99, 100, 100), ResourceBundle.of(100, 100, 100, 100),
                true, true);
        before = noSpace.stockpile().amounts();
        expectFailure(() -> noSpace.service.tradeAtTradingPost(noSpace.state, noSpace.post.id(),
                        ResourceType.FOOD, ResourceType.WOOD, 3),
                "Trading Post exceeded storage capacity");
        check(noSpace.stockpile().amounts().equals(before),
                "Trading Post storage failure was not atomic");
    }

    private static void tribeStrategiesValidateRelationProfileRateAndCooldown() {
        Scenario scenario = readyBazaar();
        TribeId farmer = registerTribe(scenario, "farmer", TribeTradeProfile.FARMER, 20);
        TribeId coastal = registerTribe(scenario, "coastal", TribeTradeProfile.COASTAL, 70);
        TribeId mountain = registerTribe(scenario, "mountain", TribeTradeProfile.MOUNTAIN, 20);
        TribeId trader = registerTribe(scenario, "trader", TribeTradeProfile.TRADER, 20);
        TribeId neutral = registerTribe(scenario, "neutral", TribeTradeProfile.TRADER, 0);

        check(scenario.service.tradeWithTribe(scenario.state, farmer,
                ResourceType.WOOD, ResourceType.FOOD, 7).targetAmount() == 5,
                "Farmer tribe did not supply Food at floored 75%");
        check(scenario.service.tradeWithTribe(scenario.state, coastal,
                ResourceType.IRON, ResourceType.FOOD, 4).targetAmount() == 3,
                "Coastal tribe did not supply Food at 75%");
        check(scenario.service.tradeWithTribe(scenario.state, mountain,
                ResourceType.FOOD, ResourceType.STONE, 7).targetAmount() == 5,
                "Mountain tribe did not supply Stone at 75%");
        check(!scenario.service.previewTribe(scenario.state, mountain,
                ResourceType.FOOD, ResourceType.WOOD, 8).allowed(),
                "Mountain tribe supplied an invalid target resource");

        var traderEndpoint = scenario.state.world().tribeTrades().require(trader);
        traderEndpoint.increaseRateBonus(10);
        check(scenario.service.tradeWithTribe(scenario.state, trader,
                ResourceType.STONE, ResourceType.WOOD, 7).targetAmount() == 6,
                "Trader integration bonus did not raise 80% to floored 90%");
        check(!scenario.service.previewTribe(scenario.state, neutral,
                ResourceType.FOOD, ResourceType.IRON, 10).allowed(),
                "Neutral tribe incorrectly allowed trade");
        expectFailure(() -> scenario.service.tradeWithTribe(scenario.state, farmer,
                        ResourceType.STONE, ResourceType.FOOD, 8),
                "Tribe accepted a second same-turn trade");
        nextTurn(scenario.state);
        scenario.service.tradeWithTribe(scenario.state, farmer,
                ResourceType.STONE, ResourceType.FOOD, 8);
    }

    private static void successfulTradePublishesIntegrationEvent() {
        Scenario scenario = readyBazaar();
        SynchronousEventBus bus = new SynchronousEventBus();
        AtomicInteger eventCount = new AtomicInteger();
        bus.subscribe(TradeCompletedEvent.class, event -> {
            check(event.result().targetAmount() == 5, "Trade event contains wrong quote");
            eventCount.incrementAndGet();
        });
        TradeService service = new TradeService(bus);
        service.tradeAtBazaar(scenario.state, scenario.bazaar.id(),
                BazaarTradeLevel.LEVEL_1, ResourceType.FOOD, ResourceType.WOOD);
        check(eventCount.get() == 1, "Successful trade did not publish exactly one event");
    }

    private static void defaultGamesContainANeutralTradingPost() {
        GameMap map = map();
        GameState state = new GameFactory(new VisibilityService()).create(
                map, CENTER, 44L, DefaultRulesetFactory.create());
        check(state.world().tradingPosts().all().size() == 1,
                "Default game did not preplace one neutral Trading Post");
        var post = state.world().tradingPosts().all().iterator().next();
        check(state.player().buildingAt(post.position()).isEmpty(),
                "Neutral Trading Post was stored as a player building");
    }

    private static void assertBazaarTrade(BazaarTradeLevel level, ResourceType source,
            ResourceType target, int spent, int received, int rate) {
        Scenario scenario = readyBazaar();
        int sourceBefore = scenario.stockpile().amountOf(source);
        int targetBefore = scenario.stockpile().amountOf(target);
        var result = scenario.service.tradeAtBazaar(
                scenario.state, scenario.bazaar.id(), level, source, target);
        check(result.sourceAmount() == spent && result.targetAmount() == received
                        && result.ratePercent() == rate,
                "Incorrect quote for " + level);
        check(scenario.stockpile().amountOf(source) == sourceBefore - spent
                        && scenario.stockpile().amountOf(target) == targetBefore + received,
                "Incorrect stockpile mutation for " + level);
    }

    private static TribeId registerTribe(
            Scenario scenario, String name, TribeTradeProfile profile, int relationScore) {
        TribeId id = new TribeId(name);
        scenario.state.world().relationships().add(new RelationshipState(
                id, relationScore, civilization.model.diplomacy.DiplomaticStance.PEACE));
        scenario.state.world().tribeTrades().add(new TribeTradeEndpoint(id, profile));
        return id;
    }

    private static Scenario readyBazaar() {
        return scenario(true, richAmounts(), richCapacity(), true, true);
    }

    private static Scenario scenario(boolean settlement, ResourceBundle amounts,
            ResourceBundle capacities, boolean addBazaar, boolean ownPost) {
        GameMap map = map();
        ResourceStockpile stockpile = new ResourceStockpile(amounts, capacities);
        TownHall hall = new TownHall(BuildingId.random(), CENTER, stockpile, new ProductionQueue());
        if (settlement) hall.upgradeTo(HallLevel.SETTLEMENT);
        Set<HexCoord> territory = ownPost
                ? Set.of(CENTER, BAZAAR_HEX, POST_HEX, new HexCoord(0, 1))
                : Set.of(CENTER, BAZAAR_HEX, new HexCoord(0, 1));
        PlayerState player = new PlayerState(hall, new UnitCapacity(30),
                new EmpireBorder(territory, map),
                new VisibilityState(allCoordinates(map), allCoordinates(map)),
                new TechnologyState());
        Bazaar bazaar = null;
        if (addBazaar) {
            bazaar = new Bazaar(BuildingId.random(), BAZAAR_HEX, 120);
            player.addBuilding(bazaar);
        }
        WorldState world = new WorldState(map);
        TradingPost post = new TradingPost(new TradingPostId("post-1"), POST_HEX);
        world.tradingPosts().add(post, map);
        GameState state = new GameState(world, player, new TurnState(), new CrisisState(), 77L,
                new SeasonState(), new SplitMix64GameplayRandom(77L));
        return new Scenario(state, DefaultRulesetFactory.create(), new TradeService(), bazaar, post);
    }

    private static GameMap map() {
        List<HexTile> tiles = new ArrayList<>();
        tiles.add(new HexTile(CENTER, TerrainType.GRASSLAND));
        CENTER.neighbors().forEach(coord -> tiles.add(new HexTile(coord, TerrainType.PLAINS)));
        return new GameMap(tiles);
    }

    private static Set<HexCoord> allCoordinates(GameMap map) {
        return Set.copyOf(map.tiles().stream().map(HexTile::coordinate).toList());
    }

    private static ResourceBundle richAmounts() { return ResourceBundle.of(1_000, 1_000, 1_000, 1_000); }
    private static ResourceBundle richCapacity() { return ResourceBundle.of(2_000, 2_000, 2_000, 2_000); }

    private static void nextTurn(GameState state) {
        state.turn().beginNextTurnResolution();
        state.turn().finishResolution(TurnEconomyReport.EMPTY);
    }

    private static void expectFailure(Runnable action, String message) {
        try { action.run(); } catch (RuntimeException expected) { return; }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record Scenario(GameState state, GameRules rules, TradeService service,
            Bazaar bazaar, TradingPost post) {
        private TownHall hall() { return state.player().townHall(); }
        private ResourceStockpile stockpile() { return hall().stockpile(); }
    }
}
