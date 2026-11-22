package civilization.rules.tribe;

import civilization.model.economy.ResourceBundle;
import civilization.model.tribe.TribeType;
import civilization.model.unit.UnitType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TribeStrategyCatalog {
    private final Map<TribeType, TribeBehaviorStrategy> strategies;

    public TribeStrategyCatalog() {
        EnumMap<TribeType, TribeBehaviorStrategy> values = new EnumMap<>(TribeType.class);
        values.put(TribeType.FARMER, strategy(TribeType.FARMER, 40,
                List.of(UnitType.SWORDSMAN), UnitType.SWORDSMAN,
                ResourceBundle.of(20, 0, 0, 0), ResourceBundle.of(2, 0, 0, 0),
                0, false, "+2 Food each turn"));
        values.put(TribeType.WARRIOR, strategy(TribeType.WARRIOR, 70,
                List.of(UnitType.SWORDSMAN, UnitType.SWORDSMAN, UnitType.CAVALRY),
                UnitType.SWORDSMAN, ResourceBundle.of(0, 0, 0, 10), ResourceBundle.ZERO,
                0, true, "One auxiliary Swordsman"));
        values.put(TribeType.TRADER, strategy(TribeType.TRADER, 50,
                List.of(UnitType.SWORDSMAN), UnitType.SWORDSMAN,
                ResourceBundle.of(5, 5, 5, 5), ResourceBundle.ZERO,
                5, false, "+5 percentage points on tribe trade"));
        values.put(TribeType.MOUNTAIN, strategy(TribeType.MOUNTAIN, 60,
                List.of(UnitType.SWORDSMAN, UnitType.ARCHER), UnitType.ARCHER,
                ResourceBundle.of(0, 0, 15, 10), ResourceBundle.of(0, 0, 1, 1),
                0, false, "+1 Stone and +1 Iron each turn"));
        values.put(TribeType.COASTAL, strategy(TribeType.COASTAL, 50,
                List.of(UnitType.ARCHER), UnitType.ARCHER,
                ResourceBundle.of(15, 10, 0, 0), ResourceBundle.of(1, 0, 0, 0),
                0, false, "+1 Food each turn and one Dock discount"));
        strategies = Map.copyOf(values);
    }

    public TribeBehaviorStrategy require(TribeType type) {
        return Objects.requireNonNull(strategies.get(Objects.requireNonNull(type, "type")));
    }

    private static TribeBehaviorStrategy strategy(TribeType type, int hp,
            List<UnitType> guards, UnitType produced, ResourceBundle loot,
            ResourceBundle alliedYield, int tradeBonus, boolean auxiliary, String description) {
        return new ConfiguredTribeBehaviorStrategy(type, hp, guards, produced, loot,
                alliedYield, tradeBonus, auxiliary, description);
    }
}
