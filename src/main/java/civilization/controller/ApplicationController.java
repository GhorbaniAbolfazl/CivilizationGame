package civilization.controller;

import civilization.application.port.AudioPlayer;
import civilization.application.GameEngine;
import civilization.controller.port.ConfirmationPrompt;
import civilization.view.game.GamePanel;
import civilization.view.dialog.SwingConfirmationPrompt;
import civilization.view.shell.MainFrame;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.Function;
import civilization.model.game.GameState;
import civilization.application.persistence.SaveSlot;
import civilization.application.persistence.GamePersistenceService;
import civilization.application.persistence.GameIdentity;
import civilization.infrastructure.persistence.AtomicFileSaveRepository;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import javax.swing.SwingWorker;
import java.util.List;
import civilization.client.controller.MultiplayerClientCoordinator;

public final class ApplicationController {
    private final MainFrame frame;
    private final AudioPlayer audio;
    private final Supplier<GameEngine> gameFactory;
    private final ConfirmationPrompt confirmations;
    private final Function<GameState, GameEngine> loadedGameFactory;
    private GameController gameController;
    private final GamePersistenceService persistence;
    private List<civilization.application.persistence.SaveEntry> loadEntries =
            java.util.Arrays.stream(SaveSlot.values())
                    .map(civilization.application.persistence.SaveEntry::empty).toList();
    private boolean loadScanInProgress;
    // PHASE 3 - Added in Phase 3
    private final MultiplayerClientCoordinator multiplayer;

    public ApplicationController(
            MainFrame frame, AudioPlayer audio, Supplier<GameEngine> gameFactory) {
        this(frame, audio, gameFactory, new SwingConfirmationPrompt(frame));
    }

    public ApplicationController(
            MainFrame frame,
            AudioPlayer audio,
            Supplier<GameEngine> gameFactory,
            ConfirmationPrompt confirmations) {
        this(frame, audio, gameFactory, null, confirmations);
    }

    public ApplicationController(MainFrame frame, AudioPlayer audio,
            Supplier<GameEngine> gameFactory,
            Function<GameState, GameEngine> loadedGameFactory,
            ConfirmationPrompt confirmations) {
        this.frame = Objects.requireNonNull(frame, "frame");
        this.audio = Objects.requireNonNull(audio, "audio");
        this.gameFactory = Objects.requireNonNull(gameFactory, "gameFactory");
        this.confirmations = Objects.requireNonNull(confirmations, "confirmations");
        this.loadedGameFactory = loadedGameFactory;
        this.persistence = new GamePersistenceService(new AtomicFileSaveRepository(
                Path.of(System.getProperty("user.home"), ".civilization", "saves")));
        // PHASE 3 - Added in Phase 3
        this.multiplayer = new MultiplayerClientCoordinator(frame);
        bind();
    }

    public void start() {
        frame.setVisible(true);
        Thread musicLoader = new Thread(audio::playLoop, "background-music-loader");
        musicLoader.setDaemon(true);
        musicLoader.start();
    }

    private void bind() {
        frame.menu().startButton().addActionListener(event -> startGame());
        // PHASE 3 - Added in Phase 3
        frame.menu().multiplayerButton().addActionListener(event -> multiplayer.show());
        frame.menu().loadButton().addActionListener(event -> showLoadMenu());
        frame.menu().settingsButton().addActionListener(event -> frame.showCard(MainFrame.SETTINGS));
        frame.menu().exitButton().addActionListener(event -> requestExit());
        frame.settings().backButton().addActionListener(event -> frame.showCard(MainFrame.MENU));
        frame.settings().volumeControl().addAdjustmentListener(event ->
                audio.setVolume(event.getValue() / 100.0));
        frame.loadMenu().resumeButton().setText("Back to Menu");
        frame.loadMenu().resumeButton().addActionListener(event -> frame.showCard(MainFrame.MENU));
        for (SaveSlot slot : SaveSlot.values()) {
            frame.loadMenu().loadButton(slot).addActionListener(event -> loadFromMenu(slot));
            frame.loadMenu().deleteButton(slot).addActionListener(event -> deleteFromMenu(slot));
        }
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                requestExit();
            }
        });
    }

    private void startGame() {
        // PHASE 3 - Added in Phase 3
        multiplayer.close();
        installGame(gameFactory.get(), GameIdentity.newGame());
    }

    private void installGame(GameEngine engine, GameIdentity identity) {
        if (gameController != null) {
            gameController.close();
        }
        GamePanel gamePanel = new GamePanel();
        gameController = new GameController(engine, gamePanel, confirmations,
                persistence, identity);
        frame.installGame(gamePanel);
        frame.showCard(MainFrame.GAME);
        gamePanel.requestFocusInWindow();
    }

    private void showLoadMenu() {
        frame.loadMenu().display(loadEntries, false, "Start or load a game before saving");
        frame.showCard(MainFrame.LOAD);
        if (loadScanInProgress) return;
        loadScanInProgress = true;
        new SwingWorker<List<civilization.application.persistence.SaveEntry>, Void>() {
            @Override protected List<civilization.application.persistence.SaveEntry> doInBackground() {
                return persistence.listSlots();
            }
            @Override protected void done() {
                try {
                    loadEntries = get();
                    frame.loadMenu().display(loadEntries, false,
                            "Start or load a game before saving");
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    frame.loadMenu().displayAutosave(false, "Save scan interrupted");
                } catch (ExecutionException failure) {
                    frame.loadMenu().displayAutosave(false,
                            "Could not scan saves: " + failure.getCause().getMessage());
                } finally {
                    loadScanInProgress = false;
                }
            }
        }.execute();
    }

    private void loadFromMenu(SaveSlot slot) {
        if (loadedGameFactory == null) {
            frame.loadMenu().displayAutosave(false, "Loaded-game factory is unavailable");
            return;
        }
        frame.loadMenu().displayAutosave(true, "validating and loading…");
        new SwingWorker<GamePersistenceService.LoadedCandidate, Void>() {
            @Override protected GamePersistenceService.LoadedCandidate doInBackground() {
                return persistence.load(slot);
            }
            @Override protected void done() {
                try {
                    var candidate = get();
                    installGame(loadedGameFactory.apply(candidate.state()), candidate.identity());
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    frame.loadMenu().displayAutosave(false, "Load interrupted; no game changed");
                } catch (ExecutionException | IllegalArgumentException | IllegalStateException failure) {
                    Throwable cause = failure instanceof ExecutionException
                            ? failure.getCause() : failure;
                    frame.loadMenu().displayAutosave(false,
                            "Load failed; no game was changed: " + cause.getMessage());
                    showLoadMenu();
                }
            }
        }.execute();
    }

    private void deleteFromMenu(SaveSlot slot) {
        if (!confirmations.confirm("Delete Save",
                "Permanently delete " + slot + "? This cannot be undone.")) return;
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() { persistence.delete(slot); return null; }
            @Override protected void done() {
                try { get(); showLoadMenu(); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
                catch (ExecutionException failure) {
                    frame.loadMenu().displayAutosave(false,
                            "Delete failed: " + failure.getCause().getMessage());
                }
            }
        }.execute();
    }

    private void requestExit() {
        if (confirmations.confirm("Confirm Exit", "Exit the game?")) {
            if (gameController != null) {
                gameController.close();
            }
            // PHASE 3 - Added in Phase 3
            multiplayer.close();
            audio.close();
            frame.dispose();
        }
    }
}
