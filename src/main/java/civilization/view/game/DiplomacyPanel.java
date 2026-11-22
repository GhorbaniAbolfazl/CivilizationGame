package civilization.view.game;

import civilization.protocol.AllianceRequestPush;
import civilization.protocol.DiplomacyStatePush;
import civilization.protocol.LobbyMemberDto;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

// PHASE 3 - Added in Phase 3
public final class DiplomacyPanel extends JPanel {
    // PHASE 3 - Added in Phase 3
    private static final long serialVersionUID = 1L;
    // PHASE 3 - Added in Phase 3
    private final JComboBox<PlayerOption> target = new JComboBox<>();
    // PHASE 3 - Added in Phase 3
    private final DefaultListModel<String> relations = new DefaultListModel<>();
    // PHASE 3 - Added in Phase 3
    private final JList<String> relationList = new JList<>(relations);
    // PHASE 3 - Added in Phase 3
    private final DefaultListModel<AllianceRequestPush> requests = new DefaultListModel<>();
    // PHASE 3 - Added in Phase 3
    private final JList<AllianceRequestPush> requestList = new JList<>(requests);
    // PHASE 3 - Added in Phase 3
    private final JButton war = new JButton("Declare War");
    // PHASE 3 - Added in Phase 3
    private final JButton requestAlliance = new JButton("Request Alliance");
    // PHASE 3 - Added in Phase 3
    private final JButton accept = new JButton("Accept");
    // PHASE 3 - Added in Phase 3
    private final JButton reject = new JButton("Reject");
    // PHASE 3 - Added in Phase 3
    private final JButton breakAlliance = new JButton("Break Alliance");
    // PHASE 3 - Added in Phase 3
    private final Map<String, PlayerOption> players = new LinkedHashMap<>();
    // PHASE 3 - Added in Phase 3
    private final Map<String, String> relationByPlayer = new LinkedHashMap<>();
    // PHASE 3 - Added in Phase 3
    private String viewerPlayerId;
    // PHASE 3 - Added in Phase 3
    private boolean activeTurn;

    // PHASE 3 - Added in Phase 3
    public DiplomacyPanel() {
        super(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder("Player Diplomacy"));
        JPanel top = new JPanel(new GridLayout(0, 1, 4, 4));
        top.add(new JLabel("Target player")); top.add(target);
        JPanel commands = new JPanel(new GridLayout(0, 1, 4, 4));
        commands.add(war); commands.add(requestAlliance); commands.add(breakAlliance);
        top.add(commands); add(top, BorderLayout.NORTH);
        JPanel center = new JPanel(new GridLayout(2, 1, 4, 4));
        center.add(new JScrollPane(relationList));
        requestList.setCellRenderer((list, value, index, selected, focused) -> {
            JLabel label = new JLabel(value.status() + " | " + name(value.requesterPlayerId())
                    + " → " + name(value.targetPlayerId()));
            label.setOpaque(true);
            label.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            return label;
        });
        center.add(new JScrollPane(requestList)); add(center, BorderLayout.CENTER);
        JPanel decisions = new JPanel(new GridLayout(1, 2, 4, 4));
        decisions.add(accept); decisions.add(reject); add(decisions, BorderLayout.SOUTH);
        target.addActionListener(event -> refreshActions());
        requestList.addListSelectionListener(event -> refreshActions());
        refreshActions();
    }

    // PHASE 3 - Added in Phase 3
    public void setPlayers(List<LobbyMemberDto> members, String viewerId) {
        viewerPlayerId = viewerId; players.clear(); target.removeAllItems();
        // PHASE 3 - Added in Phase 3
        members.stream().filter(member -> !member.playerId().equals(viewerId)).forEach(member -> {
            PlayerOption option = new PlayerOption(member.playerId(), member.username());
            players.put(option.playerId(), option); target.addItem(option);
            relationByPlayer.putIfAbsent(option.playerId(), "NEUTRAL");
        });
        renderRelations(); refreshActions();
    }

    // PHASE 3 - Added in Phase 3
    public void setActiveTurn(boolean activeTurn) {
        this.activeTurn = activeTurn; refreshActions();
    }

    // PHASE 3 - Added in Phase 3
    public void displayRelation(DiplomacyStatePush push) {
        String other = push.firstPlayerId().equals(viewerPlayerId)
                ? push.secondPlayerId() : push.secondPlayerId().equals(viewerPlayerId)
                ? push.firstPlayerId() : null;
        if (other != null) relationByPlayer.put(other, push.relation());
        renderRelations(); refreshActions();
    }

