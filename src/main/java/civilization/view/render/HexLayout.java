package civilization.view.render;

import civilization.model.map.HexCoord;

import java.awt.Polygon;
import java.awt.geom.Point2D;
import java.util.Objects;

/** Pointy-top axial/world conversion; the domain coordinate remains pixel-independent. */
public final class HexLayout {
    private static final double SQRT_THREE = Math.sqrt(3.0);
    private final double hexRadius;

    public HexLayout(double hexRadius) {
        if (!Double.isFinite(hexRadius) || hexRadius <= 0.0) {
            throw new IllegalArgumentException("Hex radius must be finite and positive");
        }
        this.hexRadius = hexRadius;
    }

    public double hexRadius() {
        return hexRadius;
    }

    public WorldPoint center(HexCoord coordinate) {
        Objects.requireNonNull(coordinate, "coordinate");
        double x = hexRadius * SQRT_THREE * (coordinate.q() + coordinate.r() / 2.0);
        double y = hexRadius * 1.5 * coordinate.r();
        return new WorldPoint(x, y);
    }

    public HexCoord coordinateAt(WorldPoint point) {
        Objects.requireNonNull(point, "point");
        double q = (SQRT_THREE / 3.0 * point.x() - point.y() / 3.0) / hexRadius;
        double r = (2.0 / 3.0 * point.y()) / hexRadius;
        return roundAxial(q, r);
    }

    public Polygon screenPolygon(HexCoord coordinate, CameraState camera, int width, int height) {
        Point2D.Double center = camera.worldToScreen(center(coordinate), width, height);
        double radius = hexRadius * camera.zoom();
        int[] x = new int[6];
        int[] y = new int[6];
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(60.0 * i - 30.0);
            x[i] = (int) Math.round(center.x + radius * Math.cos(angle));
            y[i] = (int) Math.round(center.y + radius * Math.sin(angle));
        }
        return new Polygon(x, y, 6);
    }

    private static HexCoord roundAxial(double q, double r) {
        double x = q;
        double z = r;
        double y = -x - z;
        long rx = Math.round(x);
        long ry = Math.round(y);
        long rz = Math.round(z);
        double dx = Math.abs(rx - x);
        double dy = Math.abs(ry - y);
        double dz = Math.abs(rz - z);
        if (dx > dy && dx > dz) {
            rx = -ry - rz;
        } else if (dy > dz) {
            ry = -rx - rz;
        } else {
            rz = -rx - ry;
        }
        return new HexCoord(Math.toIntExact(rx), Math.toIntExact(rz));
    }
}
