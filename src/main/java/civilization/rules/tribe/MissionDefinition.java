package civilization.rules.tribe;

import civilization.model.economy.ResourceBundle;
import civilization.model.mission.MissionType;
import civilization.model.tribe.TribeType;
import java.util.Objects;

public record MissionDefinition(TribeType tribeType, MissionType missionType,
        int durationTurns, ResourceBundle deliveryCost, ResourceBundle resourceReward,
        int relationReward) {
    public MissionDefinition {
        Objects.requireNonNull(tribeType, "tribeType");
        Objects.requireNonNull(missionType, "missionType");
        Objects.requireNonNull(deliveryCost, "deliveryCost");
        Objects.requireNonNull(resourceReward, "resourceReward");
        if (durationTurns <= 0 || relationReward <= 0) {
            throw new IllegalArgumentException("Mission duration and relation reward must be positive");
        }
    }
}
