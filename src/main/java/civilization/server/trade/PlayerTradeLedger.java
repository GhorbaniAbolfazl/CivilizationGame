package civilization.server.trade;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// PHASE 3 - Added in Phase 3
public final class PlayerTradeLedger {
    // PHASE 3 - Added in Phase 3
    private final Map<String, PlayerTradeOffer> offers = new LinkedHashMap<>();

    // PHASE 3 - Added in Phase 3
    public void add(PlayerTradeOffer offer) {
        if (offers.putIfAbsent(offer.offerId(), offer) != null) {
            throw new IllegalStateException("Duplicate trade offer ID");
        }
    }

    // PHASE 3 - Added in Phase 3
    public PlayerTradeOffer require(String offerId) {
        PlayerTradeOffer offer = offers.get(offerId);
        if (offer == null) throw new PlayerTradeException("TRADE_OFFER_NOT_FOUND", "Trade offer does not exist");
        return offer;
    }

    // PHASE 3 - Added in Phase 3
    public boolean contains(String offerId) { return offers.containsKey(offerId); }

    // PHASE 3 - Added in Phase 3
    public List<PlayerTradeOffer> offers() { return List.copyOf(offers.values()); }
}
