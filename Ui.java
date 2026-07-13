import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;

/**
 * The widgets, drawn by hand: flat, rounded, and the same everywhere.
 *
 * Swing's stock button is a grey bevelled rectangle from 1998, and there is no setting that
 * makes it not be that - you have to paint it yourself. So these do: one rounded rectangle, a
 * hairline, and a fill that answers the mouse. Three states (idle, hover, pressed) and two
 * kinds (quiet and primary) is the whole system.
 *
 * Pure interface. Nothing in here knows what a threshold device is.
 */
public class Ui {

    private static final int RADIUS = 7;

    /**
     * The name matters. Call this HEIGHT and the buttons come out two pixels tall.
     *
     * Every Swing component is an ImageObserver, and ImageObserver declares its own HEIGHT = 2.
     * An INHERITED member beats an enclosing class's member in Java's scoping rules, so inside
     * Flat - a JButton subclass nested in here - a bare "HEIGHT" quietly resolves to
     * ImageObserver's 2 rather than to this constant. It compiles, and it is wrong.
     */
    private static final int CONTROL_HEIGHT = 28;

    /** A quiet button: white, hairline border. The default. */
    public static JButton button(String text, Runnable action) {
        return new Flat(text, false, action);
    }

    /** The one button on a bar that starts something. Accent-filled. */
    public static JButton primary(String text, Runnable action) {
        return new Flat(text, true, action);
    }

    /**
     * One of a set of mutually exclusive choices - the speed buttons. Only one is lit, so a
     * glance tells you where you are; four identical grey boxes do not.
     */
    public static Segment segment(String text, Runnable action) {
        return new Segment(text, action);
    }

    public static class Segment extends Flat {
        Segment(String text, Runnable action) {
            super(text, false, action);
            setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        }

        public void setChosen(boolean chosen) {
            this.chosen = chosen;
            setForeground(chosen ? Color.WHITE : Theme.TEXT);
            repaint();
        }
    }

    /**
     * A button that drops a menu, instead of a JComboBox. A combo box cannot be styled to match
     * anything without replacing its whole UI delegate, and "pick one thing, once" was never a
     * combo box's job anyway - it is a menu.
     */
    public static JButton menu(String text, Consumer<JPopupMenu> fill) {
        Flat button = new Flat(text, false, null);
        button.chevron = true;   // drawn, not a glyph: the source stays ASCII on every machine
        button.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 26));
        button.addActionListener(event -> {
            JPopupMenu popup = new JPopupMenu();
            fill.accept(popup);
            popup.show(button, 0, button.getHeight() + 2);
        });
        return button;
    }

    /** A text box with a greyed-out hint in it while it is empty. */
    public static JTextField field(String placeholder, int width, Runnable onEnter) {
        JTextField field = new JTextField() {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = pretty(graphics);

                g.setColor(Theme.CARD);
                g.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), RADIUS, RADIUS));
                g.setColor(isFocusOwner() ? Theme.ACCENT : Theme.BUTTON_LINE);
                g.draw(new RoundRectangle2D.Double(
                        0.5, 0.5, getWidth() - 1.0, getHeight() - 1.0, RADIUS, RADIUS));

                super.paintComponent(graphics);

                // Shown whenever the box is empty, focused or not. Hiding it on focus is the
                // usual trick, but this box takes the focus the moment the window opens, so the
                // hint would never be seen at all - an empty white rectangle that explains
                // nothing is worse than a hint sitting behind the caret.
                if (getText().isEmpty()) {
                    g.setColor(Theme.FAINT);
                    g.setFont(Theme.LABEL);
                    g.drawString(placeholder, 9, getHeight() / 2 + 4);
                }
            }
        };
        field.setOpaque(false);
        field.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        field.setFont(Theme.LABEL);
        field.setForeground(Theme.TEXT);
        field.setCaretColor(Theme.ACCENT);
        field.setPreferredSize(new Dimension(width, CONTROL_HEIGHT));
        field.addActionListener(event -> onEnter.run());
        return field;
    }

    /** A section heading: small, upper-case, quiet. */
    public static JLabel heading(String text) {
        JLabel label = new JLabel(text.toUpperCase());
        label.setFont(Theme.TITLE);
        label.setForeground(Theme.MUTED);
        return label;
    }

    public static JLabel caption(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.LABEL);
        label.setForeground(Theme.MUTED);
        return label;
    }

    static Graphics2D pretty(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    // ------------------------------------------------------------------ the button itself

    static class Flat extends JButton {

        private final boolean primary;
        private boolean hover;
        boolean chevron;
        boolean chosen;

        Flat(String text, boolean primary, Runnable action) {
            super(text);
            this.primary = primary;

            setFont(Theme.BUTTON_FONT);
            setFocusable(false);
            setContentAreaFilled(false);   // we paint the background ourselves
            setBorderPainted(false);
            setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 14));
            setForeground(primary ? Color.WHITE : Theme.TEXT);
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));

            if (action != null) {
                addActionListener(event -> action.run());
            }

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        // All three sizes, not just the preferred one: BoxLayout reads the maximum, FlowLayout
        // reads the preferred, and leaving them disagreeing is how a button ends up the right
        // height in one bar and squashed in the next.
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(super.getPreferredSize().width, CONTROL_HEIGHT);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, CONTROL_HEIGHT);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = pretty(graphics);
            boolean pressed = getModel().isArmed() && getModel().isPressed();

            Color fill = primary || chosen
                    ? (pressed ? Theme.ACCENT_DARK : Theme.ACCENT)
                    : (pressed ? Theme.BUTTON_PRESS : hover ? Theme.BUTTON_HOVER : Theme.BUTTON);

            RoundRectangle2D shape = new RoundRectangle2D.Double(
                    0, 0, getWidth(), getHeight(), RADIUS, RADIUS);
            g.setColor(fill);
            g.fill(shape);

            if (!primary && !chosen) {
                g.setColor(Theme.BUTTON_LINE);
                g.draw(new RoundRectangle2D.Double(
                        0.5, 0.5, getWidth() - 1.0, getHeight() - 1.0, RADIUS, RADIUS));
            }

            super.paintComponent(graphics);   // the label, in the colour we set

            if (chevron) {
                int x = getWidth() - 16;
                int y = getHeight() / 2 - 1;
                Path2D arrow = new Path2D.Double();
                arrow.moveTo(x - 4, y - 1);
                arrow.lineTo(x + 4, y - 1);
                arrow.lineTo(x, y + 4);
                arrow.closePath();
                g.setColor(Theme.MUTED);
                g.fill(arrow);
            }
        }
    }

    /** Makes a JComponent transparent and unpadded, for when it is only a container. */
    public static <T extends JComponent> T bare(T component) {
        component.setOpaque(false);
        component.setBorder(null);
        return component;
    }
}
