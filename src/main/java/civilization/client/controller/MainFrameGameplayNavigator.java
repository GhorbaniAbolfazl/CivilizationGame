package civilization.client.controller;

import civilization.view.game.GamePanel;
import civilization.view.shell.MainFrame;
import java.util.Objects;

// PHASE 3 - Added in Phase 3
public final class MainFrameGameplayNavigator implements GameplayNavigator {
    // PHASE 3 - Added in Phase 3
    private final MainFrame frame;
    // PHASE 3 - Added in Phase 3
    public MainFrameGameplayNavigator(MainFrame frame) { this.frame = Objects.requireNonNull(frame); }
    // PHASE 3 - Added in Phase 3
    @Override public void openGameplay(GamePanel panel) { frame.installGame(panel); frame.closeLobby(); frame.showCard(MainFrame.GAME); panel.requestFocusInWindow(); }
}
