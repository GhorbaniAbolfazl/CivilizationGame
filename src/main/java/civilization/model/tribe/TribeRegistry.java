package civilization.model.tribe;

import civilization.model.identity.FactionId;
import civilization.model.identity.TribeId;
import civilization.model.map.HexCoord;
import civilization.model.world.WorldStructureId;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class TribeRegistry implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<TribeId, Tribe> tribes = new LinkedHashMap<>();

    public void add(Tribe tribe) {
        Objects.requireNonNull(tribe, "tribe");
        if (tribes.putIfAbsent(tribe.id(), tribe) != null) {
            throw new IllegalStateException("Duplicate tribe: " + tribe.id());
        }
    }

    public Tribe require(TribeId id) {
        Tribe tribe = tribes.get(Objects.requireNonNull(id, "id"));
        if (tribe == null) throw new IllegalArgumentException("Unknown tribe: " + id);
        return tribe;
    }

    public Optional<Tribe> find(TribeId id) {
        return Optional.ofNullable(tribes.get(Objects.requireNonNull(id, "id")));
    }

    public Optional<Tribe> byFaction(FactionId factionId) {
        return tribes.values().stream().filter(tribe -> tribe.id().asFactionId().equals(factionId)).findFirst();
    }

    public Optional<Tribe> byCampStructure(WorldStructureId structureId) {
        return tribes.values().stream()
                .filter(tribe -> tribe.camp().structure().id().equals(structureId)).findFirst();
    }

    public Optional<Tribe> atCamp(HexCoord coordinate) {
        return tribes.values().stream().filter(tribe -> tribe.camp().position().equals(coordinate)).findFirst();
    }

    public Collection<Tribe> all() { return Collections.unmodifiableCollection(tribes.values()); }
}
