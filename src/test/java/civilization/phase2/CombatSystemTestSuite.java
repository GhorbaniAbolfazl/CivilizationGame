package civilization.phase2;

import civilization.application.service.CombatService;
import civilization.application.service.MovementService;
import civilization.application.service.VisibilityService;
import civilization.application.turn.TurnContext;
import civilization.application.turn.WildlifeCombatStep;
import civilization.model.building.BuildingId;
import civilization.model.building.TownHall;
import civilization.model.building.ProductionBuilding;
import civilization.model.building.BuildingType;
import civilization.model.combat.AttackRequest;
import civilization.model.combat.AttackTarget;
import civilization.model.combat.CombatMode;
import civilization.model.combat.CombatOutcome;
import civilization.model.economy.ResourceBundle;
import civilization.model.economy.ResourceStockpile;
import civilization.model.game.CrisisState;
import civilization.model.game.GameState;
import civilization.model.game.TurnState;
import civilization.model.identity.FactionId;
import civilization.model.infrastructure.InfrastructureId;
import civilization.model.infrastructure.Wall;
import civilization.model.map.GameMap;
import civilization.model.map.HexCoord;
import civilization.model.map.HexEdge;
import civilization.model.map.HexTile;
import civilization.model.map.TerrainType;
import civilization.model.map.VisibilityState;
import civilization.model.player.EmpireBorder;
import civilization.model.player.PlayerState;
import civilization.model.player.UnitCapacity;
import civilization.model.random.DiceSource;
import civilization.model.technology.ProductionQueue;
import civilization.model.technology.TechnologyState;
import civilization.model.unit.ActionPoints;
import civilization.model.unit.ArcherUnit;
import civilization.model.unit.CavalryUnit;
import civilization.model.unit.MilitaryUnit;
import civilization.model.unit.SwordsmanUnit;
import civilization.model.unit.Unit;
import civilization.model.unit.UnitId;
import civilization.model.unit.WorkerUnit;
import civilization.model.world.WildAnimal;
import civilization.model.world.WildAnimalId;
import civilization.model.world.WorldStructure;
import civilization.model.world.WorldStructureId;
import civilization.model.world.WorldStructureType;
import civilization.rules.DefaultRulesetFactory;
import civilization.rules.combat.CombatResolver;
import civilization.rules.combat.DamagePriorityChain;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import civilization.view.game.CombatPanel;

/** Deterministic specification examples and boundary tests for mandatory combat. */
public final class CombatSystemTestSuite {
    private static final HexCoord CENTER = new HexCoord(0, 0);
    private static final HexCoord EAST = new HexCoord(1, 0);
    private static final HexCoord EAST_TWO = new HexCoord(2, 0);
    private static final FactionId TRIBE = new FactionId("tribe-red");

    private CombatSystemTestSuite() { }

    public static void main(String[] args) {
        exactComparisonSortingAndUnmatchedDice();
        multipleTypesRollOncePerTypeAndApplyBothSidesDamage();
        rangedAttackUsesOnlyArchersAndOneDie();
        damagePriorityAndImmediateRemoval();
        structureAndTownHallUseFixedDamage();
        militaryShieldsStructuresAndDestroyedBuildingIsRemoved();
        wallCanBeTargetedAndModifiesDefense();
        emptyHostileHexIsCapturedButUnownedHexIsNot();
        apValidationIsAtomicAndSuccessfulAttackConsumesAp();
        civiliansRemainBehindMilitaryAndUseLowerPriority();
        wildAnimalDefenseUsesOneVersusTwoDice();
        hostileMilitaryAttackUsesDefensiveCombat();
        wildlifeStepRepeatsAndNeverDamagesStructures();
        movementRejectsMilitaryStackOverflow();
        combatResultContainsCompleteTrace();
        System.out.println("CombatSystemTestSuite: all checks passed");
    }

