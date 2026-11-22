package civilization.view.render;

public record WorldPoint(double x, double y) {
    public WorldPoint interpolate(WorldPoint target, double progress) {
        if (progress < 0.0 || progress > 1.0) {
            throw new IllegalArgumentException("Progress must be between zero and one");
        }
        return new WorldPoint(
                x + (target.x - x) * progress,
                y + (target.y - y) * progress);
    }
}
