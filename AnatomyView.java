import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.Locale;
import javax.swing.JPanel;

/**
 * The picture of the vehicle in the control panel.
 *
 * Every tunable parameter is drawn where it actually acts on the vehicle, with a
 * dotted leader line out to its label. Point at a slider and the matching part of
 * the drawing lights up, so you can see WHERE the number you are about to change
 * lives before you change it.
 *
 * UI only - no physics happens here. It just reads Parameters.
 */
public class AnatomyView extends JPanel {

    // Keys, shared with ControlPanel so a slider can highlight its own part
    public static final String DETECT = "detect";
    public static final String CRUISE = "cruise";
    public static final String STEER = "steer";
    public static final String SPACING = "spacing";
    public static final String PAUSE = "pause";

    private static final double SCALE = 0.72;  // world pixels -> drawing pixels

    private String highlighted;

    // Recomputed every frame from the real panel size, so nothing is ever clipped
    private double cx, cy, width, height;

    public AnatomyView() {
        setPreferredSize(new Dimension(320, 232));
        setBackground(Theme.CARD);
    }

    public void highlight(String key) {
        if (key == null ? highlighted != null : !key.equals(highlighted)) {
            highlighted = key;
            repaint();
        }
    }

    private boolean on(String key) {
        return key.equals(highlighted);
    }

    /** Accent when this part is the one being changed, quiet grey otherwise. */
    private Color tint(String key, Color quiet) {
        return on(key) ? Theme.ACCENT : quiet;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        width = getWidth();
        height = getHeight();
        cx = width / 2;
        cy = height * 0.60;  // the vehicle sits here, pointing up

        double bodyR = Parameters.BODY_RADIUS * SCALE;
        double sensorR = Parameters.SENSOR_RADIUS * SCALE;
        double sensorY = cy - Parameters.SENSOR_FORWARD * SCALE;
        double half = Parameters.SENSOR_SPACING / 2 * SCALE;
        double detectR = Parameters.DETECT_DISTANCE * SCALE;

        drawDetectionRing(g, sensorY, detectR);
        drawSteerArc(g, bodyR);
        drawCruiseArrow(g);
        drawBody(g, bodyR, sensorY, half, sensorR);
        drawSpacingBar(g, sensorY, half, sensorR);
        drawPause(g);
    }

    // ------------------------------------------------------------------ parts

    /** Dashed circle: how close a source must be before the brain counts it. */
    private void drawDetectionRing(Graphics2D g, double sensorY, double r) {
        g.setStroke(Theme.DASHED);
        g.setColor(tint(DETECT, Theme.FAINT));
        g.draw(new Ellipse2D.Double(cx - r, sensorY - r, 2 * r, 2 * r));

        label(g, DETECT, String.format(Locale.US, "detection %.0f px", Parameters.DETECT_DISTANCE),
                width - 6, 18, Anchor.RIGHT,
                cx + r * 0.71, sensorY - r * 0.71);
    }

    /** Arrow out of the nose: how fast it drives when no source is near. */
    private void drawCruiseArrow(Graphics2D g) {
        double length = 18 + Parameters.CRUISE_SPEED * 9;
        double tipY = cy - length;

        g.setStroke(on(CRUISE) ? Theme.BOLD : Theme.THIN);
        g.setColor(tint(CRUISE, Theme.FAINT));
        g.draw(new Line2D.Double(cx, cy, cx, tipY + 5));
        arrowHead(g, cx, tipY, 0, -1);

        label(g, CRUISE, String.format(Locale.US, "cruise %.1f px/frame", Parameters.CRUISE_SPEED),
                6, 18, Anchor.LEFT, cx, tipY + 3);
    }

    /** Curved arrow beside the body: how hard it veers away from the light. */
    private void drawSteerArc(Graphics2D g, double bodyR) {
        double r = bodyR + 20;
        g.setStroke(on(STEER)
                ? new BasicStroke((float) (1.4 + Parameters.STEER_GAIN))
                : Theme.THIN);
        g.setColor(tint(STEER, Theme.FAINT));

        Path2D arc = new Path2D.Double();
        for (double a = -65; a <= 35; a += 3) {
            double rad = Math.toRadians(a);
            double px = cx + r * Math.cos(rad);
            double py = cy - r * Math.sin(rad);
            if (a == -65) arc.moveTo(px, py); else arc.lineTo(px, py);
        }
        g.draw(arc);

        double end = Math.toRadians(35);
        arrowHead(g, cx + r * Math.cos(end), cy - r * Math.sin(end), -Math.sin(end), -Math.cos(end));

        double anchor = Math.toRadians(-25);
        label(g, STEER, String.format(Locale.US, "steer %.1f", Parameters.STEER_GAIN),
                width - 6, cy + 4, Anchor.RIGHT,
                cx + r * Math.cos(anchor), cy - r * Math.sin(anchor));
    }

