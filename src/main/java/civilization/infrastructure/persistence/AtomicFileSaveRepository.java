package civilization.infrastructure.persistence;

import civilization.application.persistence.IncompatibleSaveException;
import civilization.application.persistence.SaveEntry;
import civilization.application.persistence.SaveException;
import civilization.application.persistence.SaveGameCodec;
import civilization.application.persistence.SaveMetadata;
import civilization.application.persistence.SaveSlot;
import civilization.application.port.SaveGameRepository;
import civilization.model.game.GameState;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Same-directory temporary write, fsync, and atomic replacement for each independent slot. */
public final class AtomicFileSaveRepository implements SaveGameRepository {
    private final Path directory;
    private final SaveGameCodec codec;
    private final WriteFaultInjector faultInjector;

    public AtomicFileSaveRepository(Path directory) {
        this(directory, new SaveGameCodec(), WriteFaultInjector.none());
    }

    public AtomicFileSaveRepository(Path directory, SaveGameCodec codec,
            WriteFaultInjector faultInjector) {
        this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        this.codec = Objects.requireNonNull(codec, "codec");
        this.faultInjector = Objects.requireNonNull(faultInjector, "faultInjector");
    }

    @Override
    public void write(SaveSlot slot, SaveMetadata metadata, GameState state) {
        Path target = slotPath(slot);
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            byte[] bytes = codec.encode(metadata, state);
            temporary = Files.createTempFile(directory, target.getFileName().toString() + ".", ".tmp");
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            faultInjector.beforeReplace(slot, temporary, target);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            forceDirectoryBestEffort();
        } catch (IOException failure) {
            throw new SaveException("Could not atomically write " + slot, failure);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            }
        }
    }

    @Override
    public LoadedGame read(SaveSlot slot) {
        Path path = slotPath(slot);
        if (!Files.isRegularFile(path)) throw new SaveException("Save slot is empty: " + slot);
        try {
            var decoded = codec.decode(Files.readAllBytes(path));
            return new LoadedGame(decoded.metadata(), decoded.state());
        } catch (IOException failure) {
            throw new SaveException("Could not read " + slot, failure);
        }
    }

    @Override
    public Optional<SaveMetadata> metadata(SaveSlot slot) {
        Path path = slotPath(slot);
        if (!Files.isRegularFile(path)) return Optional.empty();
        try { return Optional.of(codec.decodeMetadata(Files.readAllBytes(path)).metadata()); }
        catch (IOException failure) { throw new SaveException("Could not read " + slot, failure); }
    }

    @Override
    public List<SaveEntry> list() {
        List<SaveEntry> entries = new ArrayList<>();
        for (SaveSlot slot : SaveSlot.values()) {
            if (!exists(slot)) { entries.add(SaveEntry.empty(slot)); continue; }
            try {
                entries.add(new SaveEntry(slot, SaveEntry.Status.VALID, metadata(slot), Optional.empty()));
            } catch (IncompatibleSaveException incompatible) {
                entries.add(new SaveEntry(slot, SaveEntry.Status.INCOMPATIBLE,
                        Optional.empty(), Optional.of(incompatible.getMessage())));
            } catch (SaveException corrupted) {
                entries.add(new SaveEntry(slot, SaveEntry.Status.CORRUPTED,
                        Optional.empty(), Optional.of(corrupted.getMessage())));
            }
        }
        return entries.stream().sorted(Comparator
                .comparing((SaveEntry entry) -> entry.metadata()
                        .map(SaveMetadata::savedAt).orElse(java.time.Instant.MIN)).reversed()
                .thenComparing(entry -> entry.slot().ordinal())).toList();
    }

    @Override public boolean exists(SaveSlot slot) { return Files.isRegularFile(slotPath(slot)); }

    @Override
    public void delete(SaveSlot slot) {
        try { Files.deleteIfExists(slotPath(slot)); }
        catch (IOException failure) { throw new SaveException("Could not delete " + slot, failure); }
    }

    public Path slotPath(SaveSlot slot) {
        Objects.requireNonNull(slot, "slot");
        return directory.resolve(slot.name().toLowerCase(java.util.Locale.ROOT) + ".civsave");
    }

    private void forceDirectoryBestEffort() {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | UnsupportedOperationException ignored) { }
    }

    @FunctionalInterface
    public interface WriteFaultInjector {
        void beforeReplace(SaveSlot slot, Path temporary, Path target) throws IOException;
        static WriteFaultInjector none() { return (slot, temporary, target) -> { }; }
    }
}
