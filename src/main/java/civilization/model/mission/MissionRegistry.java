package civilization.model.mission;

import civilization.model.identity.TribeId;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class MissionRegistry implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<MissionId, Mission> missions = new LinkedHashMap<>();

    public void add(Mission mission) {
        Objects.requireNonNull(mission, "mission");
        if (missions.containsKey(mission.id())) throw new IllegalStateException("Duplicate mission: " + mission.id());
        if (mission.status().isInProgress() && activeForTribe(mission.tribeId()).isPresent()) {
            throw new IllegalStateException("A tribe already has an active mission");
        }
        missions.put(mission.id(), mission);
    }

    public Mission require(MissionId id) {
        Mission result = missions.get(Objects.requireNonNull(id, "id"));
        if (result == null) throw new IllegalArgumentException("Unknown mission: " + id);
        return result;
    }

    public void accept(MissionId id) {
        Mission mission = require(id);
        if (activeForTribe(mission.tribeId()).isPresent()) {
            throw new IllegalStateException("A tribe already has an active mission");
        }
        mission.accept();
    }

    public void accept(MissionId id, int turn) {
        Mission mission = require(id);
        if (activeForTribe(mission.tribeId()).isPresent()) {
            throw new IllegalStateException("A tribe already has an active mission");
        }
        mission.accept(turn);
    }

    public Optional<Mission> activeForTribe(TribeId tribeId) {
        return missions.values().stream()
                .filter(mission -> mission.tribeId().equals(tribeId)
                        && mission.status().isInProgress())
                .findFirst();
    }

    public Optional<Mission> unresolvedForTribe(TribeId tribeId) {
        return missions.values().stream()
                .filter(mission -> mission.tribeId().equals(tribeId))
                .filter(mission -> mission.status() == MissionStatus.RECEIVABLE
                        || mission.status().isInProgress())
                .findFirst();
    }

    public boolean failedForTribeSince(TribeId tribeId, int earliestTurn) {
        return missions.values().stream()
                .filter(mission -> mission.tribeId().equals(tribeId))
                .filter(mission -> mission.status() == MissionStatus.FAILED)
                .anyMatch(mission -> mission.resolvedTurn().orElse(Integer.MIN_VALUE) >= earliestTurn);
    }

    public Collection<Mission> all() { return Collections.unmodifiableCollection(missions.values()); }
}
