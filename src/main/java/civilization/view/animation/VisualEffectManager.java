package civilization.view.animation;

import civilization.model.map.HexCoord;
import civilization.view.render.CameraState;
import civilization.view.render.HexLayout;

import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import civilization.model.disaster.DisasterType;
import civilization.model.time.Season;

/** Non-authoritative short-lived construction and economy feedback. */
public final class VisualEffectManager {
    private static final long DURATION_NANOS = 900_000_000L;
    private final List<Effect> effects = new ArrayList<>();
    private final Runnable repaintRequest;
    private final Timer timer;
    private Season season = Season.SPRING;
    private Runnable effectStateChanged = () -> { };

    public VisualEffectManager(Runnable repaintRequest) {
        this.repaintRequest = Objects.requireNonNull(repaintRequest, "repaintRequest");
        timer = new Timer(16, event -> tick());
        timer.setCoalesce(true);
    }

    public void showBuild(HexCoord coordinate) {
        add(coordinate, "BUILT", new Color(255, 202, 78));
    }

    public void showProduction(HexCoord coordinate) {
        add(coordinate, "+ RESOURCES", new Color(101, 227, 162));
    }

    public void showDisaster(DisasterType type, java.util.Set<HexCoord> coordinates) {
        Color color = switch (type) {
            case EARTHQUAKE -> new Color(214, 137, 76);
            case FLOOD -> new Color(55, 165, 230);
            case BEAR_ATTACK -> new Color(151, 94, 54);
        };
        String label = switch (type) {
            case EARTHQUAKE -> "QUAKE";
            case FLOOD -> "FLOOD";
            case BEAR_ATTACK -> "BEAR";
        };
        coordinates.forEach(coordinate -> add(coordinate, label, color, type));
    }

    public void setSeason(Season season) {
        this.season = Objects.requireNonNull(season, "season");
        if ((season == Season.AUTUMN || season == Season.WINTER) && !timer.isRunning()) timer.start();
        repaintRequest.run();
    }

    public void render(
            Graphics2D graphics, HexLayout layout, CameraState camera, int width, int height) {
        long now = System.nanoTime();
        for (Effect effect : effects) {
            double progress = Math.min(1.0, (now - effect.startedAt()) / (double) DURATION_NANOS);
            var screen = camera.worldToScreen(layout.center(effect.coordinate()), width, height);
            int radius = (int) Math.round((18 + progress * 32) * camera.zoom());
            int alpha = (int) Math.round(220 * (1.0 - progress));
            graphics.setColor(new Color(
                    effect.color().getRed(), effect.color().getGreen(), effect.color().getBlue(), alpha));
            graphics.setStroke(new BasicStroke(3f));
            if (effect.disasterType() == DisasterType.EARTHQUAKE) {
                int x=(int)screen.x, y=(int)screen.y;
                graphics.drawPolyline(new int[]{x-radius,x-8,x-16,x+4,x+12,x+radius},
                        new int[]{y-4,y+7,y+radius/2,y-8,y+5,y-12},6);
                graphics.drawLine(x-4,y-10,x+8,y+radius);
            } else if (effect.disasterType() == DisasterType.FLOOD) {
                graphics.fillOval((int)screen.x-radius,(int)screen.y-radius/2,
                        radius*2,radius);
                graphics.setColor(new Color(210,240,255,alpha));
                graphics.drawArc((int)screen.x-radius,(int)screen.y-radius/3,
                        radius,radius/2,0,180);
            } else if (effect.disasterType() == DisasterType.BEAR_ATTACK) {
                int x=(int)screen.x, y=(int)screen.y;
                graphics.fillOval(x-8,y-5,16,18);
                for(int dx : new int[]{-11,-4,4,11}) graphics.fillOval(x+dx-3,y-14,6,7);
            } else {
                graphics.drawOval((int) screen.x - radius, (int) screen.y - radius,
                        radius * 2, radius * 2);
            }
            graphics.setFont(graphics.getFont().deriveFont(java.awt.Font.BOLD, 11f));
            graphics.drawString(effect.label(), (int) screen.x - 24,
                    (int) Math.round(screen.y - 28 - progress * 18));
        }
        renderWeather(graphics, width, height, now);
    }

    public boolean hasActiveEffects() {
        return !effects.isEmpty();
    }

    /** Called only when short-lived effects transition between empty and non-empty. */
    public void setEffectStateChanged(Runnable effectStateChanged) {
        this.effectStateChanged = Objects.requireNonNull(effectStateChanged,
                "effectStateChanged");
    }

    public void cancelAll() {
        boolean wasActive = !effects.isEmpty();
        effects.clear();
        timer.stop();
        repaintRequest.run();
        if (wasActive) effectStateChanged.run();
    }

    private void add(HexCoord coordinate, String label, Color color) {
        add(coordinate,label,color,null);
    }

    private void add(HexCoord coordinate, String label, Color color, DisasterType disasterType) {
        boolean wasEmpty = effects.isEmpty();
        effects.add(new Effect(Objects.requireNonNull(coordinate, "coordinate"), label,
                color, System.nanoTime(), disasterType));
        if (!timer.isRunning()) {
            timer.start();
        }
        repaintRequest.run();
        if (wasEmpty) effectStateChanged.run();
    }

    private void tick() {
        long now = System.nanoTime();
        boolean wasActive = !effects.isEmpty();
        effects.removeIf(effect -> now - effect.startedAt() >= DURATION_NANOS);
        if (effects.isEmpty() && season != Season.AUTUMN && season != Season.WINTER) {
            timer.stop();
        }
        repaintRequest.run();
        if (wasActive && effects.isEmpty()) effectStateChanged.run();
    }

    private void renderWeather(Graphics2D graphics, int width, int height, long now) {
        if (season != Season.AUTUMN && season != Season.WINTER) return;
        long frame = now / 35_000_000L;
        graphics.setStroke(new BasicStroke(season == Season.AUTUMN ? 2f : 1.5f));
        graphics.setColor(season == Season.AUTUMN
                ? new Color(125, 190, 235, 115) : new Color(245, 250, 255, 190));
        for (int i = 0; i < 90; i++) {
            int x = Math.floorMod(i * 97 + (int) frame * (season == Season.AUTUMN ? 5 : 1),
                    Math.max(1, width));
            int y = Math.floorMod(i * 53 + (int) frame * (season == Season.AUTUMN ? 13 : 3),
                    Math.max(1, height));
            if (season == Season.AUTUMN) graphics.drawLine(x, y, x - 5, y + 12);
            else graphics.fillOval(x, y, 3, 3);
        }
    }

    private record Effect(HexCoord coordinate, String label, Color color, long startedAt,
            DisasterType disasterType) {
    }
}
