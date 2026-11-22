package civilization.view.game;

import civilization.application.action.ActionPreview;
import civilization.model.map.HexCoord;
import civilization.model.map.HexEdge;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/** Exact coordinate/edge inputs; legality remains in application validators. */
public final class InfrastructurePanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final JSpinner firstQ=spinner(), firstR=spinner(), secondQ=spinner(), secondR=spinner();
    private final JButton buildRoad=new JButton("Build Road on Tile");
    private final JButton buildWall=new JButton("Build Wall on Exact Edge");
    private final JButton demolishRoad=new JButton("Demolish Road");
    private final JButton demolishWall=new JButton("Demolish Wall");
    private final JButton demolishBuilding=new JButton("Demolish Building");
    private final JLabel inspection=new JLabel("Select a discovered hex for inspection.");

    public InfrastructurePanel() {
        super(new GridLayout(0,2,5,5));
        setBorder(BorderFactory.createTitledBorder("Infrastructure / Hex Inspection"));
        add(new JLabel("Tile / edge first q")); add(firstQ);
        add(new JLabel("Tile / edge first r")); add(firstR);
        add(new JLabel("Edge second q")); add(secondQ);
        add(new JLabel("Edge second r")); add(secondR);
        for (JButton button : new JButton[]{buildRoad,buildWall,demolishRoad,demolishWall,
                demolishBuilding}) {
            button.setToolTipText("Select an active Builder; disabled actions explain the exact rule");
            add(button); add(new JLabel(""));
        }
        inspection.setVerticalAlignment(JLabel.TOP);
        add(inspection); add(new JLabel(""));
    }
    private static JSpinner spinner() { return new JSpinner(new SpinnerNumberModel(0,-99,99,1)); }
    public HexCoord tile() { return new HexCoord((int)firstQ.getValue(),(int)firstR.getValue()); }
    public HexEdge edge() { return new HexEdge(tile(),new HexCoord(
            (int)secondQ.getValue(),(int)secondR.getValue())); }
    public JButton buildRoadButton(){return buildRoad;} public JButton buildWallButton(){return buildWall;}
    public JButton demolishRoadButton(){return demolishRoad;}
    public JButton demolishWallButton(){return demolishWall;}
    public JButton demolishBuildingButton(){return demolishBuilding;}
    public java.util.List<JSpinner> coordinateSpinners(){return java.util.List.of(firstQ,firstR,secondQ,secondR);}
    public void showInspection(String text){inspection.setText("<html>"+text+"</html>");}
    public void roadPreview(ActionPreview p){ActionPanel.applyPreview(buildRoad,p);}
    public void wallPreview(ActionPreview p){ActionPanel.applyPreview(buildWall,p);}
    public void demolishRoadPreview(ActionPreview p){ActionPanel.applyPreview(demolishRoad,p);}
    public void demolishWallPreview(ActionPreview p){ActionPanel.applyPreview(demolishWall,p);}
    public void demolishBuildingPreview(ActionPreview p){ActionPanel.applyPreview(demolishBuilding,p);}
}
