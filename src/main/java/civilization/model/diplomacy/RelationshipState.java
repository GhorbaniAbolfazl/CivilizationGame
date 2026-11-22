package civilization.model.diplomacy;

import civilization.model.identity.TribeId;
import java.util.Objects;

public final class RelationshipState implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final TribeId tribeId;
    private int score;
    private DiplomaticStance stance;
    private RelationshipBehaviorState behavior;

    public RelationshipState(TribeId tribeId) { this(tribeId, 0, DiplomaticStance.PEACE); }

    public RelationshipState(TribeId tribeId, int score, DiplomaticStance stance) {
        this.tribeId = Objects.requireNonNull(tribeId, "tribeId");
        this.stance = Objects.requireNonNull(stance, "stance");
        requireScore(score);
        if (stance == DiplomaticStance.ALLIANCE && score < 70) {
            throw new IllegalArgumentException("Alliance requires an Allied relation score");
        }
        this.score = score;
        this.behavior = RelationshipStateFactory.forBand(RelationBand.fromScore(score));
    }

    public TribeId tribeId() { return tribeId; }
    public int score() { return score; }
    public RelationBand band() { return RelationBand.fromScore(score); }
    public RelationshipBehaviorState behavior() { return behavior; }
    public DiplomaticStance stance() { return stance; }

    public void adjustScore(int delta) {
        score = (int) Math.max(-100L, Math.min(100L, (long) score + delta));
        if (stance == DiplomaticStance.ALLIANCE && score < 70) stance = DiplomaticStance.PEACE;
        if (score <= -50) stance = DiplomaticStance.WAR;
        refreshBehavior();
    }

    public void declareWar() { stance = DiplomaticStance.WAR; score = -100; refreshBehavior(); }

    public void establishPeace(int resultingScore) {
        requireScore(resultingScore);
        stance = DiplomaticStance.PEACE;
        score = resultingScore;
        refreshBehavior();
    }

    public void formAlliance() {
        if (stance == DiplomaticStance.WAR || score < 70) {
            throw new IllegalStateException("Alliance requires peace and an Allied relation score");
        }
        stance = DiplomaticStance.ALLIANCE;
        refreshBehavior();
    }

    private void refreshBehavior() {
        behavior = RelationshipStateFactory.forBand(RelationBand.fromScore(score));
    }

    /** Rebuilds the derived behavior object after persistence restores authoritative score/stance. */
    public void rebuildDerivedBehavior() { refreshBehavior(); }

    private static void requireScore(int score) {
        if (score < -100 || score > 100) throw new IllegalArgumentException("Relation score out of range");
    }
}
