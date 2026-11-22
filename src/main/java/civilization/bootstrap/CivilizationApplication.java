package civilization.bootstrap;

import civilization.controller.ApplicationController;
import civilization.infrastructure.audio.JavaSoundAudioPlayer;
import civilization.view.dialog.SwingConfirmationPrompt;
import civilization.view.shell.MainFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class CivilizationApplication {
    private CivilizationApplication() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ignored) {
                // Swing's cross-platform look and feel remains available.
            }
            GameEngineFactory gameFactory = new GameEngineFactory();
            MainFrame frame = new MainFrame();
            new ApplicationController(
                    frame,
                    new JavaSoundAudioPlayer(),
                    gameFactory::createDefaultGame,
                    gameFactory::createGame,
                    new SwingConfirmationPrompt(frame)).start();
        });
    }
}
