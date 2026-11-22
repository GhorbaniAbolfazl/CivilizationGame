package civilization.phase2;

import civilization.application.GameFactory;
import civilization.application.generation.SeededProceduralMapGenerator;
import civilization.application.generation.TribeGenerator;
import civilization.application.factory.UnitFactory;
import civilization.application.service.VisibilityService;
import civilization.bootstrap.GameEngineFactory;
import civilization.controller.GameController;
import civilization.model.map.HexCoord;
import civilization.rules.DefaultRulesetFactory;
import civilization.view.game.GamePanel;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.SwingUtilities;

/** Generous, deterministic guardrails against accidental quadratic/stalling regressions. */
public final class PerformanceAcceptanceTestSuite {
    private static final long LARGE_MAP_BUDGET_MS = 15_000;
    private static final long SINGLE_TURN_BUDGET_MS = 2_000;

    private PerformanceAcceptanceTestSuite() { }

    public static void main(String[] args) throws Exception {
        largeMapSnapshotsAndTribeTurnsStayWithinGuardrails();
        endTurnRequestReturnsPromptlyFromTheEdt();
        System.out.println("PerformanceAcceptanceTestSuite: all checks passed");
    }

    private static void largeMapSnapshotsAndTribeTurnsStayWithinGuardrails() {
        long started = System.nanoTime();
        var rules = DefaultRulesetFactory.create();
        var visibility = new VisibilityService();
        var map = new SeededProceduralMapGenerator().generate(20, 44021L, new HexCoord(0, 0));
        var state = new GameFactory(visibility, new UnitFactory()).create(
                map, new HexCoord(0, 0), 44021L, rules);
        new TribeGenerator().generate(state, rules);
        var engine = new GameEngineFactory().createGame(state);
        check(map.size() == 1 + 3 * 20 * 21, "Large hex map has an unexpected tile count");
        for (int index = 0; index < 100; index++) {
            check(engine.snapshot().map().hexes().size() == map.size(),
                    "Snapshot lost map tiles under repeated high-volume querying");
        }
        for (int index = 0; index < 50 && !state.isGameOver(); index++) {
            long turnStarted = System.nanoTime();
            engine.endTurn();
            check(elapsedMs(turnStarted) < SINGLE_TURN_BUDGET_MS,
                    "A full economy/disaster/wildlife/tribe turn exceeded "
                            + SINGLE_TURN_BUDGET_MS + " ms");
        }
        check(elapsedMs(started) < LARGE_MAP_BUDGET_MS,
                "Large-map stress scenario exceeded " + LARGE_MAP_BUDGET_MS + " ms");
    }

    private static void endTurnRequestReturnsPromptlyFromTheEdt() throws Exception {
        GameController[] controller = new GameController[1];
        AtomicLong callMs = new AtomicLong();
        SwingUtilities.invokeAndWait(() -> {
            var engine = new GameEngineFactory().createGame(1009L);
            controller[0] = new GameController(engine, new GamePanel(),
                    (title, message) -> true);
            long started = System.nanoTime();
            controller[0].requestEndTurn();
            callMs.set(elapsedMs(started));
        });
        check(callMs.get() < 250,
                "End Turn performed long-running model work on the Swing EDT");
        long deadline = System.nanoTime() + 8_000_000_000L;
        while (controller[0].snapshot().hud().turnNumber() == 1
                && System.nanoTime() < deadline) {
            Thread.sleep(20);
            SwingUtilities.invokeAndWait(() -> { });
        }
        check(controller[0].snapshot().hud().turnNumber() == 2,
                "Background turn did not publish its completed snapshot to the EDT");
        SwingUtilities.invokeAndWait(controller[0]::close);
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
