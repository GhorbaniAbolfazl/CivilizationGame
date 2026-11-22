package civilization.server.match;

import civilization.model.game.TurnState;
import civilization.model.game.GameState;
import civilization.model.game.CrisisState;
import civilization.model.world.WorldState;
import civilization.model.time.SeasonState;
import civilization.model.random.GameplayRandom;
import civilization.model.random.SplitMix64GameplayRandom;
import civilization.model.map.GameMap;
import civilization.server.session.PlayerId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.ArrayDeque;
import civilization.server.combat.PvpBattleReport;
import civilization.server.diplomacy.PlayerDiplomacyState;
import civilization.server.trade.PlayerTradeLedger;
import civilization.model.map.HexCoord;
import java.util.HashSet;
import civilization.server.item.PlayerItemEffectRegistry;

// PHASE 3 - Added in Phase 3
public final class MultiplayerGameState {
    // PHASE 3 - Added in Phase 3
    private final String matchId;
    // PHASE 3 - Added in Phase 3
    private final String mapId;
    // PHASE 3 - Added in Phase 3
    private final GameMap map;
    // PHASE 3 - Added in Phase 3
    private final Map<PlayerId, MultiplayerPlayerState> players;
    // PHASE 3 - Added in Phase 3
    private final TurnState turn = new TurnState();
    // PHASE 3 - Added in Phase 3
    private PlayerId activePlayerId;
    // PHASE 3 - Added in Phase 3
    private final Instant createdAt;
    // PHASE 3 - Added in Phase 3
    private long stateVersion = 1;
    // PHASE 3 - Added in Phase 3
    private final WorldState world;
    // PHASE 3 - Added in Phase 3
    private final Map<PlayerId, CrisisState> crises = new LinkedHashMap<>();
    // PHASE 3 - Added in Phase 3
    private final SeasonState season = new SeasonState();
    // PHASE 3 - Added in Phase 3
    private final GameplayRandom random;
    // PHASE 3 - Added in Phase 3
    private final long mapSeed;
    // PHASE 3 - Added in Phase 3
    private final LinkedHashSet<PlayerId> connectedPlayers = new LinkedHashSet<>();
    // PHASE 3 - Added in Phase 3
    private final TimedItemEffectRegistry timedItemEffects = new TimedItemEffectRegistry();
    // PHASE 3 - Added in Phase 3
    private final PlayerDiplomacyState diplomacy;
    // PHASE 3 - Added in Phase 3
    private final Map<PlayerId, ArrayDeque<PvpBattleReport>> pendingBattleReports =
            new LinkedHashMap<>();
    // PHASE 3 - Added in Phase 3
    private final PlayerTradeLedger tradeLedger = new PlayerTradeLedger();
    // PHASE 3 - Added in Phase 3
    private final LinkedHashSet<PlayerId> eliminatedPlayers = new LinkedHashSet<>();
    // PHASE 3 - Added in Phase 3
    private MultiplayerMatchStatus matchStatus = MultiplayerMatchStatus.ACTIVE;
    // PHASE 3 - Added in Phase 3
    private PlayerId winnerPlayerId;
    // PHASE 3 - Added in Phase 3
    private final PlayerItemEffectRegistry itemEffects = new PlayerItemEffectRegistry();