    private static void exactComparisonSortingAndUnmatchedDice() {
        var resolver = new CombatResolver();
        var exact = resolver.resolve(3, 2, dice(3, 6, 1, 4, 5));
        check(exact.attackerRawRolls().equals(List.of(3, 6, 1)), "Raw attack rolls changed");
        check(exact.attackerSortedRolls().equals(List.of(6, 3, 1)), "Attack rolls not sorted");
        check(exact.defenderSortedRolls().equals(List.of(5, 4)), "Defense rolls not sorted");
        check(exact.hitsOnAttacker() == 1 && exact.hitsOnDefender() == 1,
                "Worked example comparison is wrong");
        check(exact.pairs().size() == 2, "Unmatched die incorrectly produced a comparison");

        var equal = resolver.resolve(1, 1, dice(4, 4));
        var greater = resolver.resolve(1, 1, dice(5, 4));
        var lower = resolver.resolve(1, 1, dice(2, 4));
        check(equal.hitsOnAttacker() == 1 && equal.hitsOnDefender() == 0,
                "Equal dice did not favor defender");
        check(greater.hitsOnDefender() == 1, "Greater attack die did not hit defender");
        check(lower.hitsOnAttacker() == 1, "Lower attack die did not hit attacker");
    }

    private static void multipleTypesRollOncePerTypeAndApplyBothSidesDamage() {
        Scenario s = scenario();
        MilitaryUnit sword = addPlayerSword(s, CENTER, 2);
        MilitaryUnit archer = addPlayerArcher(s, CENTER, 2);
        MilitaryUnit cavalry = addPlayerCavalry(s, CENTER, 4);
        MilitaryUnit enemySword = addWorldSword(s, EAST, TRIBE, 2);
        MilitaryUnit enemyArcher = addWorldArcher(s, EAST, TRIBE, 2);
        var result = new CombatService(dice(3, 6, 1, 4, 5)).playerAttack(
                s.state, new AttackRequest(CENTER, EAST));
        check(result.dice().attackerRawRolls().size() == 3,
                "Combat did not roll once per participating type");
        check(enemyArcher.actionPoints().current() == 1,
                "Surviving involved defender did not spend 1 AP");
        check(!containsPlayer(s, sword) && containsPlayer(s, archer) && containsPlayer(s, cavalry),
                "Attacker hit did not follow Swordsman-first priority");
        check(!containsWorld(s, enemySword) && containsWorld(s, enemyArcher),
                "Defender hit did not remove its Swordsman first");
    }

    private static void rangedAttackUsesOnlyArchersAndOneDie() {
        Scenario s = scenario();
        MilitaryUnit sword = addPlayerSword(s, CENTER, 2);
        MilitaryUnit first = addPlayerArcher(s, CENTER, 2);
        MilitaryUnit second = addPlayerArcher(s, CENTER, 2);
        addWorldSword(s, EAST_TWO, TRIBE, 2);
        var result = new CombatService(dice(6, 1, 1)).playerAttack(
                s.state, new AttackRequest(CENTER, EAST_TWO));
        check(result.dice().attackerRawRolls().size() == 1,
                "Two Archers produced more than the mandatory one ranged die");
        check(result.participatingAttackers().size() == 2,
                "Both Archers were not recorded as ranged participants");
        check(first.actionPoints().current() == 1 && second.actionPoints().current() == 1,
                "Participating Archers did not each spend AP");
        check(sword.actionPoints().current() == 2,
                "Adjacent-only Swordsman spent AP in ranged combat");
        check(sword.position().equals(CENTER), "Ranged combat moved a logical unit");
    }

    private static void damagePriorityAndImmediateRemoval() {
        Scenario s = scenario();
        MilitaryUnit sword = addPlayerSword(s, CENTER, 2);
        MilitaryUnit archer = addPlayerArcher(s, CENTER, 2);
        MilitaryUnit cavalry = addPlayerCavalry(s, CENTER, 4);
        var records = new DamagePriorityChain().applyHits(2,
                List.of(cavalry, archer, sword), s.state.player()::removeUnit);
        check(records.get(0).entityKind().equals("SWORDSMAN")
                        && records.get(1).entityKind().equals("ARCHER"),
                "Damage Chain did not apply Sword, Archer, Cavalry priority");
        check(!containsPlayer(s, sword) && !containsPlayer(s, archer) && containsPlayer(s, cavalry),
                "Zero-HP units remained in the active player collection");
    }

