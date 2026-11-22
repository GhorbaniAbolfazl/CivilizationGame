package civilization.ui;

import civilization.model.building.BuildingType;

import civilization.bootstrap.GameEngineFactory;
import civilization.controller.GameController;
import civilization.model.economy.ResourceType;
import civilization.model.unit.UnitType;
import civilization.view.game.GamePanel;
import civilization.view.menu.MainMenuPanel;
import civilization.view.menu.SettingsPanel;

import javax.swing.SwingUtilities;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/** Headless EDT smoke tests for mandatory Swing widgets and controller wiring. */
public final class SwingUiTestSuite {
    private SwingUiTestSuite() {
    }

    public static void main(String[] args) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                menuAndSettingsExposeMandatoryControls();
                gameScreenRendersMandatoryHudAndInteractions();
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });
        if (failure.get() != null) {
            throw new AssertionError("Swing UI test failed", failure.get());
        }
        System.out.println("SwingUiTestSuite: all checks passed");
    }

    private static void menuAndSettingsExposeMandatoryControls() {
        MainMenuPanel menu = new MainMenuPanel();
        check(menu.startButton().getText().equals("Start"), "Start button is missing");
        check(menu.loadButton().getText().equals("Load Game"),
                "Cold-start load access is missing");
        check(menu.settingsButton().getText().equals("Settings"), "Settings button is missing");
        check(menu.exitButton().getText().equals("Exit"), "Exit button is missing");
        SettingsPanel settings = new SettingsPanel();
        check(settings.volumeControl().getOrientation() == javax.swing.JScrollBar.HORIZONTAL,
                "Settings must use a horizontal JScrollBar for volume");
        check(settings.volumeControl().getMaximum() == 100,
                "Volume Scroll Bar must expose the full 0-100 range");
        settings.volumeControl().setValue(100);
        check(settings.volumeControl().getValue() == 100,
                "Volume Scroll Bar must reach exactly 100 without exceeding it");
        render(menu, 900, 650, Path.of("target/ui-main-menu.png"));
        render(settings, 900, 650, Path.of("target/ui-settings.png"));
    }

    private static void gameScreenRendersMandatoryHudAndInteractions() {
        var engine = new GameEngineFactory().createDefaultGame();
        GamePanel panel = new GamePanel();
        String townHallTooltip = panel.actions().openTownHallButton().getToolTipText();
        GameController controller = new GameController(engine, panel);
        var snapshot = controller.snapshot();

        check(snapshot.hud().turnNumber() == 1, "Initial turn number is not exposed to HUD");
        check(snapshot.hud().season() == civilization.model.time.Season.SPRING
                        && snapshot.hud().turnWithinSeason() == 1
                        && panel.hud().seasonLabel().getText().equals("SPRING 1/10"),
                "HUD does not expose the complete season state");
        check(panel.hud().weatherLabel().getText().equals("Weather: CLEAR"),
                "HUD does not expose season-derived weather");
        check(snapshot.hud().activeUnits() == 5 && snapshot.hud().unitCap() == 7,
                "HUD Unit Count/Cap is incorrect");
        check(snapshot.hud().unitsByType().get(UnitType.BUILDER) == 2
                        && snapshot.hud().unitsByType().get(UnitType.WORKER) == 2
                        && snapshot.hud().unitsByType().get(UnitType.EXPLORER) == 1,
                "HUD unit breakdown is incorrect");
        for (ResourceType type : ResourceType.values()) {
            check(snapshot.hud().capacities().amountOf(type) > 0,
                    "HUD is missing capacity for " + type);
            check(snapshot.hud().netPerTurn().containsKey(type),
                    "HUD is missing projected net for " + type);
        }
        check(panel.hud().endTurnButton().isVisible(), "End Turn button must always be visible");
        check(panel.actions().buildButtons().size() == BuildingType.values().length - 1,
                "Every constructible building type needs an action control");
        check(panel.actions().openTownHallButton().isEnabled(),
                "Town Hall interaction must be directly accessible");
        check(townHallTooltip.equals(panel.actions().openTownHallButton().getToolTipText()),
                "Enabled action previews must preserve explanatory tooltips");
        check(panel.minimap().isVisible(), "Bonus minimap must be present on the game screen");
        check(panel.actions().autoExploreButton().isVisible(),
                "Explorer Auto-Explore toggle must be exposed in the action panel");
        check(panel.townHall().unitButtons().size() == UnitType.values().length,
                "Town Hall is missing unit production controls");
        check(panel.townHall().upgradeButtons().size()
                        == civilization.model.technology.UpgradeId.values().length,
                "Town Hall is missing upgrade controls");
        check(panel.townHall().hallButtons().size() == 2,
                "Town Hall level upgrades are missing");
        check(panel.townHall().technologyButtons().size() == 3,
                "Technology research controls are missing");
        check(panel.townHall().cancelButton().getToolTipText() != null,
                "Production queue cancellation needs an explanatory tooltip");
        check(panel.pause().saveButton(civilization.application.persistence.SaveSlot.AUTOSAVE)
                        .isEnabled() == false,
                "Autosave must have a separate non-manual slot");
        panel.pause().display(java.util.List.of(new civilization.application.persistence.SaveEntry(
                        civilization.application.persistence.SaveSlot.MANUAL_1,
                        civilization.application.persistence.SaveEntry.Status.CORRUPTED,
                        java.util.Optional.empty(), java.util.Optional.of("checksum mismatch"))),
                true, "stable");
        check(panel.pause().statusLabel(civilization.application.persistence.SaveSlot.MANUAL_1)
                        .getText().contains("CORRUPTED")
                        && !panel.pause().loadButton(
                        civilization.application.persistence.SaveSlot.MANUAL_1).isEnabled()
                        && panel.pause().deleteButton(
                        civilization.application.persistence.SaveSlot.MANUAL_1).isEnabled(),
                "Corrupted saves must be visibly marked and impossible to load");
        check(panel.trade().sourceSelector().getItemCount() == ResourceType.values().length
                        && panel.trade().targetSelector().getItemCount() == ResourceType.values().length,
                "Trade UI is missing resource selection");
        check(panel.trade().levelSelector().getItemCount()
                        == civilization.model.trade.BazaarTradeLevel.values().length,
                "Trade UI is missing Bazaar levels");

        var tradingPost = snapshot.map().tradingPosts().stream().findFirst().orElseThrow();
        controller.selectHex(tradingPost.position());
        check(panel.trade().isVisible(),
                "Selecting a discovered Trading Center must open trade interaction");
        check(!panel.trade().tradeButton().isEnabled(),
                "An uncontrolled Trading Center must display a disabled trade action");

        var explorer = snapshot.units().stream()
                .filter(unit -> unit.type() == UnitType.EXPLORER).findFirst().orElseThrow();
        var originalCamera = panel.mapCanvas().camera().center();
        var cameraTarget = snapshot.map().hexes().stream()
                .map(civilization.application.query.HexSnapshot::coordinate)
                .filter(coordinate -> !coordinate.equals(explorer.position()))
                .findFirst().orElseThrow();
        panel.minimap().centerCameraOn(cameraTarget);
        check(!panel.mapCanvas().camera().center().equals(originalCamera),
                "Minimap navigation must reposition the main map camera");
        for (int attempt = 0;
                attempt < 12 && !panel.actions().autoExploreButton().isEnabled(); attempt++) {
            controller.selectHex(explorer.position());
        }
        check(panel.actions().buildButtons().values().stream().noneMatch(javax.swing.JButton::isEnabled),
                "Construction must be locked when a non-Builder is selected");
        check(panel.actions().autoExploreButton().isEnabled(),
                "Auto-Explore must become available for a selected Explorer");
        check(panel.mapCanvas().movementPreviews().size() == 6,
                "Selecting a unit must visibly preview all adjacent movement targets");
        controller.toggleAutoExplore();
        check(panel.actions().autoExploreButton().getText().equals("Disable Auto-Explore"),
                "Auto-Explore must expose an immediate disable control while active");
        controller.toggleAutoExplore();

        var hall = snapshot.buildings().stream()
                .filter(building -> building.type()
                        == civilization.model.building.BuildingType.TOWN_HALL)
                .findFirst().orElseThrow();
        for (int attempt = 0; attempt < 12 && !panel.townHall().isVisible(); attempt++) {
            controller.selectHex(hall.position());
        }
        check(panel.townHall().isVisible(),
                "Selecting Town Hall through the map must open production interaction");
        panel.minimap().centerCameraOn(hall.position());
        render(panel, 1360, 850, Path.of("target/ui-game-screen.png"));
        check(panel.hud().endTurnButton().getHeight() >= 20
                        && panel.hud().pauseButton().getHeight() >= 20,
                "Fixed HUD controls were clipped by the responsive layout");

        var movingEngine = new GameEngineFactory().createDefaultGame();
        GamePanel movingPanel = new GamePanel();
        GameController movingController = new GameController(movingEngine, movingPanel);
        var movingExplorer = movingController.snapshot().units().stream()
                .filter(unit -> unit.type() == UnitType.EXPLORER).findFirst().orElseThrow();
        for (int attempt = 0; attempt < 5; attempt++) {
            movingController.selectHex(movingExplorer.position());
        }
        movingController.requestMove(new civilization.model.map.HexCoord(1, 0));
        check(movingPanel.mapCanvas().animations().hasActiveAnimations(),
                "A committed map move must start a visual animation");
        check(!movingPanel.hud().endTurnButton().isEnabled()
                        && !movingPanel.actions().openTownHallButton().isEnabled()
                        && movingPanel.actions().buildButtons().values().stream()
                        .noneMatch(javax.swing.JButton::isEnabled),
                "Gameplay actions must be locked while movement is animating");
        movingPanel.mapCanvas().animations().cancelAll();
        check(movingPanel.hud().endTurnButton().isEnabled()
                        && movingPanel.actions().openTownHallButton().isEnabled(),
                "Gameplay actions must be restored when movement animation finishes");
        movingPanel.mapCanvas().effects().showBuild(movingExplorer.position());
        check(movingPanel.mapCanvas().effects().hasActiveEffects(),
                "Visual effect setup failed");
        java.util.concurrent.atomic.AtomicInteger effectTransitions =
                new java.util.concurrent.atomic.AtomicInteger();
        var standaloneEffects = new civilization.view.animation.VisualEffectManager(() -> { });
        standaloneEffects.setEffectStateChanged(effectTransitions::incrementAndGet);
        standaloneEffects.showBuild(movingExplorer.position());
        standaloneEffects.cancelAll();
        check(effectTransitions.get() == 2,
                "Visual effects did not report both unstable and stable transitions");
        movingController.refresh();
        movingPanel.hud().pauseButton().doClick();
        check(!movingPanel.pause().saveButton(
                        civilization.application.persistence.SaveSlot.MANUAL_1).isEnabled()
                        && movingPanel.pause().saveButton(
                        civilization.application.persistence.SaveSlot.MANUAL_1)
                        .getToolTipText().contains("unavailable"),
                "Save actions must explain why persistence is unavailable/unstable");
        movingController.close();
        check(!movingPanel.mapCanvas().animations().hasActiveAnimations()
                        && !movingPanel.mapCanvas().effects().hasActiveEffects(),
                "Closing a game controller must stop presentation timers and overlays");
        controller.close();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void render(JComponent component, int width, int height, Path path) {
        try {
            Files.createDirectories(path.getParent());
            component.setSize(width, height);
            component.doLayout();
            layoutDescendants(component);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            component.printAll(graphics);
            graphics.dispose();
            ImageIO.write(image, "png", path.toFile());
        } catch (IOException failure) {
            throw new IllegalStateException("Could not render Swing QA image", failure);
        }
    }

    private static void layoutDescendants(java.awt.Container parent) {
        parent.doLayout();
        for (java.awt.Component child : parent.getComponents()) {
            if (child instanceof java.awt.Container container) {
                layoutDescendants(container);
            }
        }
    }
}
