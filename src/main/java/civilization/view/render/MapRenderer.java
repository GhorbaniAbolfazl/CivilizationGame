package civilization.view.render;

import civilization.application.query.HexSnapshot;
import civilization.application.query.MapSnapshot;
import civilization.application.query.UnitSnapshot;
import civilization.application.action.ActionPreview;
import civilization.model.map.HexCoord;
import civilization.model.map.NaturalResourceType;
import civilization.model.map.TerrainType;
import civilization.view.animation.AnimationManager;
import civilization.view.animation.VisualEffectManager;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.util.Map;
import java.util.Objects;

/** Stateless Java2D map painter over immutable snapshots and UI-only animation state. */
public final class MapRenderer {
    private static final Color UNKNOWN = new Color(17, 24, 39);
    private static final Color FOG = new Color(20, 24, 32, 125);
    private static final Color BORDER = new Color(245, 194, 66);
    private static final Color HEX_EDGE = new Color(42, 48, 58);
    private static final Color BUILDING_OUTLINE = new Color(255, 230, 170);
    private static final Color HOSTILE_OUTLINE = new Color(255, 105, 97);
    private static final Color HEALTH_EMPTY = new Color(55, 20, 20);
    private static final Color HEALTH_FILLED = new Color(75, 220, 105);
    private static final BasicStroke HEX_STROKE = new BasicStroke(1.0f);
    private static final BasicStroke BORDER_STROKE = new BasicStroke(2.5f);
    private static final BasicStroke PREVIEW_STROKE = new BasicStroke(3.0f);

