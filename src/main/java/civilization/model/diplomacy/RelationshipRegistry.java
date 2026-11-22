package civilization.model.diplomacy;

import civilization.model.identity.TribeId;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class RelationshipRegistry implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<TribeId, RelationshipState> relationships = new LinkedHashMap<>();

    public void add(RelationshipState relationship) {
        Objects.requireNonNull(relationship, "relationship");
        if (relationships.putIfAbsent(relationship.tribeId(), relationship) != null) {
            throw new IllegalStateException("Relationship already exists for " + relationship.tribeId());
        }
    }

    public RelationshipState require(TribeId tribeId) {
        RelationshipState result = relationships.get(Objects.requireNonNull(tribeId, "tribeId"));
        if (result == null) throw new IllegalArgumentException("Unknown tribe: " + tribeId);
        return result;
    }

    public Collection<RelationshipState> all() {
        return Collections.unmodifiableCollection(relationships.values());
    }
}
