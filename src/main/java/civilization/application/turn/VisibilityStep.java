package civilization.application.turn;

import civilization.application.service.VisibilityService;

import java.util.Objects;

public final class VisibilityStep implements TurnStep {
    private final VisibilityService service;

    public VisibilityStep(VisibilityService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public String id() {
        return "VISIBILITY";
    }

    @Override
    public void execute(TurnContext context) {
        service.refresh(context.state(), context.rules());
    }
}
