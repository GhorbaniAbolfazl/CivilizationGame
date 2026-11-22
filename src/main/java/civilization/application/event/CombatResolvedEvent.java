package civilization.application.event;

import civilization.model.combat.CombatResult;
import java.util.Objects;

public record CombatResolvedEvent(CombatResult result) implements GameEvent {
    public CombatResolvedEvent { Objects.requireNonNull(result, "result"); }
}
