package civilization.protocol;

import java.util.List;
import java.util.Map;
import java.util.Objects;

// PHASE 3 - Added in Phase 3
final class ProtocolValidation {
    // PHASE 3 - Added in Phase 3
    static final int MAX_ID_LENGTH = 128;
    // PHASE 3 - Added in Phase 3
    static final int MAX_TEXT_LENGTH = 256;
    // PHASE 3 - Added in Phase 3
    static final int MAX_CHAT_LENGTH = 1_000;
    // PHASE 3 - Added in Phase 3
    static final int MAX_COLLECTION_SIZE = 256;
    // PHASE 3 - Added in Phase 3
    static final int MAX_VISIBLE_HEXES = 4_096;

    // PHASE 3 - Added in Phase 3
    private ProtocolValidation() {
    }

    // PHASE 3 - Added in Phase 3
    static MessageMetadata metadata(MessageMetadata value) {
        return Objects.requireNonNull(value, "metadata");
    }

    // PHASE 3 - Added in Phase 3
    static String id(String value, String name) {
        return text(value, name, MAX_ID_LENGTH);
    }

    // PHASE 3 - Added in Phase 3
    static String text(String value, String name, int maximumLength) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " must contain 1-" + maximumLength
                    + " characters");
        }
        return value;
    }

    // PHASE 3 - Added in Phase 3
    static String optionalText(String value, String name, int maximumLength) {
        if (value == null) return null;
        if (value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " must not exceed " + maximumLength
                    + " characters");
        }
        return value;
    }

    // PHASE 3 - Added in Phase 3
    static long nonNegative(long value, String name) {
        if (value < 0L) throw new IllegalArgumentException(name + " must not be negative");
        return value;
    }

    // PHASE 3 - Added in Phase 3
    static int positive(int value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    // PHASE 3 - Added in Phase 3
    static <T> List<T> list(List<T> values, String name) {
        return list(values, name, MAX_COLLECTION_SIZE);
    }

    // PHASE 3 - Added in Phase 3
    static <T> List<T> list(List<T> values, String name, int maximumSize) {
        Objects.requireNonNull(values, name);
        if (maximumSize < 0) throw new IllegalArgumentException("maximumSize must not be negative");
        if (values.size() > maximumSize || values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(name + " is too large or contains null");
        }
        return List.copyOf(values);
    }

    // PHASE 3 - Added in Phase 3
    static <K, V> Map<K, V> map(Map<K, V> values, String name) {
        Objects.requireNonNull(values, name);
        if (values.size() > MAX_COLLECTION_SIZE
                || values.keySet().stream().anyMatch(Objects::isNull)
                || values.values().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(name + " is too large or contains null");
        }
        return Map.copyOf(values);
    }
}
