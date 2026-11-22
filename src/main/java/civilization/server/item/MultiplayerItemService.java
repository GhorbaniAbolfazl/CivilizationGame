package civilization.server.item;

import civilization.application.service.ConstructionService;
import civilization.application.service.VisibilityService;
import civilization.model.building.Apothecary;
import civilization.model.building.Building;
import civilization.model.building.BuildingId;
import civilization.model.building.BuildingType;
import civilization.model.economy.ResourceBundle;
import civilization.model.item.ItemCraftingOrder;
import civilization.model.item.ItemId;
import civilization.model.item.ItemInstance;
import civilization.model.item.ItemType;
import civilization.model.map.HexCoord;
import civilization.model.map.TerrainType;
import civilization.model.unit.BuilderUnit;
import civilization.model.unit.MilitaryUnit;
import civilization.model.unit.Unit;
import civilization.model.unit.UnitId;
import civilization.rules.GameRules;
import civilization.rules.MovementCostPolicy;
import civilization.server.match.MultiplayerGameState;
import civilization.server.match.MultiplayerPlayerState;
import civilization.server.session.PlayerId;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

// PHASE 3 - Added in Phase 3
public final class MultiplayerItemService {
    // PHASE 3 - Added in Phase 3
    public static final int MOBILITY_AP_BONUS = 2;
    // PHASE 3 - Added in Phase 3
    public static final int COMBAT_DIE_BONUS = 1;
    // PHASE 3 - Added in Phase 3
    public static final int COMBAT_STRUCTURE_DAMAGE_BONUS = 5;
    // PHASE 3 - Added in Phase 3
    private static final Map<ItemType, ResourceBundle> ITEM_COSTS = costs();
    // PHASE 3 - Added in Phase 3
    private final GameRules rules;
    // PHASE 3 - Added in Phase 3
    private final Clock clock;
    // PHASE 3 - Added in Phase 3
    private final VisibilityService visibility = new VisibilityService();
    // PHASE 3 - Added in Phase 3
    private final MovementCostPolicy movement = new MovementCostPolicy();
    // PHASE 3 - Added in Phase 3
    private final ConstructionService construction = new ConstructionService(new VisibilityService());

