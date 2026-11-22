package civilization.server.combat;

import civilization.model.building.Building;
import civilization.model.building.BuildingStatus;
import civilization.model.building.TownHall;
import civilization.model.combat.CombatMode;
import civilization.model.combat.CombatOutcome;
import civilization.model.combat.CombatResult;
import civilization.model.combat.DamageRecord;
import civilization.model.combat.DiceResolution;
import civilization.model.random.DiceSource;
import civilization.model.unit.MilitaryUnit;
import civilization.model.unit.Unit;
import civilization.model.unit.UnitId;
import civilization.model.unit.UnitType;
import civilization.rules.combat.CombatResolver;
import civilization.rules.combat.DamagePriorityChain;
import civilization.server.diplomacy.PlayerDiplomaticRelation;
import civilization.server.match.MatchException;
import civilization.server.match.MultiplayerGameState;
import civilization.server.match.MultiplayerPlayerState;
import civilization.server.session.PlayerId;
import civilization.server.item.MultiplayerItemService;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

// PHASE 3 - Added in Phase 3
public final class PvpCombatService {
    // PHASE 3 - Added in Phase 3
    public static final int ATTACK_AP_COST = 1;
    // PHASE 3 - Added in Phase 3
    private final CombatResolver resolver = new CombatResolver();
    // PHASE 3 - Added in Phase 3
    private final DamagePriorityChain damage = new DamagePriorityChain();
    // PHASE 3 - Added in Phase 3
    private final DiceSource injectedDice;
    // PHASE 3 - Added in Phase 3
    private final Clock clock;

    // PHASE 3 - Added in Phase 3
    public PvpCombatService() {
        this(null, Clock.systemUTC());
    }

