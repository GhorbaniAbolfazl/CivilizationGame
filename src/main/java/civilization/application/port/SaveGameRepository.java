package civilization.application.port;

import civilization.application.persistence.SaveEntry;
import civilization.application.persistence.SaveMetadata;
import civilization.application.persistence.SaveSlot;
import civilization.model.game.GameState;
import java.util.List;
import java.util.Optional;

/** Model-only slot storage; adapters own files and atomic replacement details. */
public interface SaveGameRepository {
    void write(SaveSlot slot, SaveMetadata metadata, GameState state);
    LoadedGame read(SaveSlot slot);
    Optional<SaveMetadata> metadata(SaveSlot slot);
    List<SaveEntry> list();
    boolean exists(SaveSlot slot);
    void delete(SaveSlot slot);

    record LoadedGame(SaveMetadata metadata, GameState state) { }
}
