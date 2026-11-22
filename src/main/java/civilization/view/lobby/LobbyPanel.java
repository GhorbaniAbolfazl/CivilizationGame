package civilization.view.lobby;

import civilization.protocol.ChatMessagePush;
import civilization.protocol.LobbyMemberDto;
import civilization.protocol.LobbyStatePush;
import civilization.protocol.MapSummaryDto;
import javax.swing.*;
import java.awt.*;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

// PHASE 3 - Added in Phase 3
public final class LobbyPanel extends JPanel {
    // PHASE 3 - Added in Phase 3
    private static final long serialVersionUID = 1L;
    // PHASE 3 - Added in Phase 3
    private final DefaultListModel<String> players = new DefaultListModel<>();
    // PHASE 3 - Added in Phase 3
    private final JComboBox<MapSummaryDto> maps = new JComboBox<>();
    // PHASE 3 - Added in Phase 3
    private final JToggleButton ready = new JToggleButton("Ready");
    // PHASE 3 - Added in Phase 3
    private final JButton start = new JButton("Start Game");
    // PHASE 3 - Added in Phase 3
    private final JTextArea chat = new JTextArea();
    // PHASE 3 - Added in Phase 3
    private final JTextField chatInput = new JTextField();
    // PHASE 3 - Added in Phase 3
    private final JButton sendChat = new JButton("Send");
    // PHASE 3 - Added in Phase 3
    private final JLabel status = new JLabel("Waiting for server…");
    // PHASE 3 - Added in Phase 3
    private final JLabel connection = new JLabel("Network: connected");
    // PHASE 3 - Added in Phase 3
    private final JButton reconnect = new JButton("Reconnect");
    // PHASE 3 - Added in Phase 3
    private final JButton leave = new JButton("Leave Lobby");
    // PHASE 3 - Added in Phase 3
    private String lobbyId;
    // PHASE 3 - Added in Phase 3
    private boolean applying;
    // PHASE 3 - Added in Phase 3
    private boolean networkAvailable = true;
    // PHASE 3 - Added in Phase 3
    private LobbyStatePush lastUpdate;