    // PHASE 3 - Added in Phase 3
    public MultiplayerItemService(GameRules rules, Clock clock) {
        this.rules = Objects.requireNonNull(rules, "rules");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // PHASE 3 - Added in Phase 3
    public static ResourceBundle cost(ItemType type) {
        return ITEM_COSTS.get(Objects.requireNonNull(type, "type"));
    }

    // PHASE 3 - Added in Phase 3
    public Building buildApothecary(MultiplayerGameState state, PlayerId playerId,
                                    String builderValue, HexCoord target) {
        MultiplayerPlayerState player = state.requirePlayer(playerId);
        BuilderUnit builder = ownedBuilder(player, builderValue);
        validateGlobalBuildingOccupancy(state, builder, target);
        if (state.map().requireTile(target).terrain() != TerrainType.PLAINS) {
            throw new ItemException("APOTHECARY_REQUIRES_PLAINS",
                    "Apothecary can only be built on Plains");
        }
        if (player.empire().activeHallTowns().stream()
                .noneMatch(hall -> hall.level().number() >= 2)) {
            throw new ItemException("HALL_LEVEL_REQUIRED",
                    "Apothecary requires an active Hall Town at level 2");
        }
        // PHASE 3 - Added in Phase 3
        try {
            return construction.construct(state.activePlayerView(playerId), builder.id(),
                    BuildingType.APOTHECARY, target, rules);
        } catch (RuntimeException failure) {
            throw new ItemException("INVALID_APOTHECARY_BUILD", failure.getMessage());
        }
    }

    // PHASE 3 - Added in Phase 3
    public ItemCraftResult craft(MultiplayerGameState state, PlayerId playerId,
                                 String apothecaryValue, String itemValue, int quantity) {
        if (quantity != 1) {
            throw new ItemException("INVALID_ITEM_QUANTITY",
                    "The independent Apothecary queue accepts one item at a time");
        }
        MultiplayerPlayerState player = state.requirePlayer(playerId);
        Apothecary apothecary = ownedApothecary(player, apothecaryValue);
        ItemType type = parseType(itemValue);
        if (!apothecary.craftingQueue().isEmpty()) {
            throw new ItemException("APOTHECARY_QUEUE_OCCUPIED",
                    "This Apothecary already has an active crafting order");
        }
        ResourceBundle cost = cost(type);
        var treasury = player.empire().townHall().stockpile();
        if (!treasury.canAfford(cost)) {
            throw new ItemException("INSUFFICIENT_ITEM_RESOURCES",
                    "Insufficient resources to craft " + type);
        }
        treasury.debit(cost);
        apothecary.craftingQueue().enqueue(new ItemCraftingOrder(type, cost));
        return new ItemCraftResult(playerId, apothecary.id(), type, cost);
    }

    // PHASE 3 - Added in Phase 3
    public ItemUseResult use(MultiplayerGameState state, PlayerId playerId,
                             String itemValue, String targetValue, HexCoord destination) {
        MultiplayerPlayerState player = state.requirePlayer(playerId);
        ItemId itemId = parseItemId(itemValue);
        ItemInstance item;
        // PHASE 3 - Added in Phase 3
        try { item = player.empire().itemInventory().require(itemId); }
        catch (IllegalArgumentException failure) {
            throw new ItemException("ITEM_NOT_OWNED", "Item must belong to the requesting player");
        }
        Unit unit = ownedActiveUnit(player, targetValue);
        if (state.itemEffects().hasUsedItem(playerId, unit.id())) {
            throw new ItemException("ITEM_ALREADY_USED_ON_UNIT",
                    "Only one item may affect a unit during its turn");
        }
        // PHASE 3 - Added in Phase 3
        switch (item.type()) {
            case TELEPORT -> teleport(state, player, unit, destination);
            case MOBILITY -> unit.actionPoints().grantTemporaryBonus(MOBILITY_AP_BONUS);
            case COMBAT -> {
                if (!(unit instanceof MilitaryUnit)) {
                    throw new ItemException("COMBAT_ITEM_REQUIRES_MILITARY",
                            "Combat items require an active military unit");
                }
            }
        }
        state.itemEffects().markUsed(playerId, unit.id(), item.type());
        player.empire().itemInventory().remove(itemId);
        return new ItemUseResult(playerId, itemId, item.type(), unit.id(), destination);
    }

    // PHASE 3 - Added in Phase 3
    public List<ItemInstance> advanceCrafting(MultiplayerGameState state, PlayerId playerId) {
        MultiplayerPlayerState player = state.requirePlayer(playerId);
        ArrayList<ItemInstance> completed = new ArrayList<>();
        // PHASE 3 - Added in Phase 3
        player.empire().constructedBuildings().stream()
                .filter(Apothecary.class::isInstance).map(Apothecary.class::cast)
                .filter(Building::isActive).forEach(apothecary ->
                        apothecary.craftingQueue().currentOrder().ifPresent(order -> {
                            order.advanceOneTurn();
                            if (order.isComplete()) {
                                apothecary.craftingQueue().removeCompletedOrder();
                                ItemInstance item = new ItemInstance(uniqueItemId(state),
                                        order.itemType(), Instant.now(clock));
                                player.empire().itemInventory().add(item);
                                completed.add(item);
                            }
                        }));
        return List.copyOf(completed);
    }

    // PHASE 3 - Added in Phase 3
    private void teleport(MultiplayerGameState state, MultiplayerPlayerState player,
                          Unit unit, HexCoord destination) {
        if (destination == null || !state.map().contains(destination)) {
            throw new ItemException("INVALID_TELEPORT_DESTINATION",
                    "Teleport destination must be on the map");
        }
        if (!state.isCurrentlyVisibleTo(player.playerId(), destination)) {
            throw new ItemException("TELEPORT_DESTINATION_FOGGED",
                    "Teleport destination must currently be visible");
        }
        movement.rejection(state.activePlayerView(player.playerId()), unit, destination)
                .ifPresent(reason -> { throw new ItemException("IMPASSABLE_TELEPORT_DESTINATION", reason); });
        if (occupied(state, destination)) {
            throw new ItemException("TELEPORT_DESTINATION_OCCUPIED",
                    "Teleport destination must be unoccupied");
        }
        // PHASE 3 - Added in Phase 3
        unit.moveTo(destination);
        unit.setEmbarked(state.map().requireTile(destination).terrain() == TerrainType.SEA);
        visibility.refresh(state.activePlayerView(player.playerId()), rules);
    }

    // PHASE 3 - Added in Phase 3
    private static boolean occupied(MultiplayerGameState state, HexCoord destination) {
        return state.players().values().stream().flatMap(player -> player.empire().units().stream())
                .anyMatch(unit -> unit.position().equals(destination))
                || state.players().values().stream().flatMap(player -> player.empire().allBuildings().stream())
                .anyMatch(building -> building.position().equals(destination))
                || !state.world().units().at(destination).isEmpty()
                || state.world().structures().at(destination).isPresent()
                || state.world().wildAnimals().at(destination).isPresent()
                || state.world().tribes().atCamp(destination).isPresent()
                || state.world().tradingPosts().all().stream()
                .anyMatch(post -> post.position().equals(destination));
    }

    // PHASE 3 - Added in Phase 3
    private static void validateGlobalBuildingOccupancy(MultiplayerGameState state,
                                                        BuilderUnit builder,
                                                        HexCoord target) {
        if (!state.map().contains(target)) {
            throw new ItemException("INVALID_APOTHECARY_LOCATION", "Build location is outside the map");
        }
        if (state.players().values().stream().flatMap(player -> player.empire().allBuildings().stream())
                .anyMatch(building -> building.position().equals(target))
                || state.players().values().stream().flatMap(player -> player.empire().units().stream())
                .anyMatch(unit -> unit.position().equals(target) && !unit.id().equals(builder.id()))
                || !state.world().units().at(target).isEmpty()
                || state.world().structures().at(target).isPresent()
                || state.world().wildAnimals().at(target).isPresent()
                || state.world().tribes().atCamp(target).isPresent()
                || state.world().tradingPosts().all().stream()
                .anyMatch(post -> post.position().equals(target))) {
            throw new ItemException("APOTHECARY_LOCATION_OCCUPIED", "Build location is occupied");
        }
    }

    // PHASE 3 - Added in Phase 3
    private static BuilderUnit ownedBuilder(MultiplayerPlayerState player, String value) {
        Unit unit = ownedUnit(player, value, "BUILDER_NOT_OWNED");
        if (!(unit instanceof BuilderUnit builder) || !builder.isActive()) {
            throw new ItemException("INVALID_BUILDER", "An active owned Builder is required");
        }
        return builder;
    }

    // PHASE 3 - Added in Phase 3
    private static Apothecary ownedApothecary(MultiplayerPlayerState player, String value) {
        BuildingId id;
        try { id = new BuildingId(UUID.fromString(value)); }
        catch (RuntimeException failure) {
            throw new ItemException("INVALID_APOTHECARY", "Apothecary ID is invalid");
        }
        Building building;
        try { building = player.empire().requireBuilding(id); }
        catch (IllegalArgumentException failure) {
            throw new ItemException("APOTHECARY_NOT_OWNED", "Apothecary must belong to the player");
        }
        if (!(building instanceof Apothecary result) || !result.isActive()) {
            throw new ItemException("INVALID_APOTHECARY", "An active owned Apothecary is required");
        }
        return result;
    }

    // PHASE 3 - Added in Phase 3
    private static Unit ownedActiveUnit(MultiplayerPlayerState player, String value) {
        Unit unit = ownedUnit(player, value, "ITEM_TARGET_NOT_OWNED");
        if (!unit.isActive()) throw new ItemException("INACTIVE_ITEM_TARGET", "Item target must be active");
        return unit;
    }

    // PHASE 3 - Added in Phase 3
    private static Unit ownedUnit(MultiplayerPlayerState player, String value, String code) {
        UnitId id;
        try { id = new UnitId(UUID.fromString(value)); }
        catch (RuntimeException failure) { throw new ItemException(code, "Unit ID is invalid or not owned"); }
        try { return player.empire().requireUnit(id); }
        catch (IllegalArgumentException failure) { throw new ItemException(code, "Unit must belong to the player"); }
    }

    // PHASE 3 - Added in Phase 3
    private static ItemType parseType(String value) {
        try { return ItemType.valueOf(value); }
        catch (RuntimeException failure) {
            throw new ItemException("INVALID_ITEM_TYPE", "Only TELEPORT, MOBILITY and COMBAT are supported");
        }
    }

    // PHASE 3 - Added in Phase 3
    private static ItemId parseItemId(String value) {
        try { return new ItemId(UUID.fromString(value)); }
        catch (RuntimeException failure) { throw new ItemException("ITEM_NOT_OWNED", "Item ID is invalid"); }
    }

    // PHASE 3 - Added in Phase 3
    private static ItemId uniqueItemId(MultiplayerGameState state) {
        ItemId id;
        // PHASE 3 - Added in Phase 3
        do { id = ItemId.from(state.activePlayerView(state.activePlayerId()).gameplayRandom()); }
        while (containsItem(state, id));
        return id;
    }

    // PHASE 3 - Added in Phase 3
    private static boolean containsItem(MultiplayerGameState state, ItemId id) {
        return state.players().values().stream()
                .flatMap(player -> player.empire().itemInventory().items().stream())
                .anyMatch(item -> item.id().equals(id));
    }

    // PHASE 3 - Added in Phase 3
    private static Map<ItemType, ResourceBundle> costs() {
        EnumMap<ItemType, ResourceBundle> costs = new EnumMap<>(ItemType.class);
        costs.put(ItemType.TELEPORT, ResourceBundle.of(10, 10, 10, 5));
        costs.put(ItemType.MOBILITY, ResourceBundle.of(10, 5, 5, 0));
        costs.put(ItemType.COMBAT, ResourceBundle.of(5, 5, 10, 5));
        return Map.copyOf(costs);
    }
}
