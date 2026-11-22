package civilization.application.service;

import civilization.model.building.Building;
import civilization.model.building.BuildingType;
import civilization.model.building.Dock;
import civilization.model.economy.ResourceBundle;
import civilization.model.economy.ResourceType;
import civilization.model.game.GameState;
import civilization.model.map.NaturalResourceType;
import civilization.model.map.HexCoord;
import civilization.model.technology.UpgradeId;
import civilization.model.technology.TechnologyId;
import civilization.rules.GameRules;
import civilization.rules.SeasonPolicy;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

/**
 * Calculates production without mutating deposits or storage.
 * The committed production service and HUD forecast intentionally use this same calculation path.
 */
public final class ProductionCalculator {
    private final AdjacencyService adjacencyService;
    private final HappinessService happinessService;
    private final DockFishSourceResolver dockSources;
    private final SeasonPolicy seasons = new SeasonPolicy();

    public ProductionCalculator() {
        this(new AdjacencyService(), new HappinessService(), new DockFishSourceResolver());
    }

    public ProductionCalculator(AdjacencyService adjacencyService,
            HappinessService happinessService, DockFishSourceResolver dockSources) {
        this.adjacencyService = Objects.requireNonNull(adjacencyService, "adjacencyService");
        this.happinessService = Objects.requireNonNull(happinessService, "happinessService");
        this.dockSources = Objects.requireNonNull(dockSources, "dockSources");
    }

    public ProductionPlan calculate(GameState state, GameRules rules) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(rules, "rules");
        EnumMap<ResourceType, Integer> gross = zeroAmounts();
        if (state.player().townHall().isActive()) {
            add(gross, rules.townHallSafeguardProduction());
        }
        List<BuildingProduction> entries = new ArrayList<>();
        var adjacencyBonuses = adjacencyService.productionBonuses(state);
        var happiness = happinessService.evaluate(state);
        var workerCounts = state.player().stationedWorkerCounts();
        int workerPenalty = happiness.tier() == civilization.model.happiness.HappinessTier.DISPLEASED
                || happiness.tier() == civilization.model.happiness.HappinessTier.REVOLT ? 1 : 0;
        boolean goldenAge = happiness.tier()
                == civilization.model.happiness.HappinessTier.GOLDEN_AGE;
        var reserved = new java.util.HashMap<DepositKey, Integer>();

        for (Building building : state.player().constructedBuildings().stream()
                .sorted(java.util.Comparator
                        .comparingInt((Building item) -> item.position().q())
                        .thenComparingInt(item -> item.position().r()))
                .toList()) {
            if (!building.type().isProductionBuilding() || !building.isActive()) {
                continue;
            }
            if (state.world().disasters().blocksProductionAt(building.position())) continue;
            int workers = workerCounts.getOrDefault(building.id(), 0);
            if (workers == 0) {
                continue;
            }
            int perWorker = Math.max(0,
                    rules.building(building.type()).baseProductionPerWorker()
                            - workerPenalty);
            int requested = Math.multiplyExact(workers, perWorker);
            requested = applyTechnologyModifiers(state, building.type(), requested);
            requested = Math.addExact(requested,
                    adjacencyBonuses.getOrDefault(building.id(), 0));
            requested = Math.max(0, Math.addExact(requested,
                    seasons.productionAdjustment(state.season().season(), building.type())));
            if (goldenAge) requested = Math.multiplyExact(requested, 110) / 100;

            Source source = sourceFor(state, building);
            if (source == null) continue;
            var tile = state.map().requireTile(source.coordinate());
            NaturalResourceType depositType = source.type();
            DepositKey key = new DepositKey(source.coordinate(), depositType);
            int available = tile.resourceDeposit(depositType).orElseThrow().remainingAmount()
                    - reserved.getOrDefault(key, 0);
            int planned = Math.min(requested, available);
            if (planned == 0) {
                continue;
            }
            ResourceType output = building.type().producedResource().orElseThrow();
            entries.add(new BuildingProduction(building.id(), source.coordinate(),
                    depositType, output, planned));
            reserved.merge(key, planned, Math::addExact);
            gross.put(output, Math.addExact(gross.get(output), planned));
        }
        return new ProductionPlan(new ResourceBundle(gross), entries);
    }

    private Source sourceFor(GameState state, Building building) {
        if (building instanceof Dock) {
            return dockSources.availableSources(state, building.position(), true).stream()
                    .map(coord -> new Source(coord, NaturalResourceType.FISH))
                    .findFirst().orElse(null);
        }
        var tile = state.map().requireTile(building.position());
        return building.type().eligibleNaturalResources().stream()
                .sorted(java.util.Comparator.comparingInt(Enum::ordinal))
                .filter(tile::hasAvailableNaturalResource)
                .map(type -> new Source(building.position(), type))
                .findFirst().orElse(null);
    }

    private static int applyTechnologyModifiers(
            GameState state, BuildingType type, int baseAmount) {
        if ((type == BuildingType.STONE_MINE || type == BuildingType.IRON_MINE)
                && (state.player().technologies().isCompleted(UpgradeId.PROFESSIONAL_TOOLS)
                || state.player().technologies().isResearched(TechnologyId.STEEL_TOOLS))) {
            return Math.multiplyExact(baseAmount, 3) / 2;
        }
        return baseAmount;
    }

    private static EnumMap<ResourceType, Integer> zeroAmounts() {
        EnumMap<ResourceType, Integer> amounts = new EnumMap<>(ResourceType.class);
        for (ResourceType type : ResourceType.values()) {
            amounts.put(type, 0);
        }
        return amounts;
    }

    private static void add(EnumMap<ResourceType, Integer> target, ResourceBundle bundle) {
        for (ResourceType type : ResourceType.values()) {
            target.put(type, Math.addExact(target.get(type), bundle.amountOf(type)));
        }
    }

    private record Source(HexCoord coordinate, NaturalResourceType type) { }
    private record DepositKey(HexCoord coordinate, NaturalResourceType type) { }
}
