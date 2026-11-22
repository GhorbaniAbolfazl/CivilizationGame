package civilization.view.game;

import civilization.model.item.ItemType;
import civilization.protocol.GameSnapshotDto;
import civilization.protocol.InventoryItemDto;
import civilization.protocol.VisibleBuildingDto;
import civilization.protocol.VisibleUnitDto;
import java.awt.GridLayout;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

// PHASE 3 - Added in Phase 3
public final class MultiplayerItemPanel extends JPanel {
    // PHASE 3 - Added in Phase 3
    private static final long serialVersionUID = 1L;
    // PHASE 3 - Added in Phase 3
    private final DefaultComboBoxModel<EntityChoice> builders = new DefaultComboBoxModel<>();
    // PHASE 3 - Added in Phase 3
    private final DefaultComboBoxModel<EntityChoice> apothecaries = new DefaultComboBoxModel<>();
    // PHASE 3 - Added in Phase 3
    private final DefaultComboBoxModel<EntityChoice> units = new DefaultComboBoxModel<>();
    // PHASE 3 - Added in Phase 3
    private final DefaultComboBoxModel<ItemChoice> inventory = new DefaultComboBoxModel<>();
    // PHASE 3 - Added in Phase 3
    private final JComboBox<EntityChoice> builder = new JComboBox<>(builders);
    // PHASE 3 - Added in Phase 3
    private final JComboBox<EntityChoice> apothecary = new JComboBox<>(apothecaries);
    // PHASE 3 - Added in Phase 3
    private final JComboBox<EntityChoice> unit = new JComboBox<>(units);
    // PHASE 3 - Added in Phase 3
    private final JComboBox<ItemChoice> item = new JComboBox<>(inventory);
    // PHASE 3 - Added in Phase 3
    private final JComboBox<ItemType> craftType = new JComboBox<>(ItemType.values());
    // PHASE 3 - Added in Phase 3
    private final JSpinner q = new JSpinner(new SpinnerNumberModel(0, -99, 99, 1));
    // PHASE 3 - Added in Phase 3
    private final JSpinner r = new JSpinner(new SpinnerNumberModel(0, -99, 99, 1));
    // PHASE 3 - Added in Phase 3
    private final JButton build = new JButton("Build Apothecary");
    // PHASE 3 - Added in Phase 3
    private final JButton craft = new JButton("Craft (1 Turn)");
    // PHASE 3 - Added in Phase 3
    private final JButton use = new JButton("Use Item");
    // PHASE 3 - Added in Phase 3
    private final JLabel queueStatus = new JLabel("Queues idle");
    // PHASE 3 - Added in Phase 3
    private Consumer<BuildSelection> buildAction = ignored -> { };
    // PHASE 3 - Added in Phase 3
    private Consumer<CraftSelection> craftAction = ignored -> { };
    // PHASE 3 - Added in Phase 3
    private Consumer<UseSelection> useAction = ignored -> { };

    // PHASE 3 - Added in Phase 3
    public MultiplayerItemPanel() {
        super(new GridLayout(0, 2, 5, 5));
        setBorder(BorderFactory.createTitledBorder("Apothecary / Items"));
        add(new JLabel("Builder")); add(builder);
        add(new JLabel("Target q")); add(q);
        add(new JLabel("Target r")); add(r);
        add(build); add(new JLabel("Plains; Hall level 2"));
        add(new JLabel("Apothecary")); add(apothecary);
        add(new JLabel("Queue status")); add(queueStatus);
        add(new JLabel("Item recipe")); add(craftType);
        add(craft); add(new JLabel("Independent 1-Turn queue"));
        add(new JLabel("Inventory")); add(item);
        add(new JLabel("Target unit")); add(unit);
        add(use); add(new JLabel("Teleport uses q/r; use costs 0 AP"));
        // PHASE 3 - Added in Phase 3
        builder.addActionListener(event -> copyPosition((EntityChoice) builder.getSelectedItem()));
        unit.addActionListener(event -> copyPosition((EntityChoice) unit.getSelectedItem()));
        build.addActionListener(event -> {
            EntityChoice selected = (EntityChoice) builder.getSelectedItem();
            if (selected != null) buildAction.accept(new BuildSelection(selected.id(),
                    (int) q.getValue(), (int) r.getValue()));
        });
        craft.addActionListener(event -> {
            EntityChoice selected = (EntityChoice) apothecary.getSelectedItem();
            ItemType type = (ItemType) craftType.getSelectedItem();
            if (selected != null && type != null) {
                craftAction.accept(new CraftSelection(selected.id(), type.name()));
            }
        });
        use.addActionListener(event -> {
            ItemChoice selectedItem = (ItemChoice) item.getSelectedItem();
            EntityChoice selectedUnit = (EntityChoice) unit.getSelectedItem();
            if (selectedItem != null && selectedUnit != null) {
                useAction.accept(new UseSelection(selectedItem.id(), selectedUnit.id(),
                        (int) q.getValue(), (int) r.getValue()));
            }
        });
        setActionsAvailable(false);
    }

