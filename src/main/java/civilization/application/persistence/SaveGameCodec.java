package civilization.application.persistence;

import civilization.model.building.HallLevel;
import civilization.model.game.GameState;
import civilization.model.time.Season;
import civilization.model.time.Weather;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/** Versioned, checksummed model-only format. Presentation classes are rejected on read. */
public final class SaveGameCodec {
    public static final int CURRENT_VERSION = 2;
    public static final int OLDEST_SUPPORTED_VERSION = 1;
    private static final int MAGIC = 0x43565347; // CVSG
    private static final int CHECKSUM_BYTES = 32;
    private static final int MAX_PAYLOAD_BYTES = 256 * 1024 * 1024;

    public byte[] encode(SaveMetadata metadata, GameState state) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(state, "state");
        try {
            byte[] payload = serialize(state);
            ByteArrayOutputStream bodyBytes = new ByteArrayOutputStream();
            try (DataOutputStream body = new DataOutputStream(bodyBytes)) {
                body.writeInt(MAGIC);
                body.writeInt(CURRENT_VERSION);
                writeMetadata(body, metadata);
                body.writeInt(payload.length);
                body.write(payload);
            }
            byte[] body = bodyBytes.toByteArray();
            byte[] checksum = sha256(body);
            ByteArrayOutputStream result = new ByteArrayOutputStream(body.length + CHECKSUM_BYTES);
            result.write(body);
            result.write(checksum);
            return result.toByteArray();
        } catch (IOException failure) {
            throw new SaveException("Could not encode save", failure);
        }
    }

    public DecodedSave decode(byte[] fileBytes) {
        return decode(fileBytes, true);
    }

    public DecodedSave decodeMetadata(byte[] fileBytes) {
        return decode(fileBytes, false);
    }

    private DecodedSave decode(byte[] fileBytes, boolean includeState) {
        Objects.requireNonNull(fileBytes, "fileBytes");
        if (fileBytes.length <= CHECKSUM_BYTES) throw new CorruptedSaveException("Save is truncated");
        byte[] body = Arrays.copyOf(fileBytes, fileBytes.length - CHECKSUM_BYTES);
        byte[] storedChecksum = Arrays.copyOfRange(fileBytes,
                fileBytes.length - CHECKSUM_BYTES, fileBytes.length);
        if (!MessageDigest.isEqual(storedChecksum, sha256(body))) {
            throw new CorruptedSaveException("Save checksum does not match");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(body))) {
            if (input.readInt() != MAGIC) throw new CorruptedSaveException("Unknown save signature");
            int version = input.readInt();
            if (version < OLDEST_SUPPORTED_VERSION || version > CURRENT_VERSION) {
                throw new IncompatibleSaveException("Save version " + version
                        + " is outside supported range " + OLDEST_SUPPORTED_VERSION
                        + ".." + CURRENT_VERSION);
            }
            SaveMetadata metadata = readMetadata(input);
            int payloadLength = input.readInt();
            if (payloadLength <= 0 || payloadLength > MAX_PAYLOAD_BYTES
                    || payloadLength != input.available()) {
                throw new CorruptedSaveException("Invalid save payload length");
            }
            if (!includeState) return new DecodedSave(version, metadata, null);
            byte[] payload = input.readNBytes(payloadLength);
            GameState state = migrate(version, deserialize(payload));
            return new DecodedSave(CURRENT_VERSION, metadata, state);
        } catch (IncompatibleSaveException | CorruptedSaveException known) {
            throw known;
        } catch (IOException | ClassNotFoundException failure) {
            throw new CorruptedSaveException("Save payload cannot be decoded", failure);
        }
    }

    /**
     * Version 1 used the same model-object payload contract. New optional serialized fields use
     * model defaults, so migration is intentionally side-effect free: no RNG draw, map generation,
     * turn processing, production, movement, or tribe behavior is permitted here.
     */
    private static GameState migrate(int sourceVersion, GameState state) {
        if (sourceVersion == 1 || sourceVersion == CURRENT_VERSION) return state;
        throw new IncompatibleSaveException("No migration path from save version " + sourceVersion);
    }

    private static byte[] serialize(GameState state) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(state);
        }
        return bytes.toByteArray();
    }

    private static GameState deserialize(byte[] payload) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(payload))) {
            input.setObjectInputFilter(SaveGameCodec::filter);
            Object value = input.readObject();
            if (!(value instanceof GameState state) || input.read() != -1) {
                throw new CorruptedSaveException("Save does not contain exactly one game state");
            }
            return state;
        }
    }

    private static ObjectInputFilter.Status filter(ObjectInputFilter.FilterInfo info) {
        if (info.depth() > 128 || info.references() > 1_000_000
                || info.arrayLength() > 1_000_000) return ObjectInputFilter.Status.REJECTED;
        Class<?> type = info.serialClass();
        if (type == null || type.isPrimitive()) return ObjectInputFilter.Status.UNDECIDED;
        while (type.isArray()) type = type.getComponentType();
        if (type.isPrimitive()) return ObjectInputFilter.Status.ALLOWED;
        String name = type.getName();
        // PHASE 3 - Added in Phase 3
        return name.startsWith("civilization.model.") || name.startsWith("java.util.")
                || name.startsWith("java.lang.") || name.startsWith("java.time.")
                ? ObjectInputFilter.Status.ALLOWED : ObjectInputFilter.Status.REJECTED;
    }

    private static void writeMetadata(DataOutputStream output, SaveMetadata metadata) throws IOException {
        output.writeUTF(metadata.saveName());
        output.writeLong(metadata.gameId().getMostSignificantBits());
        output.writeLong(metadata.gameId().getLeastSignificantBits());
        output.writeLong(metadata.gameStartedAt().toEpochMilli());
        output.writeLong(metadata.savedAt().toEpochMilli());
        output.writeInt(metadata.turnNumber());
        output.writeUTF(metadata.season().name());
        output.writeInt(metadata.turnWithinSeason());
        output.writeUTF(metadata.weather().name());
        output.writeUTF(metadata.hallLevel().name());
        output.writeUTF(metadata.summary());
    }

    private static SaveMetadata readMetadata(DataInputStream input) throws IOException {
        try {
            String name = input.readUTF();
            UUID gameId = new UUID(input.readLong(), input.readLong());
            Instant startedAt = Instant.ofEpochMilli(input.readLong());
            Instant savedAt = Instant.ofEpochMilli(input.readLong());
            int turn = input.readInt();
            Season season = Season.valueOf(input.readUTF());
            int seasonTurn = input.readInt();
            Weather weather = Weather.valueOf(input.readUTF());
            HallLevel hall = HallLevel.valueOf(input.readUTF());
            return new SaveMetadata(name, gameId, startedAt, savedAt, turn, season,
                    seasonTurn, weather, hall, input.readUTF());
        } catch (IllegalArgumentException failure) {
            throw new CorruptedSaveException("Save metadata is invalid", failure);
        }
    }

    private static byte[] sha256(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public record DecodedSave(int version, SaveMetadata metadata, GameState state) { }
}
