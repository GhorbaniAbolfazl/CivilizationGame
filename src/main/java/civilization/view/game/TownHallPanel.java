package civilization.view.game;

import civilization.application.action.ActionPreview;
import civilization.model.technology.UpgradeId;
import civilization.model.technology.TechnologyId;
import civilization.model.building.HallLevel;
import civilization.application.query.TownHallSnapshot;
import civilization.model.unit.UnitType;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.EnumMap;
import java.util.Map;
import civilization.view.render.GameIconFactory;

public final class TownHallPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final Map<UnitType, JButton> unitButtons = new EnumMap<>(UnitType.class);
    private final Map<UpgradeId, JButton> upgradeButtons = new EnumMap<>(UpgradeId.class);
    private final Map<HallLevel, JButton> hallButtons = new EnumMap<>(HallLevel.class);
    private final Map<TechnologyId, JButton> technologyButtons = new java.util.LinkedHashMap<>();
    private final JButton cancel = new JButton("Cancel Current Production");
    private final JLabel status = new JLabel("Town Hall");

    public TownHallPanel() {
        super(new BorderLayout(4, 4));
        setBorder(BorderFactory.createTitledBorder("Town Hall Production"));
        add(status, BorderLayout.NORTH);
        JPanel commands = new JPanel(new GridLayout(0, 1, 4, 4));
        for (UnitType type : UnitType.values()) {
            JButton button = new JButton("Produce " + type);
            button.setIcon(GameIconFactory.unit(type));
            button.setToolTipText("Queue a " + type + " when resources and Unit Cap permit");
            unitButtons.put(type, button);
            commands.add(button);
        }
        for (UpgradeId id : UpgradeId.values()) {
            JButton button = new JButton("Upgrade " + id);
            button.setToolTipText("Queue this upgrade when its resources and prerequisites are met");
            upgradeButtons.put(id, button);
            commands.add(button);
        }
        for (HallLevel level : HallLevel.values()) {
            if (level == HallLevel.BASE_CAMP) continue;
            JButton button = new JButton("Upgrade Hall to " + level);
            button.setToolTipText("Queue the next Town Hall level");
            hallButtons.put(level, button);
            commands.add(button);
        }
        for (TechnologyId technology : java.util.List.of(TechnologyId.NAVIGATION,
                TechnologyId.STEEL_TOOLS, TechnologyId.DEFENSIVE_ARCHITECTURE)) {
            JButton button = new JButton("Research " + technology.value());
            button.setToolTipText("Queue this technology when prerequisites are satisfied");
            technologyButtons.put(technology, button);
            commands.add(button);
        }
        cancel.setToolTipText("Cancel the active order permanently; all paid resources remain spent");
        commands.add(cancel);
        add(new JScrollPane(commands), BorderLayout.CENTER);
        setVisible(false);
    }

    public Map<UnitType, JButton> unitButtons() {
        return Map.copyOf(unitButtons);
    }

    public Map<UpgradeId, JButton> upgradeButtons() {
        return Map.copyOf(upgradeButtons);
    }

    public Map<HallLevel, JButton> hallButtons() { return Map.copyOf(hallButtons); }
    public Map<TechnologyId, JButton> technologyButtons() { return Map.copyOf(technologyButtons); }
    public JButton cancelButton() { return cancel; }

    public void display(TownHallSnapshot hall) {
        status.setText("<html>%s | HP %d/%d | Defense %d<br>Storage %s / %s<br>%s | Tech: %s</html>".formatted(hall.level(),
                hall.health(), hall.maximumHealth(), hall.defense(),
                hall.stored(), hall.capacities(), hall.currentOrder()
                        .map(order -> "Order " + order.itemName() + ": "
                                + order.remainingTurns() + " turns").orElse("Order idle"),
                hall.researchedTechnologies().isEmpty() ? "none" : hall.researchedTechnologies()));
    }

    public void setUnitPreview(UnitType type, ActionPreview preview) {
        ActionPanel.applyPreview(unitButtons.get(type), preview);
    }

    public void setUpgradePreview(UpgradeId id, ActionPreview preview) {
        ActionPanel.applyPreview(upgradeButtons.get(id), preview);
    }
    public void setHallPreview(HallLevel level, ActionPreview preview) {
        ActionPanel.applyPreview(hallButtons.get(level), preview);
    }
    public void setTechnologyPreview(TechnologyId technology, ActionPreview preview) {
        ActionPanel.applyPreview(technologyButtons.get(technology), preview);
    }
    public void setCancelPreview(ActionPreview preview) { ActionPanel.applyPreview(cancel, preview); }
}