    // PHASE 3 - Added in Phase 3
    public PvpCombatService(DiceSource dice, Clock clock) {
        this.injectedDice = dice;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // PHASE 3 - Added in Phase 3
    public PvpCombatResult attack(MultiplayerGameState state, PlayerId attackerPlayerId,
                                  List<String> attackerUnitIds, String targetId) {
        Objects.requireNonNull(state, "state");
        MultiplayerPlayerState attacker = state.requirePlayer(attackerPlayerId);
        List<MilitaryUnit> attackers = resolveAttackers(attacker, attackerUnitIds);
        Target target = resolveTarget(state, attackerPlayerId, targetId);
        // PHASE 3 - Added in Phase 3
        validateVisibility(state, attackerPlayerId, target.position());
        // PHASE 3 - Added in Phase 3
        validateDiplomacy(state, attackerPlayerId, target.owner().playerId());
        validateRangeAndPosition(attackers, target.position());
        validateAttackerAp(attackers);
        // PHASE 3 - Added in Phase 3
        PvpCombatResult result = target.building() == null
                ? attackUnits(state, attacker, attackers, target)
                : attackStructure(state, attacker, attackers, target);
        state.queueBattleReport(target.owner().playerId(), result.report());
        return result;
    }

    // PHASE 3 - Added in Phase 3
    private List<MilitaryUnit> resolveAttackers(MultiplayerPlayerState attacker,
                                                List<String> requestedIds) {
        if (requestedIds == null || requestedIds.isEmpty()) {
            throw new MatchException("ATTACKERS_REQUIRED", "At least one attacker is required");
        }
        if (new HashSet<>(requestedIds).size() != requestedIds.size()) {
            throw new MatchException("DUPLICATE_ATTACKER", "An attacker unit was listed more than once");
        }
        ArrayList<MilitaryUnit> attackers = new ArrayList<>();
        // PHASE 3 - Added in Phase 3
        for (String requestedId : requestedIds) {
            Unit unit = attacker.empire().units().stream()
                    .filter(candidate -> candidate.id().value().toString().equals(requestedId))
                    .findFirst().orElseThrow(() -> new MatchException("ATTACKER_NOT_OWNED",
                            "Every attacker must be owned by the requesting player"));
            if (!(unit instanceof MilitaryUnit military) || !unit.isActive()
                    || !unit.ownerId().equals(attacker.empire().factionId())) {
                throw new MatchException("INVALID_ATTACKER", "Only active owned military units may attack");
            }
            attackers.add(military);
        }
        return List.copyOf(attackers);
    }

    // PHASE 3 - Added in Phase 3
    private static Target resolveTarget(MultiplayerGameState state, PlayerId attackerId,
                                        String targetId) {
        MultiplayerPlayerState self = state.requirePlayer(attackerId);
        boolean friendly = self.empire().units().stream().anyMatch(unit -> id(unit).equals(targetId))
                || self.empire().allBuildings().stream().anyMatch(building -> id(building).equals(targetId));
        if (friendly) throw new MatchException("FRIENDLY_TARGET", "A player cannot attack their own assets");
        // PHASE 3 - Added in Phase 3
        for (MultiplayerPlayerState candidate : state.players().values()) {
            if (candidate.playerId().equals(attackerId)) continue;
            Unit unit = candidate.empire().units().stream()
                    .filter(value -> id(value).equals(targetId)).findFirst().orElse(null);
            if (unit != null) {
                if (!(unit instanceof MilitaryUnit) || !unit.isActive()) {
                    throw new MatchException("INVALID_TARGET", "Target is not an active military unit");
                }
                return new Target(candidate, unit, null, unit.position());
            }
            Building building = candidate.empire().allBuildings().stream()
                    .filter(value -> id(value).equals(targetId)).findFirst().orElse(null);
            if (building != null) {
                if (building.status() == BuildingStatus.DESTROYED || building.health().isDepleted()) {
                    throw new MatchException("INVALID_TARGET", "Target structure is already destroyed");
                }
                return new Target(candidate, null, building, building.position());
            }
        }
        throw new MatchException("TARGET_UNAVAILABLE", "Combat target is unavailable");
    }

    // PHASE 3 - Added in Phase 3
    private static void validateDiplomacy(MultiplayerGameState state, PlayerId attacker,
                                          PlayerId defender) {
        if (state.diplomacy().relation(attacker, defender) != PlayerDiplomaticRelation.ENEMY) {
            throw new MatchException("NOT_AT_WAR", "PvP attacks require an Enemy diplomatic relation");
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void validateVisibility(MultiplayerGameState state, PlayerId attackerId,
                                           civilization.model.map.HexCoord target) {
        // PHASE 3 - Added in Phase 3
        if (!state.isCurrentlyVisibleTo(attackerId, target)) {
            throw new MatchException("TARGET_UNAVAILABLE", "Combat target is unavailable");
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void validateRangeAndPosition(List<MilitaryUnit> attackers,
                                                 civilization.model.map.HexCoord target) {
        var origin = attackers.get(0).position();
        if (attackers.stream().anyMatch(unit -> !unit.position().equals(origin))) {
            throw new MatchException("ATTACKERS_NOT_STACKED", "All attackers must occupy one hex");
        }
        int distance = origin.distanceTo(target);
        if (distance < 1 || distance > 2
                || attackers.stream().anyMatch(unit -> !unit.canAttack(target))) {
            throw new MatchException("TARGET_OUT_OF_RANGE", "Target is outside an attacker's range");
        }
        if (distance == 2 && attackers.stream().anyMatch(unit -> unit.type() != UnitType.ARCHER)) {
            throw new MatchException("RANGED_ATTACKERS_ONLY", "Only Archers may attack at distance 2");
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void validateAttackerAp(List<MilitaryUnit> attackers) {
        if (attackers.stream().anyMatch(unit -> !unit.actionPoints().canSpend(ATTACK_AP_COST))) {
            throw new MatchException("INSUFFICIENT_AP", "Every participating attacker requires 1 AP");
        }
    }

    // PHASE 3 - Added in Phase 3
    private PvpCombatResult attackUnits(MultiplayerGameState state,
                                        MultiplayerPlayerState attacker,
                                        List<MilitaryUnit> attackers, Target target) {
        List<MilitaryUnit> defenders = target.owner().empire().units().stream()
                .filter(MilitaryUnit.class::isInstance).map(MilitaryUnit.class::cast)
                .filter(Unit::isActive).filter(unit -> unit.position().equals(target.position()))
                .toList();
        if (defenders.isEmpty()) {
            throw new MatchException("INVALID_TARGET", "Defending hex has no military unit");
        }
        // PHASE 3 - Added in Phase 3
        List<Integer> attackerBonuses = attackers.stream()
                .map(unit -> state.itemEffects().hasCombatBuff(attacker.playerId(), unit.id())
                        ? MultiplayerItemService.COMBAT_DIE_BONUS : 0).toList();
        DiceResolution dice = resolver.resolve(attackers.size(), defenders.size(),
                diceSource(state, attacker), attackerBonuses, value -> value);
        spendAp(attackers);
        ArrayList<DamageRecord> records = new ArrayList<>();
        records.addAll(damage.applyHits(dice.hitsOnAttacker(), attackers,
                attacker.empire()::removeUnit));
        records.addAll(damage.applyHits(dice.hitsOnDefender(), defenders,
                target.owner().empire()::removeUnit));
        PvpBattleReport report = report(attacker, target, dice, records, 0,
                outcome(dice).name());
        return new PvpCombatResult(CombatMode.MILITARY, ids(attackers), ids(defenders),
                dice, records, ids(attackers), report);
    }

    // PHASE 3 - Added in Phase 3
    private PvpCombatResult attackStructure(MultiplayerGameState state,
                                            MultiplayerPlayerState attacker,
                                            List<MilitaryUnit> attackers, Target target) {
        boolean defended = target.owner().empire().units().stream()
                .filter(MilitaryUnit.class::isInstance).map(MilitaryUnit.class::cast)
                .anyMatch(unit -> unit.isActive() && unit.position().equals(target.position()));
        if (defended) {
            throw new MatchException("STRUCTURE_DEFENDED",
                    "Defending military must be removed before attacking the structure");
        }
        // PHASE 3 - Added in Phase 3
        int requestedDamage = attackers.stream().mapToInt(unit -> structureDamage(unit)
                + (state.itemEffects().hasCombatBuff(attacker.playerId(), unit.id())
                ? MultiplayerItemService.COMBAT_STRUCTURE_DAMAGE_BONUS : 0)).sum();
        spendAp(attackers);
        int applied = target.building().health().damage(requestedDamage);
        boolean destroyed = target.building().health().isDepleted();
        // PHASE 3 - Added in Phase 3
        if (destroyed) {
            target.owner().empire().destroyBuilding(target.building().id());
            if (target.building() instanceof TownHall) {
                state.evaluateHallSurvival(target.owner().playerId());
            }
        }
        DamageRecord record = new DamageRecord(target.building().type().name(),
                target.building().id().value().toString(), applied,
                target.building().health().current(), destroyed);
        List<DamageRecord> records = List.of(record);
        String outcome = destroyed ? CombatOutcome.TARGET_DESTROYED.name()
                : CombatOutcome.TARGET_DAMAGED.name();
        PvpBattleReport report = report(attacker, target, CombatResult.noDice(), records,
                applied, outcome);
        return new PvpCombatResult(CombatMode.STRUCTURE, ids(attackers), List.of(),
                CombatResult.noDice(), records, ids(attackers), report);
    }

    // PHASE 3 - Added in Phase 3
    private PvpBattleReport report(MultiplayerPlayerState attacker, Target target,
                                   DiceResolution dice, List<DamageRecord> records,
                                   int structureDamage, String outcome) {
        LinkedHashMap<String, Integer> remaining = new LinkedHashMap<>();
        records.forEach(record -> remaining.put(record.entityId(), record.remainingHealth()));
        List<String> lost = records.stream().filter(DamageRecord::removed)
                .map(DamageRecord::entityId).toList();
        return new PvpBattleReport(UUID.randomUUID().toString(), attacker.playerId(),
                target.owner().playerId(), attacker.sessionId(), target.owner().sessionId(),
                target.building() == null ? id(target.unit()) : id(target.building()), outcome,
                // PHASE 3 - Added in Phase 3
                dice.attackerSortedRolls(), dice.defenderSortedRolls(), lost, structureDamage,
                remaining, Instant.now(clock));
    }

    // PHASE 3 - Added in Phase 3
    private DiceSource diceSource(MultiplayerGameState state, MultiplayerPlayerState attacker) {
        return injectedDice != null ? injectedDice
                : () -> state.activePlayerView(attacker.playerId()).gameplayRandom().nextInt(6) + 1;
    }

    // PHASE 3 - Added in Phase 3
    private static void spendAp(List<MilitaryUnit> attackers) {
        attackers.forEach(unit -> unit.actionPoints().spend(ATTACK_AP_COST));
    }

    // PHASE 3 - Added in Phase 3
    private static int structureDamage(MilitaryUnit unit) {
        return switch (unit.type()) {
            case SWORDSMAN -> 10;
            case ARCHER -> 6;
            case CAVALRY -> 9;
            default -> throw new MatchException("INVALID_ATTACKER", "Unsupported structure attacker");
        };
    }

    // PHASE 3 - Added in Phase 3
    private static List<UnitId> ids(List<MilitaryUnit> units) {
        return units.stream().map(Unit::id).toList();
    }

    // PHASE 3 - Added in Phase 3
    private static String id(Unit unit) { return unit.id().value().toString(); }
    // PHASE 3 - Added in Phase 3
    private static String id(Building building) { return building.id().value().toString(); }

    // PHASE 3 - Added in Phase 3
    private static CombatOutcome outcome(DiceResolution dice) {
        if (dice.hitsOnDefender() > dice.hitsOnAttacker()) return CombatOutcome.ATTACKER_ADVANTAGE;
        if (dice.hitsOnAttacker() > dice.hitsOnDefender()) return CombatOutcome.DEFENDER_ADVANTAGE;
        return CombatOutcome.STALEMATE;
    }

    // PHASE 3 - Added in Phase 3
    private record Target(MultiplayerPlayerState owner, Unit unit, Building building,
                          civilization.model.map.HexCoord position) {}
}
