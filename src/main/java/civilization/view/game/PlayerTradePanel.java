package civilization.view.game;

import civilization.model.economy.ResourceType;
import civilization.protocol.LobbyMemberDto;
import civilization.protocol.ResourceBundleDto;
import civilization.protocol.TradeOfferPush;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

// PHASE 3 - Added in Phase 3
public final class PlayerTradePanel extends JPanel {
    // PHASE 3 - Added in Phase 3
    private static final long serialVersionUID = 1L;
    // PHASE 3 - Added in Phase 3
    private final JComboBox<PlayerOption> target = new JComboBox<>();
    // PHASE 3 - Added in Phase 3
    private final JComboBox<ResourceType> offeredType = new JComboBox<>(ResourceType.values());
    // PHASE 3 - Added in Phase 3
    private final JSpinner offeredAmount = new JSpinner(new SpinnerNumberModel(1, 1, Integer.MAX_VALUE, 1));
    // PHASE 3 - Added in Phase 3
    private final JComboBox<ResourceType> requestedType = new JComboBox<>(ResourceType.values());
    // PHASE 3 - Added in Phase 3
    private final JSpinner requestedAmount = new JSpinner(new SpinnerNumberModel(1, 1, Integer.MAX_VALUE, 1));
    // PHASE 3 - Added in Phase 3
    private final JButton create = new JButton("Send Offer");
    // PHASE 3 - Added in Phase 3
    private final DefaultListModel<TradeOfferPush> offers = new DefaultListModel<>();
    // PHASE 3 - Added in Phase 3
    private final JList<TradeOfferPush> inbox = new JList<>(offers);
    // PHASE 3 - Added in Phase 3
    private final JButton accept = new JButton("Accept");
    // PHASE 3 - Added in Phase 3
    private final JButton reject = new JButton("Reject");
    // PHASE 3 - Added in Phase 3
    private final JButton cancel = new JButton("Cancel Mine");
    // PHASE 3 - Added in Phase 3
    private String viewerPlayerId;
    // PHASE 3 - Added in Phase 3
    private boolean activeTurn;

    // PHASE 3 - Added in Phase 3
    public PlayerTradePanel() {
        super(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder("Player Trade"));
        JPanel form = new JPanel(new GridLayout(0, 2, 4, 4));
        form.add(new JLabel("Target player")); form.add(target);
        form.add(new JLabel("Offer")); form.add(offeredType);
        form.add(new JLabel("Offer amount")); form.add(offeredAmount);
        form.add(new JLabel("Request")); form.add(requestedType);
        form.add(new JLabel("Request amount")); form.add(requestedAmount);
        form.add(new JLabel("")); form.add(create);
        add(form, BorderLayout.NORTH);
        inbox.setCellRenderer((list, value, index, selected, focused) -> {
            JLabel label = new JLabel(format(value));
            label.setOpaque(true);
            label.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            return label;
        });
        add(new JScrollPane(inbox), BorderLayout.CENTER);
        JPanel decisions = new JPanel(new GridLayout(1, 3, 4, 4));
        decisions.add(accept); decisions.add(reject); decisions.add(cancel);
        add(decisions, BorderLayout.SOUTH);
        requestedType.setSelectedItem(ResourceType.WOOD);
        inbox.addListSelectionListener(event -> refreshActions());
        refreshActions();
    }

    // PHASE 3 - Added in Phase 3
    public void setPlayers(List<LobbyMemberDto> players, String viewerId) {
        viewerPlayerId = viewerId;
        Object selected = target.getSelectedItem();
        target.removeAllItems();
        // PHASE 3 - Added in Phase 3
        players.stream().filter(player -> !player.playerId().equals(viewerId))
                .forEach(player -> target.addItem(new PlayerOption(player.playerId(), player.username())));
        if (selected instanceof PlayerOption previous) selectTarget(previous.playerId());
        refreshActions();
    }

    // PHASE 3 - Added in Phase 3
    public void displayOffer(TradeOfferPush offer) {
        Objects.requireNonNull(offer, "offer");
        // PHASE 3 - Added in Phase 3
        for (int index = 0; index < offers.size(); index++) {
            if (offers.get(index).offerId().equals(offer.offerId())) {
                offers.set(index, offer); refreshActions(); return;
            }
        }
        offers.addElement(offer);
        refreshActions();
    }

