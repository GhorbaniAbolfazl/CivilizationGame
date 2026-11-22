package civilization.rules.tribe;

import civilization.model.building.BuildingType;
import civilization.model.economy.ResourceBundle;
import civilization.model.economy.ResourceType;
import civilization.model.game.GameState;
import java.util.EnumMap;
import java.util.Objects;
import java.util.Optional;

/** Developer-choice value: the one-use Coastal reward reduces the next Dock cost by 20%. */
public final class TribeDockDiscountPolicy {
    public static final int DISCOUNT_PERCENT = 20;

    public ResourceBundle effectiveCost(GameState state, BuildingType type, ResourceBundle base) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(base, "base");
        if (type != BuildingType.DOCK || availableReward(state).isEmpty()) return base;
        EnumMap<ResourceType, Integer> amounts = new EnumMap<>(ResourceType.class);
        for (ResourceType resource : ResourceType.values()) {
            int numerator = Math.multiplyExact(base.amountOf(resource), 100 - DISCOUNT_PERCENT);
            amounts.put(resource, (numerator + 99) / 100);
        }
        return new ResourceBundle(amounts);
    }

    public void consumeIfApplied(GameState state, BuildingType type) {
        if (type == BuildingType.DOCK) availableReward(state).ifPresent(
                civilization.model.tribe.Tribe::consumeNextDockDiscount);
    }

    private Optional<civilization.model.tribe.Tribe> availableReward(GameState state) {
        return state.world().tribes().all().stream()
                .filter(civilization.model.tribe.Tribe::hasNextDockDiscount).findFirst();
    }
}
