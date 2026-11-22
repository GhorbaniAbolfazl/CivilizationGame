package civilization.model.tribe;

import civilization.model.identity.TribeId;
import civilization.model.map.HexCoord;
import civilization.model.mission.MissionId;
import civilization.model.unit.UnitId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class Tribe implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final TribeId id;
    private final String name;
    private final TribeType type;
    private final TribeCamp camp;
    private final Set<HexCoord> territory;
    private final Map<UnitId, Integer> intrusionWarnings = new LinkedHashMap<>();
    private boolean discovered;
    private boolean defeated;
    private boolean outpostEstablished;
    private boolean allianceBonusActive;
    private boolean allianceRewardGranted;
    private boolean nextDockDiscount;
    private int lastMissionOfferTurn = Integer.MIN_VALUE;
    private int lastMissionFailureTurn = Integer.MIN_VALUE;
    private int lastPlayerAttackTurn = Integer.MIN_VALUE;
    private HexCoord lastAttackerHex;
    private MissionId currentMissionId;

    public Tribe(TribeId id, String name, TribeType type, TribeCamp camp,
            Set<HexCoord> territory) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name").trim();
        this.type = Objects.requireNonNull(type, "type");
        this.camp = Objects.requireNonNull(camp, "camp");
        if (this.name.isEmpty()) throw new IllegalArgumentException("Tribe name cannot be blank");
        if (!camp.tribeId().equals(id)) throw new IllegalArgumentException("Camp belongs to another tribe");
        Set<HexCoord> copy = Set.copyOf(Objects.requireNonNull(territory, "territory"));
        if (!copy.contains(camp.position())) throw new IllegalArgumentException("Territory must include camp");
        this.territory = new LinkedHashSet<>(copy);
    }

    public TribeId id() { return id; }
    public String name() { return name; }
    public TribeType type() { return type; }
    public TribeCamp camp() { return camp; }
    public Set<HexCoord> territory() { return Collections.unmodifiableSet(territory); }
    public Set<HexCoord> forbiddenArea() {
        Set<HexCoord> result = new LinkedHashSet<>(camp.position().neighbors());
        result.add(camp.position());
        return Set.copyOf(result);
    }
    public boolean isDiscovered() { return discovered; }
    public boolean isDefeated() { return defeated; }
    public boolean hasOutpost() { return outpostEstablished; }
    public boolean isAllianceBonusActive() { return allianceBonusActive; }
    public boolean isAllianceRewardGranted() { return allianceRewardGranted; }
    public boolean hasNextDockDiscount() { return nextDockDiscount; }
    public int lastMissionOfferTurn() { return lastMissionOfferTurn; }
    public int lastMissionFailureTurn() { return lastMissionFailureTurn; }
    public int lastPlayerAttackTurn() { return lastPlayerAttackTurn; }
    public Optional<HexCoord> lastAttackerHex() { return Optional.ofNullable(lastAttackerHex); }
    public Optional<MissionId> currentMissionId() { return Optional.ofNullable(currentMissionId); }

    public boolean discover() {
        if (discovered) return false;
        discovered = true;
        return true;
    }

    public void setAllianceBonusActive(boolean active) { allianceBonusActive = active; }
    public void markAllianceRewardGranted() { allianceRewardGranted = true; }
    public void grantNextDockDiscount() { nextDockDiscount = true; }
    public void consumeNextDockDiscount() {
        if (!nextDockDiscount) throw new IllegalStateException("No Dock discount is available");
        nextDockDiscount = false;
    }
    public void recordMissionOffer(int turn, MissionId id) {
        lastMissionOfferTurn = turn;
        currentMissionId = Objects.requireNonNull(id, "id");
    }
    public void clearCurrentMission(MissionId id) {
        if (Objects.equals(currentMissionId, id)) currentMissionId = null;
    }
    public void recordMissionFailure(int turn) { lastMissionFailureTurn = turn; }
    public void recordPlayerAttack(int turn, HexCoord attackerHex) {
        lastPlayerAttackTurn = turn;
        lastAttackerHex = Objects.requireNonNull(attackerHex, "attackerHex");
    }
    public void clearAttackTarget() { lastAttackerHex = null; }
    public Optional<Integer> warnedOnTurn(UnitId unitId) {
        return Optional.ofNullable(intrusionWarnings.get(unitId));
    }
    public void warnIntruder(UnitId unitId, int turn) { intrusionWarnings.put(unitId, turn); }
    public void clearIntruder(UnitId unitId) { intrusionWarnings.remove(unitId); }
    public Set<UnitId> warnedIntruders() { return Set.copyOf(intrusionWarnings.keySet()); }

    public void markDefeated() {
        if (defeated) throw new IllegalStateException("Tribe is already defeated");
        defeated = true;
        outpostEstablished = true;
        allianceBonusActive = false;
        currentMissionId = null;
    }
}
