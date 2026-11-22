package civilization.model.diplomacy;

public final class DispleasedRelationshipState implements RelationshipBehaviorState {
    private static final long serialVersionUID = 1L;
    @Override public RelationBand band() { return RelationBand.DISPLEASED; }
    @Override public boolean giftAllowed() { return true; }
    @Override public boolean tradeAllowed() { return false; }
    @Override public boolean missionAllowed() { return false; }
    @Override public boolean peaceRequestAllowed() { return false; }
    @Override public boolean allianceRequestAllowed() { return false; }
    @Override public boolean hostile() { return false; }
    @Override public int intrusionPenalty() { return -10; }
}
