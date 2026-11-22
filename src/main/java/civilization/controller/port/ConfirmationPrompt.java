package civilization.controller.port;

/** Presentation port for irreversible or potentially surprising user actions. */
@FunctionalInterface
public interface ConfirmationPrompt {
    boolean confirm(String title, String message);
}
