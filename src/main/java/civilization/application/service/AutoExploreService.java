package civilization.application.service;

import civilization.application.ActionFailure;
import civilization.application.event.UnitMovedEvent;
import civilization.model.game.GamePhase;
import civilization.model.game.GameState;
import civilization.model.map.HexCoord;
import civilization.model.unit.ExplorerUnit;
import civilization.model.unit.UnitId;
import civilization.rules.GameRules;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

/** Chooses one safe deterministic Explorer step and commits it through MovementService. */
public final class AutoExploreService {
    private final MovementService movementService;

    public AutoExploreService(MovementService movementService) {
        this.movementService = Objects.requireNonNull(movementService, "movementService");
    }

    public Optional<UnitMovedEvent> moveNext(GameState state, UnitId explorerId, GameRules rules) {
        Optional<HexCoord> destination = planNext(state, explorerId, rules);
        return destination.map(coordinate -> movementService.move(
                state, explorerId, coordinate, rules));
    }

    public Optional<HexCoord> planNext(GameState state, UnitId explorerId, GameRules rules) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(rules, "rules");
        if (state.turn().phase() != GamePhase.PLAYER_ACTIONS) {
            return Optional.empty();
        }
        if (!(state.player().requireUnit(explorerId) instanceof ExplorerUnit explorer)
                || !explorer.isActive()) {
            throw new ActionFailure("Auto-Explore requires an active Explorer");
        }
        int availableAp = explorer.actionPoints().current();
        if (availableAp == 0) {
            return Optional.empty();
        }
        HexCoord current = explorer.position();
        int visionRadius = rules.unit(explorer.type()).visionRadius();

        Map<HexCoord, Integer> revealScores = new HashMap<>();
        state.map().neighborsOf(current).forEach(tile -> revealScores.put(tile.coordinate(),
                newlyRevealedCount(state, tile.coordinate(), visionRadius)));
        List<HexCoord> revealingNeighbors = revealScores.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(Map.Entry::getKey)
                .filter(destination -> safeFrontierStep(
                        state, explorer.id(), current, destination, availableAp, rules))
                .sorted(Comparator
                        .comparingInt((HexCoord destination) -> revealScores.get(destination)).reversed()
                        .thenComparingInt(destination -> movementService.movementCost(
                                state, explorer.id(), destination, rules))
                        .thenComparingInt(HexCoord::q)
                        .thenComparingInt(HexCoord::r))
                .toList();
        if (!revealingNeighbors.isEmpty()) {
            return Optional.of(revealingNeighbors.get(0));
        }

        return firstStepTowardNearestFrontier(
                state, explorer.id(), current, availableAp, visionRadius, rules);
    }

    private Optional<HexCoord> firstStepTowardNearestFrontier(
            GameState state,
            UnitId explorerId,
            HexCoord current,
            int availableAp,
            int visionRadius,
            GameRules rules) {
        Queue<HexCoord> queue = new ArrayDeque<>();
        Set<HexCoord> visited = new HashSet<>();
        Map<HexCoord, HexCoord> firstStep = new HashMap<>();
        queue.add(current);
        visited.add(current);
        while (!queue.isEmpty()) {
            HexCoord cursor = queue.remove();
            List<HexCoord> neighbors = state.map().neighborsOf(cursor).stream()
                    .map(tile -> tile.coordinate())
                    .filter(state.player().visibility()::isDiscovered)
                    .sorted(Comparator.comparingInt(HexCoord::q).thenComparingInt(HexCoord::r))
                    .toList();
            for (HexCoord neighbor : neighbors) {
                if (!visited.add(neighbor)) {
                    continue;
                }
                HexCoord step = cursor.equals(current) ? neighbor : firstStep.get(cursor);
                firstStep.put(neighbor, step);
                if (newlyRevealedCount(state, neighbor, visionRadius) > 0
                        && movementService.movementCost(state, explorerId, step, rules) <= availableAp) {
                    return Optional.of(step);
                }
                queue.add(neighbor);
            }
        }
        return Optional.empty();
    }

    private boolean safeFrontierStep(
            GameState state,
            UnitId explorerId,
            HexCoord origin,
            HexCoord destination,
            int availableAp,
            GameRules rules) {
        int outwardCost = movementService.movementCost(state, explorerId, destination, rules);
        int returnCost = movementService.movementCostBetween(
                state, explorerId, destination, origin, rules);
        return outwardCost <= availableAp && outwardCost + returnCost <= availableAp;
    }

    private static int newlyRevealedCount(GameState state, HexCoord center, int visionRadius) {
        int count = 0;
        for (HexCoord coordinate : state.map().coordinatesWithin(center, visionRadius)) {
            if (!state.player().visibility().isDiscovered(coordinate)) {
                count++;
            }
        }
        return count;
    }
}
