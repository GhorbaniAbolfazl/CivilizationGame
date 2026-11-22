package civilization.application.service;

import civilization.application.ActionFailure;
import civilization.application.action.ActionPreview;
import civilization.application.event.EventBus;
import civilization.application.event.TribeNotificationEvent;
import civilization.application.notification.NotificationCategory;
import civilization.application.tribe.GiftResult;
import civilization.application.tribe.TribeActionAvailability;
import civilization.model.diplomacy.DiplomaticStance;
import civilization.model.economy.ResourceBundle;
import civilization.model.economy.ResourceType;
import civilization.model.game.GameState;
import civilization.model.identity.TribeId;
import civilization.model.tribe.TribeType;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Atomic, Swing-free diplomacy use cases. Every command shares its preview validation. */
public final class TribeInteractionService {
    public static final ResourceBundle PEACE_COST = ResourceBundle.of(30, 30, 0, 30);
    public static final int PEACEFUL_TURNS_REQUIRED = 3;
    public static final int ALLIANCE_FAILURE_LOOKBACK = 5;
    private final TribeDiscoveryService discovery;
    private final TribeRewardService rewards;
    private final EventBus events;

    public TribeInteractionService() {
        this(new TribeDiscoveryService(), new TribeRewardService(), EventBus.noOp());
    }

    public TribeInteractionService(TribeDiscoveryService discovery,
            TribeRewardService rewards, EventBus events) {
        this.discovery = Objects.requireNonNull(discovery, "discovery");
        this.rewards = Objects.requireNonNull(rewards, "rewards");
        this.events = Objects.requireNonNull(events, "events");
    }

    public TribeActionAvailability availability(GameState state, TribeId id) {
        return new TribeActionAvailability(id,
                previewGift(state, id), previewTrade(state, id), previewMission(state, id),
                previewWar(state, id), previewPeace(state, id), previewAlliance(state, id));
    }

    public ActionPreview previewGift(GameState state, TribeId id) {
        return preview(() -> {
            requireInteraction(state, id);
            return state.world().relationships().require(id).behavior().giftAllowed()
                    ? ActionPreview.allow() : ActionPreview.reject("Gifts are unavailable at war");
        });
    }

    public ActionPreview previewGift(GameState state, TribeId id,
            ResourceType type, int amount) {
        ActionPreview general = previewGift(state, id);
        if (!general.allowed()) return general;
        int chunk = giftChunk(Objects.requireNonNull(type, "type"));
        if (amount <= 0 || amount % chunk != 0) {
            return ActionPreview.reject(type + " gifts must be a positive multiple of " + chunk);
        }
        return state.player().townHall().stockpile().canAfford(ResourceBundle.of(type, amount))
                ? ActionPreview.allow() : ActionPreview.reject("Insufficient resources for gift");
    }

    public ActionPreview previewTrade(GameState state, TribeId id) {
        return preview(() -> {
            requireInteraction(state, id);
            if (state.world().tribes().require(id).type().tradeProfile().isEmpty()) {
                return ActionPreview.reject("This tribe type does not offer resource trade");
            }
            return state.world().relationships().require(id).behavior().tradeAllowed()
                    ? ActionPreview.allow()
                    : ActionPreview.reject("Trade requires a Friendly or Allied relationship");
        });
    }

    public ActionPreview previewMission(GameState state, TribeId id) {
        return preview(() -> {
            requireInteraction(state, id);
            var relation = state.world().relationships().require(id);
            if (!relation.behavior().missionAllowed() || relation.stance() == DiplomaticStance.WAR) {
                return ActionPreview.reject("Missions require a Friendly or Allied relationship");
            }
            var tribe = state.world().tribes().require(id);
            if (state.world().missions().unresolvedForTribe(id).isPresent()) {
                return ActionPreview.reject("This tribe already has an unresolved mission");
            }
            if (state.turn().turnNumber() - tribe.lastMissionFailureTurn() < 5) {
                return ActionPreview.reject("Mission cooldown is active after failure");
            }
            return ActionPreview.allow();
        });
    }

