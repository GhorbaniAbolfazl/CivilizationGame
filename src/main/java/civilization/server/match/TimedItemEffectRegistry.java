package civilization.server.match;

import civilization.server.session.PlayerId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// PHASE 3 - Added in Phase 3
public final class TimedItemEffectRegistry {
    // PHASE 3 - Added in Phase 3
    private final Map<PlayerId, LinkedHashMap<String,Integer>> remainingTurns = new LinkedHashMap<>();
    // PHASE 3 - Added in Phase 3
    public void register(PlayerId playerId, String effectId, int turns) {
        Objects.requireNonNull(playerId); if (effectId == null || effectId.isBlank() || turns <= 0) throw new IllegalArgumentException("valid timed item effect is required");
        remainingTurns.computeIfAbsent(playerId, ignored -> new LinkedHashMap<>()).put(effectId, turns);
    }
    // PHASE 3 - Added in Phase 3
    public List<String> advanceAndExpire(PlayerId playerId) {
        LinkedHashMap<String,Integer> effects = remainingTurns.get(playerId); if (effects == null) return List.of();
        ArrayList<String> expired = new ArrayList<>();
        // PHASE 3 - Added in Phase 3
        effects.replaceAll((id, turns) -> turns - 1); effects.forEach((id, turns) -> { if (turns == 0) expired.add(id); }); expired.forEach(effects::remove);
        if (effects.isEmpty()) remainingTurns.remove(playerId); return List.copyOf(expired);
    }
    // PHASE 3 - Added in Phase 3
    public int remainingTurns(PlayerId playerId, String effectId) { return remainingTurns.getOrDefault(playerId, new LinkedHashMap<>()).getOrDefault(effectId, 0); }
}
