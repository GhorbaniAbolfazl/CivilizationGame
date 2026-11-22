package civilization.server.trade;

import civilization.model.economy.ResourceBundle;
import civilization.model.economy.ResourceStockpile;
import civilization.model.economy.ResourceType;
import civilization.protocol.ResourceBundleDto;
import civilization.server.match.MultiplayerGameState;
import civilization.server.match.MultiplayerPlayerState;
import civilization.server.session.PlayerId;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Objects;
import java.util.UUID;

// PHASE 3 - Added in Phase 3
public final class PlayerTradeService {
    // PHASE 3 - Added in Phase 3
    private final Clock clock;

    // PHASE 3 - Added in Phase 3
    public PlayerTradeService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // PHASE 3 - Added in Phase 3
    public PlayerTradeResult create(MultiplayerGameState state, PlayerId senderId,
                                    String recipientValue, ResourceBundleDto offeredDto,
                                    ResourceBundleDto requestedDto) {
        Objects.requireNonNull(state, "state");
        MultiplayerPlayerState sender = state.requirePlayer(senderId);
        PlayerId recipientId = parsePlayerId(recipientValue);
        if (senderId.equals(recipientId)) {
            throw new PlayerTradeException("INVALID_TRADE_TARGET", "Players cannot trade with themselves");
        }
        MultiplayerPlayerState recipient = requirePlayer(state, recipientId);
        if (!state.connectedPlayers().contains(recipientId)) {
            throw new PlayerTradeException("TRADE_TARGET_DISCONNECTED", "Trade target is disconnected");
        }
        ResourceBundle offered = toBundle(offeredDto, "offered");
        ResourceBundle requested = toBundle(requestedDto, "requested");
        if (offered.isZero() || requested.isZero()) {
            throw new PlayerTradeException("INVALID_TRADE_OFFER", "Offered and requested bundles must each contain resources");
        }
        ResourceStockpile stockpile = sender.empire().townHall().stockpile();
        if (!stockpile.canReserve(offered)) {
            throw new PlayerTradeException("INSUFFICIENT_TRADE_RESOURCES", "Insufficient available offered resources");
        }
        String offerId;
        // PHASE 3 - Added in Phase 3
        do { offerId = UUID.randomUUID().toString(); }
        while (state.tradeLedger().contains(offerId));
        PlayerTradeOffer offer = new PlayerTradeOffer(offerId, senderId,
                recipientId, offered, requested, Instant.now(clock));
        // PHASE 3 - Added in Phase 3
        stockpile.reserve(offered);
        state.tradeLedger().add(offer);
        return result(offer, sender, recipient);
    }

    // PHASE 3 - Added in Phase 3
    public PlayerTradeResult accept(MultiplayerGameState state, PlayerId actorId,
                                    String offerId) {
        PlayerTradeOffer offer = requirePending(state, offerId);
        if (!offer.recipientPlayerId().equals(actorId)) {
            throw new PlayerTradeException("UNAUTHORIZED_TRADE_ACTION", "Only the trade recipient may accept this offer");
        }
        MultiplayerPlayerState sender = state.requirePlayer(offer.senderPlayerId());
        MultiplayerPlayerState recipient = state.requirePlayer(actorId);
        try {
            // PHASE 3 - Added in Phase 3
            ResourceStockpile.settleReservedExchange(sender.empire().townHall().stockpile(),
                    offer.offered(), recipient.empire().townHall().stockpile(), offer.requested());
        } catch (IllegalStateException failure) {
            throw new PlayerTradeException("TRADE_RESOURCES_UNAVAILABLE", failure.getMessage());
        }
        offer.close(PlayerTradeStatus.ACCEPTED);
        return result(offer, sender, recipient);
    }

    // PHASE 3 - Added in Phase 3
    public PlayerTradeResult reject(MultiplayerGameState state, PlayerId actorId,
                                    String offerId) {
        PlayerTradeOffer offer = requirePending(state, offerId);
        if (!offer.recipientPlayerId().equals(actorId)) {
            throw new PlayerTradeException("UNAUTHORIZED_TRADE_ACTION", "Only the trade recipient may reject this offer");
        }
        MultiplayerPlayerState sender = state.requirePlayer(offer.senderPlayerId());
        MultiplayerPlayerState recipient = state.requirePlayer(offer.recipientPlayerId());
        sender.empire().townHall().stockpile().releaseReservation(offer.offered());
        offer.close(PlayerTradeStatus.REJECTED);
        return result(offer, sender, recipient);
    }

    // PHASE 3 - Added in Phase 3
    public PlayerTradeResult cancel(MultiplayerGameState state, PlayerId actorId,
                                    String offerId) {
        PlayerTradeOffer offer = requirePending(state, offerId);
        if (!offer.senderPlayerId().equals(actorId)) {
            throw new PlayerTradeException("UNAUTHORIZED_TRADE_ACTION", "Only the trade sender may cancel this offer");
        }
        MultiplayerPlayerState sender = state.requirePlayer(offer.senderPlayerId());
        MultiplayerPlayerState recipient = state.requirePlayer(offer.recipientPlayerId());
        sender.empire().townHall().stockpile().releaseReservation(offer.offered());
        offer.close(PlayerTradeStatus.CANCELLED);
        return result(offer, sender, recipient);
    }

    // PHASE 3 - Added in Phase 3
    private static PlayerTradeOffer requirePending(MultiplayerGameState state, String offerId) {
        PlayerTradeOffer offer = state.tradeLedger().require(offerId);
        if (offer.status() != PlayerTradeStatus.PENDING) {
            throw new PlayerTradeException("TRADE_OFFER_CLOSED", "Trade offer is no longer pending");
        }
        return offer;
    }

    // PHASE 3 - Added in Phase 3
    private static ResourceBundle toBundle(ResourceBundleDto dto, String label) {
        if (dto == null) throw new PlayerTradeException("INVALID_TRADE_OFFER", label + " bundle is required");
        EnumMap<ResourceType, Integer> quantities = new EnumMap<>(ResourceType.class);
        // PHASE 3 - Added in Phase 3
        for (var entry : dto.quantities().entrySet()) {
            ResourceType type;
            try { type = ResourceType.valueOf(entry.getKey()); }
            catch (IllegalArgumentException failure) {
                throw new PlayerTradeException("INVALID_TRADE_RESOURCE", "Unknown resource: " + entry.getKey());
            }
            if (entry.getValue() > Integer.MAX_VALUE) {
                throw new PlayerTradeException("INVALID_TRADE_QUANTITY", "Trade quantity is too large");
            }
            quantities.put(type, entry.getValue().intValue());
        }
        return new ResourceBundle(quantities);
    }

    // PHASE 3 - Added in Phase 3
    private static PlayerId parsePlayerId(String value) {
        try { return new PlayerId(UUID.fromString(value)); }
        catch (RuntimeException failure) {
            throw new PlayerTradeException("INVALID_TRADE_TARGET", "Trade target ID is invalid");
        }
    }

    // PHASE 3 - Added in Phase 3
    private static MultiplayerPlayerState requirePlayer(MultiplayerGameState state, PlayerId id) {
        try { return state.requirePlayer(id); }
        catch (IllegalArgumentException failure) {
            throw new PlayerTradeException("INVALID_TRADE_TARGET", "Trade target is not in this match");
        }
    }

    // PHASE 3 - Added in Phase 3
    private static PlayerTradeResult result(PlayerTradeOffer offer,
                                            MultiplayerPlayerState sender,
                                            MultiplayerPlayerState recipient) {
        return new PlayerTradeResult(offer, sender.sessionId(), recipient.sessionId());
    }
}