    // PHASE 3 - Added in Phase 3
    public LobbyPanel(String localPlayerId, BiConsumer<String,Boolean> readyAction,
                      BiConsumer<String,String> mapAction, Consumer<String> startAction,
                      BiConsumer<String,String> chatAction) {
        setLayout(new BorderLayout(12,12)); setBorder(BorderFactory.createEmptyBorder(16,16,16,16));
        JList<String> playerList = new JList<>(players); playerList.setBorder(BorderFactory.createTitledBorder("Players")); add(new JScrollPane(playerList), BorderLayout.WEST);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT)); controls.add(new JLabel("Map:")); controls.add(maps); controls.add(ready); controls.add(start); controls.add(status); controls.add(connection); controls.add(reconnect); controls.add(leave); add(controls, BorderLayout.NORTH);
        chat.setEditable(false); chat.setLineWrap(true); JPanel chatPanel = new JPanel(new BorderLayout()); chatPanel.setBorder(BorderFactory.createTitledBorder("Public lobby chat")); chatPanel.add(new JScrollPane(chat), BorderLayout.CENTER); JPanel entry = new JPanel(new BorderLayout()); entry.add(chatInput, BorderLayout.CENTER); entry.add(sendChat, BorderLayout.EAST); chatPanel.add(entry, BorderLayout.SOUTH); add(chatPanel, BorderLayout.CENTER);
        // PHASE 3 - Added in Phase 3
        ready.addActionListener(event -> { if (!applying && lobbyId != null) readyAction.accept(lobbyId, ready.isSelected()); });
        // PHASE 3 - Added in Phase 3
        maps.addActionListener(event -> { if (!applying && lobbyId != null && maps.getSelectedItem() instanceof MapSummaryDto map) mapAction.accept(lobbyId, map.mapId()); });
        // PHASE 3 - Added in Phase 3
        start.addActionListener(event -> { if (lobbyId != null) startAction.accept(lobbyId); });
        // PHASE 3 - Added in Phase 3
        Runnable sendChat = () -> { String text = chatInput.getText().trim(); if (lobbyId != null && !text.isEmpty()) { chatAction.accept(lobbyId, text); chatInput.setText(""); } };
        this.sendChat.addActionListener(event -> sendChat.run()); chatInput.addActionListener(event -> sendChat.run());
        putClientProperty("localPlayerId", Objects.requireNonNull(localPlayerId));
        // PHASE 3 - Added in Phase 3
        reconnect.setVisible(false);
    }

    // PHASE 3 - Added in Phase 3
    public void apply(LobbyStatePush update) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Lobby UI updates must run on EDT");
        lastUpdate = Objects.requireNonNull(update, "update");
        applying = true;
        try {
            lobbyId = update.snapshot().lobbyId(); putClientProperty("lobbyId", lobbyId); players.clear(); String local = (String) getClientProperty("localPlayerId");
            // PHASE 3 - Added in Phase 3
            for (LobbyMemberDto member : update.snapshot().members()) players.addElement((member.playerId().equals(update.snapshot().hostPlayerId()) ? "★ " : "") + member.username() + (member.ready() ? " — Ready" : " — Not Ready"));
            maps.removeAllItems(); update.snapshot().availableMaps().forEach(maps::addItem);
            // PHASE 3 - Added in Phase 3
            for (int index = 0; index < maps.getItemCount(); index++) if (maps.getItemAt(index).mapId().equals(update.snapshot().selectedMapId())) maps.setSelectedIndex(index);
            LobbyMemberDto self = update.snapshot().members().stream().filter(member -> member.playerId().equals(local)).findFirst().orElse(null);
            ready.setSelected(self != null && self.ready()); boolean host = local.equals(update.snapshot().hostPlayerId());
            maps.setEnabled(host && networkAvailable);
            maps.setToolTipText(!networkAvailable ? "Reconnect before selecting a map"
                    : host ? "Select one of the server-provided predefined maps" : "Only the lobby host may select the map");
            boolean allReady = !update.snapshot().members().isEmpty()
                    && update.snapshot().members().stream().allMatch(LobbyMemberDto::ready);
            start.setEnabled(networkAvailable && host && allReady && update.snapshot().selectedMapId() != null);
            start.setToolTipText(!networkAvailable ? "Reconnect before starting the game"
                    : !host ? "Only the lobby host may start the game"
                    : update.snapshot().selectedMapId() == null ? "Select a map before starting"
                    : !allReady ? "Every connected player must be Ready"
                    : "Ask the server to validate and start the game");
            ready.setEnabled(networkAvailable && self != null && update.snapshot().lifecycle().equals("OPEN"));
            ready.setToolTipText(!networkAvailable ? "Reconnect before changing Ready state"
                    : ready.isEnabled() ? "Publish your Ready/Not Ready state"
                    : "Ready state cannot change after game start");
            status.setText(update.snapshot().lifecycle() + " · v" + update.lobbyVersion());
        } finally { applying = false; }
    }

    // PHASE 3 - Added in Phase 3
    public void append(ChatMessagePush message) { if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Lobby UI updates must run on EDT"); chat.append("[" + message.createdAt() + "] " + message.senderUsername() + ": " + message.content() + "\n"); }
    // PHASE 3 - Added in Phase 3
    public String chatText() { return chat.getText(); }
    // PHASE 3 - Added in Phase 3
    public void notifyUser(String message) { if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Lobby UI updates must run on EDT"); status.setText(message); }
    // PHASE 3 - Added in Phase 3
    public void setConnectionState(String state, boolean mayReconnect) {
        setConnectionState(state, !mayReconnect, mayReconnect);
    }
    // PHASE 3 - Added in Phase 3
    public void setConnectionState(String state, boolean available, boolean mayReconnect) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Lobby UI updates must run on EDT");
        networkAvailable = available;
        connection.setText("Network: " + state); reconnect.setVisible(mayReconnect);
        sendChat.setEnabled(available); chatInput.setEnabled(available);
        sendChat.setToolTipText(!available ? "Reconnect before sending lobby chat"
                : "Send public chat through the authoritative server");
        // PHASE 3 - Added in Phase 3
        if (lastUpdate != null) apply(lastUpdate);
    }
    // PHASE 3 - Added in Phase 3
    public void setReconnectAction(Runnable action) {
        Objects.requireNonNull(action, "action");
        for (var listener : reconnect.getActionListeners()) reconnect.removeActionListener(listener);
        reconnect.addActionListener(event -> action.run());
    }
    // PHASE 3 - Added in Phase 3
    public JButton reconnectButton() { return reconnect; }
    // PHASE 3 - Added in Phase 3
    public void setLeaveAction(Runnable action) {
        Objects.requireNonNull(action, "action");
        for (var listener : leave.getActionListeners()) leave.removeActionListener(listener);
        leave.addActionListener(event -> action.run());
    }
    // PHASE 3 - Added in Phase 3
    public JButton leaveButton() { return leave; }
    // PHASE 3 - Added in Phase 3
    public String lobbyId() { return lobbyId; }
    // PHASE 3 - Added in Phase 3
    public JToggleButton readyButton() { return ready; }
    // PHASE 3 - Added in Phase 3
    public JButton startButton() { return start; }
    // PHASE 3 - Added in Phase 3
    public JComboBox<MapSummaryDto> mapSelector() { return maps; }
}
