package civilization.model.technology;

import civilization.model.economy.ResourceBundle;
import java.util.Objects;

public final class TechnologyResearchOrder extends ProductionOrder {
    private static final long serialVersionUID = 1L;
    private final TechnologyId technologyId;

    public TechnologyResearchOrder(TechnologyId technologyId, ResourceBundle paidCost, int totalTurns) {
        super(paidCost, totalTurns);
        this.technologyId = Objects.requireNonNull(technologyId, "technologyId");
    }

    public TechnologyId technologyId() { return technologyId; }
}
