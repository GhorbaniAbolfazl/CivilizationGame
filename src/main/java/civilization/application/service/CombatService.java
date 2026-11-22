package civilization.application.service;

import civilization.application.ActionFailure;
import civilization.application.action.ActionPreview;
import civilization.application.validation.ValidationSupport;
import civilization.model.building.Building;
import civilization.model.combat.AttackRequest;
import civilization.model.combat.AttackTarget;
import civilization.model.combat.CombatMode;
import civilization.model.combat.CombatOutcome;
import civilization.model.combat.CombatResult;
import civilization.model.combat.DamageRecord;
import civilization.model.combat.DiceResolution;
import civilization.model.combat.CombatKillRecord;
import civilization.model.game.GameState;
import civilization.model.identity.FactionId;
import civilization.model.infrastructure.Wall;
import civilization.model.map.HexEdge;
import civilization.model.random.DiceSource;
import civilization.model.unit.MilitaryUnit;
import civilization.model.unit.Unit;
import civilization.model.unit.UnitId;
import civilization.model.unit.UnitType;
import civilization.model.world.WildAnimal;
import civilization.model.world.WildAnimalId;
import civilization.model.world.WorldStructure;
import civilization.rules.combat.CombatResolver;
import civilization.rules.combat.DamagePriorityChain;
import civilization.rules.combat.MilitaryStackingPolicy;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;

/** Swing-free combat transaction coordinator. Validation precedes every state mutation. */
public final class CombatService {
    public static final int ATTACK_AP_COST = 1;
    public static final int WALL_DEFENSE_BONUS = 2;

    private final CombatResolver resolver = new CombatResolver();
    private final DamagePriorityChain damageChain = new DamagePriorityChain();
    private final MilitaryStackingPolicy stackingPolicy = new MilitaryStackingPolicy();
    private final DiceSource injectedDice;
    private final TribeInteractionService tribeInteractions = new TribeInteractionService();
    private final TribeDefeatService tribeDefeats = new TribeDefeatService();
    private final StructureLifecycleService structureLifecycle = new StructureLifecycleService();

    public CombatService() {
        this.injectedDice = null;
    }

    public CombatService(DiceSource diceSource) {
        this.injectedDice = Objects.requireNonNull(diceSource, "diceSource");
    }

    public CombatResult playerAttack(GameState state, AttackRequest request) {
        Objects.requireNonNull(state, "state");
        ValidationSupport.requireAllowed(previewPlayerAttack(state, request));
        ValidationSupport.requireAllowed(ValidationSupport.playerActionPhase(state));
        validateCoordinates(state, request, true);
        int distance = request.attackingHex().distanceTo(request.defendingHex());
        List<MilitaryUnit> attackers = participants(playerMilitaryAt(state, request.attackingHex()), distance);
        validateAttackers(attackers, request.attackingHex());

        List<MilitaryUnit> defenders = worldMilitaryAt(state, request.defendingHex(), null);
        Optional<WildAnimal> animal = state.world().wildAnimals().at(request.defendingHex());
        Optional<WorldStructure> structure = state.world().structures().at(request.defendingHex());
        List<Unit> civilians = worldCiviliansAt(state, request.defendingHex(), null);
        Optional<Wall> wall = hostileWall(state, request, FactionId.PLAYER);

        if (request.target() == AttackTarget.WALL) {
            registerTribeAttack(state, wall.orElseThrow().ownerId(), request.attackingHex());
            return damageWall(state, request, attackers, wall.orElseThrow(
                    () -> new ActionFailure("No hostile Wall lies between the selected hexes")));
        }
        if (!defenders.isEmpty()) {
            if (request.target() == AttackTarget.STRUCTURE) {
                throw new ActionFailure("Defending military must be removed before the structure");
            }
            validateCombatAp(defenders, "Every participating defender requires 1 AP");
            registerTribeAttack(state, defenders.get(0).ownerId(), request.attackingHex());
            return resolveMilitary(state, request, attackers, defenders, true,
                    wall.isPresent(), CombatMode.MILITARY);
        }
        if (animal.isPresent()) {
            if (request.target() == AttackTarget.STRUCTURE) {
                throw new ActionFailure("A Wild Animal is the current combat target");
            }
            return resolvePlayerAgainstAnimal(state, request, attackers, animal.orElseThrow());
        }
        if (structure.isPresent()) {
            registerTribeAttack(state, structure.orElseThrow().ownerId(), request.attackingHex());
            return damageWorldStructure(state, request, attackers, structure.orElseThrow());
        }
        if (wall.isPresent()) {
            registerTribeAttack(state, wall.orElseThrow().ownerId(), request.attackingHex());
            return damageWall(state, request, attackers, wall.orElseThrow());
        }
        if (!civilians.isEmpty()) {
            if (!request.attackingHex().isAdjacentTo(request.defendingHex())) {
                throw new ActionFailure("Civilian capture requires an adjacent attack");
            }
            registerTribeAttack(state, civilians.get(0).ownerId(), request.attackingHex());
            return resolveCivilianTarget(state, request, attackers, civilians, state.world().units()::remove);
        }
        if (!request.attackingHex().isAdjacentTo(request.defendingHex())) {
            throw new ActionFailure("Only an adjacent empty hostile hex can be captured");
        }
        FactionId emptyOwner = state.world().territories().ownerAt(request.defendingHex())
                .orElseThrow(() -> new ActionFailure(
                        "Unowned land requires a Border Expander, not a combat attack"));
        if (emptyOwner.equals(FactionId.PLAYER)) {
            throw new ActionFailure("Cannot attack friendly territory");
        }
        registerTribeAttack(state, emptyOwner, request.attackingHex());
        return captureEmptyHostileHex(state, request, attackers);
    }

