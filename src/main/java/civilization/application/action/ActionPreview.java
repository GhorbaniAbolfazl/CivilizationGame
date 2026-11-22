package civilization.application.action;

import java.util.Objects;

public record ActionPreview(boolean allowed, String reason) {
    public ActionPreview {
        reason = Objects.requireNonNull(reason, "reason");
        if (allowed && !reason.isBlank()) {
            throw new IllegalArgumentException("Allowed actions cannot have a rejection reason");
        }
        if (!allowed && reason.isBlank()) {
            throw new IllegalArgumentException("Rejected actions need a reason");
        }
    }

    public static ActionPreview allow() {
        return new ActionPreview(true, "");
    }

    public static ActionPreview reject(String reason) {
        return new ActionPreview(false, reason);
    }
}