    // PHASE 3 - Added in Phase 3
    public void displayAllianceRequest(AllianceRequestPush push) {
        if (!push.requesterPlayerId().equals(viewerPlayerId)
                && !push.targetPlayerId().equals(viewerPlayerId)) return;
        // PHASE 3 - Added in Phase 3
        for (int index = 0; index < requests.size(); index++) {
            if (requests.get(index).requestId().equals(push.requestId())) {
                requests.set(index, push); refreshActions(); return;
            }
        }
        requests.addElement(push); refreshActions();
    }

    // PHASE 3 - Added in Phase 3
    public void setDeclareWarAction(Consumer<String> action) { war.addActionListener(event -> selectedTarget(action)); }
    // PHASE 3 - Added in Phase 3
    public void setRequestAllianceAction(Consumer<String> action) { requestAlliance.addActionListener(event -> selectedTarget(action)); }
    // PHASE 3 - Added in Phase 3
    public void setBreakAllianceAction(Consumer<String> action) { breakAlliance.addActionListener(event -> selectedTarget(action)); }
    // PHASE 3 - Added in Phase 3
    public void setAcceptAction(Consumer<AllianceRequestPush> action) { accept.addActionListener(event -> selectedRequest(action)); }
    // PHASE 3 - Added in Phase 3
    public void setRejectAction(Consumer<AllianceRequestPush> action) { reject.addActionListener(event -> selectedRequest(action)); }
    // PHASE 3 - Added in Phase 3
    public JList<String> relationList() { return relationList; }
    // PHASE 3 - Added in Phase 3
    public JList<AllianceRequestPush> requestList() { return requestList; }
    // PHASE 3 - Added in Phase 3
    public JButton declareWarButton() { return war; }
    // PHASE 3 - Added in Phase 3
    public JButton requestAllianceButton() { return requestAlliance; }
    // PHASE 3 - Added in Phase 3
    public JButton acceptButton() { return accept; }
    // PHASE 3 - Added in Phase 3
    public JButton rejectButton() { return reject; }
    // PHASE 3 - Added in Phase 3
    public JButton breakAllianceButton() { return breakAlliance; }

    // PHASE 3 - Added in Phase 3
    private void selectedTarget(Consumer<String> action) {
        PlayerOption selected = (PlayerOption) target.getSelectedItem();
        if (selected != null) action.accept(selected.playerId());
    }

    // PHASE 3 - Added in Phase 3
    private void selectedRequest(Consumer<AllianceRequestPush> action) {
        AllianceRequestPush selected = requestList.getSelectedValue();
        if (selected != null) action.accept(selected);
    }

    // PHASE 3 - Added in Phase 3
    private void renderRelations() {
        relations.clear();
        players.values().forEach(player -> relations.addElement(
                player.username() + ": " + relationByPlayer.getOrDefault(player.playerId(), "NEUTRAL")));
    }

    // PHASE 3 - Added in Phase 3
    private void refreshActions() {
        PlayerOption selected = (PlayerOption) target.getSelectedItem();
        String relation = selected == null ? "" : relationByPlayer.getOrDefault(selected.playerId(), "NEUTRAL");
        setEnabledReason(war, activeTurn && selected != null && !relation.equals("ENEMY"), "Available only on your turn for a non-enemy player");
        setEnabledReason(requestAlliance, activeTurn && selected != null && !relation.equals("ALLIED"), "Available only on your turn for a non-allied player");
        setEnabledReason(breakAlliance, activeTurn && relation.equals("ALLIED"), "Requires your turn and an existing alliance");
        AllianceRequestPush pending = requestList.getSelectedValue();
        boolean mayDecide = activeTurn && pending != null && pending.status().equals("PENDING")
                && pending.targetPlayerId().equals(viewerPlayerId);
        setEnabledReason(accept, mayDecide, "Select a pending alliance request addressed to you on your turn");
        setEnabledReason(reject, mayDecide, "Select a pending alliance request addressed to you on your turn");
    }

    // PHASE 3 - Added in Phase 3
    private static void setEnabledReason(JButton button, boolean enabled, String disabledReason) {
        button.setEnabled(enabled); button.setToolTipText(enabled ? null : disabledReason);
    }

    // PHASE 3 - Added in Phase 3
    private String name(String playerId) {
        PlayerOption player = players.get(playerId);
        return player == null ? playerId.substring(0, Math.min(8, playerId.length())) : player.username();
    }

    // PHASE 3 - Added in Phase 3
    private record PlayerOption(String playerId, String username) {
        // PHASE 3 - Added in Phase 3
        @Override public String toString() { return username; }
    }
}
