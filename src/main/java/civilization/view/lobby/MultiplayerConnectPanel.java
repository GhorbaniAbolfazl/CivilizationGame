package civilization.view.lobby;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

// PHASE 3 - Added in Phase 3
public final class MultiplayerConnectPanel extends JPanel {
    // PHASE 3 - Added in Phase 3
    private static final long serialVersionUID = 1L;
    // PHASE 3 - Added in Phase 3
    private final JTextField username = new JTextField("Player", 18);
    // PHASE 3 - Added in Phase 3
    private final JTextField host = new JTextField("127.0.0.1", 18);
    // PHASE 3 - Added in Phase 3
    private final JSpinner port = new JSpinner(new SpinnerNumberModel(7777, 1, 65535, 1));
    // PHASE 3 - Added in Phase 3
    private final JTextField lobbyId = new JTextField(18);
    // PHASE 3 - Added in Phase 3
    private final JButton create = new JButton("Connect & Create Lobby");
    // PHASE 3 - Added in Phase 3
    private final JButton join = new JButton("Connect & Join Lobby");
    // PHASE 3 - Added in Phase 3
    private final JButton back = new JButton("Back");
    // PHASE 3 - Added in Phase 3
    private final JLabel status = new JLabel("Enter a server address and player name.");

    // PHASE 3 - Added in Phase 3
    public MultiplayerConnectPanel() {
        super(new GridBagLayout());
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Multiplayer Connection"),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        form.add(new JLabel("Username")); form.add(username);
        form.add(new JLabel("Server host")); form.add(host);
        form.add(new JLabel("TCP port")); form.add(port);
        form.add(new JLabel("Lobby ID (join only)")); form.add(lobbyId);
        form.add(create); form.add(join);
        form.add(back); form.add(status);
        create.setToolTipText("Connect to the authoritative server and create a lobby");
        join.setToolTipText("A lobby ID is required to join an existing lobby");
        add(form);
        refreshJoinState();
        // PHASE 3 - Added in Phase 3
        lobbyId.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            // PHASE 3 - Added in Phase 3
            @Override public void insertUpdate(javax.swing.event.DocumentEvent event) { refreshJoinState(); }
            // PHASE 3 - Added in Phase 3
            @Override public void removeUpdate(javax.swing.event.DocumentEvent event) { refreshJoinState(); }
            // PHASE 3 - Added in Phase 3
            @Override public void changedUpdate(javax.swing.event.DocumentEvent event) { refreshJoinState(); }
        });
    }

    // PHASE 3 - Added in Phase 3
    public void setCreateAction(Runnable action) { replaceAction(create, action); }
    // PHASE 3 - Added in Phase 3
    public void setJoinAction(Runnable action) { replaceAction(join, action); }
    // PHASE 3 - Added in Phase 3
    public void setBackAction(Runnable action) { replaceAction(back, action); }
    // PHASE 3 - Added in Phase 3
    public String username() { return username.getText().trim(); }
    // PHASE 3 - Added in Phase 3
    public String host() { return host.getText().trim(); }
    // PHASE 3 - Added in Phase 3
    public int port() { return (Integer) port.getValue(); }
    // PHASE 3 - Added in Phase 3
    public String lobbyId() { return lobbyId.getText().trim(); }
    // PHASE 3 - Added in Phase 3
    public JButton createButton() { return create; }
    // PHASE 3 - Added in Phase 3
    public JButton joinButton() { return join; }
    // PHASE 3 - Added in Phase 3
    public JButton backButton() { return back; }
    // PHASE 3 - Added in Phase 3
    public String statusText() { return status.getText(); }

    // PHASE 3 - Added in Phase 3
    public void setBusy(boolean busy, String message) {
        requireEdt();
        username.setEnabled(!busy); host.setEnabled(!busy); port.setEnabled(!busy);
        lobbyId.setEnabled(!busy); create.setEnabled(!busy); back.setEnabled(!busy);
        status.setText(Objects.requireNonNullElse(message, " "));
        refreshJoinState();
    }

    // PHASE 3 - Added in Phase 3
    private void refreshJoinState() {
        boolean fieldsEnabled = lobbyId.isEnabled();
        boolean available = fieldsEnabled && !lobbyId().isEmpty();
        join.setEnabled(available);
        join.setToolTipText(available ? "Connect to the authoritative server and join this lobby"
                : "Enter the lobby ID supplied by the host");
    }

    // PHASE 3 - Added in Phase 3
    private static void replaceAction(JButton button, Runnable action) {
        Objects.requireNonNull(action, "action");
        for (var listener : button.getActionListeners()) button.removeActionListener(listener);
        button.addActionListener(event -> action.run());
    }

    // PHASE 3 - Added in Phase 3
    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Connection UI updates must run on EDT");
        }
    }
}
