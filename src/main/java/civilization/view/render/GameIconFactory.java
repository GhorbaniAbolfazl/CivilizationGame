package civilization.view.render;

import civilization.model.building.BuildingType;
import civilization.model.map.NaturalResourceType;
import civilization.model.unit.UnitType;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Small dependency-free vector icons shared by map controls and tooltips. */
public final class GameIconFactory {
    private GameIconFactory() {
    }

    public static Icon unit(UnitType type) {
        return new GlyphIcon(unitGlyph(type), new Color(49, 151, 218));
    }

    public static Icon building(BuildingType type) {
        return new GlyphIcon(buildingGlyph(type), new Color(148, 103, 55));
    }

    public static String unitGlyph(UnitType type) {
        return switch (type) {
            case EXPLORER -> "E";
            case BUILDER -> "B";
            case WORKER -> "W";
            case BORDER_EXPANDER -> "+";
            case SWORDSMAN -> "S";
            case ARCHER -> "A";
            case CAVALRY -> "C";
        };
    }

    public static String buildingGlyph(BuildingType type) {
        return switch (type) {
            case TOWN_HALL -> "H";
            case LUMBER_MILL -> "L";
            case STONE_MINE -> "S";
            case IRON_MINE -> "I";
            case FARM -> "F";
            case STABLE -> "A";
            case SETTLEMENT -> "T";
            case MILITARY_STABLE -> "M";
            case BAZAAR -> "$";
            case DOCK -> "D";
            case MONUMENT -> "U";
            // PHASE 3 - Added in Phase 3
            case APOTHECARY -> "P";
        };
    }

    public static String resourceGlyph(NaturalResourceType type) {
        return switch (type) {
            case WOOD -> "W";
            case STONE -> "S";
            case IRON -> "I";
            case WHEAT -> "G";
            case RICE -> "R";
            case CATTLE -> "C";
            case SHEEP -> "A";
            case FISH -> "F";
            case HORSE -> "H";
        };
    }

    private record GlyphIcon(String glyph, Color color) implements Icon {
        @Override
        public void paintIcon(Component component, Graphics source, int x, int y) {
            Graphics2D graphics = (Graphics2D) source.create();
            try {
                graphics.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.setColor(color);
                graphics.fillRoundRect(x + 1, y + 1, 17, 17, 6, 6);
                graphics.setColor(new Color(255, 255, 255, 210));
                graphics.setStroke(new BasicStroke(1.2f));
                graphics.drawRoundRect(x + 1, y + 1, 17, 17, 6, 6);
                graphics.setFont(graphics.getFont().deriveFont(java.awt.Font.BOLD, 11f));
                var metrics = graphics.getFontMetrics();
                graphics.drawString(glyph, x + 10 - metrics.stringWidth(glyph) / 2,
                        y + 10 + metrics.getAscent() / 2 - 1);
            } finally {
                graphics.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return 20;
        }

        @Override
        public int getIconHeight() {
            return 20;
        }
    }
}
