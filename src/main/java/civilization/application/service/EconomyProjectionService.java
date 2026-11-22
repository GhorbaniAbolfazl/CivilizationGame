package civilization.application.service;

import civilization.model.economy.ResourceType;
import civilization.model.game.GameState;
import civilization.rules.GameRules;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Pure forecast used by the HUD; it performs no extraction, payment, or state mutation. */
public final class EconomyProjectionService {
    private final ProductionCalculator productionCalculator;

    public EconomyProjectionService() {
        this(new ProductionCalculator());
    }

    public EconomyProjectionService(ProductionCalculator productionCalculator) {
        this.productionCalculator = Objects.requireNonNull(
                productionCalculator, "productionCalculator");
    }

    public Map<ResourceType, Integer> netPerTurn(GameState state, GameRules rules) {
        var gross = productionCalculator.calculate(state, rules).gross();
        EnumMap<ResourceType, Integer> net = new EnumMap<>(ResourceType.class);
        for (ResourceType type : ResourceType.values()) {
            net.put(type, gross.amountOf(type));
        }
        for (var building : state.player().constructedBuildings()) {
            if (!building.isActive()) {
                continue;
            }
            var definition = rules.building(building.type());
            definition.upkeepResourceOptional().ifPresent(resource ->
                    net.put(resource, net.get(resource) - definition.upkeepAmount()));
        }
        int foodUse = state.player().units().stream()
                .mapToInt(unit -> rules.unit(unit.type()).foodUpkeep()).sum();
        net.put(ResourceType.FOOD, net.get(ResourceType.FOOD) - foodUse);
        return Map.copyOf(net);
    }
}
