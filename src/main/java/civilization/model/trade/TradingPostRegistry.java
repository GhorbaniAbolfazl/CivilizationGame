package civilization.model.trade;

import civilization.model.map.GameMap;
import civilization.model.map.TerrainType;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class TradingPostRegistry implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<TradingPostId, TradingPost> posts = new LinkedHashMap<>();

    public void add(TradingPost post, GameMap map) {
        Objects.requireNonNull(post, "post");
        var tile = Objects.requireNonNull(map, "map").requireTile(post.position());
        if (tile.terrain() == TerrainType.SEA || tile.terrain() == TerrainType.MOUNTAIN_RANGE) {
            throw new IllegalArgumentException("Trading Post requires a traversable land hex");
        }
        if (posts.values().stream().anyMatch(existing -> existing.position().equals(post.position()))) {
            throw new IllegalStateException("A Trading Post already occupies " + post.position());
        }
        if (posts.putIfAbsent(post.id(), post) != null) {
            throw new IllegalStateException("Duplicate Trading Post ID: " + post.id());
        }
    }

    public TradingPost require(TradingPostId id) {
        TradingPost result = posts.get(Objects.requireNonNull(id, "id"));
        if (result == null) throw new IllegalArgumentException("Unknown Trading Post: " + id);
        return result;
    }

    public Collection<TradingPost> all() {
        return Collections.unmodifiableCollection(posts.values());
    }
}
