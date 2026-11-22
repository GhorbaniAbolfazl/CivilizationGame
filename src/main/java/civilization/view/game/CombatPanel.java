package civilization.view.game;

import civilization.model.combat.CombatResult;
import civilization.application.action.ActionPreview;
import civilization.model.combat.AttackTarget;
import civilization.model.map.HexCoord;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.JTextArea;
import civilization.protocol.BattleReportPush;
import civilization.protocol.GameSnapshotDto;
import civilization.protocol.VisibleBuildingDto;
import civilization.protocol.VisibleUnitDto;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import java.util.function.Consumer;

/** Commands plus a presentation-only rendering of the model's immutable combat result. */
public final class CombatPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final JTextArea trace = new JTextArea("No combat has been resolved.");
    private final JSpinner q = new JSpinner(new SpinnerNumberModel(0, -99, 99, 1));
    private final JSpinner r = new JSpinner(new SpinnerNumberModel(0, -99, 99, 1));
    private final JComboBox<AttackTarget> target = new JComboBox<>(AttackTarget.values());
    private final JButton attack = new JButton("Attack Target Hex");
    private final JLabel summary = new JLabel("No combat has been resolved.");
    private final DiceCanvas dice = new DiceCanvas();
    // PHASE 3 - Added in Phase 3
    private final DefaultListModel<EntityChoice> multiplayerAttackers = new DefaultListModel<>();
    // PHASE 3 - Added in Phase 3
    private final JList<EntityChoice> multiplayerAttackerList = new JList<>(multiplayerAttackers);
    // PHASE 3 - Added in Phase 3
    private final DefaultComboBoxModel<EntityChoice> multiplayerTargets = new DefaultComboBoxModel<>();
    // PHASE 3 - Added in Phase 3
    private final JComboBox<EntityChoice> multiplayerTarget = new JComboBox<>(multiplayerTargets);
    // PHASE 3 - Added in Phase 3
    private final JButton multiplayerAttack = new JButton("Send PvP Attack Request");
    // PHASE 3 - Added in Phase 3
    private Consumer<MultiplayerAttackSelection> multiplayerAction = ignored -> { };

    public CombatPanel() {
        super(new BorderLayout(5, 5));
        JPanel command = new JPanel(new FlowLayout(FlowLayout.LEFT));
        command.add(new JLabel("Target q")); command.add(q);
        command.add(new JLabel("r")); command.add(r);
        command.add(target); command.add(attack);
        attack.setToolTipText("Select one of your military units, then enter a visible target hex");
        JPanel commandArea = new JPanel(new BorderLayout(4, 4));
        commandArea.add(command, BorderLayout.NORTH);
        // PHASE 3 - Added in Phase 3
        JPanel multiplayer = new JPanel(new BorderLayout(4, 4));
        multiplayer.setBorder(BorderFactory.createTitledBorder("Authoritative PvP"));
        multiplayerAttackerList.setVisibleRowCount(3);
        multiplayer.add(new JScrollPane(multiplayerAttackerList), BorderLayout.CENTER);
        JPanel multiplayerSubmit = new JPanel(new BorderLayout(4, 0));
        multiplayerSubmit.add(multiplayerTarget, BorderLayout.CENTER);
        multiplayerSubmit.add(multiplayerAttack, BorderLayout.EAST);
        multiplayer.add(multiplayerSubmit, BorderLayout.SOUTH);
        commandArea.add(multiplayer, BorderLayout.CENTER);
        add(commandArea, BorderLayout.NORTH);
        JPanel result = new JPanel(new BorderLayout(4, 4));
        result.setBorder(BorderFactory.createTitledBorder("Combat Dice and Result"));
        result.add(summary, BorderLayout.NORTH);
        result.add(dice, BorderLayout.CENTER);
        trace.setEditable(false);
        trace.setLineWrap(true);
        trace.setWrapStyleWord(true);
        trace.setRows(8);
        trace.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        result.add(new JScrollPane(trace), BorderLayout.SOUTH);
        add(result, BorderLayout.CENTER);
        // PHASE 3 - Added in Phase 3
        multiplayerAttack.addActionListener(event -> {
            List<String> attackers = multiplayerAttackerList.getSelectedValuesList().stream()
                    .map(EntityChoice::id).toList();
            EntityChoice selectedTarget = (EntityChoice) multiplayerTarget.getSelectedItem();
            if (!attackers.isEmpty() && selectedTarget != null) {
                multiplayerAction.accept(new MultiplayerAttackSelection(attackers, selectedTarget.id()));
            }
        });
        setMultiplayerActionAvailable(false);
    }

    public HexCoord targetHex() { return new HexCoord((int) q.getValue(), (int) r.getValue()); }
    public AttackTarget selectedTarget() { return (AttackTarget) target.getSelectedItem(); }
    public JButton attackButton() { return attack; }
    public JSpinner qSpinner() { return q; }
    public JSpinner rSpinner() { return r; }
    public JComboBox<AttackTarget> targetSelector() { return target; }
    public void setAttackPreview(ActionPreview preview) { ActionPanel.applyPreview(attack, preview); }

    // PHASE 3 - Added in Phase 3
    public void setMultiplayerAction(Consumer<MultiplayerAttackSelection> action) {
        multiplayerAction = Objects.requireNonNull(action, "action");
    }

    // PHASE 3 - Added in Phase 3
    public void displayMultiplayerTargets(GameSnapshotDto snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        List<String> selected = multiplayerAttackerList.getSelectedValuesList().stream()
                .map(EntityChoice::id).toList();
        multiplayerAttackers.clear(); multiplayerTargets.removeAllElements();
        // PHASE 3 - Added in Phase 3
        snapshot.visibleUnits().forEach(unit -> {
            EntityChoice choice = unitChoice(unit);
            if (unit.ownerPlayerId().equals(snapshot.viewerPlayerId()) && isMilitary(unit.unitType())) {
                multiplayerAttackers.addElement(choice);
            } else if (!unit.ownerPlayerId().equals(snapshot.viewerPlayerId())) {
                multiplayerTargets.addElement(choice);
            }
        });
        // PHASE 3 - Added in Phase 3
        snapshot.visibleBuildings().stream()
                .filter(building -> !building.ownerPlayerId().equals(snapshot.viewerPlayerId()))
                .map(CombatPanel::buildingChoice).forEach(multiplayerTargets::addElement);
        // PHASE 3 - Added in Phase 3
        for (int index = 0; index < multiplayerAttackers.size(); index++) {
            if (selected.contains(multiplayerAttackers.get(index).id())) {
                multiplayerAttackerList.addSelectionInterval(index, index);
            }
        }
    }

    // PHASE 3 - Added in Phase 3
    public void setMultiplayerActionAvailable(boolean activeTurn) {
        boolean enabled = activeTurn && !multiplayerAttackers.isEmpty()
                && multiplayerTargets.getSize() > 0;
        multiplayerAttack.setEnabled(enabled);
        multiplayerAttack.setToolTipText(enabled
                ? "The server validates ownership, diplomacy, range, AP and resolves all dice"
                : !activeTurn ? "Available only during your active turn while connected"
                : multiplayerAttackers.isEmpty() ? "No visible owned military unit is available"
                : "No visible enemy unit or structure is available");
    }

    // PHASE 3 - Added in Phase 3
    public JButton multiplayerAttackButton() { return multiplayerAttack; }
    // PHASE 3 - Added in Phase 3
    public JList<?> multiplayerAttackerList() { return multiplayerAttackerList; }

    // PHASE 3 - Added in Phase 3
    private static boolean isMilitary(String unitType) {
        return unitType.equals("SWORDSMAN") || unitType.equals("ARCHER")
                || unitType.equals("CAVALRY");
    }

    // PHASE 3 - Added in Phase 3
    private static EntityChoice unitChoice(VisibleUnitDto unit) {
        return new EntityChoice(unit.unitId(), unit.unitType() + " @ " + unit.q() + "," + unit.r());
    }

    // PHASE 3 - Added in Phase 3
    private static EntityChoice buildingChoice(VisibleBuildingDto building) {
        return new EntityChoice(building.buildingId(), building.buildingType() + " @ "
                + building.q() + "," + building.r());
    }

    // PHASE 3 - Added in Phase 3
    private record EntityChoice(
            // PHASE 3 - Added in Phase 3
            String id,
            // PHASE 3 - Added in Phase 3
            String label) {
        // PHASE 3 - Added in Phase 3
        @Override public String toString() { return label; }
    }

    // PHASE 3 - Added in Phase 3
    public record MultiplayerAttackSelection(
            // PHASE 3 - Added in Phase 3
            List<String> attackerUnitIds,
            // PHASE 3 - Added in Phase 3
            String targetId) { }

    public void display(CombatResult result) {
        Objects.requireNonNull(result, "result");
        var rolls = result.dice();
        summary.setText("%s | hits: attacker %d, defender %d | captured: %s".formatted(
                result.outcome(), rolls.hitsOnAttacker(), rolls.hitsOnDefender(), result.captured()));
        dice.display(rolls.attackerRawRolls(), rolls.defenderRawRolls(),
                rolls.attackerSortedRolls(), rolls.defenderSortedRolls(), rolls.pairs().stream()
                        .map(pair -> pair.attackerRoll() + " vs " + pair.defenderRoll()
                                + " → " + pair.winner()).toList());
        trace.setText(String.join(System.lineSeparator(), result.log()));
        trace.setCaretPosition(0);
    }

    // PHASE 3 - Added in Phase 3
    public void display(BattleReportPush report) {
        Objects.requireNonNull(report, "report");
        List<Integer> attackSorted = report.attackerRolls().stream()
                .sorted(java.util.Comparator.reverseOrder()).toList();
        List<Integer> defenseSorted = report.defenderRolls().stream()
                .sorted(java.util.Comparator.reverseOrder()).toList();
        java.util.ArrayList<String> pairs = new java.util.ArrayList<>();
        // PHASE 3 - Added in Phase 3
        for (int index = 0; index < Math.min(attackSorted.size(), defenseSorted.size()); index++) {
            int attackRoll = attackSorted.get(index);
            int defenseRoll = defenseSorted.get(index);
            pairs.add(attackRoll + " vs " + defenseRoll + " → "
                    + (attackRoll > defenseRoll ? "ATTACKER" : "DEFENDER"));
        }
        summary.setText(report.outcome() + " | structure damage: "
                + report.structureDamage() + " | lost units: " + report.lostUnitIds().size());
        dice.display(report.attackerRolls(), report.defenderRolls(), attackSorted,
                defenseSorted, pairs);
        trace.setText("Battle " + report.battleId() + " at " + report.createdAt()
                + System.lineSeparator() + "Attacker: " + report.attackerPlayerId()
                + System.lineSeparator() + "Defender: " + report.defenderPlayerId()
                + System.lineSeparator() + "Target: " + report.targetId()
                + System.lineSeparator() + "Lost units: " + report.lostUnitIds()
                + System.lineSeparator() + "Remaining HP: " + report.remainingHitPoints());
        trace.setCaretPosition(0);
    }

    public String displayedTrace() { return trace.getText(); }
    public String resultSummary() { return summary.getText(); }
    public DiceCanvas diceCanvas() { return dice; }

    /** Draws supplied dice; it deliberately has no RNG or combat rules. */
    public static final class DiceCanvas extends JPanel {
        private static final long serialVersionUID = 1L;
        private List<Integer> attackerRaw=List.of(), defenderRaw=List.of();
        private List<Integer> attackerSorted=List.of(), defenderSorted=List.of();
        private List<String> pairs=List.of();
        DiceCanvas() { setPreferredSize(new java.awt.Dimension(290, 190)); }
        void display(List<Integer> ar, List<Integer> dr, List<Integer> as, List<Integer> ds,
                List<String> paired) {
            attackerRaw=List.copyOf(ar); defenderRaw=List.copyOf(dr);
            attackerSorted=List.copyOf(as); defenderSorted=List.copyOf(ds);
            pairs=List.copyOf(paired); repaint();
        }
        public List<Integer> attackerSorted() { return attackerSorted; }
        public List<Integer> defenderSorted() { return defenderSorted; }
        public List<String> pairedComparisons() { return pairs; }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(new Color(190,55,65)); g.drawString("Attacker raw: " + attackerRaw,8,22);
            g.setColor(new Color(55,105,190)); g.drawString("Defender raw: " + defenderRaw,8,45);
            g.setColor(getForeground()); g.drawString("Attacker sorted: " + attackerSorted,8,72);
            g.drawString("Defender sorted: " + defenderSorted,8,95);
            g.drawString("Paired comparisons:",8,122);
            int y=144; for (String pair : pairs) { g.drawString(pair,20,y); y += 19; }
        }
    }
}
