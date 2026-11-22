package civilization.application.query;

import civilization.model.diplomacy.DiplomaticStance;
import civilization.model.diplomacy.RelationBand;
import civilization.model.identity.TribeId;
import civilization.model.map.HexCoord;
import civilization.model.mission.MissionStatus;
import civilization.model.mission.MissionType;
import civilization.model.mission.MissionId;
import civilization.model.tribe.TribeType;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Visibility-filtered tribe data. Current relation, HP and mission are hidden in fog. */
public record TribeSnapshot(TribeId id, String name, TribeType type, HexCoord camp,
        boolean currentlyVisible, boolean defeated, OptionalInt relationship,
        Optional<RelationBand> relationshipState, Optional<DiplomaticStance> stance,
        OptionalInt campHealth, Optional<MissionType> missionType,
        Optional<MissionStatus> missionStatus, Optional<MissionId> missionId,
        OptionalInt missionDeadline, Optional<String> tradeOffer,
        String activeBonus, String missionObjective, String missionReward) {
    public TribeSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(camp, "camp");
        Objects.requireNonNull(relationship, "relationship");
        Objects.requireNonNull(relationshipState, "relationshipState");
        Objects.requireNonNull(stance, "stance");
        Objects.requireNonNull(campHealth, "campHealth");
        Objects.requireNonNull(missionType, "missionType");
        Objects.requireNonNull(missionStatus, "missionStatus");
        Objects.requireNonNull(missionId, "missionId");
        Objects.requireNonNull(missionDeadline, "missionDeadline");
        Objects.requireNonNull(tradeOffer, "tradeOffer");
        activeBonus = Objects.requireNonNull(activeBonus, "activeBonus");
        missionObjective = Objects.requireNonNull(missionObjective, "missionObjective");
        missionReward = Objects.requireNonNull(missionReward, "missionReward");
        if (!currentlyVisible && (relationship.isPresent() || relationshipState.isPresent()
                || stance.isPresent() || campHealth.isPresent() || missionType.isPresent()
                || missionStatus.isPresent() || missionId.isPresent()
                || missionDeadline.isPresent() || tradeOffer.isPresent()
                || !activeBonus.isEmpty() || !missionObjective.isEmpty()
                || !missionReward.isEmpty())) {
            throw new IllegalArgumentException("Fogged tribes cannot expose current information");
        }
    }
}