    public void render(
            Graphics2D source,
            MapSnapshot snapshot,
            HexLayout layout,
            CameraState camera,
            AnimationManager animations,
            VisualEffectManager effects,
            Map<HexCoord, ActionPreview> movementPreviews,
            int width,
            int height) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(animations, "animations");
        Objects.requireNonNull(effects, "effects");
        Objects.requireNonNull(movementPreviews, "movementPreviews");
        Graphics2D graphics = (Graphics2D) source.create();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            snapshot.hexes().stream()
                    .filter(hex -> isOnScreen(hex.coordinate(), layout, camera, width, height))
                    .forEach(hex -> drawHex(graphics, hex, layout, camera, width, height));
            drawInfrastructure(graphics, snapshot, layout, camera, width, height);
            snapshot.buildings().stream()
                    .filter(building -> isOnScreen(building.position(), layout, camera, width, height))
                    .forEach(building -> drawBuilding(graphics, building, layout, camera, width, height));
            snapshot.tradingPosts().stream()
                    .filter(post -> isOnScreen(post.position(), layout, camera, width, height))
                    .forEach(post -> drawTradingPost(graphics, post, layout, camera, width, height));
            snapshot.tribes().stream().filter(civilization.application.query.TribeSnapshot::currentlyVisible)
                    .filter(tribe -> isOnScreen(tribe.camp(), layout, camera, width, height))
                    .forEach(tribe -> drawTribeCamp(
                            graphics, tribe, layout, camera, width, height));
            int unitRadius = Math.max(7,
                    (int) Math.round(layout.hexRadius() * camera.zoom() * 0.29));
            graphics.setFont(graphics.getFont().deriveFont(java.awt.Font.BOLD,
                    Math.max(9f, unitRadius)));
            snapshot.units().stream()
                    .filter(unit -> isOnScreen(unit.position(), layout, camera, width, height)
                            || animations.isAnimating(unit.id()))
                    .forEach(unit -> drawUnit(graphics, unit, layout, camera, animations, width, height));
            snapshot.wildAnimals().stream()
                    .filter(animal -> isOnScreen(animal.position(), layout, camera, width, height))
                    .forEach(animal -> drawAnimal(graphics, animal, layout, camera, width, height));
            effects.render(graphics, layout, camera, width, height);
            snapshot.hexes().stream()
                    .filter(HexSnapshot::discovered)
                    .filter(hex -> !hex.currentlyVisible())
                    .filter(hex -> isOnScreen(hex.coordinate(), layout, camera, width, height))
                    .forEach(hex -> drawFog(graphics, hex, layout, camera, width, height));
            movementPreviews.forEach((coordinate, preview) -> drawMovementPreview(
                    graphics, coordinate, preview, layout, camera, width, height));
        } finally {
            graphics.dispose();
        }
    }

    private static void drawInfrastructure(Graphics2D graphics, MapSnapshot snapshot,
            HexLayout layout, CameraState camera, int width, int height) {
        java.util.Set<HexCoord> roads = snapshot.roads();
        graphics.setStroke(new BasicStroke(Math.max(2f, (float) (3 * camera.zoom()))));
        graphics.setColor(new Color(194, 177, 142));
        for (HexCoord road : roads) {
            if (!isOnScreen(road, layout, camera, width, height)) continue;
            Point2D.Double from = camera.worldToScreen(layout.center(road), width, height);
            for (HexCoord neighbor : road.neighbors()) {
                if (!roads.contains(neighbor)) continue;
                Point2D.Double to = camera.worldToScreen(layout.center(neighbor), width, height);
                graphics.drawLine((int) from.x, (int) from.y, (int) to.x, (int) to.y);
            }
            graphics.fillOval((int) from.x - 3, (int) from.y - 3, 6, 6);
        }
        graphics.setStroke(new BasicStroke(Math.max(2f, (float) (4 * camera.zoom()))));
        graphics.setColor(new Color(47, 152, 220));
        snapshot.rivers().stream().filter(edge -> isOnScreen(edge.first(), layout, camera, width, height)
                || isOnScreen(edge.second(), layout, camera, width, height)).forEach(edge ->
                drawEdgeLine(graphics, edge, layout, camera, width, height));
        graphics.setStroke(new BasicStroke(Math.max(3f, (float) (5 * camera.zoom()))));
        graphics.setColor(new Color(119, 83, 55));
        snapshot.walls().stream().filter(wall -> isOnScreen(wall.edge().first(), layout, camera, width, height)
                || isOnScreen(wall.edge().second(), layout, camera, width, height)).forEach(wall ->
                drawEdgeLine(graphics, wall.edge(), layout, camera, width, height));
    }

    private static boolean isOnScreen(HexCoord coordinate, HexLayout layout, CameraState camera,
            int width, int height) {
        Point2D.Double screen = camera.worldToScreen(layout.center(coordinate), width, height);
        double margin = Math.max(12.0, layout.hexRadius() * camera.zoom() * 1.25);
        return screen.x >= -margin && screen.x <= width + margin
                && screen.y >= -margin && screen.y <= height + margin;
    }

    private static void drawEdgeLine(Graphics2D graphics,
            civilization.model.map.HexEdge edge, HexLayout layout, CameraState camera,
            int width, int height) {
        Point2D.Double first = camera.worldToScreen(layout.center(edge.first()), width, height);
        Point2D.Double second = camera.worldToScreen(layout.center(edge.second()), width, height);
        graphics.drawLine((int) first.x, (int) first.y, (int) second.x, (int) second.y);
    }

    private static void drawTradingPost(
            Graphics2D graphics,
            civilization.application.query.TradingPostSnapshot post,
            HexLayout layout,
            CameraState camera,
            int width,
            int height) {
        Point2D.Double screen = camera.worldToScreen(layout.center(post.position()), width, height);
        int radius = Math.max(8, (int) Math.round(layout.hexRadius() * camera.zoom() * 0.25));
        graphics.setColor(post.controlled() ? new Color(229, 181, 66) : new Color(155, 164, 177));
        graphics.fillRect((int) screen.x - radius, (int) screen.y - radius, radius * 2, radius * 2);
        graphics.setColor(Color.WHITE);
        graphics.drawRect((int) screen.x - radius, (int) screen.y - radius, radius * 2, radius * 2);
        String label = "80%";
        FontMetrics metrics = graphics.getFontMetrics();
        graphics.drawString(label, (int) screen.x - metrics.stringWidth(label) / 2,
                (int) screen.y + metrics.getAscent() / 2);
    }

    private static void drawTribeCamp(Graphics2D graphics,
            civilization.application.query.TribeSnapshot tribe, HexLayout layout,
            CameraState camera, int width, int height) {
        Point2D.Double screen = camera.worldToScreen(layout.center(tribe.camp()), width, height);
        int radius = Math.max(9, (int) Math.round(layout.hexRadius() * camera.zoom() * 0.28));
        graphics.setColor(tribe.defeated() ? new Color(72, 112, 145) : new Color(138, 82, 47));
        if (tribe.defeated()) {
            graphics.fillRect((int) screen.x - radius, (int) screen.y - radius,
                    radius * 2, radius * 2);
        } else {
            graphics.fillOval((int) screen.x - radius, (int) screen.y - radius,
                    radius * 2, radius * 2);
        }
        graphics.setColor(new Color(255, 222, 142));
        graphics.drawOval((int) screen.x - radius, (int) screen.y - radius,
                radius * 2, radius * 2);
        String label = tribe.defeated() ? "O" : tribe.type().name().substring(0, 1);
        FontMetrics metrics = graphics.getFontMetrics();
        graphics.drawString(label, (int) screen.x - metrics.stringWidth(label) / 2,
                (int) screen.y + metrics.getAscent() / 2);
    }

    private static void drawMovementPreview(
            Graphics2D graphics,
            HexCoord coordinate,
            ActionPreview preview,
            HexLayout layout,
            CameraState camera,
            int width,
            int height) {
        Polygon polygon = layout.screenPolygon(coordinate, camera, width, height);
        Color color = preview.allowed()
                ? new Color(88, 224, 143, 205)
                : new Color(244, 91, 91, 190);
        graphics.setColor(color);
        graphics.setStroke(PREVIEW_STROKE);
        graphics.drawPolygon(polygon);
        if (!preview.allowed()) {
            var bounds = polygon.getBounds();
            String marker = "X";
            graphics.setFont(graphics.getFont().deriveFont(java.awt.Font.BOLD, 14f));
            FontMetrics metrics = graphics.getFontMetrics();
            graphics.drawString(marker,
                    bounds.x + (bounds.width - metrics.stringWidth(marker)) / 2,
                    bounds.y + (bounds.height + metrics.getAscent()) / 2);
        }
    }

    private static void drawBuilding(
            Graphics2D graphics,
            civilization.application.query.BuildingSnapshot building,
            HexLayout layout,
            CameraState camera,
            int width,
            int height) {
        Point2D.Double screen = camera.worldToScreen(layout.center(building.position()), width, height);
        int size = Math.max(8, (int) Math.round(layout.hexRadius() * camera.zoom() * 0.48));
        Color base = building.status() == civilization.model.building.BuildingStatus.BROKEN
                ? new Color(120, 43, 43) : buildingColor(building.type());
        graphics.setColor(base);
        int[] x = {(int) screen.x, (int) screen.x + size / 2,
                (int) screen.x + size / 2, (int) screen.x - size / 2,
                (int) screen.x - size / 2};
        int[] y = {(int) screen.y - size / 2, (int) screen.y - size / 6,
                (int) screen.y + size / 2, (int) screen.y + size / 2,
                (int) screen.y - size / 6};
        graphics.fillPolygon(x, y, x.length);
        graphics.setColor(BUILDING_OUTLINE);
        graphics.drawPolygon(x, y, x.length);
        String label = GameIconFactory.buildingGlyph(building.type());
        FontMetrics metrics = graphics.getFontMetrics();
        graphics.drawString(label,
                (int) Math.round(screen.x) - metrics.stringWidth(label) / 2,
                (int) Math.round(screen.y) + metrics.getAscent() / 2);
    }

    private static void drawHex(
            Graphics2D graphics,
            HexSnapshot hex,
            HexLayout layout,
            CameraState camera,
            int width,
            int height) {
        Polygon polygon = layout.screenPolygon(hex.coordinate(), camera, width, height);
        graphics.setColor(hex.discoveredTerrain().map(MapRenderer::terrainColor).orElse(UNKNOWN));
        graphics.fillPolygon(polygon);
        graphics.setColor(HEX_EDGE);
        graphics.setStroke(HEX_STROKE);
        graphics.drawPolygon(polygon);
        if (hex.owned()) {
            graphics.setColor(BORDER);
            graphics.setStroke(BORDER_STROKE);
            graphics.drawPolygon(polygon);
        }
        if (hex.discovered()) {
            drawResourceLabel(graphics, hex, polygon);
        }
    }

    private static void drawFog(
            Graphics2D graphics,
            HexSnapshot hex,
            HexLayout layout,
            CameraState camera,
            int width,
            int height) {
        graphics.setColor(FOG);
        graphics.fillPolygon(layout.screenPolygon(hex.coordinate(), camera, width, height));
    }

    private static void drawResourceLabel(Graphics2D graphics, HexSnapshot hex, Polygon polygon) {
        if (hex.remainingResources().isEmpty()) {
            return;
        }
        String label = hex.remainingResources().entrySet().stream()
                .map(MapRenderer::resourceLabel)
                .sorted()
                .reduce((first, second) -> first + "/" + second)
                .orElse("");
        FontMetrics metrics = graphics.getFontMetrics();
        int centerX = polygon.getBounds().x + polygon.getBounds().width / 2;
        int centerY = polygon.getBounds().y + polygon.getBounds().height / 2;
        graphics.setColor(Color.WHITE);
        graphics.drawString(label, centerX - metrics.stringWidth(label) / 2, centerY + 4);
    }

    private static String resourceLabel(Map.Entry<NaturalResourceType, Integer> entry) {
        String icon = GameIconFactory.resourceGlyph(entry.getKey());
        return entry.getValue() == 0 ? icon + " X" : icon;
    }

    private static void drawUnit(
            Graphics2D graphics,
            UnitSnapshot unit,
            HexLayout layout,
            CameraState camera,
            AnimationManager animations,
            int width,
            int height) {
        WorldPoint visualWorld = animations.visualPosition(unit.id())
                .orElseGet(() -> layout.center(unit.position()));
        Point2D.Double screen = camera.worldToScreen(visualWorld, width, height);
        int radius = Math.max(7, (int) Math.round(layout.hexRadius() * camera.zoom() * 0.29));
        graphics.setColor(unitColor(unit.type()));
        if (unit.embarked()) {
            int x = (int) Math.round(screen.x), y = (int) Math.round(screen.y);
            graphics.fillPolygon(new int[]{x-radius,x+radius,x+radius/2,x-radius/2},
                    new int[]{y,y,y+radius,y+radius},4);
            graphics.setColor(Color.WHITE);
            graphics.drawLine(x,y-radius,x,y+radius/2);
            graphics.drawLine(x,y-radius,x+radius,y);
        } else {
            graphics.fillOval((int) Math.round(screen.x) - radius,
                    (int) Math.round(screen.y) - radius, radius * 2, radius * 2);
        }
        graphics.setColor(Color.WHITE);
        if (!unit.embarked()) graphics.drawOval((int) Math.round(screen.x) - radius,
                (int) Math.round(screen.y) - radius, radius * 2, radius * 2);
        if (!unit.owner().equals(civilization.model.identity.FactionId.PLAYER)) {
            graphics.setColor(HOSTILE_OUTLINE);
            graphics.setStroke(BORDER_STROKE);
            graphics.drawOval((int)screen.x-radius-3,(int)screen.y-radius-3,
                    radius*2+6,radius*2+6);
        }
        String glyph = GameIconFactory.unitGlyph(unit.type());
        FontMetrics metrics = graphics.getFontMetrics();
        graphics.drawString(glyph, (int) screen.x - metrics.stringWidth(glyph) / 2,
                (int) screen.y + metrics.getAscent() / 2 - 1);
        if (unit.maximumHealth() > 0) {
            int bar=radius*2;
            graphics.setColor(HEALTH_EMPTY);
            graphics.fillRect((int)screen.x-radius,(int)screen.y+radius+3,bar,3);
            graphics.setColor(HEALTH_FILLED);
            graphics.fillRect((int)screen.x-radius,(int)screen.y+radius+3,
                    bar*unit.health()/unit.maximumHealth(),3);
        }
    }

    private static void drawAnimal(Graphics2D graphics,
            civilization.application.query.WildAnimalSnapshot animal, HexLayout layout,
            CameraState camera, int width, int height) {
        Point2D.Double screen=camera.worldToScreen(layout.center(animal.position()),width,height);
        int radius=Math.max(7,(int)Math.round(layout.hexRadius()*camera.zoom()*0.24));
        graphics.setColor(animal.bear()?new Color(105,63,37):new Color(125,95,65));
        graphics.fillOval((int)screen.x-radius,(int)screen.y-radius,radius*2,radius*2);
        graphics.setColor(Color.WHITE);
        graphics.drawString(animal.bear()?"B":"A",(int)screen.x-4,(int)screen.y+4);
    }

    private static Color unitColor(civilization.model.unit.UnitType type) {
        return switch (type) {
            case EXPLORER -> new Color(46, 174, 235);
            case BUILDER -> new Color(238, 154, 54);
            case WORKER -> new Color(72, 190, 121);
            case BORDER_EXPANDER -> new Color(176, 105, 224);
            case SWORDSMAN -> new Color(191, 65, 65);
            case ARCHER -> new Color(61, 132, 78);
            case CAVALRY -> new Color(71, 84, 153);
        };
    }

    private static Color buildingColor(civilization.model.building.BuildingType type) {
        return switch (type) {
            case TOWN_HALL -> new Color(143, 90, 54);
            case LUMBER_MILL -> new Color(106, 72, 43);
            case STONE_MINE -> new Color(89, 97, 108);
            case IRON_MINE -> new Color(68, 78, 91);
            case FARM -> new Color(166, 129, 43);
            case STABLE -> new Color(140, 91, 48);
            case SETTLEMENT -> new Color(183, 112, 53);
            case MILITARY_STABLE -> new Color(112, 76, 49);
            case BAZAAR -> new Color(171, 117, 31);
            case DOCK -> new Color(44, 112, 148);
            case MONUMENT -> new Color(174, 157, 111);
            // PHASE 3 - Added in Phase 3
            case APOTHECARY -> new Color(126, 75, 145);
        };
    }

    private static Color terrainColor(TerrainType terrain) {
        return switch (terrain) {
            case FOREST -> new Color(46, 108, 59);
            case PLAINS -> new Color(193, 172, 93);
            case MOUNTAIN -> new Color(112, 118, 127);
            case GRASSLAND -> new Color(103, 164, 82);
            case SEA -> new Color(51, 116, 180);
            case MOUNTAIN_RANGE -> new Color(82, 84, 88);
        };
    }
}
