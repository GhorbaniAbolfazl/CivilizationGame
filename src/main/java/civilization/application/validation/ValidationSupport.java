package civilization.application.validation;

import civilization.application.ActionFailure;
import civilization.application.action.ActionPreview;
import civilization.model.game.GamePhase;
import civilization.model.game.GameState;

import java.util.Objects;

public final class ValidationSupport {
    private ValidationSupport() {
    }

    public static ActionPreview playerActionPhase(GameState state) {
        Objects.requireNonNull(state, "state");
        return state.turn().phase() == GamePhase.PLAYER_ACTIONS
                ? ActionPreview.allow()
                : ActionPreview.reject("Actions are allowed only during the player action phase");
    }

    public static void requireAllowed(ActionPreview preview) {
        Objects.requireNonNull(preview, "preview");
        if (!preview.allowed()) {
            throw new ActionFailure(preview.reason());
        }
    }
}
