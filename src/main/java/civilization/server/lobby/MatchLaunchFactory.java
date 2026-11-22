package civilization.server.lobby;

import civilization.server.map.PredefinedMapDefinition;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import civilization.application.factory.UnitFactory;
import civilization.rules.DefaultRulesetFactory;
import civilization.server.map.PredefinedMapLoader;
import civilization.server.match.MultiplayerGameInitializer;
import civilization.server.match.MultiplayerGameState;

// PHASE 3 - Added in Phase 3
public final class MatchLaunchFactory {
    // PHASE 3 - Added in Phase 3
    private final Clock clock;
    // PHASE 3 - Added in Phase 3
    private final MultiplayerGameInitializer initializer;
    // PHASE 3 - Added in Phase 3
    public MatchLaunchFactory(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
        initializer = new MultiplayerGameInitializer(new PredefinedMapLoader(), DefaultRulesetFactory.create(), new UnitFactory(), clock);
    }

    // PHASE 3 - Added in Phase 3
    public MatchLaunch create(LobbyId lobbyId, List<LobbyMember> members, PredefinedMapDefinition map) {
        String matchId = UUID.randomUUID().toString();
        MultiplayerGameState game = initializer.initialize(matchId, map, members);
        LinkedHashMap<civilization.server.session.PlayerId, InitialPlayerSetup> setups = new LinkedHashMap<>();
        List<civilization.server.map.SpawnSlot> spawnSlots = map.spawnSlotsFor(members.size());
        // PHASE 3 - Added in Phase 3
        for (int index = 0; index < members.size(); index++) {
            LobbyMember member = members.get(index);
            var empire = game.requirePlayer(member.playerId()).empire();
            LinkedHashMap<String, Long> resources = new LinkedHashMap<>();
            // PHASE 3 - Added in Phase 3
            for (civilization.model.economy.ResourceType type : civilization.model.economy.ResourceType.values()) resources.put(type.name(), (long) empire.townHall().stockpile().amountOf(type));
            setups.put(member.playerId(), new InitialPlayerSetup(member.playerId(), spawnSlots.get(index),
                    empire.townHall().id().value().toString(), empire.units().stream().map(unit -> unit.id().value().toString()).toList(), resources));
        }
        return new MatchLaunch(matchId, lobbyId, map, game.activePlayerId(), members, setups, Instant.now(clock), game);
    }
}
