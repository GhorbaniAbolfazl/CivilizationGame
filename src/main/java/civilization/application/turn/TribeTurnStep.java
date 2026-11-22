package civilization.application.turn;

import civilization.application.service.TribeTurnService;
import java.util.Objects;

public final class TribeTurnStep implements TurnStep {
    private final TribeTurnService service;
    public TribeTurnStep(TribeTurnService service) {
        this.service = Objects.requireNonNull(service, "service");
    }
    @Override public String id() { return "TRIBE_TURN"; }
    @Override public void execute(TurnContext context) {
        service.process(context.state(), context.rules());
    }
}
