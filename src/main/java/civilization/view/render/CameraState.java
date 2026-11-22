package civilization.view.render;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Objects;

/** UI-only world center and discrete zoom level. */
public final class CameraState {
    public static final List<Double> DEFAULT_ZOOM_LEVELS = List.of(0.75, 1.0, 1.25, 1.5, 2.0);

    private final List<Double> zoomLevels;
    private int zoomIndex;
    private double centerX;
    private double centerY;

    public CameraState() {
        this(DEFAULT_ZOOM_LEVELS, 1, new WorldPoint(0.0, 0.0));
    }

    public CameraState(List<Double> zoomLevels, int zoomIndex, WorldPoint center) {
        this.zoomLevels = List.copyOf(Objects.requireNonNull(zoomLevels, "zoomLevels"));
        if (this.zoomLevels.isEmpty()
                || this.zoomLevels.stream().anyMatch(value -> value == null
                || !Double.isFinite(value) || value <= 0.0)) {
            throw new IllegalArgumentException("Zoom levels must be finite and positive");
        }
        for (int i = 1; i < this.zoomLevels.size(); i++) {
            if (this.zoomLevels.get(i) <= this.zoomLevels.get(i - 1)) {
                throw new IllegalArgumentException("Zoom levels must be strictly increasing");
            }
        }
        if (zoomIndex < 0 || zoomIndex >= this.zoomLevels.size()) {
            throw new IllegalArgumentException("Zoom index is outside the configured levels");
        }
        this.zoomIndex = zoomIndex;
        centerOn(center);
    }

    public double zoom() {
        return zoomLevels.get(zoomIndex);
    }

    public int zoomIndex() {
        return zoomIndex;
    }

    public List<Double> zoomLevels() {
        return zoomLevels;
    }

    public WorldPoint center() {
        return new WorldPoint(centerX, centerY);
    }

    public boolean zoomIn() {
        if (zoomIndex == zoomLevels.size() - 1) {
            return false;
        }
        zoomIndex++;
        return true;
    }

    public boolean zoomOut() {
        if (zoomIndex == 0) {
            return false;
        }
        zoomIndex--;
        return true;
    }

    public void panScreenPixels(double deltaX, double deltaY) {
        if (!Double.isFinite(deltaX) || !Double.isFinite(deltaY)) {
            throw new IllegalArgumentException("Pan distance must be finite");
        }
        centerX += deltaX / zoom();
        centerY += deltaY / zoom();
    }

    public void centerOn(WorldPoint point) {
        Objects.requireNonNull(point, "point");
        if (!Double.isFinite(point.x()) || !Double.isFinite(point.y())) {
            throw new IllegalArgumentException("Camera center must be finite");
        }
        centerX = point.x();
        centerY = point.y();
    }

    public Point2D.Double worldToScreen(WorldPoint point, int viewportWidth, int viewportHeight) {
        Objects.requireNonNull(point, "point");
        return new Point2D.Double(
                (point.x() - centerX) * zoom() + viewportWidth / 2.0,
                (point.y() - centerY) * zoom() + viewportHeight / 2.0);
    }

    public WorldPoint screenToWorld(double screenX, double screenY, int width, int height) {
        return new WorldPoint(
                (screenX - width / 2.0) / zoom() + centerX,
                (screenY - height / 2.0) / zoom() + centerY);
    }
}
