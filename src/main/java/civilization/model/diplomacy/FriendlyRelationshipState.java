package civilization.model.diplomacy;

public final class FriendlyRelationshipState implements RelationshipBehaviorState {
    private static final long serialVersionUID = 1L;
    @Override public RelationBand band() { return RelationBand.FRIENDLY; }
    @Override public boolean giftAllowed() { return true; }
    @Override public boolean tradeAllowed() { return true; }
    @Override public boolean missionAllowed() { return true; }
    @Override public boolean peaceRequestAllowed() { return false; }
    @Override public boolean allianceRequestAllowed() { return false; }
    @Override public boolean hostile() { return false; }
    @Override public int intrusionPenalty() { return 0; }
}
