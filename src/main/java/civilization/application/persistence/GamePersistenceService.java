package civilization.application.persistence;

import civilization.application.port.SaveGameRepository;
import civilization.model.game.GameState;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Save/load use cases. Loading returns a validated candidate and never mutates the caller. */
public final class GamePersistenceService {
    private final SaveGameRepository repository;
    private final SaveValidator validator;
    private final DerivedStateRebuilder derivedStateRebuilder;
    private final Clock clock;

    public GamePersistenceService(SaveGameRepository repository) {
        this(repository, new SaveValidator(), new DerivedStateRebuilder(), Clock.systemUTC());
    }

    public GamePersistenceService(SaveGameRepository repository, SaveValidator validator,
            DerivedStateRebuilder derivedStateRebuilder, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.derivedStateRebuilder = Objects.requireNonNull(derivedStateRebuilder, "derivedStateRebuilder");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public SaveResult saveManual(SaveSlot slot, String name, GameIdentity identity,
            GameState state, OverwriteConfirmation confirmation) {
        if (!Objects.requireNonNull(slot, "slot").isManual()) {
            throw new IllegalArgumentException("Manual save requires a manual slot");
        }
        Objects.requireNonNull(confirmation, "confirmation");
        validator.validateForSave(state);
        SaveMetadata replacement = metadata(name, identity, state);
        var existing = repository.metadata(slot);
        if (existing.isPresent() && !confirmation.confirm(slot, existing.orElseThrow(), replacement)) {
            return SaveResult.declined();
        }
        repository.write(slot, replacement, state);
        return SaveResult.saved(replacement);
    }

    /** Autosave errors are data, not gameplay failures; the previous file remains intact. */
    public SaveResult autosave(GameIdentity identity, GameState state) {
        try {
            validator.validateForSave(state);
            SaveMetadata metadata = metadata("Autosave", identity, state);
            repository.write(SaveSlot.AUTOSAVE, metadata, state);
            return SaveResult.saved(metadata);
        } catch (SaveException failure) {
            return SaveResult.failed(failure.getMessage());
        }
    }

    public LoadedCandidate load(SaveSlot slot) {
        SaveGameRepository.LoadedGame loaded = repository.read(slot);
        GameState candidate = loaded.state();
        derivedStateRebuilder.rebuild(candidate);
        validator.validateLoaded(candidate, loaded.metadata());
        return new LoadedCandidate(new GameIdentity(loaded.metadata().gameId(),
                loaded.metadata().gameStartedAt()), loaded.metadata(), candidate);
    }

    public java.util.List<SaveEntry> listSlots() { return repository.list(); }

    /** Explicit user-requested removal, including corrupt and incompatible slots. */
    public void delete(SaveSlot slot) {
        repository.delete(Objects.requireNonNull(slot, "slot"));
    }

    private SaveMetadata metadata(String name, GameIdentity identity, GameState state) {
        Objects.requireNonNull(identity, "identity");
        Instant savedAt = clock.instant();
        String summary = "Turn %d, %s, Hall %s, %d units, %d buildings".formatted(
                state.turn().turnNumber(), state.season().season(),
                state.player().townHall().level(), state.player().units().size(),
                state.player().allBuildings().size());
        return new SaveMetadata(name, identity.gameId(), identity.startedAt(), savedAt,
                state.turn().turnNumber(), state.season().season(),
                state.season().turnWithinSeason(), state.season().weather(),
                state.player().townHall().level(), summary);
    }

    public record LoadedCandidate(GameIdentity identity, SaveMetadata metadata, GameState state) { }
}
