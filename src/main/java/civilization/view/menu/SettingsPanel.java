package civilization.view.menu;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import java.awt.BorderLayout;
import java.awt.Color;

public final class SettingsPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final JScrollBar volume = new JScrollBar(JScrollBar.HORIZONTAL, 55, 0, 0, 100);
    private final JButton back = new JButton("Back");

    public SettingsPanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(160, 200, 160, 200));
        setBackground(new Color(24, 34, 48));
        JPanel controls = new JPanel();
        controls.setLayout(new javax.swing.BoxLayout(controls, javax.swing.BoxLayout.Y_AXIS));
        JLabel label = new JLabel("Background music volume", JLabel.CENTER);
        label.setForeground(new Color(245, 198, 82));
        label.setAlignmentX(CENTER_ALIGNMENT);
        volume.setAlignmentX(CENTER_ALIGNMENT);
        volume.setMaximumSize(new java.awt.Dimension(500, 28));
        back.setAlignmentX(CENTER_ALIGNMENT);
        back.setMaximumSize(new java.awt.Dimension(500, 34));
        controls.add(label);
        controls.add(javax.swing.Box.createVerticalStrut(14));
        controls.add(volume);
        controls.add(javax.swing.Box.createVerticalStrut(24));
        controls.add(back);
        controls.setOpaque(false);
        volume.setToolTipText("Continuous soundtrack volume: 0 to 100 percent");
        back.setToolTipText("Return to the main menu");
        add(controls, BorderLayout.CENTER);
    }

    public JScrollBar volumeControl() {
        return volume;
    }

    public JButton backButton() {
        return back;
    }
}
