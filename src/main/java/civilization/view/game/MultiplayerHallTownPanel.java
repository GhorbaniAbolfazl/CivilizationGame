package civilization.view.game;

import civilization.protocol.VisibleUnitDto;
import java.awt.GridLayout;
import java.util.List;
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
public final class MultiplayerHallTownPanel extends JPanel {
    // PHASE 3 - Added in Phase 3
    private static final long serialVersionUID = 1L;
    // PHASE 3 - Added in Phase 3
    private final DefaultComboBoxModel<BuilderChoice> builders = new DefaultComboBoxModel<>();
    // PHASE 3 - Added in Phase 3
    private final JComboBox<BuilderChoice> builder = new JComboBox<>(builders);
    // PHASE 3 - Added in Phase 3
    private final JSpinner q = new JSpinner(new SpinnerNumberModel(0, -99, 99, 1));
    // PHASE 3 - Added in Phase 3
    private final JSpinner r = new JSpinner(new SpinnerNumberModel(0, -99, 99, 1));
    // PHASE 3 - Added in Phase 3
    private final JButton establish = new JButton("Establish Hall Town");
    // PHASE 3 - Added in Phase 3
    private Consumer<BuildSelection> action = ignored -> { };
    // PHASE 3 - Added in Phase 3
    private final JLabel activeHallCount = new JLabel("Active Hall Towns: 0");

    // PHASE 3 - Added in Phase 3
    public MultiplayerHallTownPanel() {
        super(new GridLayout(0, 2, 5, 5));
        setBorder(BorderFactory.createTitledBorder("Multiple Hall Towns"));
        add(new JLabel("Builder")); add(builder);
        add(new JLabel("Target q")); add(q);
        add(new JLabel("Target r")); add(r);
        add(new JLabel("Cost")); add(new JLabel("30 Food, 60 Wood, 50 Stone, 20 Iron"));
        add(new JLabel("Builder cost")); add(new JLabel("2 AP + 1 charge"));
        add(new JLabel("Territories")); add(activeHallCount);
        add(establish); add(new JLabel(""));
        establish.setToolTipText("The server validates turn, ownership, AP, resources and placement");
        // PHASE 3 - Added in Phase 3
        builder.addActionListener(event -> useSelectedBuilderPosition());
        establish.addActionListener(event -> {
            BuilderChoice choice = (BuilderChoice) builder.getSelectedItem();
            if (choice != null) action.accept(new BuildSelection(
                    choice.unitId(), (int) q.getValue(), (int) r.getValue()));
        });
        setActionAvailable(false);
    }

    // PHASE 3 - Added in Phase 3
    public void setBuilders(List<VisibleUnitDto> visibleUnits, String viewerPlayerId) {
        Objects.requireNonNull(visibleUnits, "visibleUnits");
        String selected = selectedBuilderId();
        builders.removeAllElements();
        // PHASE 3 - Added in Phase 3
        visibleUnits.stream().filter(unit -> unit.ownerPlayerId().equals(viewerPlayerId))
                .filter(unit -> unit.unitType().equals("BUILDER"))
                .forEach(unit -> builders.addElement(new BuilderChoice(
                        unit.unitId(), unit.q(), unit.r(), unit.remainingActionPoints())));
        selectBuilder(selected);
        useSelectedBuilderPosition();
    }

    // PHASE 3 - Added in Phase 3
    public void setEstablishAction(Consumer<BuildSelection> action) {
        this.action = Objects.requireNonNull(action, "action");
    }

    // PHASE 3 - Added in Phase 3
    public void setActionAvailable(boolean available) {
        boolean enabled = available && builders.getSize() > 0;
        establish.setEnabled(enabled);
        establish.setToolTipText(enabled ? "The server validates ownership, cost, AP and placement"
                : !available ? "Available only during your active turn while connected"
                : "No visible owned Builder is available");
    }

    // PHASE 3 - Added in Phase 3
    public void setActiveHallCount(long count) { activeHallCount.setText("Active Hall Towns: " + count); }

    // PHASE 3 - Added in Phase 3
    public JButton establishButton() { return establish; }

    // PHASE 3 - Added in Phase 3
    private String selectedBuilderId() {
        BuilderChoice choice = (BuilderChoice) builder.getSelectedItem();
        return choice == null ? null : choice.unitId();
    }

    // PHASE 3 - Added in Phase 3
    private void selectBuilder(String unitId) {
        if (unitId == null) return;
        // PHASE 3 - Added in Phase 3
        for (int index = 0; index < builders.getSize(); index++) {
            if (builders.getElementAt(index).unitId().equals(unitId)) {
                builder.setSelectedIndex(index);
                return;
            }
        }
    }

    // PHASE 3 - Added in Phase 3
    private void useSelectedBuilderPosition() {
        BuilderChoice choice = (BuilderChoice) builder.getSelectedItem();
        if (choice == null) return;
        q.setValue(choice.q()); r.setValue(choice.r());
    }

    // PHASE 3 - Added in Phase 3
    private record BuilderChoice(
            // PHASE 3 - Added in Phase 3
            String unitId,
            // PHASE 3 - Added in Phase 3
            int q,
            // PHASE 3 - Added in Phase 3
            int r,
            // PHASE 3 - Added in Phase 3
            int actionPoints) {
        // PHASE 3 - Added in Phase 3
        @Override public String toString() {
            String shortId = unitId.length() > 8 ? unitId.substring(0, 8) : unitId;
            return shortId + " @ " + q + "," + r + " (AP " + actionPoints + ")";
        }
    }

    // PHASE 3 - Added in Phase 3
    public record BuildSelection(
            // PHASE 3 - Added in Phase 3
            String builderId,
            // PHASE 3 - Added in Phase 3
            int q,
            // PHASE 3 - Added in Phase 3
            int r) { }
}
