package civilization.application.service;

import civilization.application.ActionFailure;
import civilization.application.event.EventBus;
import civilization.application.event.TradeCompletedEvent;
import civilization.application.trade.TradePreview;
import civilization.application.trade.TradeResult;
import civilization.application.validation.ValidationSupport;
import civilization.model.building.Bazaar;
import civilization.model.building.BuildingId;
import civilization.model.economy.ResourceStockpile;
import civilization.model.economy.ResourceType;
import civilization.model.game.GameState;
import civilization.model.identity.TribeId;
import civilization.model.trade.BazaarTradeLevel;
import civilization.model.trade.TradeCooldown;
import civilization.model.trade.TradeEndpointKind;
import civilization.model.trade.TradeQuote;
import civilization.model.trade.TradeRequest;
import civilization.model.trade.TradingPostId;
import civilization.rules.trade.BazaarExchangeRateStrategy;
import civilization.rules.trade.ExchangeRateStrategy;
import civilization.rules.trade.TradingPostExchangeRateStrategy;
import civilization.rules.trade.TribeExchangeRateStrategy;
import java.util.Objects;
import java.util.function.Supplier;

/** UI-independent atomic workflow shared by every mandatory trade endpoint. */
public final class TradeService {
    private final EventBus eventBus;
    private final ExchangeRateStrategy tradingPostStrategy = new TradingPostExchangeRateStrategy();

    public TradeService() { this(EventBus.noOp()); }