    private static void structureAndTownHallUseFixedDamage() {
        Scenario playerAttack = scenario();
        addPlayerSword(playerAttack, CENTER, 2);
        addPlayerArcher(playerAttack, CENTER, 2);
        WorldStructure camp = new WorldStructure(WorldStructureId.random(),
                WorldStructureType.TRIBE_CAMP, TRIBE, EAST, 30);
        playerAttack.state.world().structures().add(camp, playerAttack.state.map());
        var result = new CombatService(dice()).playerAttack(playerAttack.state,
                new AttackRequest(CENTER, EAST, AttackTarget.STRUCTURE));
        check(result.mode() == CombatMode.STRUCTURE && camp.health().current() == 14,
                "Swordsman 10 + Archer 6 fixed structure damage is wrong");
        check(result.dice().pairs().isEmpty(), "Structure combat incorrectly rolled dice");

        Scenario hostile = scenario();
        MilitaryUnit barbarian = addWorldSword(hostile, EAST, FactionId.BARBARIANS, 2);
        int hallBefore = hostile.state.player().townHall().health().current();
        var hallResult = new CombatService(dice()).hostileAttack(hostile.state,
                new AttackRequest(EAST, CENTER, AttackTarget.STRUCTURE), FactionId.BARBARIANS);
        check(hostile.state.player().townHall().health().current() == hallBefore - 10,
                "Hostile fixed damage did not reach Town Hall HP");
        check(barbarian.actionPoints().current() == 1 && hallResult.damage().size() == 1,
                "Town Hall attack AP/result is incomplete");
    }

    private static void wallCanBeTargetedAndModifiesDefense() {
        Scenario bonus = scenario();
        addPlayerSword(bonus, CENTER, 2);
        addWorldSword(bonus, EAST, TRIBE, 2);
        Wall wall = new Wall(new InfrastructureId(UUID.randomUUID()), new HexEdge(CENTER, EAST),
                TRIBE, new civilization.model.common.Health(10));
        bonus.state.world().infrastructure().add(wall, bonus.state.map());
        var defended = new CombatService(dice(6, 5, 1)).playerAttack(
                bonus.state, new AttackRequest(CENTER, EAST));
        check(defended.dice().defenderRawRolls().equals(List.of(5, 1))
                        && defended.dice().defenderSortedRolls().equals(List.of(6, 3)),
                "Wall +2 defense modifier/cap was not applied after raw rolls");
        check(defended.dice().hitsOnAttacker() == 1,
                "Wall-created tie did not favor defender");

        Scenario direct = scenario();
        addPlayerSword(direct, CENTER, 2);
        addWorldSword(direct, EAST, TRIBE, 2);
        Wall fragile = new Wall(new InfrastructureId(UUID.randomUUID()), new HexEdge(CENTER, EAST),
                TRIBE, new civilization.model.common.Health(10));
        direct.state.world().infrastructure().add(fragile, direct.state.map());
        var destroyed = new CombatService(dice()).playerAttack(direct.state,
                new AttackRequest(CENTER, EAST, AttackTarget.WALL));
        check(destroyed.outcome() == CombatOutcome.TARGET_DESTROYED
                        && direct.state.world().infrastructure().byId(fragile.id()).isEmpty(),
                "Direct Wall attack did not remove zero-HP Wall despite defenders");
    }

