import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Stroke;
import java.util.Arrays;
import java.util.List;

/**
 * The design tokens: every colour, font and stroke the interface uses, and nothing else.
 *
 * Swing's stock look is grey boxes with hard corners, and it makes a careful piece of work look
 * careless. Nothing here changes what the vehicle does - but a control you can read is a control
 * you can think with, and this is where that is decided. The widgets built out of these live in
 * Ui.java.
 */
public class Theme {

    // ---------------------------------------------------------------- surfaces
    public static final Color BG = new Color(0xF4, 0xF6, 0xF9);   // the page behind everything
    public static final Color CARD = Color.WHITE;                  // panels that sit on it
    public static final Color LINE = new Color(0xE3, 0xE7, 0xED);  // hairlines between them
    public static final Color WELL = new Color(0xFA, 0xFB, 0xFC);  // recessed areas (the board)

    // ---------------------------------------------------------------- text
    public static final Color TEXT = new Color(0x1B, 0x21, 0x2C);  // body
    public static final Color MUTED = new Color(0x77, 0x80, 0x92); // captions, secondary
    public static final Color FAINT = new Color(0xC3, 0xC9, 0xD3); // idle wires, disabled

    // ---------------------------------------------------------------- accents
    public static final Color ACCENT = new Color(0x2B, 0x63, 0xE0);      // selection, primary
    public static final Color ACCENT_DARK = new Color(0x1E, 0x4E, 0xC0); // pressed
    public static final Color ACCENT_BG = new Color(0xE8, 0xEF, 0xFD);   // selected halo

    // A device that has latched on, and the brief flash the instant it does.
    public static final Color ACTIVE = new Color(0x1F, 0xA8, 0x5C);
    public static final Color ACTIVE_BG = new Color(0xE6, 0xF7, 0xEC);
    public static final Color FLASH = new Color(0x12, 0x7A, 0x42);
    public static final Color FLASH_BG = new Color(0xB6, 0xEF, 0xCB);

    // Inhibitory wires: the ones that force a device off.
    public static final Color INHIBIT = new Color(0xD3, 0x3B, 0x3B);
    public static final Color INHIBIT_FAINT = new Color(0xF0, 0xC7, 0xC7);

    // The vehicle itself
    public static final Color BODY = new Color(0x3C, 0x5A, 0xDC);
    public static final Color SENSOR = new Color(0x2E, 0xB8, 0x6B);

    // ---------------------------------------------------------------- controls
    public static final Color BUTTON = Color.WHITE;
    public static final Color BUTTON_HOVER = new Color(0xF1, 0xF4, 0xF9);
    public static final Color BUTTON_PRESS = new Color(0xE4, 0xE9, 0xF1);
    public static final Color BUTTON_LINE = new Color(0xD3, 0xD9, 0xE2);

    // ---------------------------------------------------------------- type
    /**
     * Segoe UI on Windows, and whatever the machine has otherwise. Asking for a font that is not
     * installed silently hands back a default that is usually Dialog - so the list is checked
     * rather than guessed, and the interface looks deliberate on every machine instead of only
     * on this one.
     */
    private static final String FACE = pickFace("Segoe UI", "Inter", "Helvetica Neue", "SansSerif");
    private static final String MONO = pickFace("Consolas", "Menlo", "DejaVu Sans Mono", "Monospaced");

    public static final Font TITLE = new Font(FACE, Font.BOLD, 12);
    public static final Font LABEL = new Font(FACE, Font.PLAIN, 12);
    public static final Font BUTTON_FONT = new Font(FACE, Font.PLAIN, 12);
    public static final Font SMALL = new Font(FACE, Font.PLAIN, 11);
    public static final Font VALUE = new Font(MONO, Font.BOLD, 12);
    public static final Font DEVICE = new Font(FACE, Font.BOLD, 13);

    private static String pickFace(String... wanted) {
        List<String> installed = Arrays.asList(GraphicsEnvironment
                .getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String face : wanted) {
            if (installed.contains(face)) {
                return face;
            }
        }
        return wanted[wanted.length - 1];
    }

    // ---------------------------------------------------------------- strokes
    public static final Stroke THIN = new BasicStroke(1.2f,
            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    public static final Stroke BOLD = new BasicStroke(2.4f,
            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    public static final Stroke DASHED = new BasicStroke(
            1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] { 4f, 4f }, 0f);
    public static final Stroke LEADER = new BasicStroke(
            1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] { 2f, 3f }, 0f);
}
