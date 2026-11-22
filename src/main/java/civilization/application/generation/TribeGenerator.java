package civilization.application.generation;

import civilization.application.ActionFailure;
import civilization.application.factory.UnitFactory;
import civilization.model.diplomacy.RelationshipState;
import civilization.model.game.GameState;
import civilization.model.identity.TribeId;
import civilization.model.map.HexCoord;
import civilization.model.map.TerrainType;
import civilization.model.trade.TribeTradeEndpoint;
import civilization.model.tribe.Tribe;
import civilization.model.tribe.TribeCamp;
import civilization.model.tribe.TribeType;
import civilization.rules.GameRules;
import civilization.rules.tribe.TribeStrategyCatalog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Deterministic neutral-tribe placement and registration for a generated world. */
public final class TribeGenerator {
    public static final int DEFAULT_TRIBE_COUNT = 6;
    public static final int MINIMUM_HALL_DISTANCE = 6;
    private final TribeStrategyCatalog strategies;
    private final UnitFactory unitFactory;

    public TribeGenerator() { this(new TribeStrategyCatalog(), new UnitFactory()); }

    public TribeGenerator(TribeStrategyCatalog strategies, UnitFactory unitFactory) {
        this.strategies = Objects.requireNonNull(strategies, "strategies");
        this.unitFactory = Objects.requireNonNull(unitFactory, "unitFactory");
    }

    public List<Tribe> generate(GameState state, GameRules rules) {
        return generate(state, rules, DEFAULT_TRIBE_COUNT);
    }

    public List<Tribe> generate(GameState state, GameRules rules, int count) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(rules, "rules");
        if (count <= 0) throw new IllegalArgumentException("Tribe count must be positive");
        if (!state.world().tribes().all().isEmpty()) {
            throw new IllegalStateException("Tribes have already been generated");
        }
        List<TribeType> order = List.of(TribeType.MOUNTAIN, TribeType.COASTAL,
                TribeType.FARMER, TribeType.WARRIOR, TribeType.TRADER, TribeType.FARMER,
                TribeType.MOUNTAIN);
        List<HexCoord> camps = new ArrayList<>();
        List<Tribe> generated = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            int tribeNumber = index + 1;
            TribeType type = order.get(index % order.size());
            HexCoord campHex = candidates(state, type, camps).stream().findFirst()
                    .orElseThrow(() -> new ActionFailure(
                            "Map cannot place mandatory " + type + " tribe " + tribeNumber));
            camps.add(campHex);
            TribeId id = new TribeId(type.name().toLowerCase() + "-" + (index + 1));
            var strategy = strategies.require(type);
            TribeCamp camp = new TribeCamp(id, campHex, strategy.campMaximumHealth());
            Set<HexCoord> territory = territory(state, campHex);
            Tribe tribe = new Tribe(id, displayName(type, index + 1), type, camp, territory);
            register(state, rules, tribe, strategy.initialGuardTypes());
            generated.add(tribe);
        }
        return List.copyOf(generated);
    }

    private static List<HexCoord> candidates(
            GameState state, TribeType type, List<HexCoord> selected) {
        HexCoord hall = state.player().townHall().position();
        return state.map().tiles().stream().map(tile -> tile.coordinate())
                .filter(coord -> coord.distanceTo(hall) >= MINIMUM_HALL_DISTANCE)
                .filter(coord -> selected.stream().allMatch(existing -> existing.distanceTo(coord) >= 3))
                .filter(coord -> state.world().tradingPosts().all().stream()
                        .noneMatch(post -> post.position().equals(coord)))
                .filter(coord -> validForType(state, coord, type))
                .sorted(Comparator.comparingInt(HexCoord::q).thenComparingInt(HexCoord::r))
                .toList();
    }

    private static boolean validForType(GameState state, HexCoord coordinate, TribeType type) {
        TerrainType terrain = state.map().requireTile(coordinate).terrain();
        if (terrain == TerrainType.SEA || terrain == TerrainType.MOUNTAIN_RANGE) return false;
        return switch (type) {
            case MOUNTAIN -> terrain == TerrainType.MOUNTAIN;
            case COASTAL -> state.map().neighborsOf(coordinate).stream()
                    .anyMatch(tile -> tile.terrain() == TerrainType.SEA);
            case FARMER -> terrain == TerrainType.FOREST || terrain == TerrainType.PLAINS
                    || terrain == TerrainType.GRASSLAND;
            case WARRIOR, TRADER -> true;
        };
    }

    private static Set<HexCoord> territory(GameState state, HexCoord camp) {
        Set<HexCoord> result = new LinkedHashSet<>();
        result.add(camp);
        state.map().neighborsOf(camp).stream()
                .filter(tile -> tile.terrain() != TerrainType.SEA
                        && tile.terrain() != TerrainType.MOUNTAIN_RANGE)
                .map(tile -> tile.coordinate())
                .filter(coord -> !state.player().border().owns(coord))
                .forEach(result::add);
        return Set.copyOf(result);
    }

    private void register(GameState state, GameRules rules, Tribe tribe,
            List<civilization.model.unit.UnitType> guardTypes) {
        state.world().tribes().add(tribe);
        state.world().relationships().add(new RelationshipState(tribe.id()));
        tribe.type().tradeProfile().ifPresent(profile -> state.world().tribeTrades()
                .add(new TribeTradeEndpoint(tribe.id(), profile)));
        state.world().structures().add(tribe.camp().structure(), state.map());
        tribe.territory().forEach(coord ->
                state.world().territories().claim(coord, tribe.id().asFactionId(), state.map()));
        for (var type : guardTypes) {
            state.world().units().add(unitFactory.create(type, tribe.camp().position(), rules,
                    tribe.id().asFactionId()), state.map());
        }
    }

    private static String displayName(TribeType type, int number) {
        String word = type.name().charAt(0) + type.name().substring(1).toLowerCase();
        return word + " Clan " + number;
    }
}
