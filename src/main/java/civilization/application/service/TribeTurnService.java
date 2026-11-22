package civilization.application.service;

import civilization.application.ActionFailure;
import civilization.application.event.EventBus;
import civilization.application.event.TribeNotificationEvent;
import civilization.application.factory.UnitFactory;
import civilization.application.notification.NotificationCategory;
import civilization.model.combat.AttackRequest;
import civilization.model.combat.AttackTarget;
import civilization.model.diplomacy.DiplomaticStance;
import civilization.model.game.GameState;
import civilization.model.map.HexCoord;
import civilization.model.map.TerrainType;
import civilization.model.mission.MissionStatus;
import civilization.model.mission.MissionType;
import civilization.model.tribe.Tribe;
import civilization.model.unit.MilitaryUnit;
import civilization.model.unit.Unit;
import civilization.rules.GameRules;
import civilization.rules.MovementCostPolicy;
import civilization.rules.combat.MilitaryStackingPolicy;
import civilization.rules.tribe.TribeStrategyCatalog;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Deterministic mandatory tribe turn. It deliberately omits optional autonomous aggression. */
public final class TribeTurnService {
    private final TribeDiscoveryService discovery;
    private final TribeMissionService missions;
    private final CombatService combat;
    private final UnitFactory units;
    private final TribeStrategyCatalog strategies;
    private final MilitaryStackingPolicy stacking = new MilitaryStackingPolicy();
    private final MovementCostPolicy movement = new MovementCostPolicy();
    private final EventBus events;

    public TribeTurnService(EventBus events) {
        this.events = Objects.requireNonNull(events, "events");
        this.discovery = new TribeDiscoveryService(events);
        this.missions = new TribeMissionService(new civilization.rules.tribe.MissionCatalog(),
                discovery, new TribeRewardService(), events);
        this.combat = new CombatService();
        this.units = new UnitFactory();
        this.strategies = new TribeStrategyCatalog();
    }

    public void process(GameState state, GameRules rules) {
        discovery.refresh(state);
        for (Tribe tribe : state.world().tribes().all()) {
            if (tribe.isDefeated()) continue;
            List<MilitaryUnit> guards = guards(state, tribe);
            guards.forEach(guard -> guard.actionPoints().refill());
            var relation = state.world().relationships().require(tribe.id());
            missions.processDeadline(state, tribe.id());
            if (relation.stance() != DiplomaticStance.ALLIANCE) {
                tribe.setAllianceBonusActive(false);
            }
            if (relation.behavior().hostile()) {
                processEnemy(state, rules, tribe, guards);
            } else if (relation.stance() == DiplomaticStance.ALLIANCE
                    && tribe.isAllianceBonusActive()) {
                applyAllianceYield(state, tribe);
            } else if (relation.behavior().intrusionPenalty() < 0) {
                processForbiddenArea(state, tribe, relation.behavior().intrusionPenalty());
            } else if (relation.behavior().missionAllowed()) {
                maybeOfferMission(state, tribe);
            }
        }
        pruneResolvedCombatEvidence(state);
    }

    private static void pruneResolvedCombatEvidence(GameState state) {
        int earliestAcceptedTurn = state.world().missions().all().stream()
                .filter(mission -> mission.type() == MissionType.WARRIOR_BOUNTY)
                .filter(mission -> mission.status() == MissionStatus.ACTIVE)
                .mapToInt(mission -> mission.acceptedTurn().orElse(mission.issuedTurn()))
                .min().orElse(Integer.MAX_VALUE);
        if (earliestAcceptedTurn == Integer.MAX_VALUE) {
            state.world().combatHistory().clear();
        } else {
            state.world().combatHistory().pruneBefore(earliestAcceptedTurn);
        }
    }

    private void processEnemy(GameState state, GameRules rules, Tribe tribe,
            List<MilitaryUnit> guards) {
        HexCoord target = tribe.lastAttackerHex().orElse(null);
        boolean liveTarget = target != null && state.player().units().stream()
                .anyMatch(unit -> unit.position().equals(target));
        if (liveTarget && !guards.isEmpty()) {
            MilitaryUnit guard = guards.stream()
                    .filter(unit -> unit.position().distanceTo(target) <= unit.attackRange())
                    .findFirst().orElse(null);
            if (guard != null) {
                try {
                    combat.hostileAttack(state,
                            new AttackRequest(guard.position(), target, AttackTarget.AUTO),
                            tribe.id().asFactionId());
                    notify(state, tribe, NotificationCategory.COMBAT,
                            tribe.name() + " attacked in response to aggression", target);
                } catch (ActionFailure ignored) {
                    // An AP-less defender or changed target safely suppresses this turn's response.
                }
                return;
            }
            var mover = guards.stream().filter(unit -> unit.actionPoints().current() > 0)
                    .min(Comparator.comparingInt(unit -> unit.position().distanceTo(target)))
                    .orElse(null);
            if (mover != null && moveOneStepToward(state, rules, mover, target)) {
                if (mover.position().distanceTo(target) <= mover.attackRange()
                        && mover.actionPoints().current() >= CombatService.ATTACK_AP_COST) {
                    try {
                        combat.hostileAttack(state,
                                new AttackRequest(mover.position(), target, AttackTarget.AUTO),
                                tribe.id().asFactionId());
                        notify(state, tribe, NotificationCategory.COMBAT,
                                tribe.name() + " moved and counterattacked", target);
                    } catch (ActionFailure ignored) { }
                }
                return;
            }
        }
        if (state.turn().turnNumber() % 3 == 0 && guards.size() < tribe.type().guardCap()) {
            spawnGuard(state, rules, tribe);
        }
    }

