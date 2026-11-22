package civilization.view.menu;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Font;
import java.awt.Color;

public final class MainMenuPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final JButton start = new JButton("Start");
    // PHASE 3 - Added in Phase 3
    private final JButton multiplayer = new JButton("Multiplayer");
    private final JButton load = new JButton("Load Game");
    private final JButton settings = new JButton("Settings");
    private final JButton exit = new JButton("Exit");

    public MainMenuPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(120, 280, 120, 280));
        setBackground(new Color(24, 34, 48));
        JLabel title = new JLabel("Civilization");
        title.setForeground(new Color(245, 198, 82));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 36f));
        title.setAlignmentX(CENTER_ALIGNMENT);
        add(title);
        add(Box.createVerticalStrut(45));
        for (JButton button : new JButton[]{start, multiplayer, load, settings, exit}) {
            button.setAlignmentX(CENTER_ALIGNMENT);
            button.setMaximumSize(new java.awt.Dimension(260, 46));
            button.setToolTipText(switch (button.getText()) {
                case "Start" -> "Generate a new procedural world and begin playing";
                // PHASE 3 - Added in Phase 3
                case "Multiplayer" -> "Connect to an authoritative Phase 3 server";
                case "Load Game" -> "Open manual and autosave slots without generating a map";
                case "Settings" -> "Adjust continuous background music volume";
                default -> "Exit after confirmation";
            });
            add(button);
            add(Box.createVerticalStrut(14));
        }
    }

    public JButton startButton() {
        return start;
    }

    public JButton settingsButton() {
        return settings;
    }
    // PHASE 3 - Added in Phase 3
    public JButton multiplayerButton() { return multiplayer; }
    public JButton loadButton() { return load; }

    public JButton exitButton() {
        return exit;
    }
}
