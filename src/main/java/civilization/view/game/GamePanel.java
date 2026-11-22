package civilization.view.game;

import civilization.view.render.CameraState;
import civilization.view.render.HexLayout;
import civilization.view.render.MapCanvas;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.function.BooleanSupplier;

public final class GamePanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final HudPanel hud = new HudPanel();
    private final MapCanvas mapCanvas = new MapCanvas(new HexLayout(42.0), new CameraState());
    private final ActionPanel actions = new ActionPanel();
    private final TownHallPanel townHall = new TownHallPanel();
    private final TradePanel trade = new TradePanel();
    // PHASE 3 - Added in Phase 3
    private final PlayerTradePanel playerTrade = new PlayerTradePanel();
    // PHASE 3 - Added in Phase 3
    private final DiplomacyPanel diplomacy = new DiplomacyPanel();
    // PHASE 3 - Added in Phase 3
    private final MultiplayerHallTownPanel multiplayerHallTown = new MultiplayerHallTownPanel();
    // PHASE 3 - Added in Phase 3
    private final MultiplayerItemPanel multiplayerItems = new MultiplayerItemPanel();
    private final CombatPanel combat = new CombatPanel();
    private final TribePanel tribe = new TribePanel();
    private final InfrastructurePanel infrastructure = new InfrastructurePanel();
    private final PauseMenuPanel pause = new PauseMenuPanel();
    // PHASE 3 - Added in Phase 3
    private final GameChatPanel chat = new GameChatPanel();
    private final MinimapPanel minimap = new MinimapPanel(mapCanvas.hexLayout(), mapCanvas.camera());
    private final JTabbedPane interactionTabs = new JTabbedPane();
    private final JLabel notification = new JLabel(" ");
    private final JTextArea notificationHistory = new JTextArea(3, 80);

    public GamePanel() {
        super(new BorderLayout(8, 8));
        JPanel side = new JPanel(new BorderLayout(5, 5));
        interactionTabs.addTab("Actions", actions);
        interactionTabs.addTab("Town Hall", townHall);
        interactionTabs.addTab("Trade", trade);
        // PHASE 3 - Added in Phase 3
        interactionTabs.addTab("Player Trade", playerTrade);
        // PHASE 3 - Added in Phase 3
        interactionTabs.addTab("Diplomacy", diplomacy);
        // PHASE 3 - Added in Phase 3
        interactionTabs.addTab("New Hall", multiplayerHallTown);
        // PHASE 3 - Added in Phase 3
        interactionTabs.addTab("Items", multiplayerItems);
        interactionTabs.addTab("Combat", combat);
        interactionTabs.addTab("Tribe", tribe);
        interactionTabs.addTab("Build/Inspect", infrastructure);
        interactionTabs.addTab("Pause", pause);
        // PHASE 3 - Added in Phase 3
        interactionTabs.addTab("Chat", chat);
        interactionTabs.setToolTipTextAt(0, "Contextual unit and construction commands");
        interactionTabs.setToolTipTextAt(1, "Unit production and technology upgrades");
        interactionTabs.setToolTipTextAt(2, "Bazaar and neutral Trading Center exchange");
        // PHASE 3 - Added in Phase 3
        interactionTabs.setToolTipTextAt(3, "Server-authoritative offers and trade inbox");
        // PHASE 3 - Added in Phase 3
        interactionTabs.setToolTipTextAt(4, "Server-authoritative war and alliance actions");
        // PHASE 3 - Added in Phase 3
        interactionTabs.setToolTipTextAt(5, "Use an owned Builder to establish another controlled region");
        // PHASE 3 - Added in Phase 3
        interactionTabs.setToolTipTextAt(6, "Build Apothecaries, craft items, and use inventory");
        interactionTabs.setToolTipTextAt(7, "Complete dice, pairing, damage, and outcome trace");
        interactionTabs.setToolTipTextAt(8, "Diplomacy, gifts, trade, missions, war and alliance");
        interactionTabs.setToolTipTextAt(9, "Exact Road tile, Wall edge, demolition and hex inspection");
        interactionTabs.setToolTipTextAt(10, "Manual saves, autosave status, and safe loading");
        // PHASE 3 - Added in Phase 3
        interactionTabs.setToolTipTextAt(11, "Real-time public match chat");
        side.add(interactionTabs, BorderLayout.CENTER);
        side.add(minimap, BorderLayout.SOUTH);
        side.setPreferredSize(new java.awt.Dimension(390, 680));
        minimap.setCameraMoved(mapCanvas::repaint);
        notification.setBorder(BorderFactory.createEmptyBorder(4, 8, 6, 8));
        notification.setForeground(new Color(255, 208, 115));
        notification.setOpaque(true);
        notification.setBackground(new Color(20, 28, 39));
        setBackground(new Color(14, 21, 31));
        add(hud, BorderLayout.NORTH);
        add(mapCanvas, BorderLayout.CENTER);
        add(side, BorderLayout.EAST);
        notificationHistory.setEditable(false);
        notificationHistory.setLineWrap(true);
        notificationHistory.setWrapStyleWord(true);
        notificationHistory.setBackground(new Color(20, 28, 39));
        notificationHistory.setForeground(new Color(210, 220, 232));
        JPanel notices = new JPanel(new BorderLayout());
        notices.add(notification, BorderLayout.NORTH);
        notices.add(new JScrollPane(notificationHistory), BorderLayout.CENTER);
        add(notices, BorderLayout.SOUTH);
    }

    public HudPanel hud() {
        return hud;
    }

    public MapCanvas mapCanvas() {
        return mapCanvas;
    }

    public ActionPanel actions() {
        return actions;
    }

    public TownHallPanel townHall() {
        return townHall;
    }

    public TradePanel trade() { return trade; }
    // PHASE 3 - Added in Phase 3
    public PlayerTradePanel playerTrade() { return playerTrade; }
    // PHASE 3 - Added in Phase 3
    public DiplomacyPanel diplomacy() { return diplomacy; }
    // PHASE 3 - Added in Phase 3
    public MultiplayerHallTownPanel multiplayerHallTown() { return multiplayerHallTown; }
    // PHASE 3 - Added in Phase 3
    public MultiplayerItemPanel multiplayerItems() { return multiplayerItems; }

    public CombatPanel combat() { return combat; }
    public TribePanel tribe() { return tribe; }
    public InfrastructurePanel infrastructure() { return infrastructure; }
    public PauseMenuPanel pause() { return pause; }
    // PHASE 3 - Added in Phase 3
    public GameChatPanel chat() { return chat; }

    public void showPause(boolean open) {
        if (open) interactionTabs.setSelectedComponent(pause);
        else interactionTabs.setSelectedComponent(actions);
    }

    public void showCombatResult(civilization.model.combat.CombatResult result) {
        combat.display(result);
        interactionTabs.setSelectedComponent(combat);
    }

    // PHASE 3 - Added in Phase 3
    public void showBattleReport(civilization.protocol.BattleReportPush report) {
        if (!javax.swing.SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Battle reports must be displayed on the EDT");
        }
        combat.display(report);
        interactionTabs.setSelectedComponent(combat);
    }

    public MinimapPanel minimap() {
        return minimap;
    }

    public void setTownHallOpen(boolean open) {
        townHall.setVisible(open);
        interactionTabs.setSelectedComponent(open ? townHall : actions);
    }

    public void setTradeOpen(boolean open) {
        trade.setVisible(open);
        if (open) interactionTabs.setSelectedComponent(trade);
    }

    public void setTribeOpen(boolean open) {
        tribe.setVisible(open);
        if (open) interactionTabs.setSelectedComponent(tribe);
    }

    public void notifyUser(String message) {
        String safe = message == null || message.isBlank() ? " " : message;
        notification.setText(safe);
        if (!safe.isBlank()) {
            notificationHistory.append(safe + System.lineSeparator());
            notificationHistory.setCaretPosition(notificationHistory.getDocument().getLength());
        }
    }

    public String notificationHistory() { return notificationHistory.getText(); }

    public void setActionInputGate(BooleanSupplier actionInputAllowed) {
        java.util.Objects.requireNonNull(actionInputAllowed, "actionInputAllowed");
        mapCanvas.setActionInputGate(actionInputAllowed);
    }
}
