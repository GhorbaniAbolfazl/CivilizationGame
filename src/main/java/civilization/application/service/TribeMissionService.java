package civilization.application.service;

import civilization.application.ActionFailure;
import civilization.application.action.ActionPreview;
import civilization.application.event.EventBus;
import civilization.application.event.TribeNotificationEvent;
import civilization.application.notification.NotificationCategory;
import civilization.model.building.BuildingType;
import civilization.model.diplomacy.DiplomaticStance;
import civilization.model.economy.ResourceStockpile;
import civilization.model.game.GameState;
import civilization.model.identity.FactionId;
import civilization.model.identity.TribeId;
import civilization.model.infrastructure.Road;
import civilization.model.map.HexCoord;
import civilization.model.mission.Mission;
import civilization.model.mission.MissionId;
import civilization.model.mission.MissionStatus;
import civilization.model.mission.MissionType;
import civilization.rules.GameRules;
import civilization.rules.tribe.MissionCatalog;
import civilization.rules.tribe.MissionDefinition;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/** Mission lifecycle and objective detection. Objective truth comes only from model state. */
public final class TribeMissionService {
    public static final int FAILURE_RELATION_PENALTY = -10;
    public static final int CANCELLATION_RELATION_PENALTY = -5;
    public static final int FAILURE_COOLDOWN_TURNS = 5;
    private final MissionCatalog catalog;
    private final TribeDiscoveryService discovery;
    private final TribeRewardService rewards;
    private final EventBus events;

    public TribeMissionService() {
        this(new MissionCatalog(), new TribeDiscoveryService(),
                new TribeRewardService(), EventBus.noOp());
    }