    // PHASE 3 - Added in Phase 3
    public void display(GameSnapshotDto snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        builders.removeAllElements(); apothecaries.removeAllElements();
        units.removeAllElements(); inventory.removeAllElements();
        // PHASE 3 - Added in Phase 3
        snapshot.visibleUnits().stream()
                .filter(value -> value.ownerPlayerId().equals(snapshot.viewerPlayerId()))
                .forEach(value -> {
                    EntityChoice choice = unitChoice(value);
                    units.addElement(choice);
                    if (value.unitType().equals("BUILDER")) builders.addElement(choice);
                });
        // PHASE 3 - Added in Phase 3
        snapshot.visibleBuildings().stream()
                .filter(value -> value.ownerPlayerId().equals(snapshot.viewerPlayerId()))
                .filter(value -> value.buildingType().equals("APOTHECARY"))
                .filter(value -> value.status().equals("ACTIVE"))
                .map(MultiplayerItemPanel::buildingChoice).forEach(apothecaries::addElement);
        snapshot.viewerItems().stream().map(MultiplayerItemPanel::itemChoice)
                .forEach(inventory::addElement);
        // PHASE 3 - Added in Phase 3
        queueStatus.setText(snapshot.apothecaryQueues().isEmpty() ? "Queues idle"
                : snapshot.apothecaryQueues().stream()
                .map(queue -> queue.itemType() + ": " + queue.remainingTurns() + " turn")
                .collect(java.util.stream.Collectors.joining(", ")));
    }

    // PHASE 3 - Added in Phase 3
    public void setActionsAvailable(boolean activeTurn) {
        setEnabledReason(build, activeTurn && builders.getSize() > 0,
                !activeTurn ? "Available only during your active turn while connected"
                        : "An owned visible Builder is required");
        setEnabledReason(craft, activeTurn && apothecaries.getSize() > 0,
                !activeTurn ? "Available only during your active turn while connected"
                        : "Build an active Apothecary first (Hall Town level 2 required)");
        setEnabledReason(use, activeTurn && inventory.getSize() > 0 && units.getSize() > 0,
                !activeTurn ? "Available only during your active turn while connected"
                        : inventory.getSize() == 0 ? "Your server inventory contains no item"
                        : "An owned active target unit is required");
    }

    // PHASE 3 - Added in Phase 3
    private static void setEnabledReason(JButton button, boolean enabled, String reason) {
        button.setEnabled(enabled); button.setToolTipText(enabled ? null : reason);
    }

    // PHASE 3 - Added in Phase 3
    public JButton buildButton() { return build; }
    // PHASE 3 - Added in Phase 3
    public JButton craftButton() { return craft; }
    // PHASE 3 - Added in Phase 3
    public JButton useButton() { return use; }

    // PHASE 3 - Added in Phase 3
    public void setBuildAction(Consumer<BuildSelection> action) { buildAction = Objects.requireNonNull(action); }
    // PHASE 3 - Added in Phase 3
    public void setCraftAction(Consumer<CraftSelection> action) { craftAction = Objects.requireNonNull(action); }
    // PHASE 3 - Added in Phase 3
    public void setUseAction(Consumer<UseSelection> action) { useAction = Objects.requireNonNull(action); }

    // PHASE 3 - Added in Phase 3
    private void copyPosition(EntityChoice selected) {
        if (selected != null) { q.setValue(selected.q()); r.setValue(selected.r()); }
    }

    // PHASE 3 - Added in Phase 3
    private static EntityChoice unitChoice(VisibleUnitDto value) {
        return new EntityChoice(value.unitId(), value.unitType(), value.q(), value.r());
    }

    // PHASE 3 - Added in Phase 3
    private static EntityChoice buildingChoice(VisibleBuildingDto value) {
        return new EntityChoice(value.buildingId(), "Apothecary", value.q(), value.r());
    }

    // PHASE 3 - Added in Phase 3
    private static ItemChoice itemChoice(InventoryItemDto value) {
        return new ItemChoice(value.itemId(), value.itemType());
    }

    // PHASE 3 - Added in Phase 3
    private record EntityChoice(
            // PHASE 3 - Added in Phase 3
            String id,
            // PHASE 3 - Added in Phase 3
            String label,
            // PHASE 3 - Added in Phase 3
            int q,
            // PHASE 3 - Added in Phase 3
            int r) {
        // PHASE 3 - Added in Phase 3
        @Override public String toString() { return label + " @ " + q + "," + r; }
    }
    // PHASE 3 - Added in Phase 3
    private record ItemChoice(
            // PHASE 3 - Added in Phase 3
            String id,
            // PHASE 3 - Added in Phase 3
            String type) {
        // PHASE 3 - Added in Phase 3
        @Override public String toString() { return type + " — " + id.substring(0, Math.min(8, id.length())); }
    }
    // PHASE 3 - Added in Phase 3
    public record BuildSelection(
            // PHASE 3 - Added in Phase 3
            String builderId,
            // PHASE 3 - Added in Phase 3
            int q,
            // PHASE 3 - Added in Phase 3
            int r) { }
    // PHASE 3 - Added in Phase 3
    public record CraftSelection(
            // PHASE 3 - Added in Phase 3
            String apothecaryId,
            // PHASE 3 - Added in Phase 3
            String itemType) { }
    // PHASE 3 - Added in Phase 3
    public record UseSelection(
            // PHASE 3 - Added in Phase 3
            String itemId,
            // PHASE 3 - Added in Phase 3
            String unitId,
            // PHASE 3 - Added in Phase 3
            int q,
            // PHASE 3 - Added in Phase 3
            int r) { }
}