    /** Read-only validation used by both Swing previews and the committing command. */
    public ActionPreview previewPlayerAttack(GameState state, AttackRequest request) {
        try {
            Objects.requireNonNull(state,"state");
            ActionPreview phase=ValidationSupport.playerActionPhase(state);
            if(!phase.allowed()) return phase;
            validateCoordinates(state,request,true);
            int distance=request.attackingHex().distanceTo(request.defendingHex());
            List<MilitaryUnit> attackers=participants(playerMilitaryAt(state,request.attackingHex()),distance);
            validateAttackers(attackers,request.attackingHex());
            List<MilitaryUnit> defenders=worldMilitaryAt(state,request.defendingHex(),null);
            Optional<WildAnimal> animal=state.world().wildAnimals().at(request.defendingHex());
            Optional<WorldStructure> structure=state.world().structures().at(request.defendingHex());
            List<Unit> civilians=worldCiviliansAt(state,request.defendingHex(),null);
            Optional<Wall> wall=hostileWall(state,request,FactionId.PLAYER);
            if(request.target()==AttackTarget.WALL) {
                if(wall.isEmpty()) return ActionPreview.reject("No hostile Wall lies between the selected hexes");
                if(distance!=1) return ActionPreview.reject("A Wall can only be targeted across its adjacent edge");
                return ActionPreview.allow();
            }
            if(!defenders.isEmpty()) {
                if(request.target()==AttackTarget.STRUCTURE)
                    return ActionPreview.reject("Defending military must be removed before the structure");
                validateCombatAp(defenders,"Every participating defender requires 1 AP");
                return ActionPreview.allow();
            }
            if(animal.isPresent()) return request.target()==AttackTarget.STRUCTURE
                    ? ActionPreview.reject("A Wild Animal is the current combat target")
                    : ActionPreview.allow();
            if(structure.isPresent() || wall.isPresent()) return ActionPreview.allow();
            if(!civilians.isEmpty()) return distance==1 ? ActionPreview.allow()
                    : ActionPreview.reject("Civilian capture requires an adjacent attack");
            if(request.target()==AttackTarget.STRUCTURE)
                return ActionPreview.reject("No hostile structure occupies the defending hex");
            if(distance!=1) return ActionPreview.reject("Only an adjacent empty hostile hex can be captured");
            FactionId owner=state.world().territories().ownerAt(request.defendingHex())
                    .orElse(null);
            if(owner==null) return ActionPreview.reject("Unowned land requires a Border Expander, not a combat attack");
            return owner.equals(FactionId.PLAYER) ? ActionPreview.reject("Cannot attack friendly territory")
                    : ActionPreview.allow();
        } catch(ActionFailure|IllegalArgumentException|IllegalStateException failure) {
            return ActionPreview.reject(failure.getMessage());
        }
    }

