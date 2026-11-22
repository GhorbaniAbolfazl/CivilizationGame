package civilization.application.service;

import civilization.model.game.GameState;
import civilization.model.map.HexCoord;
import civilization.model.map.TerrainType;
import civilization.model.unit.MilitaryUnit;
import civilization.model.unit.Unit;
import java.util.Comparator;
import java.util.List;

/** Deterministic per-turn pursuit and safe cleanup for bears created by a disaster. */
public final class BearAttackService {
    public void advance(GameState state) {
        for (var bear : List.copyOf(state.world().wildAnimals().all())) {
            if (!bear.isBear()) continue;
            List<Unit> targets = state.player().units().stream()
                    .filter(unit -> unit.position().distanceTo(bear.nest()) <= 3)
                    .sorted(Comparator.comparing((Unit unit) -> unit instanceof MilitaryUnit)
                            .thenComparingInt(unit -> unit.position().distanceTo(bear.position()))
                            .thenComparing(unit -> unit.id().value())).toList();
            if (targets.isEmpty()) {
                state.world().wildAnimals().remove(bear.id());
                continue;
            }
            bear.refillActionPoints();
            HexCoord target = targets.get(0).position();
            while (!bear.position().equals(target) && bear.actionPoints() > 0) {
                HexCoord next = bear.position().neighbors().stream()
                        .filter(state.map()::contains)
                        .filter(coord -> passable(state, coord))
                        .filter(coord -> coord.distanceTo(bear.nest()) <= 3)
                        .min(Comparator.comparingInt((HexCoord coord) -> coord.distanceTo(target))
                                .thenComparingInt(HexCoord::q).thenComparingInt(HexCoord::r))
                        .orElse(null);
                if (next == null || next.distanceTo(target) >= bear.position().distanceTo(target)) break;
                bear.moveTo(next);
                bear.spendActionPoint();
            }
        }
    }

    private static boolean passable(GameState state, HexCoord coordinate) {
        TerrainType terrain = state.map().requireTile(coordinate).terrain();
        return terrain != TerrainType.SEA && terrain != TerrainType.MOUNTAIN_RANGE
                && state.world().wildAnimals().at(coordinate).isEmpty();
    }
}
