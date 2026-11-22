package civilization.application.turn;

import civilization.application.service.CombatService;
import java.util.List;
import java.util.Objects;
import civilization.application.service.BearAttackService;

/** Resolves mandatory co-located Wild Animal attacks once per turn. */
public final class WildlifeCombatStep implements TurnStep {
    private final CombatService combatService;
    private final BearAttackService bears = new BearAttackService();

    public WildlifeCombatStep(CombatService combatService) {
        this.combatService = Objects.requireNonNull(combatService, "combatService");
    }

    @Override public String id() { return "WILDLIFE_COMBAT"; }

    @Override
    public void execute(TurnContext context) {
        var state = context.state();
        bears.advance(state);
        for (var animal : List.copyOf(state.world().wildAnimals().all())) {
            boolean hasPlayerTarget = state.player().units().stream()
                    .anyMatch(unit -> unit.position().equals(animal.position()));
            if (hasPlayerTarget && (!animal.isBear()
                    || state.gameplayRandom().nextDouble() < animal.attackStrength() / 100.0)) {
                combatService.wildAnimalAttack(state, animal.id());
            }
        }
    }
}
