package civilization.application.persistence;

import civilization.model.game.GamePhase;
import civilization.model.game.GameState;
import civilization.model.infrastructure.EdgeInfrastructure;
import civilization.model.infrastructure.TileInfrastructure;
import civilization.model.time.Weather;
import civilization.model.unit.WorkerUnit;
import java.util.HashSet;
import java.util.Objects;

/** Structural, reference, and stable-state validation run before save and before replacement. */
public final class SaveValidator {
    public void validateForSave(GameState state) {
        validate(state);
        if (state.turn().phase() != GamePhase.PLAYER_ACTIONS
                && state.turn().phase() != GamePhase.GAME_OVER) {
            throw new SaveValidationException("Game can only be saved in a stable player-action state");
        }
    }

    public void validateLoaded(GameState state, SaveMetadata metadata) {
        validateForSave(state);
        Objects.requireNonNull(metadata, "metadata");
        if (metadata.turnNumber() != state.turn().turnNumber()
                || metadata.season() != state.season().season()
                || metadata.turnWithinSeason() != state.season().turnWithinSeason()
                || metadata.weather() != Weather.forSeason(state.season().season())
                || metadata.hallLevel() != state.player().townHall().level()) {
            throw new SaveValidationException("Save metadata does not match its model state");
        }
    }

    private void validate(GameState state) {
        Objects.requireNonNull(state, "state");
        try {
            state.validateSpatialReferences();
            validateInfrastructure(state);
            validateWorkers(state);
            validateTribesAndMissions(state);
            if (state.gameplayRandom().snapshot().algorithm().isBlank()) {
                throw new SaveValidationException("Gameplay RNG state is invalid");
            }
        } catch (SaveValidationException known) {
            throw known;
        } catch (RuntimeException failure) {
            throw new SaveValidationException("Model state failed persistence validation", failure);
        }
    }

    private static void validateInfrastructure(GameState state) {
        HashSet<Object> ids = new HashSet<>();
        for (var infrastructure : state.world().infrastructure().all()) {
            if (!ids.add(infrastructure.id())) throw new SaveValidationException("Duplicate infrastructure ID");
            if (infrastructure instanceof TileInfrastructure tile) {
                state.map().requireTile(tile.position());
                if (state.world().infrastructure().at(tile.position()).orElse(null) != tile) {
                    throw new SaveValidationException("Broken tile-infrastructure index");
                }
            } else if (infrastructure instanceof EdgeInfrastructure edge) {
                state.map().requireEdge(edge.edge().first(), edge.edge().second());
                if (state.world().infrastructure().on(edge.edge()).orElse(null) != edge) {
                    throw new SaveValidationException("Broken edge-infrastructure index");
                }
            }
        }
    }

    private static void validateWorkers(GameState state) {
        for (var unit : state.player().units()) {
            if (unit instanceof WorkerUnit worker && worker.stationedAt().isPresent()) {
                var building = state.player().requireBuilding(worker.stationedAt().orElseThrow());
                if (!(building instanceof civilization.model.building.WorkerStationBuilding)) {
                    throw new SaveValidationException("Worker references a non-station building");
                }
            }
        }
    }

    private static void validateTribesAndMissions(GameState state) {
        for (var relationship : state.world().relationships().all()) {
            state.world().tribes().require(relationship.tribeId());
        }
        for (var mission : state.world().missions().all()) {
            var tribe = state.world().tribes().require(mission.tribeId());
            if (tribe.currentMissionId().filter(mission.id()::equals).isPresent()
                    && mission.status() != civilization.model.mission.MissionStatus.RECEIVABLE
                    && !mission.status().isInProgress()) {
                throw new SaveValidationException("Tribe references a terminal mission");
            }
        }
        for (var tribe : state.world().tribes().all()) {
            tribe.currentMissionId().ifPresent(id -> {
                var mission = state.world().missions().require(id);
                if (!mission.tribeId().equals(tribe.id())) {
                    throw new SaveValidationException("Mission belongs to another tribe");
                }
            });
        }
    }
}
