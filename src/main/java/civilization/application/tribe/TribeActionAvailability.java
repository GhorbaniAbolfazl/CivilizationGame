package civilization.application.tribe;

import civilization.application.action.ActionPreview;
import civilization.model.identity.TribeId;
import java.util.Objects;

/** Controller-ready state used to disable invalid UI actions with an exact reason. */
public record TribeActionAvailability(TribeId tribeId, ActionPreview gift,
        ActionPreview trade, ActionPreview mission, ActionPreview war,
        ActionPreview peace, ActionPreview alliance) {
    public TribeActionAvailability {
        Objects.requireNonNull(tribeId, "tribeId");
        Objects.requireNonNull(gift, "gift");
        Objects.requireNonNull(trade, "trade");
        Objects.requireNonNull(mission, "mission");
        Objects.requireNonNull(war, "war");
        Objects.requireNonNull(peace, "peace");
        Objects.requireNonNull(alliance, "alliance");
    }
}
