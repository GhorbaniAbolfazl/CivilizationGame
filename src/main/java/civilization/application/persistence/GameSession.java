package civilization.application.persistence;

import civilization.model.game.GameState;
import java.util.Objects;

/** Transactional owner of the replaceable model root. */
public final class GameSession {
    private final GamePersistenceService persistence;
    private GameIdentity identity;
    private GameState state;

    public GameSession(GameState state, GamePersistenceService persistence) {
        this(state, GameIdentity.newGame(), persistence);
    }

    public GameSession(GameState state, GameIdentity identity, GamePersistenceService persistence) {
        this.state = Objects.requireNonNull(state, "state");
        this.identity = Objects.requireNonNull(identity, "identity");
        this.persistence = Objects.requireNonNull(persistence, "persistence");
    }

    public synchronized GameState state() { return state; }
    public synchronized GameIdentity identity() { return identity; }

    public synchronized SaveResult saveManual(SaveSlot slot, String name,
            OverwriteConfirmation confirmation) {
        return persistence.saveManual(slot, name, identity, state, confirmation);
    }

    public synchronized SaveResult autosave() { return persistence.autosave(identity, state); }

    /** Candidate creation and validation finish before either active-session field changes. */
    public synchronized SaveMetadata load(SaveSlot slot) {
        var candidate = persistence.load(slot);
        state = candidate.state();
        identity = candidate.identity();
        return candidate.metadata();
    }
}
