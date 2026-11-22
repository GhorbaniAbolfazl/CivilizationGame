package civilization.model.mission;

import civilization.model.identity.TribeId;
import java.util.Objects;

/** Mission lifecycle only; objectives, rewards and penalties are separate feature policies. */
public final class Mission implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final MissionId id;
    private final TribeId tribeId;
    private final MissionType type;
    private final int issuedTurn;
    private final int deadlineTurn;
    private MissionStatus status;
    private Integer acceptedTurn;
    private Integer resolvedTurn;

    public Mission(MissionId id, TribeId tribeId, MissionType type, int issuedTurn, int deadlineTurn) {
        this(id, tribeId, type, issuedTurn, deadlineTurn, MissionStatus.RECEIVABLE);
    }

    /** Restore constructor; persistence supplies the exact authoritative lifecycle status. */
    public Mission(MissionId id, TribeId tribeId, MissionType type,
            int issuedTurn, int deadlineTurn, MissionStatus status) {
        this.id = Objects.requireNonNull(id, "id");
        this.tribeId = Objects.requireNonNull(tribeId, "tribeId");
        this.type = Objects.requireNonNull(type, "type");
        if (issuedTurn < 1 || deadlineTurn < issuedTurn) {
            throw new IllegalArgumentException("Invalid mission turn range");
        }
        this.issuedTurn = issuedTurn;
        this.deadlineTurn = deadlineTurn;
        this.status = Objects.requireNonNull(status, "status");
    }

    public MissionId id() { return id; }
    public TribeId tribeId() { return tribeId; }
    public MissionType type() { return type; }
    public int issuedTurn() { return issuedTurn; }
    public int deadlineTurn() { return deadlineTurn; }
    public MissionStatus status() { return status; }
    public java.util.OptionalInt acceptedTurn() {
        return acceptedTurn == null ? java.util.OptionalInt.empty()
                : java.util.OptionalInt.of(acceptedTurn);
    }
    public java.util.OptionalInt resolvedTurn() {
        return resolvedTurn == null ? java.util.OptionalInt.empty()
                : java.util.OptionalInt.of(resolvedTurn);
    }

    void accept(int turn) {
        requireTurn(turn);
        if (turn > deadlineTurn) throw new IllegalStateException("Mission deadline has passed");
        transition(MissionStatus.RECEIVABLE, MissionStatus.ACTIVE);
        acceptedTurn = turn;
    }
    void accept() { accept(issuedTurn); }
    public void markReady() { transition(MissionStatus.ACTIVE, MissionStatus.READY); }
    public void complete(int turn) {
        requireTurn(turn);
        transition(MissionStatus.READY, MissionStatus.COMPLETED);
        resolvedTurn = turn;
    }
    public void complete() { complete(deadlineTurn); }

    public void fail(int turn) {
        requireTurn(turn);
        requireActiveOrReady("fail");
        status = MissionStatus.FAILED;
        resolvedTurn = turn;
    }
    public void fail() { fail(deadlineTurn); }

    public void cancel(int turn) {
        requireTurn(turn);
        if (status != MissionStatus.RECEIVABLE && status != MissionStatus.ACTIVE
                && status != MissionStatus.READY) {
            throw new IllegalStateException("Cannot cancel a mission in state " + status);
        }
        status = MissionStatus.CANCELLED;
        resolvedTurn = turn;
    }
    public void cancel() { cancel(deadlineTurn); }

    private void transition(MissionStatus expected, MissionStatus next) {
        if (status != expected) throw new IllegalStateException("Cannot transition " + status + " to " + next);
        status = next;
    }

    private void requireActiveOrReady(String action) {
        if (status != MissionStatus.ACTIVE && status != MissionStatus.READY) {
            throw new IllegalStateException("Cannot " + action + " a mission in state " + status);
        }
    }

    private static void requireTurn(int turn) {
        if (turn < 1) throw new IllegalArgumentException("Mission turn must be positive");
    }
}