    private static void militaryShieldsStructuresAndDestroyedBuildingIsRemoved() {
        Scenario shielded = scenario();
        MilitaryUnit attacker = addPlayerSword(shielded, CENTER, 2);
        addWorldSword(shielded, EAST, TRIBE, 2);
        WorldStructure camp = new WorldStructure(WorldStructureId.random(),
                WorldStructureType.TRIBE_CAMP, TRIBE, EAST, 25);
        shielded.state.world().structures().add(camp, shielded.state.map());
        expectFailure(() -> new CombatService(dice()).playerAttack(shielded.state,
                new AttackRequest(CENTER, EAST, AttackTarget.STRUCTURE)),
                "Structure targeting bypassed defending military");
        check(camp.health().current() == 25 && attacker.actionPoints().current() == 2,
                "Rejected structure targeting mutated HP or AP");

        Scenario building = scenario();
        ProductionBuilding mill = new ProductionBuilding(BuildingId.random(),
                BuildingType.LUMBER_MILL, EAST, 1);
        mill.health().damage(90);
        building.state.player().addBuilding(mill);
        WorkerUnit worker = new WorkerUnit(id("stationed-worker"), EAST,
                new ActionPoints(4));
        worker.stationAt(mill.id());
        building.state.player().addUnit(worker);
        addWorldSword(building, EAST_TWO, FactionId.BARBARIANS, 2);
        var destroyed = new CombatService(dice()).hostileAttack(building.state,
                new AttackRequest(EAST_TWO, EAST, AttackTarget.STRUCTURE),
                FactionId.BARBARIANS);
        check(destroyed.outcome() == CombatOutcome.TARGET_DESTROYED
                        && building.state.player().buildingAt(EAST).isEmpty()
                        && !worker.isStationed(),
                "Destroyed building remained registered or retained stationed Worker");
    }

    private static void emptyHostileHexIsCapturedButUnownedHexIsNot() {
        Scenario hostile = scenario();
        addPlayerSword(hostile, CENTER, 2);
        hostile.state.world().territories().claim(EAST, TRIBE, hostile.state.map());
        var result = new CombatService(dice()).playerAttack(
                hostile.state, new AttackRequest(CENTER, EAST));
        check(result.captured() && hostile.state.player().border().owns(EAST)
                        && hostile.state.world().territories().ownerAt(EAST).isEmpty(),
                "Empty hostile target did not transfer exactly the target tile");

        Scenario unowned = scenario();
        MilitaryUnit attacker = addPlayerSword(unowned, CENTER, 2);
        expectFailure(() -> new CombatService(dice()).playerAttack(
                unowned.state, new AttackRequest(CENTER, EAST)),
                "Combat captured ordinary unowned land");
        check(attacker.actionPoints().current() == 2,
                "Rejected unowned capture consumed AP");
    }

    private static void apValidationIsAtomicAndSuccessfulAttackConsumesAp() {
        Scenario invalid = scenario();
        MilitaryUnit ready = addPlayerSword(invalid, CENTER, 2);
        addPlayerArcher(invalid, CENTER, 0);
        addWorldSword(invalid, EAST, TRIBE, 2);
        expectFailure(() -> new CombatService(dice(6, 1, 1)).playerAttack(
                invalid.state, new AttackRequest(CENTER, EAST)),
                "Attack accepted an involved zero-AP unit");
        check(ready.actionPoints().current() == 2,
                "Atomic validation failure spent another participant's AP");

        Scenario blockedDefense = scenario();
        MilitaryUnit eager = addPlayerSword(blockedDefense, CENTER, 2);
        MilitaryUnit exhaustedDefender = addWorldSword(blockedDefense, EAST, TRIBE, 0);
        expectFailure(() -> new CombatService(dice(6, 1, 1)).playerAttack(
                blockedDefense.state, new AttackRequest(CENTER, EAST)),
                "Combat accepted an involved zero-AP defender");
        check(eager.actionPoints().current() == 2
                        && exhaustedDefender.actionPoints().current() == 0,
                "Defender AP validation failure partially mutated combat state");

        Scenario valid = scenario();
        MilitaryUnit attacker = addPlayerSword(valid, CENTER, 2);
        addWorldSword(valid, EAST, TRIBE, 2);
        var result = new CombatService(dice(6, 1, 1)).playerAttack(
                valid.state, new AttackRequest(CENTER, EAST));
        check(attacker.actionPoints().current() == 1
                        && result.apSpentBy().contains(attacker.id())
                        && result.apSpentBy().size() == 2,
                "Successful combat did not consume/report AP for both involved sides");
    }

