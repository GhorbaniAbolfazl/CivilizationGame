package civilization.view.dialog;

import civilization.controller.port.ConfirmationPrompt;

import javax.swing.JOptionPane;
import java.awt.Component;
import java.util.Objects;

/** Swing adapter for controller confirmation requests. */
public final class SwingConfirmationPrompt implements ConfirmationPrompt {
    private final Component parent;

    public SwingConfirmationPrompt(Component parent) {
        this.parent = Objects.requireNonNull(parent, "parent");
    }

    @Override
    public boolean confirm(String title, String message) {
        int answer = JOptionPane.showConfirmDialog(
                parent,
                Objects.requireNonNull(message, "message"),
                Objects.requireNonNull(title, "title"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        return answer == JOptionPane.YES_OPTION;
    }
}
