package civilization.application.query;

import civilization.model.game.GameState;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public final class TribeQuery {
    public List<TribeSnapshot> discoveredTribes(GameState state) {
        return state.world().tribes().all().stream().filter(tribe -> tribe.isDiscovered())
                .map(tribe -> {
                    boolean visible = state.player().visibility()
                            .isCurrentlyVisible(tribe.camp().position());
                    var relation = state.world().relationships().require(tribe.id());
                    var mission = state.world().missions().unresolvedForTribe(tribe.id());
                    var strategy = new civilization.rules.tribe.TribeStrategyCatalog()
                            .require(tribe.type());
                    return new TribeSnapshot(tribe.id(), tribe.name(), tribe.type(),
                            tribe.camp().position(), visible, tribe.isDefeated(),
                            visible ? OptionalInt.of(relation.score()) : OptionalInt.empty(),
                            visible ? Optional.of(relation.band()) : Optional.empty(),
                            visible ? Optional.of(relation.stance()) : Optional.empty(),
                            visible ? OptionalInt.of(tribe.camp().health().current()) : OptionalInt.empty(),
                            visible ? mission.map(civilization.model.mission.Mission::type)
                                    : Optional.empty(),
                            visible ? mission.map(civilization.model.mission.Mission::status)
                                    : Optional.empty(),
                            visible ? mission.map(civilization.model.mission.Mission::id)
                                    : Optional.empty(),
                            visible && mission.isPresent()
                                    ? OptionalInt.of(mission.orElseThrow().deadlineTurn())
                                    : OptionalInt.empty(),
                            visible ? tribe.type().tradeProfile().map(Enum::name) : Optional.empty(),
                            visible ? (tribe.isAllianceBonusActive()
                                    ? "Active: " + strategy.allianceRewardDescription()
                                    : "Inactive: " + strategy.allianceRewardDescription()) : "",
                            visible ? mission.map(value -> objective(value.type())).orElse("") : "",
                            visible ? mission.map(value -> reward(value.type())).orElse("") : "");
                }).toList();
    }

    private static String objective(civilization.model.mission.MissionType type) {
        return switch (type) {
            case FARMER_SUPPLY -> "Deliver 20 Wood + 10 Stone";
            case TRADER_ROAD -> "Continuous Road from a player building to beside camp";
            case WARRIOR_BOUNTY -> "Defeat 2 Enemy/Barbarian units within radius 5";
            case MOUNTAIN_SUPPLY -> "Deliver 15 Wood + 10 Iron";
            case COASTAL_DOCK -> "Build a Dock within distance 4";
        };
    }

    private static String reward(civilization.model.mission.MissionType type) {
        return switch (type) {
            case FARMER_SUPPLY -> "30 Food +15 relation";
            case TRADER_ROAD -> "+10 trade points +20 relation";
            case WARRIOR_BOUNTY -> "3 Swordsmen +20 relation (cap/space required)";
            case MOUNTAIN_SUPPLY -> "20 Stone +15 relation";
            case COASTAL_DOCK -> "30 Food + one-use next-Dock discount";
        };
    }
}
