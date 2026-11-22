package civilization.application.service;

import civilization.application.action.ActionPreview;
import civilization.application.factory.UnitFactory;
import civilization.model.game.GameState;
import civilization.model.identity.TribeId;
import civilization.model.map.HexCoord;
import civilization.model.map.TerrainType;
import civilization.model.unit.UnitType;
import civilization.rules.GameRules;
import civilization.rules.combat.MilitaryStackingPolicy;
import civilization.rules.tribe.TribeStrategyCatalog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Validates and grants tribe rewards atomically, including unit-cap and stacking constraints. */
public final class TribeRewardService {
    private final UnitFactory units = new UnitFactory();
    private final MilitaryStackingPolicy stacking = new MilitaryStackingPolicy();
    private final TribeStrategyCatalog strategies = new TribeStrategyCatalog();

    public ActionPreview previewAllianceReward(GameState state, TribeId tribeId) {
        var tribe = state.world().tribes().require(tribeId);
        var strategy = strategies.require(tribe.type());
        if (strategy.grantsAuxiliarySwordsmanOnAlliance() && !tribe.isAllianceRewardGranted()) {
            return previewUnits(state, 1, UnitType.SWORDSMAN);
        }
        return ActionPreview.allow();
    }

    public void grantAllianceReward(GameState state, TribeId tribeId, GameRules rules) {
        var tribe = state.world().tribes().require(tribeId);
        if (tribe.isAllianceRewardGranted()) return;
        var strategy = strategies.require(tribe.type());
        if (strategy.allianceTradeBonusPercent() > 0) {
            state.world().tribeTrades().require(tribeId)
                    .increaseRateBonus(strategy.allianceTradeBonusPercent());
        }
        if (strategy.grantsAuxiliarySwordsmanOnAlliance()) {
            grantUnits(state, 1, UnitType.SWORDSMAN, rules);
        }
        if (tribe.type() == civilization.model.tribe.TribeType.COASTAL) {
            tribe.grantNextDockDiscount();
        }
        tribe.markAllianceRewardGranted();
    }

    public ActionPreview previewUnits(GameState state, int count, UnitType type) {
        Objects.requireNonNull(state, "state");
        if (count <= 0 || !type.isMilitary()) throw new IllegalArgumentException("Invalid reward units");
        if (state.player().activeUnitCount() + count > state.player().totalUnitCapacity()) {
            return ActionPreview.reject("Unit Cap cannot accept mission reward");
        }
        if (state.player().activeMilitaryUnitCount() + count
                > state.player().militaryUnitCapacity()) {
            return ActionPreview.reject("Military Unit Cap cannot accept mission reward");
        }
        return plannedSpawns(state, count, type).size() == count
                ? ActionPreview.allow() : ActionPreview.reject("No legal space for mission reward units");
    }

    public void grantUnits(GameState state, int count, UnitType type, GameRules rules) {
        ActionPreview preview = previewUnits(state, count, type);
        if (!preview.allowed()) throw new IllegalStateException(preview.reason());
        for (HexCoord spawn : plannedSpawns(state, count, type)) {
            state.player().addCompletedUnit(units.create(
                    civilization.model.unit.UnitId.from(state.gameplayRandom()),
                    type, spawn, rules,
                    state.player().factionId()));
        }
    }

    private List<HexCoord> plannedSpawns(GameState state, int count, UnitType type) {
        List<HexCoord> candidates = state.map().tiles().stream()
                .map(tile -> tile.coordinate())
                .filter(state.player().border()::owns)
                .filter(coord -> state.map().requireTile(coord).terrain() != TerrainType.SEA)
                .filter(coord -> state.map().requireTile(coord).terrain() != TerrainType.MOUNTAIN_RANGE)
                .sorted(Comparator.comparingInt((HexCoord coord) ->
                                coord.distanceTo(state.player().townHall().position()))
                        .thenComparingInt(HexCoord::q).thenComparingInt(HexCoord::r))
                .toList();
        List<HexCoord> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            HexCoord selected = candidates.stream().filter(coord -> {
                long existing = state.player().units().stream()
                        .filter(unit -> unit.position().equals(coord) && unit.type() == type).count();
                long planned = result.stream().filter(coord::equals).count();
                return existing + planned < stacking.maximum(type);
            }).findFirst().orElse(null);
            if (selected == null) break;
            result.add(selected);
        }
        return List.copyOf(result);
    }
}
