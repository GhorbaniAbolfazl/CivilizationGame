package civilization.application.persistence;

import java.util.List;

public enum SaveSlot {
    MANUAL_1(false),
    MANUAL_2(false),
    MANUAL_3(false),
    AUTOSAVE(true);

    private final boolean autosave;
    SaveSlot(boolean autosave) { this.autosave = autosave; }
    public boolean isAutosave() { return autosave; }
    public boolean isManual() { return !autosave; }
    public static List<SaveSlot> manualSlots() {
        return List.of(MANUAL_1, MANUAL_2, MANUAL_3);
    }
}
