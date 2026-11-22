package civilization.model.trade;

import java.util.Objects;
import java.util.UUID;

public record TradingPostId(String value) implements Comparable<TradingPostId>, java.io.Serializable {
    public TradingPostId {
        value = Objects.requireNonNull(value, "value").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Trading Post ID cannot be blank");
    }

    public static TradingPostId random() { return new TradingPostId(UUID.randomUUID().toString()); }

    @Override public int compareTo(TradingPostId other) { return value.compareTo(other.value); }
}
