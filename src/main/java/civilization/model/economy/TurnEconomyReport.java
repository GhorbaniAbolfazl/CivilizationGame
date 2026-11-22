package civilization.model.economy;

import java.util.Objects;

public record TurnEconomyReport(
        ResourceBundle produced,
        ResourceBundle consumed,
        ResourceBundle accepted,
        ResourceBundle overflow,
        ResourceBundle shortage) implements java.io.Serializable {

    public static final TurnEconomyReport EMPTY = new TurnEconomyReport(
            ResourceBundle.ZERO,
            ResourceBundle.ZERO,
            ResourceBundle.ZERO,
            ResourceBundle.ZERO,
            ResourceBundle.ZERO);

    public TurnEconomyReport {
        Objects.requireNonNull(produced, "produced");
        Objects.requireNonNull(consumed, "consumed");
        Objects.requireNonNull(accepted, "accepted");
        Objects.requireNonNull(overflow, "overflow");
        Objects.requireNonNull(shortage, "shortage");
    }

    public int net(ResourceType type) {
        return produced.amountOf(type) - consumed.amountOf(type);
    }
}
