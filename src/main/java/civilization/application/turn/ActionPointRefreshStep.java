package civilization.application.turn;

import civilization.application.service.ActionPointService;

import java.util.Objects;

public final class ActionPointRefreshStep implements TurnStep {
    private final ActionPointService service;

    public ActionPointRefreshStep(ActionPointService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public String id() {
        return "AP_REFRESH";
    }

    @Override
    public void execute(TurnContext context) {
        service.refillLivingUnits(context.state());
    }
}
