package civilization.application.service;

import civilization.application.validation.DemolitionValidator;
import civilization.application.validation.ValidationSupport;
import civilization.model.building.Building;
import civilization.model.building.BuildingId;
import civilization.model.game.GameState;
import civilization.model.infrastructure.Infrastructure;
import civilization.model.infrastructure.InfrastructureId;
import civilization.model.unit.BuilderUnit;
import civilization.model.unit.UnitId;
import java.util.Objects;

public final class DemolitionService {
    private final DemolitionValidator validator;
    private final StructureLifecycleService lifecycle;

    public DemolitionService() { this(new DemolitionValidator(), new StructureLifecycleService()); }

    public DemolitionService(DemolitionValidator validator, StructureLifecycleService lifecycle) {
        this.validator = Objects.requireNonNull(validator, "validator");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    }

    public Building demolishBuilding(GameState state, UnitId builderId, BuildingId buildingId) {
        ValidationSupport.requireAllowed(validator.building(state, builderId, buildingId));
        BuilderUnit builder = (BuilderUnit) state.player().requireUnit(builderId);
        builder.actionPoints().spend(DemolitionValidator.AP_COST);
        Building removed = lifecycle.demolishBuilding(state, buildingId);
        state.validateSpatialReferences();
        state.incrementVersion();
        return removed;
    }

    public Infrastructure demolishInfrastructure(
            GameState state, UnitId builderId, InfrastructureId id) {
        ValidationSupport.requireAllowed(validator.infrastructure(state, builderId, id));
        BuilderUnit builder = (BuilderUnit) state.player().requireUnit(builderId);
        builder.actionPoints().spend(DemolitionValidator.AP_COST);
        Infrastructure removed = state.world().infrastructure().remove(id);
        state.validateSpatialReferences();
        state.incrementVersion();
        return removed;
    }
}