    // PHASE 3 - Added in Phase 3
    public void setCreateAction(Runnable action) {
        create.addActionListener(event -> action.run());
    }

    // PHASE 3 - Added in Phase 3
    public void setAcceptAction(Consumer<TradeOfferPush> action) {
        accept.addActionListener(event -> selectedOffer(action));
    }

    // PHASE 3 - Added in Phase 3
    public void setRejectAction(Consumer<TradeOfferPush> action) {
        reject.addActionListener(event -> selectedOffer(action));
    }

    // PHASE 3 - Added in Phase 3
    public void setCancelAction(Consumer<TradeOfferPush> action) {
        cancel.addActionListener(event -> selectedOffer(action));
    }

    // PHASE 3 - Added in Phase 3
    public String selectedTargetPlayerId() {
        PlayerOption value = (PlayerOption) target.getSelectedItem();
        if (value == null) throw new IllegalStateException("Select a trade target");
        return value.playerId();
    }

    // PHASE 3 - Added in Phase 3
    public ResourceBundleDto offeredBundle() {
        return bundle((ResourceType) offeredType.getSelectedItem(), (Integer) offeredAmount.getValue());
    }

    // PHASE 3 - Added in Phase 3
    public ResourceBundleDto requestedBundle() {
        return bundle((ResourceType) requestedType.getSelectedItem(), (Integer) requestedAmount.getValue());
    }

    // PHASE 3 - Added in Phase 3
    public JList<TradeOfferPush> inbox() { return inbox; }
    // PHASE 3 - Added in Phase 3
    public JButton createButton() { return create; }
    // PHASE 3 - Added in Phase 3
    public JButton acceptButton() { return accept; }
    // PHASE 3 - Added in Phase 3
    public JButton rejectButton() { return reject; }
    // PHASE 3 - Added in Phase 3
    public JButton cancelButton() { return cancel; }

    // PHASE 3 - Added in Phase 3
    public void setActiveTurn(boolean activeTurn) {
        this.activeTurn = activeTurn;
        refreshActions();
    }

    // PHASE 3 - Added in Phase 3
    private void selectTarget(String playerId) {
        // PHASE 3 - Added in Phase 3
        for (int index = 0; index < target.getItemCount(); index++) {
            if (target.getItemAt(index).playerId().equals(playerId)) target.setSelectedIndex(index);
        }
    }

    // PHASE 3 - Added in Phase 3
    private void selectedOffer(Consumer<TradeOfferPush> action) {
        TradeOfferPush selected = inbox.getSelectedValue();
        if (selected != null) action.accept(selected);
    }

    // PHASE 3 - Added in Phase 3
    private void refreshActions() {
        TradeOfferPush selected = inbox.getSelectedValue();
        boolean pending = selected != null && selected.status().equals("PENDING");
        setEnabledReason(create, activeTurn && target.getItemCount() > 0,
                !activeTurn ? "Trade offers can be created only during your turn"
                        : "No other player is available");
        boolean recipient = activeTurn && pending && selected.recipientPlayerId().equals(viewerPlayerId);
        setEnabledReason(accept, recipient,
                "Select a pending offer addressed to you during your turn");
        setEnabledReason(reject, recipient,
                "Select a pending offer addressed to you during your turn");
        boolean sender = activeTurn && pending && selected.senderPlayerId().equals(viewerPlayerId);
        setEnabledReason(cancel, sender,
                "Select one of your pending offers during your turn");
    }

    // PHASE 3 - Added in Phase 3
    private static void setEnabledReason(JButton button, boolean enabled, String reason) {
        button.setEnabled(enabled); button.setToolTipText(enabled ? null : reason);
    }

    // PHASE 3 - Added in Phase 3
    private static ResourceBundleDto bundle(ResourceType type, int amount) {
        return new ResourceBundleDto(Map.of(type.name(), (long) amount));
    }

    // PHASE 3 - Added in Phase 3
    private static String format(TradeOfferPush offer) {
        return "%s | %s offers %s for %s".formatted(offer.status(),
                shortId(offer.senderPlayerId()), offer.offered().quantities(),
                offer.requested().quantities());
    }

    // PHASE 3 - Added in Phase 3
    private static String shortId(String id) { return id.substring(0, Math.min(8, id.length())); }

    // PHASE 3 - Added in Phase 3
    private record PlayerOption(String playerId, String username) {
        // PHASE 3 - Added in Phase 3
        @Override public String toString() { return username; }
    }
}
