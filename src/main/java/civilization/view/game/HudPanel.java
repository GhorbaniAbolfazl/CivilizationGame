package civilization.view.game;

import civilization.application.query.HudSnapshot;
import civilization.model.economy.ResourceType;
import civilization.model.unit.UnitType;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.EnumMap;
import java.util.Map;
import java.awt.GridLayout;

public final class HudPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final Map<ResourceType, JLabel> resources = new EnumMap<>(ResourceType.class);
    private final JLabel turn = new JLabel();
    private final JLabel season = new JLabel();
    private final JLabel weather = new JLabel();
    private final JLabel units = new JLabel();
    private final JLabel breakdown = new JLabel();
    private final JLabel queue = new JLabel();
    private final JLabel crisis = new JLabel();
    private final JLabel happiness = new JLabel();
    private final JButton endTurn = new JButton("End Turn");
    private final JButton pause = new JButton("Pause");
    // PHASE 3 - Added in Phase 3
    private final JLabel connection = new JLabel("Network: local");
    // PHASE 3 - Added in Phase 3
    private final JButton reconnect = new JButton("Reconnect");

    public HudPanel() {
        super(new BorderLayout(8, 4));
        setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
        setBackground(new Color(29, 38, 50));
        JPanel top = row();
        turn.setForeground(Color.WHITE);
        turn.setFont(turn.getFont().deriveFont(Font.BOLD));
        top.add(turn);
        season.setForeground(new Color(137, 207, 240));
        season.setFont(season.getFont().deriveFont(Font.BOLD));
        top.add(season);
        weather.setForeground(new Color(180, 220, 245));
        top.add(weather);
        for (ResourceType type : ResourceType.values()) {
            JLabel label = new JLabel();
            resources.put(type, label);
            top.add(label);
        }
        JPanel middle = row();
        middle.add(units);
        happiness.setForeground(Color.WHITE);
        middle.add(happiness);
        middle.add(queue);
        middle.add(endTurn);
        middle.add(pause);
        // PHASE 3 - Added in Phase 3
        middle.add(connection); middle.add(reconnect); reconnect.setVisible(false);
        JPanel lower = row();
        lower.add(breakdown);
        crisis.setForeground(new Color(255, 119, 102));
        crisis.setFont(crisis.getFont().deriveFont(Font.BOLD));
        lower.add(crisis);
        JPanel rows = new JPanel(new GridLayout(0, 1));
        rows.setOpaque(false);
        rows.add(top);
        rows.add(middle);
        rows.add(lower);
        add(rows, BorderLayout.CENTER);
    }

    public JButton endTurnButton() {
        return endTurn;
    }
    public JButton pauseButton() { return pause; }
    // PHASE 3 - Added in Phase 3
    public JButton reconnectButton() { return reconnect; }

    public void display(HudSnapshot snapshot) {
        turn.setText("Turn " + snapshot.turnNumber());
        season.setText("%s %d/10".formatted(snapshot.season(), snapshot.turnWithinSeason()));
        weather.setText("Weather: " + civilization.model.time.Weather.forSeason(snapshot.season()));
        for (ResourceType type : ResourceType.values()) {
            int net = snapshot.netPerTurn().getOrDefault(type, 0);
            JLabel label = resources.get(type);
            label.setText("%s %d/%d (%+d)".formatted(
                    type, snapshot.stored().amountOf(type), snapshot.capacities().amountOf(type), net));
            label.setForeground(net < 0 ? new Color(255, 105, 97) : Color.WHITE);
        }
        units.setText("Units %d/%d | Civilian %d | Military %d/%d".formatted(
                snapshot.activeUnits(), snapshot.unitCap(), snapshot.civilianUnits(),
                snapshot.militaryUnits(), snapshot.militaryUnitCap()));
        units.setForeground(Color.WHITE);
        happiness.setText("Happiness %+d (%s)".formatted(
                snapshot.happiness(), snapshot.happinessTier()));
        happiness.setToolTipText("Sources: events %+d, Monuments %+d, Hall garrison %+d".formatted(
                snapshot.accumulatedHappiness(), snapshot.monumentHappiness(),
                snapshot.garrisonHappiness()));
        breakdown.setText(java.util.Arrays.stream(UnitType.values())
                .map(type -> type + ": " + snapshot.unitsByType().getOrDefault(type, 0))
                .collect(java.util.stream.Collectors.joining("   ")));
        breakdown.setForeground(new Color(203, 213, 225));
        queue.setText(snapshot.currentQueue()
                .map(item -> "Queue: " + item.itemName() + " - " + item.remainingTurns() + " turns")
                .orElse("Queue: idle"));
        queue.setForeground(Color.WHITE);
        if (snapshot.gameOver()) {
            crisis.setText("GAME OVER - " + snapshot.outcomeReason());
            endTurn.setEnabled(false);
            endTurn.setToolTipText(snapshot.outcomeReason());
        } else if (snapshot.starvation()) {
            crisis.setText("STARVATION - population growth stopped");
        } else if (snapshot.resourceCrisis()) {
            crisis.setText("RESOURCE CRISIS");
        } else {
            crisis.setText(" ");
        }
        if (!snapshot.gameOver()) {
            endTurn.setEnabled(true);
            endTurn.setToolTipText(null);
        }
    }

    // PHASE 3 - Added in Phase 3
    public void displayMultiplayerInitial(int turnNumber, Map<String, Integer> authoritativeResources,
                                          int visibleOwnUnits, boolean activePlayer) {
        turn.setText("Turn " + turnNumber); season.setText("Multiplayer"); weather.setText("");
        // PHASE 3 - Added in Phase 3
        for (ResourceType type : ResourceType.values()) {
            resources.get(type).setText(type + " " + authoritativeResources.getOrDefault(type.name(), 0));
            resources.get(type).setForeground(Color.WHITE);
        }
        units.setText("Starting units " + visibleOwnUnits); breakdown.setText(""); queue.setText(""); crisis.setText(""); happiness.setText("");
        endTurn.setEnabled(false); endTurn.setToolTipText(activePlayer ? "Gameplay commands are sent to the authoritative server" : "Waiting for active player");
    }

    // PHASE 3 - Added in Phase 3
    public void displayMultiplayer(int turnNumber, String activePlayerId, String viewerPlayerId,
                                   Map<String, Integer> authoritativeResources,
                                   int visibleOwnUnits) {
        displayMultiplayerInitial(turnNumber, authoritativeResources, visibleOwnUnits,
                java.util.Objects.equals(activePlayerId, viewerPlayerId));
        String active = activePlayerId == null ? "none" : activePlayerId.substring(
                0, Math.min(8, activePlayerId.length()));
        turn.setText("Turn " + turnNumber + " · Active: "
                + (java.util.Objects.equals(activePlayerId, viewerPlayerId) ? "You" : active));
    }

    // PHASE 3 - Added in Phase 3
    public void setConnectionState(String state, boolean mayReconnect) {
        connection.setText("Network: " + state);
        reconnect.setVisible(mayReconnect);
        reconnect.setToolTipText(mayReconnect ? "Resume the same server session and empire"
                : "The authoritative server connection is active");
    }

    public JLabel seasonLabel() { return season; }
    public JLabel weatherLabel() { return weather; }

    private static JPanel row() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 2));
        panel.setOpaque(false);
        return panel;
    }
}
