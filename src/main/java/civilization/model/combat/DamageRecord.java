package civilization.model.combat;

import java.util.Objects;

public record DamageRecord(
        String entityKind,
        String entityId,
        int damage,
        int remainingHealth,
        boolean removed) implements java.io.Serializable {
    public DamageRecord {
        entityKind = Objects.requireNonNull(entityKind, "entityKind");
        entityId = Objects.requireNonNull(entityId, "entityId");
        if (damage < 0 || remainingHealth < 0) {
            throw new IllegalArgumentException("Damage and remaining HP cannot be negative");
        }
    }
}
