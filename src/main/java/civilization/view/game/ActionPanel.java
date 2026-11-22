package civilization.view.game;

import civilization.application.action.ActionPreview;
import civilization.application.query.BuildingSnapshot;
import civilization.application.query.UnitDetailSnapshot;
import civilization.model.building.BuildingType;
import civilization.view.render.GameIconFactory;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.EnumMap;
import java.util.Map;

public final class ActionPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final String ENABLED_TOOLTIP = "enabledTooltip";
    private final JLabel selection = new JLabel("Select a unit or building");
    private final Map<BuildingType, JButton> buildButtons = new EnumMap<>(BuildingType.class);
    private final JButton station = new JButton("Station Worker");
    private final JButton unstation = new JButton("Leave Station");
    private final JButton expandBorder = new JButton("Expand Border Here");
    private final JButton openTownHall = new JButton("Open Town Hall");
    private final JButton autoExplore = new JButton("Enable Auto-Explore");

    public ActionPanel() {
        super(new BorderLayout(6, 6));
        setBorder(BorderFactory.createTitledBorder("Actions"));
        add(selection, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new GridLayout(0, 1, 4, 4));
        for (BuildingType type : BuildingType.values()) {
            if (type == BuildingType.TOWN_HALL) {
                continue;
            }
            JButton button = new JButton("Build " + type);
            button.setIcon(GameIconFactory.building(type));
            button.setToolTipText("Select a Builder standing on a valid explored in-border hex");
            buildButtons.put(type, button);
            buttons.add(button);
        }
        buttons.add(station);
        buttons.add(unstation);
        buttons.add(expandBorder);
        buttons.add(autoExplore);
        buttons.add(openTownHall);
        station.setToolTipText("Station the selected Worker in the production building on this hex");
        unstation.setToolTipText("Return the stationed Worker to manual control without refunding AP");
        expandBorder.setToolTipText("Claim this explored hex and all six explored neighbors");
        autoExplore.setIcon(GameIconFactory.unit(civilization.model.unit.UnitType.EXPLORER));
        autoExplore.setToolTipText("Automatically choose safe Explorer moves toward unknown territory");
        openTownHall.setIcon(GameIconFactory.building(BuildingType.TOWN_HALL));
        openTownHall.setToolTipText("Open unit production and technology upgrades");
        add(buttons, BorderLayout.CENTER);
    }

    public Map<BuildingType, JButton> buildButtons() {
        return Map.copyOf(buildButtons);
    }

    public JButton stationButton() {
        return station;
    }

    public JButton unstationButton() {
        return unstation;
    }

    public JButton expandBorderButton() {
        return expandBorder;
    }

    public JButton openTownHallButton() {
        return openTownHall;
    }

    public JButton autoExploreButton() {
        return autoExplore;
    }

    public void displayAutoExplore(boolean enabled) {
        autoExplore.setText(enabled ? "Disable Auto-Explore" : "Enable Auto-Explore");
    }

    public void displaySelection(UnitDetailSnapshot unit, BuildingSnapshot building) {
        if (unit != null) {
            selection.setText("%s | AP %d/%d%s%s%s".formatted(
                    unit.type(), unit.remainingAp(), unit.apCapacity(),
                    unit.builderCharges() > 0 ? " | charges " + unit.builderCharges() : "",
                    unit.maximumHealth() > 0 ? " | HP " + unit.health() + "/" + unit.maximumHealth()
                            + " | range " + unit.attackRange() : "",
                    unit.embarked() ? " | embarked" : ""));
        } else if (building != null) {
            selection.setText("%s | %s | Workers %d/%d".formatted(
                    building.type(), building.status(),
                    building.stationedWorkers(), building.workerCapacity()));
        } else {
            selection.setText("Select a unit or building");
        }
    }

    public static void applyPreview(JButton button, ActionPreview preview) {
        if (button.getClientProperty(ENABLED_TOOLTIP) == null) {
            button.putClientProperty(ENABLED_TOOLTIP, button.getToolTipText());
        }
        button.setEnabled(preview.allowed());
        button.setToolTipText(preview.allowed()
                ? (String) button.getClientProperty(ENABLED_TOOLTIP)
                : preview.reason());
    }
}
