package civilization.rules.tribe;

import civilization.model.economy.ResourceBundle;
import civilization.model.tribe.TribeType;
import civilization.model.unit.UnitType;
import java.util.List;

/** Tribe-type Strategy: content values without type switches in turn/action services. */
public interface TribeBehaviorStrategy {
    TribeType type();
    int campMaximumHealth();
    List<UnitType> initialGuardTypes();
    UnitType producedGuardType();
    ResourceBundle conquestLoot();
    ResourceBundle alliedYieldPerTurn();
    int allianceTradeBonusPercent();
    boolean grantsAuxiliarySwordsmanOnAlliance();
    String allianceRewardDescription();
}
