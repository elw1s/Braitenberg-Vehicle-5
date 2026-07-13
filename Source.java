import java.awt.Color;
import java.awt.Graphics2D;

/**
 * A light source: a point of light at (x, y).
 *
 * It has no brightness of its own - every source is identical. What a sensor reads
 * is decided by the inverse-square law in Vehicle.readSensor(). The radius here is
 * only how big the glowing disc is drawn; it has no effect on the physics.
 */
public class Source {

    final double x, y;
    final Color color;

    public Source(double x, double y, Color color) {
        this.x = x;
        this.y = y;
        this.color = color;
    }

    public void draw(Graphics2D g) {
        Draw.disc(g, x, y, Parameters.SOURCE_RADIUS, color);
    }
}