    private void drawBody(Graphics2D g, double bodyR, double sensorY, double half, double sensorR) {
        g.setColor(Theme.BODY);
        g.setStroke(Theme.BOLD);
        g.draw(new Line2D.Double(cx - half, sensorY, cx, cy));
        g.draw(new Line2D.Double(cx + half, sensorY, cx, cy));
        g.fill(new Ellipse2D.Double(cx - bodyR, cy - bodyR, 2 * bodyR, 2 * bodyR));

        g.setColor(Theme.SENSOR);
        g.fill(new Ellipse2D.Double(cx - half - sensorR, sensorY - sensorR, 2 * sensorR, 2 * sensorR));
        g.fill(new Ellipse2D.Double(cx + half - sensorR, sensorY - sensorR, 2 * sensorR, 2 * sensorR));
    }

    /** The bar between the two sensors: how far apart they sit. */
    private void drawSpacingBar(Graphics2D g, double sensorY, double half, double sensorR) {
        double y = sensorY - sensorR - 8;

        g.setStroke(on(SPACING) ? Theme.BOLD : Theme.THIN);
        g.setColor(tint(SPACING, Theme.FAINT));
        g.draw(new Line2D.Double(cx - half, y, cx + half, y));
        arrowHead(g, cx - half, y, -1, 0);
        arrowHead(g, cx + half, y, 1, 0);

        label(g, SPACING, String.format(Locale.US, "spacing %.0f px", Parameters.SENSOR_SPACING),
                6, cy + 4, Anchor.LEFT, cx - half, y);
    }

    /**
     * How long it stands still once the circuit fires.
     *
     * The chain of threshold devices used to be sketched here too. It is not any more: the
     * circuit editor under the world draws the real thing, live, and a second cartoon of it
     * that could disagree with it is worse than no cartoon at all.
     */
    private void drawPause(Graphics2D g) {
        text(g, PAUSE, String.format(Locale.US, "pause %.1f s", Parameters.PAUSE_SECONDS),
                (float) width - 6, (float) height - 10, Anchor.RIGHT);
    }

    // --------------------------------------------------------- drawing helpers

    private enum Anchor { LEFT, RIGHT, CENTER }

    /** A label, plus a dotted leader line from the label to the part it names. */
    private void label(Graphics2D g, String key, String caption,
                       double lx, double ly, Anchor anchor, double targetX, double targetY) {
        g.setStroke(Theme.LEADER);
        g.setColor(on(key) ? Theme.ACCENT : Theme.FAINT);
        double from = anchor == Anchor.LEFT
                ? lx + textWidth(g, caption) + 4
                : lx - textWidth(g, caption) - 4;
        g.draw(new Line2D.Double(from, ly - 4, targetX, targetY));

        text(g, key, caption, (float) lx, (float) ly, anchor);
    }

    private void text(Graphics2D g, String key, String caption, float x, float y, Anchor anchor) {
        g.setFont(Theme.SMALL);
        g.setColor(on(key) ? Theme.ACCENT : Theme.MUTED);
        float w = textWidth(g, caption);
        float drawX = anchor == Anchor.LEFT ? x : anchor == Anchor.RIGHT ? x - w : x - w / 2;
        g.drawString(caption, drawX, y);
    }

    private float textWidth(Graphics2D g, String s) {
        g.setFont(Theme.SMALL);
        return g.getFontMetrics().stringWidth(s);
    }

    /** A small filled triangle at (x, y) pointing along the unit vector (dx, dy). */
    private void arrowHead(Graphics2D g, double x, double y, double dx, double dy) {
        double size = 5.5;
        double px = -dy, py = dx;  // perpendicular
        Path2D head = new Path2D.Double();
        head.moveTo(x, y);
        head.lineTo(x - dx * size + px * size * 0.5, y - dy * size + py * size * 0.5);
        head.lineTo(x - dx * size - px * size * 0.5, y - dy * size - py * size * 0.5);
        head.closePath();
        g.fill(head);
    }
}
