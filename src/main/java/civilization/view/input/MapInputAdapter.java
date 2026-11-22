package civilization.view.input;

import civilization.model.map.HexCoord;
import civilization.view.render.CameraState;
import civilization.view.render.HexLayout;

import java.awt.Point;
import java.util.Objects;

/** Converts Swing pixels to map intent without deciding gameplay legality. */
public final class MapInputAdapter {
    private final HexLayout layout;
    private final CameraState camera;

    public MapInputAdapter(HexLayout layout, CameraState camera) {
        this.layout = Objects.requireNonNull(layout, "layout");
        this.camera = Objects.requireNonNull(camera, "camera");
    }

    public HexCoord hexAt(Point point, int viewportWidth, int viewportHeight) {
        Objects.requireNonNull(point, "point");
        return layout.coordinateAt(camera.screenToWorld(
                point.x, point.y, viewportWidth, viewportHeight));
    }

    public boolean zoomByWheelRotation(int wheelRotation) {
        if (wheelRotation < 0) {
            return camera.zoomIn();
        }
        if (wheelRotation > 0) {
            return camera.zoomOut();
        }
        return false;
    }

    public void panDrag(int previousX, int previousY, int currentX, int currentY) {
        camera.panScreenPixels(previousX - currentX, previousY - currentY);
    }
}
