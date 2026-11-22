package civilization.application.turn;

import civilization.application.service.ResourceProductionService;

import java.util.Objects;

public final class ResourceProductionStep implements TurnStep {
    private final ResourceProductionService service;

    public ResourceProductionStep(ResourceProductionService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public String id() {
        return "RESOURCE_PRODUCTION";
    }

    @Override
    public void execute(TurnContext context) {
        context.recordProduction(service.produce(context.state(), context.rules()));
    }
}
