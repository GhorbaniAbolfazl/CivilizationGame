package civilization.view.game;

import civilization.application.persistence.SaveEntry;
import civilization.application.persistence.SaveSlot;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Slot/status view only. Persistence operations are bound by GameController. */
public final class PauseMenuPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final Map<SaveSlot, SlotRow> rows = new EnumMap<>(SaveSlot.class);
    private final JButton resume = new JButton("Resume Game");
    private final JLabel autosaveStatus = new JLabel("Autosave: not yet attempted");

    public PauseMenuPanel() {
        super(new BorderLayout(6,6));
        setBorder(BorderFactory.createTitledBorder("Paused — Save / Load"));
        JPanel slots = new JPanel(new GridLayout(0,1,5,5));
        for (SaveSlot slot : SaveSlot.values()) {
            SlotRow row = new SlotRow(slot);
            rows.put(slot,row); slots.add(row.panel);
        }
        add(slots,BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        footer.add(autosaveStatus,BorderLayout.CENTER); footer.add(resume,BorderLayout.EAST);
        add(footer,BorderLayout.SOUTH);
    }

    public JButton saveButton(SaveSlot slot) { return rows.get(slot).save; }
    public JButton loadButton(SaveSlot slot) { return rows.get(slot).load; }
    public JButton deleteButton(SaveSlot slot) { return rows.get(slot).delete; }
    public JButton resumeButton() { return resume; }
    public JLabel autosaveStatusLabel() { return autosaveStatus; }
    public JLabel statusLabel(SaveSlot slot) { return rows.get(slot).status; }

    public void display(List<SaveEntry> entries, boolean saveStable, String unstableReason) {
        for (SaveEntry entry : entries) {
            SlotRow row=rows.get(entry.slot());
            String text=switch(entry.status()) {
                case EMPTY -> "Empty";
                case VALID -> entry.metadata().orElseThrow().summary()+" — "
                        +entry.metadata().orElseThrow().savedAt();
                case CORRUPTED -> "CORRUPTED: "+entry.error().orElse("invalid data");
                case INCOMPATIBLE -> "INCOMPATIBLE: "+entry.error().orElse("wrong version");
            };
            row.status.setText(text);
            row.status.setForeground(entry.status()==SaveEntry.Status.CORRUPTED
                    || entry.status()==SaveEntry.Status.INCOMPATIBLE ? Color.RED.darker() : getForeground());
            row.load.setEnabled(entry.status()==SaveEntry.Status.VALID);
            row.load.setToolTipText(row.load.isEnabled()?"Validate and load without advancing gameplay"
                    : "This slot does not contain a compatible valid save");
            row.delete.setEnabled(entry.status() != SaveEntry.Status.EMPTY);
            row.delete.setToolTipText(row.delete.isEnabled()
                    ? "Permanently delete this save file after confirmation"
                    : "This slot is empty");
            row.save.setEnabled(entry.slot().isManual() && saveStable);
            row.save.setToolTipText(entry.slot().isAutosave()?"Autosave is managed after completed turns"
                    : saveStable?"Atomically save the complete model state":unstableReason);
        }
    }

    public void displayAutosave(boolean success, String message) {
        autosaveStatus.setText("Autosave: "+message);
        autosaveStatus.setForeground(success?new Color(25,125,70):Color.RED.darker());
    }

    private static final class SlotRow {
        final JPanel panel=new JPanel(new BorderLayout(5,5));
        final JLabel status=new JLabel();
        final JButton save=new JButton("Save");
        final JButton load=new JButton("Load");
        final JButton delete=new JButton("Delete");
        SlotRow(SaveSlot slot) {
            panel.setBorder(BorderFactory.createTitledBorder(slot.name().replace('_',' ')));
            JPanel buttons=new JPanel(); buttons.add(save); buttons.add(load); buttons.add(delete);
            panel.add(status,BorderLayout.CENTER); panel.add(buttons,BorderLayout.EAST);
            if(slot.isAutosave()) save.setEnabled(false);
        }
    }
}