    public TribeMissionService(MissionCatalog catalog, TribeDiscoveryService discovery,
            TribeRewardService rewards, EventBus events) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.discovery = Objects.requireNonNull(discovery, "discovery");
        this.rewards = Objects.requireNonNull(rewards, "rewards");
        this.events = Objects.requireNonNull(events, "events");
    }

    public ActionPreview previewOffer(GameState state, TribeId id) {
        return preview(() -> validateCanReceive(state, id));
    }

    public Mission offer(GameState state, TribeId id) {
        requireAllowed(previewOffer(state, id));
        var tribe = state.world().tribes().require(id);
        MissionDefinition definition = catalog.require(tribe.type());
        int turn = state.turn().turnNumber();
        Mission mission = new Mission(new MissionId(id.value() + ":" + turn), id,
                definition.missionType(), turn, turn + definition.durationTurns());
        state.world().missions().add(mission);
        tribe.recordMissionOffer(turn, mission.id());
        state.incrementVersion();
        notify(state, id, "New mission available: " + mission.type());
        return mission;
    }

    public void accept(GameState state, MissionId id) {
        Mission mission = state.world().missions().require(id);
        requireInteraction(state, mission.tribeId());
        if (mission.status() != MissionStatus.RECEIVABLE) {
            throw new ActionFailure("Mission is not receivable");
        }
        state.world().missions().accept(id, state.turn().turnNumber());
        state.incrementVersion();
        notify(state, mission.tribeId(), "Mission accepted: " + mission.type());
    }

    public boolean evaluateObjective(GameState state, MissionId id) {
        Mission mission = state.world().missions().require(id);
        if (mission.status() == MissionStatus.READY) return true;
        if (mission.status() != MissionStatus.ACTIVE) return false;
        var tribe = state.world().tribes().require(mission.tribeId());
        boolean ready = objectiveSatisfied(state, mission);
        if (ready) {
            mission.markReady();
            notify(state, mission.tribeId(), "Mission objective is ready for delivery");
        }
        return ready;
    }

    private boolean objectiveSatisfied(GameState state, Mission mission) {
        var tribe = state.world().tribes().require(mission.tribeId());
        return switch (mission.type()) {
            case FARMER_SUPPLY, MOUNTAIN_SUPPLY -> state.player().townHall().stockpile()
                    .canAfford(catalog.require(tribe.type()).deliveryCost());
            case TRADER_ROAD -> hasRoadPath(state, tribe.id());
            case COASTAL_DOCK -> state.player().constructedBuildings().stream()
                    .anyMatch(building -> building.isActive()
                            && building.type() == BuildingType.DOCK
                            && building.position().distanceTo(tribe.camp().position()) <= 4);
            case WARRIOR_BOUNTY -> qualifyingKills(state, mission) >= 2;
        };
    }

    public ActionPreview previewDelivery(GameState state, MissionId id) {
        return preview(() -> {
            Mission mission = state.world().missions().require(id);
            requireInteraction(state, mission.tribeId());
            if (mission.status() != MissionStatus.READY
                    && (mission.status() != MissionStatus.ACTIVE
                    || !objectiveSatisfied(state, mission))) {
                return ActionPreview.reject("Mission objective is not complete");
            }
            var tribe = state.world().tribes().require(mission.tribeId());
            MissionDefinition definition = catalog.require(tribe.type());
            ResourceStockpile stockpile = state.player().townHall().stockpile();
            if (!stockpile.canAfford(definition.deliveryCost())) {
                return ActionPreview.reject("Mission delivery resources are unavailable");
            }
            if (!stockpile.canAccept(definition.resourceReward())) {
                return ActionPreview.reject("Mission reward exceeds storage capacity");
            }
            if (mission.type() == MissionType.WARRIOR_BOUNTY) {
                return rewards.previewUnits(state, 3,
                        civilization.model.unit.UnitType.SWORDSMAN);
            }
            return ActionPreview.allow();
        });
    }

    public void deliver(GameState state, MissionId id, GameRules rules) {
        requireAllowed(previewDelivery(state, id));
        Mission mission = state.world().missions().require(id);
        if (mission.status() == MissionStatus.ACTIVE) evaluateObjective(state, id);
        var tribe = state.world().tribes().require(mission.tribeId());
        MissionDefinition definition = catalog.require(tribe.type());
        ResourceStockpile stockpile = state.player().townHall().stockpile();
        stockpile.debit(definition.deliveryCost());
        if (!stockpile.creditExact(definition.resourceReward())) {
            throw new IllegalStateException("Mission reward validation changed during delivery");
        }
        switch (mission.type()) {
            case TRADER_ROAD -> state.world().tribeTrades().require(tribe.id())
                    .increaseRateBonus(10);
            case WARRIOR_BOUNTY -> rewards.grantUnits(state, 3,
                    civilization.model.unit.UnitType.SWORDSMAN, rules);
            case COASTAL_DOCK -> tribe.grantNextDockDiscount();
            case FARMER_SUPPLY, MOUNTAIN_SUPPLY -> { }
        }
        state.world().relationships().require(tribe.id())
                .adjustScore(definition.relationReward());
        mission.complete(state.turn().turnNumber());
        tribe.clearCurrentMission(mission.id());
        state.incrementVersion();
        notify(state, tribe.id(), "Mission completed: " + mission.type());
    }

    public void cancel(GameState state, MissionId id) {
        Mission mission = state.world().missions().require(id);
        requireInteraction(state, mission.tribeId());
        mission.cancel(state.turn().turnNumber());
        state.world().relationships().require(mission.tribeId())
                .adjustScore(CANCELLATION_RELATION_PENALTY);
        deactivateBrokenAlliance(state, mission.tribeId());
        state.world().tribes().require(mission.tribeId()).clearCurrentMission(id);
        state.incrementVersion();
        notify(state, mission.tribeId(), "Mission cancelled");
    }

    /** Evaluates objectives first, then fails only still-active overdue missions. */
    public void processDeadlines(GameState state) {
        for (var tribe : state.world().tribes().all()) {
            processDeadline(state, tribe.id());
        }
    }

    public void processDeadline(GameState state, TribeId tribeId) {
        for (Mission mission : state.world().missions().all().stream()
                .filter(candidate -> candidate.tribeId().equals(tribeId)).toList()) {
            if (mission.status() != MissionStatus.ACTIVE) continue;
            evaluateObjective(state, mission.id());
            if (mission.status() == MissionStatus.ACTIVE
                    && state.turn().turnNumber() > mission.deadlineTurn()) {
                mission.fail(state.turn().turnNumber());
                var tribe = state.world().tribes().require(mission.tribeId());
                tribe.recordMissionFailure(state.turn().turnNumber());
                tribe.clearCurrentMission(mission.id());
                state.world().relationships().require(mission.tribeId())
                        .adjustScore(FAILURE_RELATION_PENALTY);
                deactivateBrokenAlliance(state, mission.tribeId());
                notify(state, mission.tribeId(), "Mission failed: deadline expired");
            }
        }
    }

    private ActionPreview validateCanReceive(GameState state, TribeId id) {
        requireInteraction(state, id);
        var relation = state.world().relationships().require(id);
        if (!relation.behavior().missionAllowed() || relation.stance() == DiplomaticStance.WAR) {
            return ActionPreview.reject("Missions require a Friendly or Allied relationship");
        }
        if (state.world().missions().unresolvedForTribe(id).isPresent()) {
            return ActionPreview.reject("This tribe already has an unresolved mission");
        }
        var tribe = state.world().tribes().require(id);
        if (tribe.lastMissionFailureTurn() != Integer.MIN_VALUE
                && state.turn().turnNumber() - tribe.lastMissionFailureTurn()
                < FAILURE_COOLDOWN_TURNS) {
            return ActionPreview.reject("Mission cooldown is active after failure");
        }
        return ActionPreview.allow();
    }

    private void requireInteraction(GameState state, TribeId id) {
        if (!discovery.canInteract(state, id)) {
            throw new ActionFailure("Tribe camp must be discovered and currently visible");
        }
    }

    private static boolean hasRoadPath(GameState state, TribeId id) {
        HexCoord camp = state.world().tribes().require(id).camp().position();
        Set<HexCoord> goals = new HashSet<>();
        for (var tile : state.map().neighborsOf(camp)) goals.add(tile.coordinate());
        ArrayDeque<HexCoord> open = new ArrayDeque<>();
        Set<HexCoord> visited = new HashSet<>();
        for (var building : state.player().allBuildings()) {
            if (building.isActive()) {
                open.add(building.position());
                visited.add(building.position());
            }
        }
        while (!open.isEmpty()) {
            HexCoord current = open.removeFirst();
            if (goals.contains(current) && state.world().infrastructure().at(current)
                    .filter(Road.class::isInstance).isPresent()) return true;
            for (var neighbor : state.map().neighborsOf(current)) {
                HexCoord next = neighbor.coordinate();
                if (visited.add(next) && state.world().infrastructure().at(next)
                        .filter(Road.class::isInstance).isPresent()) open.addLast(next);
            }
        }
        return false;
    }

    private static long qualifyingKills(GameState state, Mission mission) {
        int accepted = mission.acceptedTurn().orElse(mission.issuedTurn());
        HexCoord camp = state.world().tribes().require(mission.tribeId()).camp().position();
        return state.world().combatHistory().kills().stream()
                .filter(kill -> kill.turnNumber() >= accepted)
                .filter(kill -> kill.location().distanceTo(camp) <= 5)
                .filter(kill -> isEnemyForMission(state, kill.defeatedOwner()))
                .count();
    }

    private static boolean isEnemyForMission(GameState state, FactionId owner) {
        if (owner.equals(FactionId.BARBARIANS)) return true;
        return state.world().tribes().byFaction(owner)
                .map(tribe -> state.world().relationships().require(tribe.id())
                        .stance() == DiplomaticStance.WAR)
                .orElse(false);
    }

    private static ActionPreview preview(Supplier<ActionPreview> action) {
        try { return action.get(); }
        catch (ActionFailure | IllegalArgumentException | IllegalStateException failure) {
            return ActionPreview.reject(failure.getMessage());
        }
    }

    private static void deactivateBrokenAlliance(GameState state, TribeId id) {
        if (state.world().relationships().require(id).stance() != DiplomaticStance.ALLIANCE) {
            state.world().tribes().require(id).setAllianceBonusActive(false);
        }
    }

    private static void requireAllowed(ActionPreview preview) {
        if (!preview.allowed()) throw new ActionFailure(preview.reason());
    }

    private void notify(GameState state, TribeId id, String message) {
        events.publish(new TribeNotificationEvent(id, state.turn().turnNumber(),
                NotificationCategory.MISSION, message,
                Optional.of(state.world().tribes().require(id).camp().position())));
    }
}