    public TradeService(EventBus eventBus) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
    }

    public TradePreview previewBazaar(GameState state, BuildingId bazaarId,
            BazaarTradeLevel level, ResourceType source, ResourceType target) {
        return safelyPreview(() -> {
            Bazaar bazaar = requireBazaar(state, bazaarId);
            return validateAndQuote(state, bazaar.tradeCooldown(),
                    new BazaarExchangeRateStrategy(Objects.requireNonNull(level, "level")),
                    new TradeRequest(source, target, level.sourceAmount()));
        });
    }

    public TradeResult tradeAtBazaar(GameState state, BuildingId bazaarId,
            BazaarTradeLevel level, ResourceType source, ResourceType target) {
        Bazaar bazaar = requireBazaar(state, bazaarId);
        TradeRequest request = new TradeRequest(source, target,
                Objects.requireNonNull(level, "level").sourceAmount());
        return commit(state, bazaar.tradeCooldown(),
                new BazaarExchangeRateStrategy(level), request,
                TradeEndpointKind.BAZAAR, bazaar.id().value().toString());
    }

    public TradePreview previewTradingPost(GameState state, TradingPostId postId,
            ResourceType source, ResourceType target, int sourceAmount) {
        return safelyPreview(() -> {
            var post = state.world().tradingPosts().require(postId);
            if (!state.player().border().owns(post.position())) {
                return TradePreview.reject("Trading Post hex is outside the empire");
            }
            return validateAndQuote(state, post.tradeCooldown(), tradingPostStrategy,
                    new TradeRequest(source, target, sourceAmount));
        });
    }

    public TradeResult tradeAtTradingPost(GameState state, TradingPostId postId,
            ResourceType source, ResourceType target, int sourceAmount) {
        var post = state.world().tradingPosts().require(postId);
        if (!state.player().border().owns(post.position())) {
            throw new ActionFailure("Trading Post hex is outside the empire");
        }
        return commit(state, post.tradeCooldown(), tradingPostStrategy,
                new TradeRequest(source, target, sourceAmount),
                TradeEndpointKind.TRADING_POST, post.id().value());
    }

    public TradePreview previewTribe(GameState state, TribeId tribeId,
            ResourceType source, ResourceType target, int sourceAmount) {
        return safelyPreview(() -> {
            requireFriendlyTradeRelation(state, tribeId);
            var endpoint = state.world().tribeTrades().require(tribeId);
            return validateAndQuote(state, endpoint.tradeCooldown(),
                    new TribeExchangeRateStrategy(endpoint),
                    new TradeRequest(source, target, sourceAmount));
        });
    }

    public TradeResult tradeWithTribe(GameState state, TribeId tribeId,
            ResourceType source, ResourceType target, int sourceAmount) {
        requireFriendlyTradeRelation(state, tribeId);
        var endpoint = state.world().tribeTrades().require(tribeId);
        return commit(state, endpoint.tradeCooldown(),
                new TribeExchangeRateStrategy(endpoint),
                new TradeRequest(source, target, sourceAmount),
                TradeEndpointKind.TRIBE, tribeId.value());
    }

    private TradeResult commit(GameState state, TradeCooldown cooldown,
            ExchangeRateStrategy strategy, TradeRequest request,
            TradeEndpointKind kind, String endpointId) {
        TradePreview preview = validateAndQuote(state, cooldown, strategy, request);
        if (!preview.allowed()) throw new ActionFailure(preview.reason());
        TradeQuote quote = preview.quote().orElseThrow();
        state.player().townHall().stockpile().exchangeExact(
                request.source(), quote.sourceAmount(), request.target(), quote.targetAmount());
        cooldown.markUsed(state.turn().turnNumber());
        if (kind == TradeEndpointKind.TRIBE) {
            new RelationEventPolicy().tribeTrade(state, new TribeId(endpointId));
        }
        state.incrementVersion();
        TradeResult result = new TradeResult(kind, endpointId, request.source(), request.target(),
                quote.sourceAmount(), quote.targetAmount(), quote.ratePercent(),
                state.turn().turnNumber());
        eventBus.publish(new TradeCompletedEvent(result));
        return result;
    }

    private static TradePreview validateAndQuote(GameState state, TradeCooldown cooldown,
            ExchangeRateStrategy strategy, TradeRequest request) {
        var phase = ValidationSupport.playerActionPhase(state);
        if (!phase.allowed()) return TradePreview.reject(phase.reason());
        if (!cooldown.isAvailable(state.turn().turnNumber())) {
            return TradePreview.reject("Trade endpoint was already used this turn");
        }
        TradeQuote quote = strategy.quote(request);
        if (quote.targetAmount() == 0) {
            return TradePreview.reject("Trade amount is too small to produce one target resource");
        }
        ResourceStockpile stockpile = state.player().townHall().stockpile();
        // PHASE 3 - Added in Phase 3
        if (stockpile.availableAmountOf(request.source()) < quote.sourceAmount()) {
            return TradePreview.reject("Insufficient source resources");
        }
        if (stockpile.capacityOf(request.target()) - stockpile.amountOf(request.target())
                < quote.targetAmount()) {
            return TradePreview.reject("Insufficient destination storage capacity");
        }
        return TradePreview.allow(quote);
    }

    private static Bazaar requireBazaar(GameState state, BuildingId bazaarId) {
        var building = state.player().requireBuilding(Objects.requireNonNull(bazaarId, "bazaarId"));
        if (!(building instanceof Bazaar bazaar)) throw new ActionFailure("Selected building is not a Bazaar");
        if (!bazaar.isActive()) throw new ActionFailure("Bazaar is not active");
        if (state.player().townHall().level().number() < 2) {
            throw new ActionFailure("Bazaar requires Town Hall level 2");
        }
        return bazaar;
    }

    private static void requireFriendlyTradeRelation(GameState state, TribeId tribeId) {
        var relation = state.world().relationships().require(tribeId);
        if (!relation.behavior().tradeAllowed()) {
            throw new ActionFailure("Tribe trade requires a Friendly or Allied relationship");
        }
        state.world().tribes().find(tribeId).ifPresent(tribe -> {
            if (!tribe.isDiscovered()
                    || !state.player().visibility().isCurrentlyVisible(tribe.camp().position())) {
                throw new ActionFailure("Tribe camp must be discovered and currently visible");
            }
        });
    }

    private static TradePreview safelyPreview(Supplier<TradePreview> operation) {
        try {
            return operation.get();
        } catch (ActionFailure | IllegalArgumentException | IllegalStateException failure) {
            return TradePreview.reject(failure.getMessage());
        }
    }
}