    private static void civiliansRemainBehindMilitaryAndUseLowerPriority() {
        Scenario s = scenario();
        addPlayerSword(s, CENTER, 2);
        MilitaryUnit defender = addWorldSword(s, EAST, TRIBE, 2);
        Unit civilian = new WorkerUnit(id("neutral-worker"), EAST, new ActionPoints(1), TRIBE);
        s.state.world().units().add(civilian, s.state.map());
        new CombatService(dice(6, 1, 1)).playerAttack(s.state, new AttackRequest(CENTER, EAST));
        check(!containsWorld(s, defender) && containsWorld(s, civilian),
                "Civilian was damaged before defending military");

        Scenario civilianOnly = scenario();
        MilitaryUnit attacker = addPlayerSword(civilianOnly, CENTER, 2);
        Unit first = new WorkerUnit(id("civilian-a"), EAST, new ActionPoints(1), TRIBE);
        Unit second = new WorkerUnit(id("civilian-b"), EAST, new ActionPoints(1), TRIBE);
        civilianOnly.state.world().units().add(first, civilianOnly.state.map());
        civilianOnly.state.world().units().add(second, civilianOnly.state.map());
        var result = new CombatService(dice()).playerAttack(
                civilianOnly.state, new AttackRequest(CENTER, EAST));
        check(result.mode() == CombatMode.CIVILIAN
                        && civilianOnly.state.world().units().all().size() == 1
                        && attacker.actionPoints().current() == 1,
                "Civilian-only target did not use deterministic lower-priority resolution");
    }

    private static void wildAnimalDefenseUsesOneVersusTwoDice() {
        Scenario animalWins = scenario();
        MilitaryUnit defender = addPlayerCavalry(animalWins, EAST, 4);
        WildAnimal animal = new WildAnimal(WildAnimalId.random(), EAST);
        animalWins.state.world().wildAnimals().add(animal, animalWins.state.map());
        var attack = new CombatService(dice(6, 1, 1)).wildAnimalAttack(
                animalWins.state, animal.id());
        check(attack.dice().attackerRawRolls().size() == 1
                        && attack.dice().defenderRawRolls().size() == 2,
                "Wild Animal defensive dice counts are wrong");
        check(defender.health().current() == 1
                        && animalWins.state.world().wildAnimals().at(EAST).isPresent(),
                "Winning animal did not survive and apply one military hit");

        Scenario playerWins = scenario();
        addPlayerSword(playerWins, EAST, 2);
        WildAnimal weaker = new WildAnimal(WildAnimalId.random(), EAST);
        playerWins.state.world().wildAnimals().add(weaker, playerWins.state.map());
        new CombatService(dice(2, 6, 1)).wildAnimalAttack(playerWins.state, weaker.id());
        check(playerWins.state.world().wildAnimals().at(EAST).isEmpty(),
                "Defensive superiority did not remove Wild Animal");
    }

    private static void movementRejectsMilitaryStackOverflow() {
        Scenario s = scenario();
        addPlayerSword(s, EAST, 2);
        addPlayerSword(s, EAST, 2);
        MilitaryUnit third = addPlayerSword(s, CENTER, 2);
        expectFailure(() -> new MovementService(new VisibilityService()).move(
                s.state, third.id(), EAST, DefaultRulesetFactory.create()),
                "Movement created a third Swordsman on one hex");
        check(third.position().equals(CENTER) && third.actionPoints().current() == 2,
                "Rejected stack-overflow movement mutated unit state");
    }

