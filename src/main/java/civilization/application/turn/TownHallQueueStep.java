package civilization.application.turn;

import civilization.application.service.ProductionQueueService;

import java.util.Objects;

public final class TownHallQueueStep implements TurnStep {
    private final ProductionQueueService service;

    public TownHallQueueStep(ProductionQueueService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public String id() {
        return "TOWN_HALL_QUEUE";
    }

    @Override
    public void execute(TurnContext context) {
        service.advance(context.state(), context.rules()).ifPresent(context::recordCompletedUnit);
    }
}
