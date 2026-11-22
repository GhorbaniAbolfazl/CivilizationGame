package civilization.application.service;

import civilization.application.event.DisasterViewEvent;
import civilization.model.game.GameState;
import java.util.Optional;
import java.util.Set;

/** Converts an internal result to the only disaster data the current viewer may receive. */
public final class DisasterNotificationProjector {
    public DisasterViewEvent project(GameState state, DisasterResult result) {
        boolean visible = state.player().visibility()
                .isCurrentlyVisible(result.occurrence().center());
        String message = visible
                ? readable(result.occurrence().type()) + " struck a visible part of the map."
                : "A natural disaster occurred in an unexplored or fog-covered region.";
        return new DisasterViewEvent(
                visible ? Optional.of(result.occurrence().type()) : Optional.empty(),
                visible ? result.occurrence().affectedHexes() : Set.of(), visible, message);
    }

    private static String readable(civilization.model.disaster.DisasterType type) {
        String value = type.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