    public ActionPreview previewWar(GameState state, TribeId id) {
        return preview(() -> {
            requireInteraction(state, id);
            return state.world().relationships().require(id).stance() == DiplomaticStance.WAR
                    ? ActionPreview.reject("Already at war with this tribe") : ActionPreview.allow();
        });
    }

    public ActionPreview previewPeace(GameState state, TribeId id) {
        return preview(() -> {
            requireInteraction(state, id);
            var relation = state.world().relationships().require(id);
            var tribe = state.world().tribes().require(id);
            if (!relation.behavior().peaceRequestAllowed()
                    || relation.stance() != DiplomaticStance.WAR) {
                return ActionPreview.reject("Peace can only be requested from an Enemy tribe");
            }
            if (tribe.lastPlayerAttackTurn() != Integer.MIN_VALUE
                    && state.turn().turnNumber() - tribe.lastPlayerAttackTurn()
                    < PEACEFUL_TURNS_REQUIRED) {
                return ActionPreview.reject("Three turns without attacking are required");
            }
            return state.player().townHall().stockpile().canAfford(PEACE_COST)
                    ? ActionPreview.allow() : ActionPreview.reject("Insufficient resources for peace");
        });
    }

    public ActionPreview previewAlliance(GameState state, TribeId id) {
        return preview(() -> {
            requireInteraction(state, id);
            var relation = state.world().relationships().require(id);
            if (relation.stance() == DiplomaticStance.ALLIANCE) {
                return ActionPreview.reject("Alliance is already active");
            }
            if (!relation.behavior().allianceRequestAllowed()
                    || relation.stance() == DiplomaticStance.WAR) {
                return ActionPreview.reject("Alliance requires relationship 70 or higher and peace");
            }
            if (state.world().missions().failedForTribeSince(id,
                    state.turn().turnNumber() - ALLIANCE_FAILURE_LOOKBACK + 1)) {
                return ActionPreview.reject("A mission failed for this tribe in the last five turns");
            }
            TribeType candidate = state.world().tribes().require(id).type();
            for (var other : state.world().tribes().all()) {
                if (other.id().equals(id)) continue;
                if (state.world().relationships().require(other.id()).stance()
                        != DiplomaticStance.ALLIANCE) continue;
                if (candidate == TribeType.WARRIOR || other.type() == TribeType.WARRIOR) {
                    return ActionPreview.reject("A Warrior alliance is exclusive");
                }
                if ((candidate == TribeType.FARMER && other.type() == TribeType.MOUNTAIN)
                        || (candidate == TribeType.MOUNTAIN && other.type() == TribeType.FARMER)) {
                    return ActionPreview.reject("Farmer and Mountain alliances are mutually exclusive");
                }
            }
            return rewards.previewAllianceReward(state, id);
        });
    }

    public GiftResult giveGift(GameState state, TribeId id, ResourceType type, int amount) {
        requireAllowed(previewGift(state, id));
        int chunk = giftChunk(Objects.requireNonNull(type, "type"));
        int relationPerChunk = type == ResourceType.FOOD || type == ResourceType.WOOD ? 2 : 3;
        if (amount <= 0 || amount % chunk != 0) {
            throw new ActionFailure(type + " gifts must be a positive multiple of " + chunk);
        }
        ResourceBundle cost = ResourceBundle.of(type, amount);
        if (!state.player().townHall().stockpile().canAfford(cost)) {
            throw new ActionFailure("Insufficient resources for gift");
        }
        int delta = Math.multiplyExact(amount / chunk, relationPerChunk);
        state.player().townHall().stockpile().debit(cost);
        var relation = state.world().relationships().require(id);
        relation.adjustScore(delta);
        deactivateBrokenAlliance(state, id);
        state.incrementVersion();
        notify(state, id, "Gift improved relationship to " + relation.score());
        return new GiftResult(id, type, amount, delta, relation.score());
    }