    private void processForbiddenArea(GameState state, Tribe tribe, int penalty) {
        for (Unit unit : state.player().units()) {
            if (!(unit instanceof MilitaryUnit)) continue;
            if (!tribe.forbiddenArea().contains(unit.position())) {
                tribe.clearIntruder(unit.id());
                continue;
            }
            Optional<Integer> warned = tribe.warnedOnTurn(unit.id());
            if (warned.isEmpty()) {
                tribe.warnIntruder(unit.id(), state.turn().turnNumber());
                notify(state, tribe, NotificationCategory.DIPLOMACY,
                        "Warning: leave " + tribe.name() + " forbidden area", unit.position());
            } else if (warned.orElseThrow() < state.turn().turnNumber()) {
                state.world().relationships().require(tribe.id()).adjustScore(penalty);
                tribe.warnIntruder(unit.id(), state.turn().turnNumber());
                notify(state, tribe, NotificationCategory.DIPLOMACY,
                        "Continued intrusion changed relationship by " + penalty, unit.position());
            }
        }
    }

    private void maybeOfferMission(GameState state, Tribe tribe) {
        if (!tribe.isDiscovered()
                || !state.player().visibility().isCurrentlyVisible(tribe.camp().position())) return;
        if (tribe.lastMissionOfferTurn() != Integer.MIN_VALUE
                && state.turn().turnNumber() - tribe.lastMissionOfferTurn() < 5) return;
        if (missions.previewOffer(state, tribe.id()).allowed()) missions.offer(state, tribe.id());
    }

    private void applyAllianceYield(GameState state, Tribe tribe) {
        var yield = strategies.require(tribe.type()).alliedYieldPerTurn();
        if (!yield.isZero()) state.player().townHall().stockpile().credit(yield);
    }

    private void spawnGuard(GameState state, GameRules rules, Tribe tribe) {
        if (guards(state, tribe).size() >= tribe.type().guardCap()) return;
        var type = strategies.require(tribe.type()).producedGuardType();
        HexCoord spawn = tribe.territory().stream()
                .filter(coord -> state.map().requireTile(coord).terrain() != TerrainType.SEA)
                .filter(coord -> state.map().requireTile(coord).terrain() != TerrainType.MOUNTAIN_RANGE)
                .sorted(Comparator.comparingInt((HexCoord coord) ->
                                coord.distanceTo(tribe.camp().position()))
                        .thenComparingInt(HexCoord::q).thenComparingInt(HexCoord::r))
                .filter(coord -> stacking.allows(state.world().units().at(coord), type))
                .findFirst().orElse(null);
        if (spawn == null) return;
        state.world().units().add(units.create(
                civilization.model.unit.UnitId.from(state.gameplayRandom()),
                type, spawn, rules, tribe.id().asFactionId()), state.map());
        notify(state, tribe, NotificationCategory.COMBAT,
                tribe.name() + " created a defensive guard", spawn);
    }

    private boolean moveOneStepToward(GameState state, GameRules rules,
            MilitaryUnit unit, HexCoord target) {
        HexCoord destination = state.map().neighborsOf(unit.position()).stream()
                .map(tile -> tile.coordinate())
                .filter(coord -> state.map().requireTile(coord).terrain() != TerrainType.SEA)
                .filter(coord -> state.map().requireTile(coord).terrain() != TerrainType.MOUNTAIN_RANGE)
                .filter(coord -> stacking.allows(state.world().units().at(coord), unit.type()))
                .filter(coord -> movement.cost(state, unit, coord, rules)
                        <= unit.actionPoints().current())
                .min(Comparator.comparingInt((HexCoord coord) -> coord.distanceTo(target))
                        .thenComparingInt(HexCoord::q).thenComparingInt(HexCoord::r))
                .orElse(null);
        if (destination == null || destination.distanceTo(target) >= unit.position().distanceTo(target)) {
            return false;
        }
        unit.actionPoints().spend(movement.cost(state, unit, destination, rules));
        unit.moveTo(destination);
        return true;
    }

    private static List<MilitaryUnit> guards(GameState state, Tribe tribe) {
        return state.world().units().all().stream()
                .filter(MilitaryUnit.class::isInstance).map(MilitaryUnit.class::cast)
                .filter(unit -> unit.ownerId().equals(tribe.id().asFactionId())).toList();
    }

    private void notify(GameState state, Tribe tribe, NotificationCategory category,
            String message, HexCoord location) {
        events.publish(new TribeNotificationEvent(tribe.id(), state.turn().turnNumber(),
                category, message, Optional.ofNullable(location)));
    }
}
