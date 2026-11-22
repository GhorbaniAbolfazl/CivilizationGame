package civilization.model.diplomacy;

public final class NeutralRelationshipState implements RelationshipBehaviorState {
    private static final long serialVersionUID = 1L;
    @Override public RelationBand band() { return RelationBand.NEUTRAL; }
    @Override public boolean giftAllowed() { return true; }
    @Override public boolean tradeAllowed() { return false; }
    @Override public boolean missionAllowed() { return false; }
    @Override public boolean peaceRequestAllowed() { return false; }
    @Override public boolean allianceRequestAllowed() { return false; }
    @Override public boolean hostile() { return false; }
    @Override public int intrusionPenalty() { return -5; }
}
