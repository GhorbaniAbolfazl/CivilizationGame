package civilization.model.trade;

import civilization.model.identity.TribeId;
import java.util.Objects;

/** Minimal tribe integration state; the later Tribe aggregate can own the same endpoint. */
public final class TribeTradeEndpoint implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final TribeId tribeId;
    private final TribeTradeProfile profile;
    private final TradeCooldown tradeCooldown;
    private int rateBonusPercent;

    public TribeTradeEndpoint(TribeId tribeId, TribeTradeProfile profile) {
        this(tribeId, profile, new TradeCooldown(), 0);
    }

    public TribeTradeEndpoint(TribeId tribeId, TribeTradeProfile profile,
            TradeCooldown tradeCooldown, int rateBonusPercent) {
        this.tribeId = Objects.requireNonNull(tribeId, "tribeId");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.tradeCooldown = Objects.requireNonNull(tradeCooldown, "tradeCooldown");
        if (rateBonusPercent < 0 || rateBonusPercent > 20) {
            throw new IllegalArgumentException("Tribe trade bonus must be between 0 and 20");
        }
        this.rateBonusPercent = rateBonusPercent;
    }

    public TribeId tribeId() { return tribeId; }
    public TribeTradeProfile profile() { return profile; }
    public TradeCooldown tradeCooldown() { return tradeCooldown; }
    public int rateBonusPercent() { return rateBonusPercent; }

    /** Hook for the Trader-road mission's permanent +10 percentage-point reward. */
    public void increaseRateBonus(int percentagePoints) {
        if (percentagePoints <= 0) throw new IllegalArgumentException("Rate bonus must be positive");
        int increased = Math.addExact(rateBonusPercent, percentagePoints);
        if (increased > 20) {
            throw new IllegalStateException("Tribe trade bonus exceeds the supported range");
        }
        rateBonusPercent = increased;
    }
}
