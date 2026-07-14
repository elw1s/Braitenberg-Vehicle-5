import java.awt.Color;
import java.awt.Graphics2D;

/**
 * A light source: a point of light at (x, y).
 *
 * It has no brightness of its own - every source is identical. What a sensor reads is decided by
 * the inverse-square law in Vehicle.readSensor(). The radius here is only how big the glowing disc
 * is DRAWN; it has no effect on the physics, and a sensor that passes just outside it still reads
 * plenty of light.
 *
 * x and y are not final: the sources can be dragged around the world while the vehicle drives.
 */
public class Source {

    public double x, y;
    final Color color;

    public Source(double x, double y, Color color) {
        this.x = x;
        this.y = y;
        this.color = color;
    }

    /** Whether a click at (px, py) lands on this source. Generous, so it is easy to grab. */
    public boolean contains(double px, double py) {
        double dx = px - x;
        double dy = py - y;
        return dx * dx + dy * dy <= (Parameters.SOURCE_RADIUS + 6) * (Parameters.SOURCE_RADIUS + 6);
    }

    public void draw(Graphics2D g) {
        Draw.disc(g, x, y, Parameters.SOURCE_RADIUS, color);
    }
}