    public void declareWar(GameState state, TribeId id) {
        requireAllowed(previewWar(state, id));
        var relation = state.world().relationships().require(id);
        boolean allied = relation.stance() == DiplomaticStance.ALLIANCE;
        boolean friendly = relation.behavior().tradeAllowed();
        relation.declareWar();
        var tribe = state.world().tribes().require(id);
        tribe.recordPlayerAttack(state.turn().turnNumber(), tribe.camp().position());
        state.world().missions().unresolvedForTribe(id).ifPresent(mission -> {
            mission.cancel(state.turn().turnNumber());
            tribe.clearCurrentMission(mission.id());
        });
        tribe.setAllianceBonusActive(false);
        if (allied) state.player().happiness().applyOnce("allied-war:" + id.value(), -15);
        else if (friendly) state.player().happiness().applyOnce("friendly-war:" + id.value(), -5);
        state.incrementVersion();
        notify(state, id, "War declared on " + tribe.name());
    }

    /** Called by combat before an undeclared player attack is committed. */
    public void registerPlayerAttack(GameState state, TribeId id,
            civilization.model.map.HexCoord attackerHex) {
        var relation = state.world().relationships().require(id);
        if (relation.stance() != DiplomaticStance.WAR) declareWarWithoutVisibility(state, id);
        state.world().tribes().require(id).recordPlayerAttack(state.turn().turnNumber(), attackerHex);
    }

    public void requestPeace(GameState state, TribeId id) {
        requireAllowed(previewPeace(state, id));
        state.player().townHall().stockpile().debit(PEACE_COST);
        state.world().relationships().require(id).establishPeace(-10);
        state.world().tribes().require(id).clearAttackTarget();
        state.incrementVersion();
        notify(state, id, "Peace accepted; relationship is -10");
    }

    public void requestAlliance(GameState state, TribeId id,
            civilization.rules.GameRules rules) {
        requireAllowed(previewAlliance(state, id));
        state.world().relationships().require(id).formAlliance();
        state.world().tribes().require(id).setAllianceBonusActive(true);
        rewards.grantAllianceReward(state, id, rules);
        state.incrementVersion();
        notify(state, id, "Alliance formed");
    }

    private void declareWarWithoutVisibility(GameState state, TribeId id) {
        var relation = state.world().relationships().require(id);
        boolean allied = relation.stance() == DiplomaticStance.ALLIANCE;
        boolean friendly = relation.behavior().tradeAllowed();
        relation.declareWar();
        var tribe = state.world().tribes().require(id);
        state.world().missions().unresolvedForTribe(id).ifPresent(mission -> {
            mission.cancel(state.turn().turnNumber());
            tribe.clearCurrentMission(mission.id());
        });
        tribe.setAllianceBonusActive(false);
        if (allied) state.player().happiness().applyOnce("allied-war:" + id.value(), -15);
        else if (friendly) state.player().happiness().applyOnce("friendly-war:" + id.value(), -5);
        notify(state, id, "Attack caused immediate war");
    }

    private void requireInteraction(GameState state, TribeId id) {
        if (!discovery.canInteract(state, id)) {
            throw new ActionFailure("Tribe camp must be discovered and currently visible");
        }
    }

    private void deactivateBrokenAlliance(GameState state, TribeId id) {
        var tribe = state.world().tribes().require(id);
        if (state.world().relationships().require(id).stance() != DiplomaticStance.ALLIANCE) {
            tribe.setAllianceBonusActive(false);
        }
    }

    private static ActionPreview preview(Supplier<ActionPreview> action) {
        try { return action.get(); }
        catch (ActionFailure | IllegalArgumentException | IllegalStateException failure) {
            return ActionPreview.reject(failure.getMessage());
        }
    }

    private static int giftChunk(ResourceType type) {
        return switch (type) {
            case FOOD, WOOD, STONE -> 10;
            case IRON -> 5;
        };
    }

    private static void requireAllowed(ActionPreview preview) {
        if (!preview.allowed()) throw new ActionFailure(preview.reason());
    }

    private void notify(GameState state, TribeId id, String message) {
        events.publish(new TribeNotificationEvent(id, state.turn().turnNumber(),
                NotificationCategory.DIPLOMACY, message,
                Optional.of(state.world().tribes().require(id).camp().position())));
    }
}
