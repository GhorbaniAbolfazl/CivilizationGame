package civilization.view.shell;

import civilization.view.game.GamePanel;
import civilization.view.menu.MainMenuPanel;
import civilization.view.menu.SettingsPanel;
import civilization.view.game.PauseMenuPanel;
import civilization.view.lobby.LobbyPanel;
import civilization.view.lobby.MultiplayerConnectPanel;

import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.CardLayout;

public final class MainFrame extends JFrame {
    private static final long serialVersionUID = 1L;
    public static final String MENU = "menu";
    public static final String SETTINGS = "settings";
    public static final String GAME = "game";
    public static final String LOAD = "load";
    // PHASE 3 - Added in Phase 3
    public static final String LOBBY = "lobby";
    // PHASE 3 - Added in Phase 3
    public static final String MULTIPLAYER_CONNECT = "multiplayer-connect";

    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final MainMenuPanel menu = new MainMenuPanel();
    private final SettingsPanel settings = new SettingsPanel();
    private final PauseMenuPanel load = new PauseMenuPanel();
    private GamePanel game;
    // PHASE 3 - Added in Phase 3
    private LobbyPanel lobby;
    // PHASE 3 - Added in Phase 3
    private final MultiplayerConnectPanel multiplayerConnect = new MultiplayerConnectPanel();

    public MainFrame() {
        super("Civilization");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new java.awt.Dimension(1100, 720));
        setSize(1360, 850);
        setLocationRelativeTo(null);
        content.add(menu, MENU);
        content.add(settings, SETTINGS);
        content.add(load, LOAD);
        // PHASE 3 - Added in Phase 3
        content.add(multiplayerConnect, MULTIPLAYER_CONNECT);
        setContentPane(content);
        showCard(MENU);
    }

    public MainMenuPanel menu() {
        return menu;
    }

    public SettingsPanel settings() {
        return settings;
    }
    public PauseMenuPanel loadMenu() { return load; }
    // PHASE 3 - Added in Phase 3
    public MultiplayerConnectPanel multiplayerConnect() { return multiplayerConnect; }

    public void installGame(GamePanel panel) {
        if (game != null) {
            content.remove(game);
        }
        game = panel;
        content.add(game, GAME);
        content.revalidate();
    }

    // PHASE 3 - Added in Phase 3
    public void installLobby(LobbyPanel panel) {
        if (lobby != null) content.remove(lobby);
        lobby = java.util.Objects.requireNonNull(panel, "panel");
        content.add(lobby, LOBBY);
        content.revalidate(); content.repaint();
    }

    // PHASE 3 - Added in Phase 3
    public void closeLobby() {
        if (lobby == null) return;
        content.remove(lobby); lobby = null;
        content.revalidate(); content.repaint();
    }

    public void showCard(String card) {
        cards.show(content, card);
    }
}
