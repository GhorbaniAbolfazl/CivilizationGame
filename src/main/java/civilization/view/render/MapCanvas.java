package civilization.view.render;

import civilization.application.query.MapSnapshot;
import civilization.application.event.UnitMovedEvent;
import civilization.application.action.ActionPreview;
import civilization.model.map.HexCoord;
import civilization.view.animation.AnimationManager;
import civilization.view.animation.VisualEffectManager;
import civilization.view.input.KeyBindingInstaller;
import civilization.view.input.MapInputAdapter;

import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import javax.swing.ToolTipManager;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.Map;

/** Minimal Swing map surface with discrete wheel zoom, middle-drag pan, and hex intents. */
public final class MapCanvas extends JPanel {
    private static final long serialVersionUID = 1L;
    private final HexLayout layout;
    private final CameraState camera;
    private final MapRenderer renderer;
    private final AnimationManager animations;
    private final VisualEffectManager effects;
    private final MapInputAdapter input;
    private MapSnapshot snapshot = new MapSnapshot(
            0, java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of());
    private Consumer<HexCoord> primaryHexIntent = coordinate -> { };
    private Consumer<HexCoord> secondaryHexIntent = coordinate -> { };
    private BooleanSupplier actionInputAllowed = () -> true;
    private Map<HexCoord, ActionPreview> movementPreviews = Map.of();

    public MapCanvas(HexLayout layout, CameraState camera) {
        this.layout = Objects.requireNonNull(layout, "layout");
        this.camera = Objects.requireNonNull(camera, "camera");
        this.renderer = new MapRenderer();
        this.animations = new AnimationManager(layout, this::repaint);
        this.effects = new VisualEffectManager(this::repaint);
        this.input = new MapInputAdapter(layout, camera);
        setPreferredSize(new Dimension(900, 650));
        setBackground(new java.awt.Color(12, 18, 27));
        setToolTipText(" ");
        ToolTipManager.sharedInstance().setInitialDelay(250);
        installMouseInput();
        KeyBindingInstaller.installCameraPan(this, camera, this::repaint);
    }

    public CameraState camera() {
        return camera;
    }

    public AnimationManager animations() {
        return animations;
    }

    public VisualEffectManager effects() {
        return effects;
    }

    public HexLayout hexLayout() {
        return layout;
    }

    public void setSnapshot(MapSnapshot snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        repaint();
    }

    /** Installs committed state and its visual move before any destination-only repaint can occur. */
    public void displayCommittedMovement(MapSnapshot snapshot, UnitMovedEvent event) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(event, "event");
        if (snapshot.stateVersion() != event.stateVersion()) {
            throw new IllegalArgumentException("Snapshot and movement event versions differ");
        }
        this.snapshot = snapshot;
        animations.startIfCurrent(event, snapshot.stateVersion());
    }

    public void setPrimaryHexIntent(Consumer<HexCoord> primaryHexIntent) {
        this.primaryHexIntent = Objects.requireNonNull(primaryHexIntent, "primaryHexIntent");
    }

    public void setSecondaryHexIntent(Consumer<HexCoord> secondaryHexIntent) {
        this.secondaryHexIntent = Objects.requireNonNull(secondaryHexIntent, "secondaryHexIntent");
    }

    public void setActionInputGate(BooleanSupplier actionInputAllowed) {
        this.actionInputAllowed = Objects.requireNonNull(actionInputAllowed, "actionInputAllowed");
    }

    public void setMovementPreviews(Map<HexCoord, ActionPreview> movementPreviews) {
        this.movementPreviews = Map.copyOf(Objects.requireNonNull(
                movementPreviews, "movementPreviews"));
        repaint();
    }

    public Map<HexCoord, ActionPreview> movementPreviews() {
        return movementPreviews;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        renderer.render((Graphics2D) graphics, snapshot, layout, camera, animations, effects,
                movementPreviews, getWidth(), getHeight());
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        HexCoord coordinate = input.hexAt(event.getPoint(), getWidth(), getHeight());
        var hex = snapshot.hexes().stream()
                .filter(candidate -> candidate.coordinate().equals(coordinate))
                .findFirst().orElse(null);
        if (hex == null) {
            return null;
        }
        if (!hex.discovered()) {
            return "<html><b>Unknown territory</b><br>Explore to reveal terrain and resources.</html>";
        }
        String resources = hex.remainingResources().isEmpty() ? "None"
                : hex.remainingResources().entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .sorted().collect(java.util.stream.Collectors.joining(", "));
        String units = snapshot.units().stream().filter(unit -> unit.position().equals(coordinate))
                .map(unit -> unit.type() + " (AP " + unit.remainingAp() + ")")
                .collect(java.util.stream.Collectors.joining(", "));
        String buildings = snapshot.buildings().stream()
                .filter(building -> building.position().equals(coordinate))
                .map(building -> building.type() + " - " + building.status())
                .collect(java.util.stream.Collectors.joining(", "));
        boolean tradingPost = snapshot.tradingPosts().stream()
                .anyMatch(post -> post.position().equals(coordinate));
        var tribe = snapshot.tribes().stream().filter(candidate -> candidate.currentlyVisible()
                && candidate.camp().equals(coordinate)).findFirst().orElse(null);
        return "<html><b>" + coordinate + " - " + hex.discoveredTerrain().orElseThrow()
                + "</b><br>Resources: " + resources
                + "<br>Empire: " + (hex.owned() ? "inside border" : "outside border")
                + (buildings.isEmpty() ? "" : "<br>Building: " + buildings)
                + (tradingPost ? "<br>Neutral Trading Center" : "")
                + (tribe == null ? "" : "<br>Tribe: " + tribe.name() + " (" + tribe.type() + ")")
                + (units.isEmpty() ? "" : "<br>Units: " + units) + "</html>";
    }

    private void installMouseInput() {
        MouseAdapter adapter = new MouseAdapter() {
            private Point previousPanPoint;

            @Override
            public void mousePressed(MouseEvent event) {
                if (event.getButton() == MouseEvent.BUTTON2) {
                    previousPanPoint = event.getPoint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                if (event.getButton() == MouseEvent.BUTTON2) {
                    previousPanPoint = null;
                }
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (previousPanPoint != null) {
                    input.panDrag(previousPanPoint.x, previousPanPoint.y, event.getX(), event.getY());
                    previousPanPoint = event.getPoint();
                    repaint();
                }
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getButton() == MouseEvent.BUTTON1
                        && animations.acceptsInput() && actionInputAllowed.getAsBoolean()) {
                    primaryHexIntent.accept(input.hexAt(
                            event.getPoint(), getWidth(), getHeight()));
                } else if (event.getButton() == MouseEvent.BUTTON3
                        && animations.acceptsInput() && actionInputAllowed.getAsBoolean()) {
                    secondaryHexIntent.accept(input.hexAt(
                            event.getPoint(), getWidth(), getHeight()));
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent event) {
                if (input.zoomByWheelRotation(event.getWheelRotation())) {
                    repaint();
                }
            }
        };
        addMouseListener(adapter);
        addMouseMotionListener(adapter);
        addMouseWheelListener(adapter);
    }
}
