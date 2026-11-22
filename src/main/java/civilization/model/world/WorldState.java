package civilization.model.world;

import civilization.model.diplomacy.RelationshipRegistry;
import civilization.model.disaster.DisasterState;
import civilization.model.infrastructure.InfrastructureState;
import civilization.model.map.GameMap;
import civilization.model.mission.MissionRegistry;
import civilization.model.trade.TradingPostRegistry;
import civilization.model.trade.TribeTradeRegistry;
import civilization.model.tribe.TribeRegistry;
import civilization.model.combat.CombatHistory;
import java.util.Objects;

/** Shared world aggregate for systems that are not owned exclusively by the player. */
public final class WorldState implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final GameMap map;
    private final InfrastructureState infrastructure;
    private final RelationshipRegistry relationships;
    private final MissionRegistry missions;
    private final DisasterState disasters;
    private final TradingPostRegistry tradingPosts;
    private final TribeTradeRegistry tribeTrades;
    private final WorldUnitRegistry units;
    private final WorldStructureRegistry structures;
    private final WildAnimalRegistry wildAnimals;
    private final TerritoryRegistry territories;
    private final TribeRegistry tribes;
    private final CombatHistory combatHistory;

    public WorldState(GameMap map) {
        this(map, new InfrastructureState(), new RelationshipRegistry(),
                new MissionRegistry(), new DisasterState(),
                new TradingPostRegistry(), new TribeTradeRegistry(),
                new WorldUnitRegistry(), new WorldStructureRegistry(),
                new WildAnimalRegistry(), new TerritoryRegistry(), new TribeRegistry(),
                new CombatHistory());
    }

    public WorldState(GameMap map, InfrastructureState infrastructure,
            RelationshipRegistry relationships, MissionRegistry missions, DisasterState disasters) {
        this(map, infrastructure, relationships, missions, disasters,
                new TradingPostRegistry(), new TribeTradeRegistry(),
                new WorldUnitRegistry(), new WorldStructureRegistry(),
                new WildAnimalRegistry(), new TerritoryRegistry(), new TribeRegistry(),
                new CombatHistory());
    }

    public WorldState(GameMap map, InfrastructureState infrastructure,
            RelationshipRegistry relationships, MissionRegistry missions, DisasterState disasters,
            TradingPostRegistry tradingPosts, TribeTradeRegistry tribeTrades) {
        this(map, infrastructure, relationships, missions, disasters, tradingPosts, tribeTrades,
                new WorldUnitRegistry(), new WorldStructureRegistry(),
                new WildAnimalRegistry(), new TerritoryRegistry(), new TribeRegistry(),
                new CombatHistory());
    }

    public WorldState(GameMap map, InfrastructureState infrastructure,
            RelationshipRegistry relationships, MissionRegistry missions, DisasterState disasters,
            TradingPostRegistry tradingPosts, TribeTradeRegistry tribeTrades,
            WorldUnitRegistry units, WorldStructureRegistry structures,
            WildAnimalRegistry wildAnimals, TerritoryRegistry territories) {
        this(map, infrastructure, relationships, missions, disasters, tradingPosts, tribeTrades,
                units, structures, wildAnimals, territories, new TribeRegistry(),
                new CombatHistory());
    }

    public WorldState(GameMap map, InfrastructureState infrastructure,
            RelationshipRegistry relationships, MissionRegistry missions, DisasterState disasters,
            TradingPostRegistry tradingPosts, TribeTradeRegistry tribeTrades,
            WorldUnitRegistry units, WorldStructureRegistry structures,
            WildAnimalRegistry wildAnimals, TerritoryRegistry territories, TribeRegistry tribes) {
        this(map, infrastructure, relationships, missions, disasters, tradingPosts, tribeTrades,
                units, structures, wildAnimals, territories, tribes, new CombatHistory());
    }

    public WorldState(GameMap map, InfrastructureState infrastructure,
            RelationshipRegistry relationships, MissionRegistry missions, DisasterState disasters,
            TradingPostRegistry tradingPosts, TribeTradeRegistry tribeTrades,
            WorldUnitRegistry units, WorldStructureRegistry structures,
            WildAnimalRegistry wildAnimals, TerritoryRegistry territories, TribeRegistry tribes,
            CombatHistory combatHistory) {
        this.map = Objects.requireNonNull(map, "map");
        this.infrastructure = Objects.requireNonNull(infrastructure, "infrastructure");
        this.relationships = Objects.requireNonNull(relationships, "relationships");
        this.missions = Objects.requireNonNull(missions, "missions");
        this.disasters = Objects.requireNonNull(disasters, "disasters");
        this.tradingPosts = Objects.requireNonNull(tradingPosts, "tradingPosts");
        this.tribeTrades = Objects.requireNonNull(tribeTrades, "tribeTrades");
        this.units = Objects.requireNonNull(units, "units");
        this.structures = Objects.requireNonNull(structures, "structures");
        this.wildAnimals = Objects.requireNonNull(wildAnimals, "wildAnimals");
        this.territories = Objects.requireNonNull(territories, "territories");
        this.tribes = Objects.requireNonNull(tribes, "tribes");
        this.combatHistory = Objects.requireNonNull(combatHistory, "combatHistory");
    }

    public GameMap map() { return map; }
    public InfrastructureState infrastructure() { return infrastructure; }
    public RelationshipRegistry relationships() { return relationships; }
    public MissionRegistry missions() { return missions; }
    public DisasterState disasters() { return disasters; }
    public TradingPostRegistry tradingPosts() { return tradingPosts; }
    public TribeTradeRegistry tribeTrades() { return tribeTrades; }
    public WorldUnitRegistry units() { return units; }
    public WorldStructureRegistry structures() { return structures; }
    public WildAnimalRegistry wildAnimals() { return wildAnimals; }
    public TerritoryRegistry territories() { return territories; }
    public TribeRegistry tribes() { return tribes; }
    public CombatHistory combatHistory() { return combatHistory; }
}
