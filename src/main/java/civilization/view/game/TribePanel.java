package civilization.view.game;

import civilization.application.action.ActionPreview;
import civilization.application.query.TribeSnapshot;
import civilization.application.trade.TradePreview;
import civilization.application.tribe.TribeActionAvailability;
import civilization.model.economy.ResourceType;
import civilization.model.mission.MissionStatus;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.GridLayout;

/** Rules-free tribe interaction view; previews control every command and tooltip. */
public final class TribePanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final JLabel summary = new JLabel("Select a visible tribe camp");
    private final JLabel mission = new JLabel("No mission");
    private final JLabel details = new JLabel(" ");
    private final JLabel consequences = new JLabel(
            "<html>Peace: 30 Food + 30 Wood + 30 Iron after 3 turns.<br>Alliance: relation 70+, peace, no recent failed mission.</html>");
    private final JComboBox<ResourceType> giftResource = new JComboBox<>(ResourceType.values());
    private final JSpinner giftAmount = new JSpinner(new SpinnerNumberModel(10, 5, 100, 5));
    private final JButton gift = new JButton("Send Gift");
    private final JComboBox<ResourceType> tradeSource = new JComboBox<>(ResourceType.values());
    private final JComboBox<ResourceType> tradeTarget = new JComboBox<>(ResourceType.values());
    private final JSpinner tradeAmount = new JSpinner(new SpinnerNumberModel(10, 1, 100, 1));
    private final JButton trade = new JButton("Trade");
    private final JButton missionAction = new JButton("Request Mission");
    private final JButton deliver = new JButton("Deliver Mission");
    private final JButton cancelMission = new JButton("Cancel Mission");
    private final JButton war = new JButton("Declare War");
    private final JButton peace = new JButton("Request Peace");
    private final JButton alliance = new JButton("Request Alliance");

    public TribePanel() {
        super(new GridLayout(0, 1, 4, 4));
        setBorder(BorderFactory.createTitledBorder("Neutral Tribe"));
        add(summary);
        add(mission);
        add(details);
        add(consequences);
        add(new JLabel("Gift resource / amount")); add(giftResource); add(giftAmount); add(gift);
        add(new JLabel("Trade source / target / amount"));
        add(tradeSource); add(tradeTarget); add(tradeAmount); add(trade);
        add(missionAction); add(deliver); add(cancelMission);
        add(war); add(peace); add(alliance);
    }

    public void display(TribeSnapshot tribe, TribeActionAvailability availability,
            ActionPreview giftPreview, TradePreview tradePreview,
            ActionPreview deliveryPreview) {
        summary.setText("<html><b>%s</b> | %s<br>Relation %d (%s) | %s | Camp HP %d</html>"
                .formatted(tribe.name(), tribe.type(), tribe.relationship().orElseThrow(),
                        tribe.relationshipState().orElseThrow(), tribe.stance().orElseThrow(),
                        tribe.campHealth().orElseThrow()));
        if (tribe.missionType().isPresent()) {
            mission.setText("%s | %s | deadline turn %d".formatted(
                    tribe.missionType().orElseThrow(), tribe.missionStatus().orElseThrow(),
                    tribe.missionDeadline().orElseThrow()));
        } else mission.setText("No mission");
        details.setText("<html>Trade: %s<br>Alliance bonus: %s%s</html>".formatted(
                tribe.tradeOffer().orElse("not offered by this type"), tribe.activeBonus(),
                tribe.missionType().isPresent() ? "<br>Objective: " + tribe.missionObjective()
                        + "<br>Reward: " + tribe.missionReward() : ""));
        MissionStatus status = tribe.missionStatus().orElse(null);
        missionAction.setText(status == MissionStatus.RECEIVABLE
                ? "Accept Mission" : "Request Mission");
        ActionPanel.applyPreview(gift, giftPreview);
        displayTradePreview(tradePreview);
        ActionPanel.applyPreview(missionAction,
                status == MissionStatus.RECEIVABLE ? ActionPreview.allow()
                        : status == null ? availability.mission()
                        : ActionPreview.reject("Resolve the current mission first"));
        ActionPanel.applyPreview(deliver, deliveryPreview);
        ActionPanel.applyPreview(cancelMission,
                status == MissionStatus.RECEIVABLE || status == MissionStatus.ACTIVE
                        || status == MissionStatus.READY ? ActionPreview.allow()
                        : ActionPreview.reject("No cancellable mission"));
        ActionPanel.applyPreview(war, availability.war());
        ActionPanel.applyPreview(peace, availability.peace());
        ActionPanel.applyPreview(alliance, availability.alliance());
    }

    private void displayTradePreview(TradePreview preview) {
        ActionPanel.applyPreview(trade, preview.allowed()
                ? ActionPreview.allow() : ActionPreview.reject(preview.reason()));
        if (preview.allowed()) trade.setToolTipText(preview.quote().map(quote ->
                "Receive " + quote.targetAmount() + " at " + quote.ratePercent() + "%")
                .orElse("Trade"));
    }

    public ResourceType giftResource() { return (ResourceType) giftResource.getSelectedItem(); }
    public int giftAmount() { return (Integer) giftAmount.getValue(); }
    public ResourceType tradeSource() { return (ResourceType) tradeSource.getSelectedItem(); }
    public ResourceType tradeTarget() { return (ResourceType) tradeTarget.getSelectedItem(); }
    public int tradeAmount() { return (Integer) tradeAmount.getValue(); }
    public JComboBox<ResourceType> giftResourceSelector() { return giftResource; }
    public JSpinner giftAmountSpinner() { return giftAmount; }
    public JComboBox<ResourceType> tradeSourceSelector() { return tradeSource; }
    public JComboBox<ResourceType> tradeTargetSelector() { return tradeTarget; }
    public JSpinner tradeAmountSpinner() { return tradeAmount; }
    public JButton giftButton() { return gift; }
    public JButton tradeButton() { return trade; }
    public JButton missionButton() { return missionAction; }
    public JButton deliverButton() { return deliver; }
    public JButton cancelMissionButton() { return cancelMission; }
    public JButton warButton() { return war; }
    public JButton peaceButton() { return peace; }
    public JButton allianceButton() { return alliance; }
}