    // PHASE 3 - Added in Phase 3
    public MultiplayerGameState(String matchId, String mapId, GameMap map,
                                List<MultiplayerPlayerState> orderedPlayers, Instant createdAt) {
        this.matchId = Objects.requireNonNull(matchId); this.mapId = Objects.requireNonNull(mapId); this.map = Objects.requireNonNull(map); this.createdAt = Objects.requireNonNull(createdAt);
        if (orderedPlayers.size() < 2) throw new IllegalArgumentException("Multiplayer game needs at least two players");
        LinkedHashMap<PlayerId, MultiplayerPlayerState> indexed = new LinkedHashMap<>();
        // PHASE 3 - Added in Phase 3
        for (MultiplayerPlayerState player : orderedPlayers) if (indexed.putIfAbsent(player.playerId(), player) != null) throw new IllegalArgumentException("duplicate player");
        players = java.util.Collections.unmodifiableMap(indexed); activePlayerId = orderedPlayers.get(0).playerId();
        connectedPlayers.addAll(indexed.keySet()); indexed.keySet().forEach(id -> crises.put(id, new CrisisState()));
        // PHASE 3 - Added in Phase 3
        diplomacy = new PlayerDiplomacyState(List.copyOf(indexed.keySet()));
        world = new WorldState(map); mapSeed = ((long) matchId.hashCode() << 32) ^ mapId.hashCode(); random = new SplitMix64GameplayRandom(mapSeed); validate();
    }
    // PHASE 3 - Added in Phase 3
    private void validate() {
        // PHASE 3 - Added in Phase 3
        for (MultiplayerPlayerState player : players.values()) {
            // PHASE 3 - Added in Phase 3
            for (var hall : player.empire().activeHallTowns()) {
                if (!map.contains(hall.position())) throw new IllegalStateException("Hall Town is outside map");
                if (!player.empire().border().owns(hall.position())) throw new IllegalStateException("Hall Town must be owned");
            }
            player.empire().units().forEach(unit -> { if (!map.contains(unit.position())) throw new IllegalStateException("unit is outside map"); });
        }
    }
    // PHASE 3 - Added in Phase 3
    public String matchId() { return matchId; }
    // PHASE 3 - Added in Phase 3
    public String mapId() { return mapId; }
    // PHASE 3 - Added in Phase 3
    public GameMap map() { return map; }
    // PHASE 3 - Added in Phase 3
    public WorldState world() { return world; }
    // PHASE 3 - Added in Phase 3
    public Map<PlayerId, MultiplayerPlayerState> players() { return players; }
    // PHASE 3 - Added in Phase 3
    public MultiplayerPlayerState requirePlayer(PlayerId id) { MultiplayerPlayerState player = players.get(id); if (player == null) throw new IllegalArgumentException("unknown match player"); return player; }
    // PHASE 3 - Added in Phase 3
    public TurnState turn() { return turn; }
    // PHASE 3 - Added in Phase 3
    public PlayerId activePlayerId() { return activePlayerId; }
    // PHASE 3 - Added in Phase 3
    public Instant createdAt() { return createdAt; }
    // PHASE 3 - Added in Phase 3
    public long stateVersion() { return stateVersion; }
    // PHASE 3 - Added in Phase 3
    public GameState activePlayerView(PlayerId playerId) { return new GameState(world, requirePlayer(playerId).empire(), turn, crises.get(playerId), mapSeed, season, random); }
    // PHASE 3 - Added in Phase 3
    public void requireActive(PlayerId playerId, long observedVersion) {
        requirePlayer(playerId);
        // PHASE 3 - Added in Phase 3
        if (matchStatus == MultiplayerMatchStatus.FINISHED) throw new MatchException("MATCH_FINISHED", "The match has already finished");
        if (eliminatedPlayers.contains(playerId)) throw new MatchException("PLAYER_ELIMINATED", "Eliminated players cannot perform game actions");
        if (!connectedPlayers.contains(playerId)) throw new MatchException("PLAYER_DISCONNECTED", "Player is disconnected");
        if (!activePlayerId.equals(playerId)) throw new MatchException("NOT_ACTIVE_PLAYER", "Only the active player may change game state");
        if (observedVersion != stateVersion) throw new MatchException("STALE_STATE", "Client state version is stale");
    }
    // PHASE 3 - Added in Phase 3
    public void completeTurn(PlayerId playerId) {
        if (!activePlayerId.equals(playerId)) throw new IllegalStateException("turn owner changed during resolution");
        stateVersion = Math.incrementExact(stateVersion); activePlayerId = nextConnected(playerId);
    }
    // PHASE 3 - Added in Phase 3
    public void commitAction(PlayerId playerId) {
        if (!activePlayerId.equals(playerId)) throw new IllegalStateException("action owner changed during commit");
        stateVersion = Math.incrementExact(stateVersion);
    }
    // PHASE 3 - Added in Phase 3
    public boolean disconnect(PlayerId playerId) {
        if (!connectedPlayers.remove(playerId)) return false; boolean changedTurn = playerId.equals(activePlayerId);
        if (changedTurn && !connectedPlayers.isEmpty()) activePlayerId = nextConnected(playerId);
        stateVersion = Math.incrementExact(stateVersion);
        // PHASE 3 - Added in Phase 3
        return true;
    }
    // PHASE 3 - Added in Phase 3
    public boolean disconnectForTurnResolution(PlayerId playerId) {
        if (!activePlayerId.equals(playerId)) throw new IllegalStateException("Only the active player can be disconnected through turn resolution");
        return connectedPlayers.remove(playerId);
    }
    // PHASE 3 - Added in Phase 3
    public boolean reconnect(PlayerId playerId) {
        requirePlayer(playerId);
        boolean hadConnectedPlayers = !connectedPlayers.isEmpty();
        if (!connectedPlayers.add(playerId)) return false;
        if (!hadConnectedPlayers) activePlayerId = playerId;
        stateVersion = Math.incrementExact(stateVersion);
        return true;
    }
    // PHASE 3 - Added in Phase 3
    private PlayerId nextConnected(PlayerId current) {
        List<PlayerId> order = List.copyOf(players.keySet()); int start = order.indexOf(current);
        // PHASE 3 - Added in Phase 3
        for (int offset = 1; offset <= order.size(); offset++) { PlayerId candidate = order.get(Math.floorMod(start + offset, order.size())); if (connectedPlayers.contains(candidate) && !eliminatedPlayers.contains(candidate)) return candidate; }
        return current;
    }
    // PHASE 3 - Added in Phase 3
    public Set<PlayerId> connectedPlayers() { return Set.copyOf(connectedPlayers); }
    // PHASE 3 - Added in Phase 3
    public TimedItemEffectRegistry timedItemEffects() { return timedItemEffects; }
    // PHASE 3 - Added in Phase 3
    public PlayerItemEffectRegistry itemEffects() { return itemEffects; }
    // PHASE 3 - Added in Phase 3
    public PlayerDiplomacyState diplomacy() { return diplomacy; }
    // PHASE 3 - Added in Phase 3
    public void queueBattleReport(PlayerId defender, PvpBattleReport report) {
        requirePlayer(defender);
        if (!report.defenderPlayerId().equals(defender)) {
            throw new IllegalArgumentException("Battle report defender does not match queue owner");
        }
        pendingBattleReports.computeIfAbsent(defender, ignored -> new ArrayDeque<>())
                .addLast(report);
    }
    // PHASE 3 - Added in Phase 3
    public List<PvpBattleReport> drainBattleReports(PlayerId defender) {
        ArrayDeque<PvpBattleReport> reports = pendingBattleReports.remove(defender);
        return reports == null ? List.of() : List.copyOf(reports);
    }
    // PHASE 3 - Added in Phase 3
    public PlayerTradeLedger tradeLedger() { return tradeLedger; }
    // PHASE 3 - Added in Phase 3
    public Set<HexCoord> discoveredFor(PlayerId viewerId) {
        requirePlayer(viewerId);
        HashSet<HexCoord> result = new HashSet<>(
                requirePlayer(viewerId).empire().visibility().discoveredHexes());
        // PHASE 3 - Added in Phase 3
        diplomacy.alliesOf(viewerId).forEach(ally -> result.addAll(
                requirePlayer(ally).empire().visibility().discoveredHexes()));
        return Set.copyOf(result);
    }
    // PHASE 3 - Added in Phase 3
    public Set<HexCoord> currentlyVisibleFor(PlayerId viewerId) {
        requirePlayer(viewerId);
        HashSet<HexCoord> result = new HashSet<>(
                requirePlayer(viewerId).empire().visibility().currentlyVisibleHexes());
        // PHASE 3 - Added in Phase 3
        diplomacy.alliesOf(viewerId).forEach(ally -> result.addAll(
                requirePlayer(ally).empire().visibility().currentlyVisibleHexes()));
        return Set.copyOf(result);
    }
    // PHASE 3 - Added in Phase 3
    public boolean isCurrentlyVisibleTo(PlayerId viewerId, HexCoord coordinate) {
        return currentlyVisibleFor(viewerId).contains(Objects.requireNonNull(coordinate));
    }
    // PHASE 3 - Added in Phase 3
    public PlayerEliminationResult evaluateHallSurvival(PlayerId playerId) {
        MultiplayerPlayerState player = requirePlayer(playerId);
        if (!player.empire().activeHallTowns().isEmpty() || eliminatedPlayers.contains(playerId)) {
            return new PlayerEliminationResult(false, playerId,
                    matchStatus == MultiplayerMatchStatus.FINISHED, winnerPlayerId);
        }
        eliminatedPlayers.add(playerId);
        player.empire().eliminateRemainingAssets();
        // PHASE 3 - Added in Phase 3
        if (activePlayerId.equals(playerId)) activePlayerId = nextConnected(playerId);
        List<PlayerId> survivors = survivingPlayerIds();
        if (survivors.size() == 1) {
            matchStatus = MultiplayerMatchStatus.FINISHED;
            winnerPlayerId = survivors.get(0);
            activePlayerId = winnerPlayerId;
        }
        return new PlayerEliminationResult(true, playerId,
                matchStatus == MultiplayerMatchStatus.FINISHED, winnerPlayerId);
    }
    // PHASE 3 - Added in Phase 3
    public List<PlayerId> survivingPlayerIds() {
        // PHASE 3 - Added in Phase 3
        return players.entrySet().stream()
                .filter(entry -> !entry.getValue().empire().activeHallTowns().isEmpty())
                .map(Map.Entry::getKey).toList();
    }
    // PHASE 3 - Added in Phase 3
    public boolean isEliminated(PlayerId playerId) {
        requirePlayer(playerId); return eliminatedPlayers.contains(playerId);
    }
    // PHASE 3 - Added in Phase 3
    public Set<PlayerId> eliminatedPlayers() { return Set.copyOf(eliminatedPlayers); }
    // PHASE 3 - Added in Phase 3
    public MultiplayerMatchStatus matchStatus() { return matchStatus; }
    // PHASE 3 - Added in Phase 3
    public java.util.Optional<PlayerId> winnerPlayerId() { return java.util.Optional.ofNullable(winnerPlayerId); }
}
