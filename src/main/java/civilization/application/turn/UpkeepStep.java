package civilization.application.turn;

import civilization.application.service.ResourceUpkeepService;

import java.util.Objects;

public final class UpkeepStep implements TurnStep {
    private final ResourceUpkeepService service;

    public UpkeepStep(ResourceUpkeepService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public String id() {
        return "UPKEEP";
    }

    @Override
    public void execute(TurnContext context) {
        context.recordUpkeep(service.apply(context.state(), context.rules()));
    }
}
