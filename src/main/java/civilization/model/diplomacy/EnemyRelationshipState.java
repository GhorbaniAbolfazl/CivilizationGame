package civilization.model.diplomacy;

public final class EnemyRelationshipState implements RelationshipBehaviorState {
    private static final long serialVersionUID = 1L;
    @Override public RelationBand band() { return RelationBand.ENEMY; }
    @Override public boolean giftAllowed() { return false; }
    @Override public boolean tradeAllowed() { return false; }
    @Override public boolean missionAllowed() { return false; }
    @Override public boolean peaceRequestAllowed() { return true; }
    @Override public boolean allianceRequestAllowed() { return false; }
    @Override public boolean hostile() { return true; }
    @Override public int intrusionPenalty() { return 0; }
}
