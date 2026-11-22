package civilization.model.diplomacy;

/** State Pattern policy for actions and mandatory behavior at one relationship band. */
public sealed interface RelationshipBehaviorState extends java.io.Serializable permits EnemyRelationshipState,
        DispleasedRelationshipState, NeutralRelationshipState,
        FriendlyRelationshipState, AlliedRelationshipState {
    RelationBand band();
    boolean giftAllowed();
    boolean tradeAllowed();
    boolean missionAllowed();
    boolean peaceRequestAllowed();
    boolean allianceRequestAllowed();
    boolean hostile();
    int intrusionPenalty();
}
