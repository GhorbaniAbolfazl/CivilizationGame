package civilization.view.game;

import civilization.application.trade.TradePreview;
import civilization.model.economy.ResourceType;
import civilization.model.trade.BazaarTradeLevel;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.GridLayout;

/** Trade input/output surface only; all quotes and validation come from GameEngine. */
public final class TradePanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final JLabel endpoint = new JLabel("Select a Bazaar or Trading Center");
    private final JComboBox<ResourceType> source = new JComboBox<>(ResourceType.values());
    private final JComboBox<ResourceType> target = new JComboBox<>(ResourceType.values());
    private final JComboBox<BazaarTradeLevel> level = new JComboBox<>(BazaarTradeLevel.values());
    private final JSpinner amount = new JSpinner(
            new SpinnerNumberModel(2, 1, Integer.MAX_VALUE, 1));
    private final JLabel quote = new JLabel(" ");
    private final JButton trade = new JButton("Trade Now");

    public TradePanel() {
        super(new GridLayout(0, 1, 5, 5));
        setBorder(BorderFactory.createTitledBorder("Trade"));
        target.setSelectedItem(ResourceType.WOOD);
        add(endpoint);
        add(new JLabel("Sell resource"));
        add(source);
        add(new JLabel("Receive resource"));
        add(target);
        add(new JLabel("Bazaar level"));
        add(level);
        add(new JLabel("Trading Center amount"));
        add(amount);
        add(quote);
        add(trade);
    }

    public JComboBox<ResourceType> sourceSelector() { return source; }
    public JComboBox<ResourceType> targetSelector() { return target; }
    public JComboBox<BazaarTradeLevel> levelSelector() { return level; }
    public JSpinner amountSpinner() { return amount; }
    public JButton tradeButton() { return trade; }

    public ResourceType selectedSource() { return (ResourceType) source.getSelectedItem(); }
    public ResourceType selectedTarget() { return (ResourceType) target.getSelectedItem(); }
    public BazaarTradeLevel selectedLevel() { return (BazaarTradeLevel) level.getSelectedItem(); }
    public int selectedAmount() { return (Integer) amount.getValue(); }

    public void displayEndpoint(String label, boolean bazaar) {
        endpoint.setText(label);
        level.setEnabled(bazaar);
        amount.setEnabled(!bazaar);
    }

    public void displayPreview(TradePreview preview) {
        if (preview.allowed()) {
            var value = preview.quote().orElseThrow();
            quote.setText("Spend %d, receive %d (%d%%)".formatted(
                    value.sourceAmount(), value.targetAmount(), value.ratePercent()));
        } else {
            quote.setText(preview.reason());
        }
        trade.setEnabled(preview.allowed());
        trade.setToolTipText(preview.allowed() ? "Commit this immediate transaction" : preview.reason());
    }
}
