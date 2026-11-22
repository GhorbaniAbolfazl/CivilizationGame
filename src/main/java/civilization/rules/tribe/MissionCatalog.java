package civilization.rules.tribe;

import civilization.model.economy.ResourceBundle;
import civilization.model.mission.MissionType;
import civilization.model.tribe.TribeType;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class MissionCatalog {
    private final Map<TribeType, MissionDefinition> definitions;

    public MissionCatalog() {
        EnumMap<TribeType, MissionDefinition> values = new EnumMap<>(TribeType.class);
        values.put(TribeType.FARMER, new MissionDefinition(TribeType.FARMER,
                MissionType.FARMER_SUPPLY, 5, ResourceBundle.of(0, 20, 10, 0),
                ResourceBundle.of(30, 0, 0, 0), 15));
        values.put(TribeType.TRADER, new MissionDefinition(TribeType.TRADER,
                MissionType.TRADER_ROAD, 10, ResourceBundle.ZERO, ResourceBundle.ZERO, 20));
        values.put(TribeType.WARRIOR, new MissionDefinition(TribeType.WARRIOR,
                MissionType.WARRIOR_BOUNTY, 8, ResourceBundle.ZERO, ResourceBundle.ZERO, 20));
        values.put(TribeType.MOUNTAIN, new MissionDefinition(TribeType.MOUNTAIN,
                MissionType.MOUNTAIN_SUPPLY, 6, ResourceBundle.of(0, 15, 0, 10),
                ResourceBundle.of(0, 0, 20, 0), 15));
        values.put(TribeType.COASTAL, new MissionDefinition(TribeType.COASTAL,
                MissionType.COASTAL_DOCK, 10, ResourceBundle.ZERO,
                ResourceBundle.of(30, 0, 0, 0), 20));
        definitions = Map.copyOf(values);
    }

    public MissionDefinition require(TribeType type) {
        return Objects.requireNonNull(definitions.get(Objects.requireNonNull(type, "type")));
    }
}
