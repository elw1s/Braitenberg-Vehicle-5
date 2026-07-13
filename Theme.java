import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Stroke;

/** One place for the colours, fonts and strokes of the control panel. UI only. */
public class Theme {

    public static final Color BG = new Color(0xF5, 0xF6, 0xF8);
    public static final Color CARD = Color.WHITE;
    public static final Color LINE = new Color(0xE2, 0xE5, 0xEA);
    public static final Color TEXT = new Color(0x1E, 0x23, 0x2E);
    public static final Color MUTED = new Color(0x8B, 0x92, 0xA1);
    public static final Color FAINT = new Color(0xC7, 0xCC, 0xD4);
    public static final Color ACCENT = new Color(0x2F, 0x6F, 0xED);
    public static final Color ACCENT_BG = new Color(0xE8, 0xEF, 0xFD);

    public static final Color BODY = new Color(0x3C, 0x5A, 0xDC);
    public static final Color SENSOR = new Color(0x2E, 0xB8, 0x6B);

    public static final Font TITLE = new Font("SansSerif", Font.BOLD, 13);
    public static final Font LABEL = new Font("SansSerif", Font.PLAIN, 12);
    public static final Font VALUE = new Font("Monospaced", Font.BOLD, 12);
    public static final Font SMALL = new Font("SansSerif", Font.PLAIN, 10);

    public static final Stroke THIN = new BasicStroke(1f);
    public static final Stroke BOLD = new BasicStroke(2.2f);
    public static final Stroke DASHED = new BasicStroke(
            1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] { 4f, 4f }, 0f);
    public static final Stroke LEADER = new BasicStroke(
            1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] { 2f, 3f }, 0f);
}
