package civilization.model.diplomacy;

public final class RelationshipStateFactory implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private static final RelationshipBehaviorState ENEMY = new EnemyRelationshipState();
    private static final RelationshipBehaviorState DISPLEASED = new DispleasedRelationshipState();
    private static final RelationshipBehaviorState NEUTRAL = new NeutralRelationshipState();
    private static final RelationshipBehaviorState FRIENDLY = new FriendlyRelationshipState();
    private static final RelationshipBehaviorState ALLIED = new AlliedRelationshipState();

    private RelationshipStateFactory() { }

    public static RelationshipBehaviorState forBand(RelationBand band) {
        return switch (band) {
            case ENEMY -> ENEMY;
            case DISPLEASED -> DISPLEASED;
            case NEUTRAL -> NEUTRAL;
            case FRIENDLY -> FRIENDLY;
            case ALLIED -> ALLIED;
        };
    }
}
