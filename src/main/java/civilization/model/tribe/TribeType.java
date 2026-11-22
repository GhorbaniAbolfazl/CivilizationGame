package civilization.model.tribe;

import civilization.model.trade.TribeTradeProfile;
import java.util.Optional;

public enum TribeType {
    FARMER(40, TribeTradeProfile.FARMER, 3),
    WARRIOR(70, null, 5),
    TRADER(50, TribeTradeProfile.TRADER, 3),
    MOUNTAIN(60, TribeTradeProfile.MOUNTAIN, 3),
    COASTAL(50, TribeTradeProfile.COASTAL, 3);

    private final int campHealth;
    private final TribeTradeProfile tradeProfile;
    private final int guardCap;

    TribeType(int campHealth, TribeTradeProfile tradeProfile, int guardCap) {
        this.campHealth = campHealth;
        this.tradeProfile = tradeProfile;
        this.guardCap = guardCap;
    }

    public int campHealth() { return campHealth; }
    public Optional<TribeTradeProfile> tradeProfile() { return Optional.ofNullable(tradeProfile); }
    public int guardCap() { return guardCap; }
}