    private static void hostileMilitaryAttackUsesDefensiveCombat() {
        Scenario s = scenario();
        MilitaryUnit enemySword = addWorldSword(s, EAST, FactionId.BARBARIANS, 2);
        MilitaryUnit enemyArcher = addWorldArcher(s, EAST, FactionId.BARBARIANS, 2);
        MilitaryUnit defender = addPlayerCavalry(s, CENTER, 4);
        var result = new CombatService(dice(6, 1, 5, 2)).hostileAttack(s.state,
                new AttackRequest(EAST, CENTER), FactionId.BARBARIANS);
        check(result.dice().attackerRawRolls().size() == 2
                        && result.dice().defenderRawRolls().size() == 2,
                "Hostile-versus-player defensive dice were not resolved");
        check(!containsWorld(s, enemySword) && containsWorld(s, enemyArcher)
                        && defender.health().current() == 1,
                "Defensive combat did not apply one hit to each side with correct priority");
        check(enemyArcher.actionPoints().current() == 1
                        && defender.actionPoints().current() == 3,
                "Hostile attackers or player defenders did not spend AP");
    }

    private static void wildlifeStepRepeatsAndNeverDamagesStructures() {
        Scenario s = scenario();
        MilitaryUnit cavalry = addPlayerCavalry(s, EAST, 4);
        WorkerUnit civilian = new WorkerUnit(id("wildlife-civilian"), EAST,
                new ActionPoints(4));
        s.state.player().addUnit(civilian);
        ProductionBuilding building = new ProductionBuilding(BuildingId.random(),
                BuildingType.LUMBER_MILL, EAST, 1);
        s.state.player().addBuilding(building);
        WildAnimal animal = new WildAnimal(WildAnimalId.random(), EAST);
        s.state.world().wildAnimals().add(animal, s.state.map());
        int buildingHp = building.health().current();
        WildlifeCombatStep first = new WildlifeCombatStep(
                new CombatService(dice(6, 1, 1)));
        first.execute(new TurnContext(s.state, DefaultRulesetFactory.create()));
        check(cavalry.health().current() == 1 && containsPlayer(s, civilian),
                "First wildlife turn did not prioritize military");
        WildlifeCombatStep second = new WildlifeCombatStep(
                new CombatService(dice(6, 1, 1)));
        second.execute(new TurnContext(s.state, DefaultRulesetFactory.create()));
        check(!containsPlayer(s, cavalry) && containsPlayer(s, civilian),
                "Surviving animal did not attack military again next turn");
        WildlifeCombatStep third = new WildlifeCombatStep(new CombatService(dice()));
        third.execute(new TurnContext(s.state, DefaultRulesetFactory.create()));
        check(!containsPlayer(s, civilian) && building.health().current() == buildingHp
                        && s.state.world().wildAnimals().at(EAST).isPresent(),
                "Wildlife civilian targeting damaged a structure or removed the survivor");
    }

