package civilization.server.trade;

import civilization.model.economy.ResourceBundle;
import civilization.protocol.MessageMetadata;
import civilization.protocol.ResourceBundleDto;
import civilization.protocol.TradeOfferPush;
import java.util.LinkedHashMap;

// PHASE 3 - Added in Phase 3
public final class PlayerTradeProtocolMapper {
    // PHASE 3 - Added in Phase 3
    public TradeOfferPush toPush(String matchId, long stateVersion, PlayerTradeOffer offer) {
        return new TradeOfferPush(MessageMetadata.createNow(), matchId, stateVersion,
                offer.offerId(), offer.senderPlayerId().toString(),
                offer.recipientPlayerId().toString(), bundle(offer.offered()),
                bundle(offer.requested()), offer.status().name(), offer.createdAt());
    }

    // PHASE 3 - Added in Phase 3
    private static ResourceBundleDto bundle(ResourceBundle resources) {
        LinkedHashMap<String, Long> values = new LinkedHashMap<>();
        // PHASE 3 - Added in Phase 3
        resources.asMap().forEach((type, amount) -> {
            if (amount > 0) values.put(type.name(), (long) amount);
        });
        return new ResourceBundleDto(values);
    }
}
