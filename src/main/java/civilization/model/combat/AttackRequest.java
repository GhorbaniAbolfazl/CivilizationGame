package civilization.model.combat;

import civilization.model.map.HexCoord;
import java.util.Objects;

public record AttackRequest(HexCoord attackingHex, HexCoord defendingHex, AttackTarget target) implements java.io.Serializable {
    public AttackRequest {
        Objects.requireNonNull(attackingHex, "attackingHex");
        Objects.requireNonNull(defendingHex, "defendingHex");
        Objects.requireNonNull(target, "target");
    }

    public AttackRequest(HexCoord attackingHex, HexCoord defendingHex) {
        this(attackingHex, defendingHex, AttackTarget.AUTO);
    }
}
