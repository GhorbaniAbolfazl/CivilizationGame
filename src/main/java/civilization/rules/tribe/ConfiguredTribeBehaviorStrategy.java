package civilization.rules.tribe;

import civilization.model.economy.ResourceBundle;
import civilization.model.tribe.TribeType;
import civilization.model.unit.UnitType;
import java.util.List;
import java.util.Objects;

record ConfiguredTribeBehaviorStrategy(
        TribeType type,
        int campMaximumHealth,
        List<UnitType> initialGuardTypes,
        UnitType producedGuardType,
        ResourceBundle conquestLoot,
        ResourceBundle alliedYieldPerTurn,
        int allianceTradeBonusPercent,
        boolean grantsAuxiliarySwordsmanOnAlliance,
        String allianceRewardDescription) implements TribeBehaviorStrategy {
    ConfiguredTribeBehaviorStrategy {
        Objects.requireNonNull(type, "type");
        if (campMaximumHealth <= 0) throw new IllegalArgumentException("Camp HP must be positive");
        initialGuardTypes = List.copyOf(initialGuardTypes);
        if (initialGuardTypes.stream().anyMatch(unit -> !unit.isMilitary())
                || !Objects.requireNonNull(producedGuardType, "producedGuardType").isMilitary()) {
            throw new IllegalArgumentException("Tribe guards must be military units");
        }
        Objects.requireNonNull(conquestLoot, "conquestLoot");
        Objects.requireNonNull(alliedYieldPerTurn, "alliedYieldPerTurn");
        if (allianceTradeBonusPercent < 0) throw new IllegalArgumentException("Trade bonus cannot be negative");
        allianceRewardDescription = Objects.requireNonNull(
                allianceRewardDescription, "allianceRewardDescription");
    }
}