    /** Resolves an explicit barbarian/tribe attack against player-owned entities. */
    public CombatResult hostileAttack(GameState state, AttackRequest request, FactionId attacker) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(attacker, "attacker");
        if (attacker.equals(FactionId.PLAYER) || attacker.equals(FactionId.WILD_ANIMALS)) {
            throw new IllegalArgumentException("hostileAttack requires a non-player military faction");
        }
        validateCoordinates(state, request, false);
        int distance = request.attackingHex().distanceTo(request.defendingHex());
        List<MilitaryUnit> attackers = participants(
                worldMilitaryAt(state, request.attackingHex(), attacker), distance);
        validateAttackers(attackers, request.attackingHex());
        List<MilitaryUnit> defenders = playerMilitaryAt(state, request.defendingHex());
        Optional<Wall> wall = playerWall(state, request);
        if (request.target() == AttackTarget.WALL) {
            return damageWall(state, request, attackers, wall.orElseThrow(
                    () -> new ActionFailure("No player Wall lies between the selected hexes")));
        }
        if (!defenders.isEmpty()) {
            if (request.target() == AttackTarget.STRUCTURE) {
                throw new ActionFailure("Defending military must be removed before the structure");
            }
            return resolveMilitary(state, request, attackers, defenders, false,
                    wall.isPresent(), CombatMode.MILITARY);
        }
        Optional<Building> building = state.player().buildingAt(request.defendingHex());
        if (building.isPresent()) {
            return damagePlayerBuilding(state, request, attackers, building.orElseThrow());
        }
        if (wall.isPresent()) return damageWall(state, request, attackers, wall.orElseThrow());
        List<Unit> civilians = playerCiviliansAt(state, request.defendingHex());
        if (!civilians.isEmpty()) {
            return resolveCivilianTarget(state, request, attackers, civilians, state.player()::removeUnit);
        }
        throw new ActionFailure("The defending hex has no hostile combat target");
    }

    /** Mandatory defensive case: one animal die versus two player defense dice. */
    public CombatResult wildAnimalAttack(GameState state, WildAnimalId animalId) {
        Objects.requireNonNull(state, "state");
        WildAnimal animal = state.world().wildAnimals().require(animalId);
        var request = new AttackRequest(animal.position(), animal.position(), AttackTarget.AUTO);
        List<MilitaryUnit> defenders = playerMilitaryAt(state, animal.position());
        if (!defenders.isEmpty()) {
            validateCombatAp(defenders, "Every participating defender requires 1 AP");
            DiceResolution dice = resolver.resolve(1, 2, diceSource(state));
            spendAttackAp(defenders);
            List<DamageRecord> damage = new ArrayList<>();
            if (dice.hitsOnDefender() > 0) {
                damage.addAll(damageChain.applyHits(dice.hitsOnDefender(), defenders,
                        state.player()::removeUnit));
            }
            if (dice.hitsOnAttacker() > 0) {
                int applied = animal.health().damage(dice.hitsOnAttacker());
                boolean removed = animal.health().isDepleted();
                if (removed) state.world().wildAnimals().remove(animal.id());
                damage.add(new DamageRecord("WILD_ANIMAL", animal.id().value().toString(),
                        applied, animal.health().current(), removed));
            }
            CombatOutcome outcome = outcome(dice);
            state.incrementVersion();
            return result(request, CombatMode.WILD_ANIMAL, List.of(), defenders, dice,
                    damage, ids(defenders), outcome, false);
        }

        List<Unit> civilians = playerCiviliansAt(state, animal.position());
        if (civilians.isEmpty()) {
            throw new ActionFailure("Wild Animal has no player target on its hex");
        }
        List<DamageRecord> damage = new ArrayList<>();
        for (Unit civilian : List.copyOf(civilians)) {
            state.player().removeUnit(civilian.id());
            damage.add(new DamageRecord(civilian.type().name(), civilian.id().value().toString(),
                    1, 0, true));
        }
        state.incrementVersion();
        return result(request, CombatMode.CIVILIAN, List.of(), List.of(), CombatResult.noDice(),
                damage, List.of(), CombatOutcome.ATTACKER_ADVANTAGE, false);
    }

    private CombatResult resolveMilitary(GameState state, AttackRequest request,
            List<MilitaryUnit> attackers, List<MilitaryUnit> defenders, boolean playerAttacks,
            boolean protectedByWall, CombatMode mode) {
        int distance = request.attackingHex().distanceTo(request.defendingHex());
        int attackDice = distance == 2 ? 1 : distinctTypeCount(attackers);
        int defenseDice = 2;
        validateCombatAp(defenders, "Every participating defender requires 1 AP");
        IntUnaryOperator modifier = protectedByWall
                ? value -> Math.min(6, value + WALL_DEFENSE_BONUS) : value -> value;
        DiceResolution dice = resolver.resolve(attackDice, defenseDice, diceSource(state), modifier);
        spendAttackAp(attackers);
        spendAttackAp(defenders);
        List<DamageRecord> damage = new ArrayList<>();
        Consumer<UnitId> removeAttacker = playerAttacks
                ? state.player()::removeUnit : state.world().units()::remove;
        Consumer<UnitId> removeDefender = playerAttacks
                ? state.world().units()::remove : state.player()::removeUnit;
        damage.addAll(damageChain.applyHits(dice.hitsOnAttacker(), attackers, removeAttacker));
        damage.addAll(damageChain.applyHits(dice.hitsOnDefender(), defenders, removeDefender));
        if (playerAttacks) recordWorldKills(state, defenders, damage, request.defendingHex());
        state.incrementVersion();
        List<UnitId> apSpent = new ArrayList<>(ids(attackers));
        apSpent.addAll(ids(defenders));
        return result(request, mode, attackers, defenders, dice, damage, apSpent,
                outcome(dice), false);
    }

    private CombatResult resolvePlayerAgainstAnimal(GameState state, AttackRequest request,
            List<MilitaryUnit> attackers, WildAnimal animal) {
        int distance = request.attackingHex().distanceTo(request.defendingHex());
        int attackDice = distance == 2 ? 1 : distinctTypeCount(attackers);
        DiceResolution dice = resolver.resolve(attackDice, 1, diceSource(state));
        spendAttackAp(attackers);
        List<DamageRecord> damage = new ArrayList<>();
        damage.addAll(damageChain.applyHits(dice.hitsOnAttacker(), attackers,
                state.player()::removeUnit));
        if (dice.hitsOnDefender() > 0) {
            int applied = animal.health().damage(dice.hitsOnDefender());
            boolean removed = animal.health().isDepleted();
            if (removed) state.world().wildAnimals().remove(animal.id());
            damage.add(new DamageRecord("WILD_ANIMAL", animal.id().value().toString(),
                    applied, animal.health().current(), removed));
        }
        state.incrementVersion();
        return result(request, CombatMode.WILD_ANIMAL, attackers, List.of(), dice,
                damage, ids(attackers), outcome(dice), false);
    }

    private CombatResult damageWorldStructure(GameState state, AttackRequest request,
            List<MilitaryUnit> attackers, WorldStructure structure) {
        requireAdjacentOrArcherRange(request, attackers);
        int damageAmount = structuralDamage(attackers);
        spendAttackAp(attackers);
        int applied = structure.health().damage(damageAmount);
        boolean removed = structure.health().isDepleted();
        if (removed) state.world().structures().remove(structure.id());
        DamageRecord record = new DamageRecord(structure.type().name(),
                structure.id().value().toString(), applied, structure.health().current(), removed);
        state.incrementVersion();
        if (removed) state.world().tribes().byCampStructure(structure.id())
                .ifPresent(tribe -> tribeDefeats.defeat(state, tribe.id()));
        return result(request, CombatMode.STRUCTURE, attackers, List.of(), CombatResult.noDice(),
                List.of(record), ids(attackers), removed ? CombatOutcome.TARGET_DESTROYED
                        : CombatOutcome.TARGET_DAMAGED, false);
    }

    private CombatResult damagePlayerBuilding(GameState state, AttackRequest request,
            List<MilitaryUnit> attackers, Building building) {
        requireAdjacentOrArcherRange(request, attackers);
        int damageAmount = structuralDamage(attackers);
        spendAttackAp(attackers);
        int applied = building.health().damage(damageAmount);
        boolean removed = building.health().isDepleted();
        if (removed) {
            structureLifecycle.destroyDepletedBuilding(state, building.id());
        }
        DamageRecord record = new DamageRecord(building.type().name(),
                building.id().value().toString(), applied, building.health().current(), removed);
        state.incrementVersion();
        return result(request, CombatMode.STRUCTURE, attackers, List.of(), CombatResult.noDice(),
                List.of(record), ids(attackers), removed ? CombatOutcome.TARGET_DESTROYED
                        : CombatOutcome.TARGET_DAMAGED, false);
    }

    private CombatResult damageWall(GameState state, AttackRequest request,
            List<MilitaryUnit> attackers, Wall wall) {
        if (request.attackingHex().distanceTo(request.defendingHex()) != 1) {
            throw new ActionFailure("A Wall can only be targeted across its adjacent edge");
        }
        int damageAmount = structuralDamage(attackers);
        spendAttackAp(attackers);
        int applied = wall.health().damage(damageAmount);
        boolean removed = wall.health().isDepleted();
        if (removed) structureLifecycle.destroyDepletedWall(state, wall.id());
        DamageRecord record = new DamageRecord("WALL", wall.id().value().toString(), applied,
                wall.health().current(), removed);
        state.incrementVersion();
        return result(request, CombatMode.WALL, attackers, List.of(), CombatResult.noDice(),
                List.of(record), ids(attackers), removed ? CombatOutcome.TARGET_DESTROYED
                        : CombatOutcome.TARGET_DAMAGED, false);
    }

    private CombatResult resolveCivilianTarget(GameState state, AttackRequest request,
            List<MilitaryUnit> attackers, List<Unit> civilians, Consumer<UnitId> remove) {
        if (request.attackingHex().distanceTo(request.defendingHex()) != 1) {
            throw new ActionFailure("Civilian capture requires an adjacent attack");
        }
        Unit target = civilians.stream().min((left, right) ->
                left.id().value().compareTo(right.id().value())).orElseThrow();
        spendAttackAp(attackers);
        remove.accept(target.id());
        if (!target.ownerId().equals(FactionId.PLAYER)) {
            state.world().combatHistory().recordKill(new CombatKillRecord(target.id(),
                    target.ownerId(), request.defendingHex(), state.turn().turnNumber()));
        }
        DamageRecord record = new DamageRecord(target.type().name(),
                target.id().value().toString(), 1, 0, true);
        state.incrementVersion();
        return result(request, CombatMode.CIVILIAN, attackers, List.of(), CombatResult.noDice(),
                List.of(record), ids(attackers), CombatOutcome.ATTACKER_ADVANTAGE, false);
    }

    private CombatResult captureEmptyHostileHex(GameState state, AttackRequest request,
            List<MilitaryUnit> attackers) {
        if (request.target() == AttackTarget.STRUCTURE) {
            throw new ActionFailure("No hostile structure occupies the defending hex");
        }
        if (!request.attackingHex().isAdjacentTo(request.defendingHex())) {
            throw new ActionFailure("Only an adjacent empty hostile hex can be captured");
        }
        FactionId previous = state.world().territories().ownerAt(request.defendingHex())
                .orElseThrow(() -> new ActionFailure(
                        "Unowned land requires a Border Expander, not a combat attack"));
        if (previous.equals(FactionId.PLAYER)) throw new ActionFailure("Cannot attack friendly territory");
        spendAttackAp(attackers);
        state.world().territories().relinquish(request.defendingHex());
        state.player().border().claimAll(java.util.Set.of(request.defendingHex()), state.map());
        state.incrementVersion();
        return result(request, CombatMode.EMPTY_CAPTURE, attackers, List.of(), CombatResult.noDice(),
                List.of(), ids(attackers), CombatOutcome.HEX_CAPTURED, true);
    }

    private static void validateCoordinates(GameState state, AttackRequest request, boolean visible) {
        Objects.requireNonNull(request, "request");
        state.map().requireTile(request.attackingHex());
        state.map().requireTile(request.defendingHex());
        if (request.attackingHex().equals(request.defendingHex())) {
            throw new ActionFailure("Attacking and defending hexes must be different");
        }
        int distance = request.attackingHex().distanceTo(request.defendingHex());
        if (distance < 1 || distance > 2) throw new ActionFailure("Target is outside attack range");
        if (visible && !state.player().visibility().isCurrentlyVisible(request.defendingHex())) {
            throw new ActionFailure("The defending hex is not currently visible");
        }
    }

    private void validateAttackers(List<MilitaryUnit> attackers, civilization.model.map.HexCoord hex) {
        if (attackers.isEmpty()) throw new ActionFailure("No eligible military unit occupies " + hex);
        if (!stackingPolicy.isLegal(attackers)) throw new ActionFailure("Attacking hex exceeds stack limits");
        validateCombatAp(attackers, "Every participating attacker requires 1 AP");
    }

    private static void validateCombatAp(List<MilitaryUnit> participants, String message) {
        if (participants.stream().anyMatch(unit -> !unit.actionPoints().canSpend(ATTACK_AP_COST))) {
            throw new ActionFailure(message);
        }
    }

    private static void requireAdjacentOrArcherRange(
            AttackRequest request, List<MilitaryUnit> attackers) {
        int distance = request.attackingHex().distanceTo(request.defendingHex());
        if (distance == 2 && attackers.stream().anyMatch(unit -> unit.type() != UnitType.ARCHER)) {
            throw new ActionFailure("Only Archers participate at distance 2");
        }
    }

    private static List<MilitaryUnit> participants(List<MilitaryUnit> occupants, int distance) {
        if (distance == 1) return List.copyOf(occupants);
        return occupants.stream().filter(unit -> unit.type() == UnitType.ARCHER).toList();
    }

    private static List<MilitaryUnit> playerMilitaryAt(GameState state,
            civilization.model.map.HexCoord coordinate) {
        return state.player().units().stream().filter(MilitaryUnit.class::isInstance)
                .map(MilitaryUnit.class::cast).filter(unit -> unit.position().equals(coordinate)).toList();
    }

    private static List<MilitaryUnit> worldMilitaryAt(GameState state,
            civilization.model.map.HexCoord coordinate, FactionId owner) {
        return state.world().units().at(coordinate).stream().filter(MilitaryUnit.class::isInstance)
                .map(MilitaryUnit.class::cast)
                .filter(unit -> owner == null || unit.ownerId().equals(owner)).toList();
    }

    private static List<Unit> playerCiviliansAt(GameState state,
            civilization.model.map.HexCoord coordinate) {
        return state.player().units().stream().filter(unit -> !(unit instanceof MilitaryUnit))
                .filter(unit -> unit.position().equals(coordinate)).toList();
    }

    private static List<Unit> worldCiviliansAt(GameState state,
            civilization.model.map.HexCoord coordinate, FactionId owner) {
        return state.world().units().at(coordinate).stream()
                .filter(unit -> !(unit instanceof MilitaryUnit))
                .filter(unit -> owner == null || unit.ownerId().equals(owner)).toList();
    }

    private static Optional<Wall> hostileWall(GameState state, AttackRequest request,
            FactionId friendly) {
        if (!request.attackingHex().isAdjacentTo(request.defendingHex())) return Optional.empty();
        return state.world().infrastructure()
                .on(new HexEdge(request.attackingHex(), request.defendingHex()))
                .filter(Wall.class::isInstance).map(Wall.class::cast)
                .filter(wall -> !wall.ownerId().equals(friendly));
    }

    private static Optional<Wall> playerWall(GameState state, AttackRequest request) {
        if (!request.attackingHex().isAdjacentTo(request.defendingHex())) return Optional.empty();
        return state.world().infrastructure()
                .on(new HexEdge(request.attackingHex(), request.defendingHex()))
                .filter(Wall.class::isInstance).map(Wall.class::cast)
                .filter(wall -> wall.ownerId().equals(FactionId.PLAYER));
    }

    private DiceSource diceSource(GameState state) {
        return injectedDice != null ? injectedDice : () -> state.gameplayRandom().nextInt(6) + 1;
    }

    private static int distinctTypeCount(List<MilitaryUnit> units) {
        EnumSet<UnitType> types = EnumSet.noneOf(UnitType.class);
        units.forEach(unit -> types.add(unit.type()));
        return types.size();
    }

    private static int structuralDamage(List<MilitaryUnit> attackers) {
        return attackers.stream().mapToInt(unit -> switch (unit.type()) {
            case SWORDSMAN -> 10;
            case ARCHER -> 6;
            case CAVALRY -> 8;
            default -> throw new IllegalStateException("Unsupported military type " + unit.type());
        }).sum();
    }

    private static void spendAttackAp(List<MilitaryUnit> attackers) {
        attackers.forEach(unit -> unit.actionPoints().spend(ATTACK_AP_COST));
    }

    private static List<UnitId> ids(List<MilitaryUnit> units) {
        return units.stream().map(Unit::id).toList();
    }

    private static CombatOutcome outcome(DiceResolution dice) {
        if (dice.hitsOnDefender() > dice.hitsOnAttacker()) return CombatOutcome.ATTACKER_ADVANTAGE;
        if (dice.hitsOnAttacker() > dice.hitsOnDefender()) return CombatOutcome.DEFENDER_ADVANTAGE;
        return CombatOutcome.STALEMATE;
    }

    private void registerTribeAttack(GameState state, FactionId owner,
            civilization.model.map.HexCoord attackerHex) {
        state.world().tribes().byFaction(owner)
                .ifPresent(tribe -> tribeInteractions.registerPlayerAttack(
                        state, tribe.id(), attackerHex));
    }

    private static void recordWorldKills(GameState state, List<MilitaryUnit> defenders,
            List<DamageRecord> damage, civilization.model.map.HexCoord location) {
        Set<String> removedIds = damage.stream().filter(DamageRecord::removed)
                .map(DamageRecord::entityId).collect(java.util.stream.Collectors.toSet());
        defenders.stream().filter(unit -> removedIds.contains(unit.id().value().toString()))
                .forEach(unit -> state.world().combatHistory().recordKill(
                        new CombatKillRecord(unit.id(), unit.ownerId(), location,
                                state.turn().turnNumber())));
    }

    private static CombatResult result(AttackRequest request, CombatMode mode,
            List<MilitaryUnit> attackers, List<MilitaryUnit> defenders, DiceResolution dice,
            List<DamageRecord> damage, List<UnitId> apSpent, CombatOutcome outcome,
            boolean captured) {
        List<String> log = new ArrayList<>();
        log.add("Combat: " + request.attackingHex() + " -> " + request.defendingHex()
                + " [" + mode + "]");
        if (!dice.attackerRawRolls().isEmpty() || !dice.defenderRawRolls().isEmpty()) {
            log.add("Raw rolls - attack " + dice.attackerRawRolls()
                    + ", defense " + dice.defenderRawRolls());
            log.add("Sorted rolls - attack " + dice.attackerSortedRolls()
                    + ", defense " + dice.defenderSortedRolls());
            dice.pairs().forEach(pair -> log.add(pair.attackerRoll() + " vs "
                    + pair.defenderRoll() + ": " + pair.winner()));
        }
        damage.forEach(item -> log.add(item.entityKind() + " " + item.entityId()
                + " took " + item.damage() + " damage; HP " + item.remainingHealth()
                + (item.removed() ? " (removed)" : "")));
        if (captured) log.add("Defending hex captured");
        log.add("Outcome: " + outcome);
        return new CombatResult(request, mode, ids(attackers), ids(defenders), dice,
                damage, apSpent, outcome, captured, log);
    }
}
