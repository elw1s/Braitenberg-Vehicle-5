import java.awt.Color;
import java.awt.Graphics2D;

/** Small drawing helpers, so the simulation classes stay readable. */
public class Draw {

    /** A filled circle with a black outline. */
    public static void disc(Graphics2D g, double centerX, double centerY, double radius, Color fill) {
        int diameter = (int) (2 * radius);
        g.setColor(fill);
        g.fillOval((int) (centerX - radius), (int) (centerY - radius), diameter, diameter);
        g.setColor(Color.BLACK);
        g.drawOval((int) (centerX - radius), (int) (centerY - radius), diameter, diameter);
    }
}
