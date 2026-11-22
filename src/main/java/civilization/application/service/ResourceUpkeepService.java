package civilization.application.service;

import civilization.model.building.Building;
import civilization.model.economy.ResourceBundle;
import civilization.model.economy.ResourceStockpile;
import civilization.model.economy.ResourceType;
import civilization.model.game.GameState;
import civilization.model.unit.Unit;
import civilization.rules.GameRules;

import java.util.EnumMap;

public final class ResourceUpkeepService {
    public UpkeepResult apply(GameState state, GameRules rules) {
        EnumMap<ResourceType, Integer> consumed = zeroAmounts();
        EnumMap<ResourceType, Integer> shortage = zeroAmounts();
        ResourceStockpile stockpile = state.player().townHall().stockpile();

        int foodDue = state.player().units().stream()
                .filter(Unit::isActive)
                .mapToInt(unit -> rules.unit(unit.type()).foodUpkeep())
                .sum();
        // PHASE 3 - Added in Phase 3
        int foodPaid = Math.min(foodDue, stockpile.availableAmountOf(ResourceType.FOOD));
        if (foodPaid > 0) {
            stockpile.debit(ResourceBundle.of(ResourceType.FOOD, foodPaid));
        }
        consumed.put(ResourceType.FOOD, foodPaid);
        shortage.put(ResourceType.FOOD, foodDue - foodPaid);

        for (Building building : state.player().constructedBuildings()) {
            if (!building.isActive()) {
                continue;
            }
            var definition = rules.building(building.type());
            if (definition.upkeepAmount() == 0) {
                building.resetUnpaidUpkeepSequence();
                continue;
            }
            ResourceType resource = definition.upkeepResourceOptional().orElseThrow();
            ResourceBundle cost = ResourceBundle.of(resource, definition.upkeepAmount());
            if (stockpile.canAfford(cost)) {
                stockpile.debit(cost);
                consumed.put(resource, Math.addExact(consumed.get(resource), definition.upkeepAmount()));
                building.resetUnpaidUpkeepSequence();
            } else {
                shortage.put(resource, Math.addExact(shortage.get(resource), definition.upkeepAmount()));
                boolean broke = building.recordUnpaidUpkeep();
                if (broke) {
                    state.player().releaseWorkersFromBuilding(building.id());
                }
            }
        }
        return new UpkeepResult(new ResourceBundle(consumed), new ResourceBundle(shortage));
    }

    private static EnumMap<ResourceType, Integer> zeroAmounts() {
        EnumMap<ResourceType, Integer> amounts = new EnumMap<>(ResourceType.class);
        for (ResourceType type : ResourceType.values()) {
            amounts.put(type, 0);
        }
        return amounts;
    }
}
