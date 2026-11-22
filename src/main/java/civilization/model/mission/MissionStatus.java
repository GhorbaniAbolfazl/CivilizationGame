package civilization.model.mission;

public enum MissionStatus {
    RECEIVABLE, ACTIVE, READY, COMPLETED, FAILED, CANCELLED;

    public boolean isInProgress() {
        return this == ACTIVE || this == READY;
    }
}
