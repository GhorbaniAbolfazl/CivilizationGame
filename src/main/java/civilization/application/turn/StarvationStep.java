package civilization.application.turn;

import civilization.application.service.StarvationService;

import java.util.Objects;

public final class StarvationStep implements TurnStep {
    private final StarvationService service;

    public StarvationStep(StarvationService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public String id() {
        return "STARVATION";
    }

    @Override
    public void execute(TurnContext context) {
        service.update(context.state(), context.upkeep(), context.rules());
    }
}
