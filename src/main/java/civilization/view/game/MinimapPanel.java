package civilization.view.game;

import civilization.application.query.BuildingSnapshot;
import civilization.application.query.HexSnapshot;
import civilization.application.query.MapSnapshot;
import civilization.model.building.BuildingType;
import civilization.model.map.HexCoord;
import civilization.view.render.CameraState;
import civilization.view.render.HexLayout;
import civilization.view.render.WorldPoint;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.Objects;

/** Visibility-safe overview; it consumes the same immutable map snapshot as the main map. */
public final class MinimapPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final int PADDING = 12;
    private final HexLayout worldLayout;
    private final CameraState mainCamera;
    private MapSnapshot snapshot = new MapSnapshot(0, java.util.List.of(),
            java.util.List.of(), java.util.List.of(), java.util.List.of());
    private Runnable cameraMoved = () -> { };

    public MinimapPanel(HexLayout worldLayout, CameraState mainCamera) {
        this.worldLayout = Objects.requireNonNull(worldLayout, "worldLayout");
        this.mainCamera = Objects.requireNonNull(mainCamera, "mainCamera");
        setPreferredSize(new Dimension(270, 185));
        setMinimumSize(new Dimension(180, 130));
        setBorder(BorderFactory.createTitledBorder("Minimap - click to center"));
        setToolTipText("Discovered terrain, empire border, Town Hall, settlements, and active units");
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                centerCameraAt(event.getPoint());
            }
        });
    }

    public void setSnapshot(MapSnapshot snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        repaint();
    }

    public void setCameraMoved(Runnable cameraMoved) {
        this.cameraMoved = Objects.requireNonNull(cameraMoved, "cameraMoved");
    }

    public void centerCameraOn(HexCoord coordinate) {
        Objects.requireNonNull(coordinate, "coordinate");
        if (snapshot.hexes().stream().noneMatch(hex -> hex.coordinate().equals(coordinate))) {
            throw new IllegalArgumentException("Minimap coordinate is outside the displayed map");
        }
        mainCamera.centerOn(worldLayout.center(coordinate));
        cameraMoved.run();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics source) {
        super.paintComponent(source);
        if (snapshot.hexes().isEmpty()) {
            return;
        }
        Graphics2D graphics = (Graphics2D) source.create();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Transform transform = transform();
            for (HexSnapshot hex : snapshot.hexes()) {
                Point2D.Double point = transform.screen(hex.coordinate());
                int radius = Math.max(2, (int) Math.round(transform.scale() * 22));
                graphics.setColor(hex.discovered() ? terrainColor(hex) : new Color(24, 32, 46));
                graphics.fillOval((int) point.x - radius, (int) point.y - radius,
                        radius * 2, radius * 2);
                if (hex.owned()) {
                    graphics.setColor(new Color(246, 196, 74));
                    graphics.setStroke(new BasicStroke(1.6f));
                    graphics.drawOval((int) point.x - radius, (int) point.y - radius,
                            radius * 2, radius * 2);
                }
            }
            for (BuildingSnapshot building : snapshot.buildings()) {
                if (building.type() != BuildingType.TOWN_HALL
                        && building.type() != BuildingType.SETTLEMENT) {
                    continue;
                }
                Point2D.Double point = transform.screen(building.position());
                graphics.setColor(building.type() == BuildingType.TOWN_HALL
                        ? Color.WHITE : new Color(255, 165, 72));
                int size = building.type() == BuildingType.TOWN_HALL ? 8 : 6;
                graphics.fillRect((int) point.x - size / 2, (int) point.y - size / 2, size, size);
            }
            graphics.setColor(new Color(224, 185, 75));
            for (var post : snapshot.tradingPosts()) {
                Point2D.Double point = transform.screen(post.position());
                graphics.drawRect((int) point.x - 3, (int) point.y - 3, 6, 6);
            }
            graphics.setColor(new Color(202, 112, 68));
            for (var tribe : snapshot.tribes()) {
                if (!tribe.currentlyVisible() || tribe.defeated()) continue;
                Point2D.Double point = transform.screen(tribe.camp());
                graphics.fillOval((int) point.x - 4, (int) point.y - 4, 8, 8);
            }
            graphics.setColor(new Color(61, 199, 255));
            for (var unit : snapshot.units()) {
                Point2D.Double point = transform.screen(unit.position());
                graphics.fillOval((int) point.x - 2, (int) point.y - 2, 5, 5);
            }
            Point2D.Double camera = transform.screenWorld(mainCamera.center());
            graphics.setColor(new Color(255, 255, 255, 190));
            graphics.drawOval((int) camera.x - 6, (int) camera.y - 6, 12, 12);
        } finally {
            graphics.dispose();
        }
    }

    private void centerCameraAt(java.awt.Point click) {
        if (snapshot.hexes().isEmpty()) {
            return;
        }
        Transform transform = transform();
        HexCoord nearest = snapshot.hexes().stream()
                .map(HexSnapshot::coordinate)
                .min(java.util.Comparator.comparingDouble(coordinate -> {
                    Point2D.Double point = transform.screen(coordinate);
                    return point.distanceSq(click.x, click.y);
                })).orElseThrow();
        centerCameraOn(nearest);
    }

    private Transform transform() {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (HexSnapshot hex : snapshot.hexes()) {
            WorldPoint point = worldLayout.center(hex.coordinate());
            minX = Math.min(minX, point.x());
            maxX = Math.max(maxX, point.x());
            minY = Math.min(minY, point.y());
            maxY = Math.max(maxY, point.y());
        }
        double scale = Math.min(
                Math.max(1, getWidth() - PADDING * 2) / Math.max(1.0, maxX - minX),
                Math.max(1, getHeight() - PADDING * 2 - 12) / Math.max(1.0, maxY - minY));
        return new Transform(minX, minY, scale);
    }

    private static Color terrainColor(HexSnapshot hex) {
        if (!hex.currentlyVisible()) {
            return new Color(70, 79, 88);
        }
        return switch (hex.discoveredTerrain().orElseThrow()) {
            case FOREST -> new Color(47, 129, 73);
            case PLAINS -> new Color(199, 174, 91);
            case MOUNTAIN -> new Color(126, 133, 145);
            case GRASSLAND -> new Color(103, 170, 91);
            case SEA -> new Color(43, 105, 170);
            case MOUNTAIN_RANGE -> new Color(87, 88, 92);
        };
    }

    private final class Transform {
        private final double minX;
        private final double minY;
        private final double scale;

        private Transform(double minX, double minY, double scale) {
            this.minX = minX;
            this.minY = minY;
            this.scale = scale;
        }

        private double scale() {
            return scale;
        }

        private Point2D.Double screen(HexCoord coordinate) {
            return screenWorld(worldLayout.center(coordinate));
        }

        private Point2D.Double screenWorld(WorldPoint point) {
            return new Point2D.Double(
                    PADDING + (point.x() - minX) * scale,
                    PADDING + 14 + (point.y() - minY) * scale);
        }
    }
}