    private static void combatResultContainsCompleteTrace() {
        Scenario s = scenario();
        addPlayerSword(s, CENTER, 2);
        addWorldSword(s, EAST, TRIBE, 2);
        var result = new CombatService(dice(6, 2, 4)).playerAttack(
                s.state, new AttackRequest(CENTER, EAST));
        String trace = String.join("\n", result.log());
        check(trace.contains("Raw rolls") && trace.contains("Sorted rolls")
                        && trace.contains("6 vs 4") && trace.contains("Outcome"),
                "Combat result omitted rolls, pairing, or outcome from its presentation trace");
        AtomicReference<String> displayed = new AtomicReference<>();
        AtomicReference<List<Integer>> attackerSorted = new AtomicReference<>();
        AtomicReference<List<String>> paired = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                CombatPanel panel = new CombatPanel();
                panel.display(result);
                displayed.set(panel.displayedTrace());
                attackerSorted.set(panel.diceCanvas().attackerSorted());
                paired.set(panel.diceCanvas().pairedComparisons());
            });
        } catch (Exception failure) {
            throw new AssertionError("Combat result could not be presented on the EDT", failure);
        }
        check(displayed.get().equals(trace),
                "Swing CombatPanel recalculated or omitted authoritative result content");
        check(attackerSorted.get().equals(result.dice().attackerSortedRolls())
                        && paired.get().size() == result.dice().pairs().size()
                        && paired.get().get(0).contains("6 vs 4"),
                "Combat dice visualization omitted sorted or paired authoritative values");
    }

    private static Scenario scenario() {
        List<HexTile> tiles = new ArrayList<>();
        for (int q = -2; q <= 2; q++) {
            for (int r = -2; r <= 2; r++) {
                HexCoord coordinate = new HexCoord(q, r);
                if (CENTER.distanceTo(coordinate) <= 2) {
                    tiles.add(new HexTile(coordinate, TerrainType.PLAINS));
                }
            }
        }
        GameMap map = new GameMap(tiles);
        TownHall hall = new TownHall(BuildingId.random(), CENTER,
                new ResourceStockpile(ResourceBundle.ZERO,
                        ResourceBundle.of(1_000, 1_000, 1_000, 1_000)),
                new ProductionQueue());
        Set<HexCoord> visible = Set.copyOf(map.tiles().stream().map(HexTile::coordinate).toList());
        PlayerState player = new PlayerState(hall, new UnitCapacity(30),
                new EmpireBorder(Set.of(CENTER), map),
                new VisibilityState(visible, visible), new TechnologyState());
        return new Scenario(new GameState(map, player, new TurnState(), new CrisisState(), 99L));
    }

    private static MilitaryUnit addPlayerSword(Scenario s, HexCoord at, int ap) {
        MilitaryUnit unit = new SwordsmanUnit(id("ps-" + at + "-" + s.next()), at,
                new ActionPoints(2, ap), FactionId.PLAYER, 1, 1);
        s.state.player().addUnit(unit);
        return unit;
    }

    private static MilitaryUnit addPlayerArcher(Scenario s, HexCoord at, int ap) {
        MilitaryUnit unit = new ArcherUnit(id("pa-" + at + "-" + s.next()), at,
                new ActionPoints(2, ap), FactionId.PLAYER, 1, 2);
        s.state.player().addUnit(unit);
        return unit;
    }

    private static MilitaryUnit addPlayerCavalry(Scenario s, HexCoord at, int ap) {
        MilitaryUnit unit = new CavalryUnit(id("pc-" + at + "-" + s.next()), at,
                new ActionPoints(4, ap), FactionId.PLAYER, 2, 1);
        s.state.player().addUnit(unit);
        return unit;
    }

    private static MilitaryUnit addWorldSword(
            Scenario s, HexCoord at, FactionId owner, int ap) {
        MilitaryUnit unit = new SwordsmanUnit(id("ws-" + at + "-" + s.next()), at,
                new ActionPoints(2, ap), owner, 1, 1);
        s.state.world().units().add(unit, s.state.map());
        return unit;
    }

    private static MilitaryUnit addWorldArcher(
            Scenario s, HexCoord at, FactionId owner, int ap) {
        MilitaryUnit unit = new ArcherUnit(id("wa-" + at + "-" + s.next()), at,
                new ActionPoints(2, ap), owner, 1, 2);
        s.state.world().units().add(unit, s.state.map());
        return unit;
    }

    private static boolean containsPlayer(Scenario s, Unit unit) {
        return s.state.player().units().stream().anyMatch(other -> other.id().equals(unit.id()));
    }

    private static boolean containsWorld(Scenario s, Unit unit) {
        return s.state.world().units().all().stream().anyMatch(other -> other.id().equals(unit.id()));
    }

    private static UnitId id(String seed) {
        return new UnitId(UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)));
    }

    private static DiceSource dice(int... rolls) {
        return new SequenceDice(rolls);
    }

    private static void expectFailure(Runnable action, String message) {
        try { action.run(); } catch (RuntimeException expected) { return; }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class SequenceDice implements DiceSource {
        private final Deque<Integer> rolls = new ArrayDeque<>();
        private SequenceDice(int... values) {
            for (int value : values) rolls.addLast(value);
        }
        @Override public int rollD6() {
            if (rolls.isEmpty()) throw new AssertionError("Combat requested an unexpected die");
            return rolls.removeFirst();
        }
    }

    private static final class Scenario {
        private final GameState state;
        private int sequence;
        private Scenario(GameState state) { this.state = state; }
        private int next() { return ++sequence; }
    }
}
