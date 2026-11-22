package civilization.server.item;

import civilization.model.item.ItemType;
import civilization.model.player.PlayerState;
import civilization.model.unit.UnitId;
import civilization.server.session.PlayerId;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// PHASE 3 - Added in Phase 3
public final class PlayerItemEffectRegistry {
    // PHASE 3 - Added in Phase 3
    private final Map<PlayerId, Set<UnitId>> usedThisTurn = new LinkedHashMap<>();
    // PHASE 3 - Added in Phase 3
    private final Map<PlayerId, Set<UnitId>> combatBuffs = new LinkedHashMap<>();

    // PHASE 3 - Added in Phase 3
    public boolean hasUsedItem(PlayerId playerId, UnitId unitId) {
        return usedThisTurn.getOrDefault(Objects.requireNonNull(playerId), Set.of())
                .contains(Objects.requireNonNull(unitId));
    }

    // PHASE 3 - Added in Phase 3
    public void markUsed(PlayerId playerId, UnitId unitId, ItemType type) {
        if (hasUsedItem(playerId, unitId)) {
            throw new ItemException("ITEM_ALREADY_USED_ON_UNIT",
                    "Only one item may affect a unit during its turn");
        }
        usedThisTurn.computeIfAbsent(playerId, ignored -> new HashSet<>()).add(unitId);
        // PHASE 3 - Added in Phase 3
        if (type == ItemType.COMBAT) {
            combatBuffs.computeIfAbsent(playerId, ignored -> new HashSet<>()).add(unitId);
        }
    }

    // PHASE 3 - Added in Phase 3
    public boolean hasCombatBuff(PlayerId playerId, UnitId unitId) {
        return combatBuffs.getOrDefault(Objects.requireNonNull(playerId), Set.of())
                .contains(Objects.requireNonNull(unitId));
    }

    // PHASE 3 - Added in Phase 3
    public List<String> expireTurn(PlayerId playerId, PlayerState empire) {
        Set<UnitId> used = usedThisTurn.remove(Objects.requireNonNull(playerId));
        Set<UnitId> combat = combatBuffs.remove(playerId);
        // PHASE 3 - Added in Phase 3
        empire.units().forEach(unit -> unit.actionPoints().clearTemporaryBonus());
        if ((used == null || used.isEmpty()) && (combat == null || combat.isEmpty())) {
            return List.of();
        }
        return List.of("PLAYER_ITEM_EFFECTS");
    }
}
