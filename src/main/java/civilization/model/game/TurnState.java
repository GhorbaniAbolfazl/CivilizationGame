package civilization.model.game;

import civilization.model.economy.TurnEconomyReport;

import java.util.Objects;

public final class TurnState implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private int turnNumber;
    private GamePhase phase;
    private TurnEconomyReport lastEconomyReport;

    public TurnState() {
        this(1, GamePhase.PLAYER_ACTIONS, TurnEconomyReport.EMPTY);
    }

    public TurnState(int turnNumber, GamePhase phase, TurnEconomyReport lastEconomyReport) {
        if (turnNumber <= 0) {
            throw new IllegalArgumentException("Turn number must be positive");
        }
        this.turnNumber = turnNumber;
        this.phase = Objects.requireNonNull(phase, "phase");
        this.lastEconomyReport = Objects.requireNonNull(lastEconomyReport, "lastEconomyReport");
    }

    public int turnNumber() {
        return turnNumber;
    }

    public GamePhase phase() {
        return phase;
    }

    public TurnEconomyReport lastEconomyReport() {
        return lastEconomyReport;
    }

    /** Called by TurnProcessor immediately before the new turn's AP refresh. */
    public void beginNextTurnResolution() {
        if (phase != GamePhase.PLAYER_ACTIONS) {
            throw new IllegalStateException("Turn is already being resolved");
        }
        turnNumber = Math.incrementExact(turnNumber);
        phase = GamePhase.RESOLVING_TURN;
    }

    /** Called only by TurnProcessor after all mandatory phases finish. */
    public void finishResolution(TurnEconomyReport economyReport) {
        if (phase != GamePhase.RESOLVING_TURN) {
            throw new IllegalStateException("Cannot finish a turn that is not resolving");
        }
        lastEconomyReport = Objects.requireNonNull(economyReport, "economyReport");
        phase = GamePhase.PLAYER_ACTIONS;
    }

    /** Irreversibly stops turn processing after a terminal model outcome. */
    public void finishGame() {
        phase = GamePhase.GAME_OVER;
    }
}
