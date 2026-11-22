package civilization.model.trade;

import civilization.model.identity.TribeId;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class TribeTradeRegistry implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<TribeId, TribeTradeEndpoint> endpoints = new LinkedHashMap<>();

    public void add(TribeTradeEndpoint endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        if (endpoints.putIfAbsent(endpoint.tribeId(), endpoint) != null) {
            throw new IllegalStateException("Tribe trade endpoint already exists: " + endpoint.tribeId());
        }
    }

    public TribeTradeEndpoint require(TribeId tribeId) {
        TribeTradeEndpoint result = endpoints.get(Objects.requireNonNull(tribeId, "tribeId"));
        if (result == null) throw new IllegalArgumentException("Unknown tribe trade endpoint: " + tribeId);
        return result;
    }

    public Collection<TribeTradeEndpoint> all() {
        return Collections.unmodifiableCollection(endpoints.values());
    }
}
