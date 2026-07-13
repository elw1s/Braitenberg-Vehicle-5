import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;

/**
 * The panel on the right of the window: one slider per tunable parameter.
 *
 * Everything is live - the vehicle keeps driving while you drag a slider, and the
 * change takes effect on the very next frame. Touch a slider and the part of the
 * drawing above it lights up, and the box underneath spells out the formula the
 * number goes into, with today's values filled in.
 *
 * UI only - no physics happens here. It just writes to Parameters.
 */
public class ControlPanel extends JPanel {

    private final AnatomyView anatomy = new AnatomyView();
    private final JLabel explanation = new JLabel();

    /** One entry per slider: pulls the slider back to whatever Parameters now says. */
    private final List<Runnable> refreshers = new ArrayList<>();

    public ControlPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Theme.BG);
        setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        add(title("VEHICLE PARAMETERS"));
        add(Box.createVerticalStrut(8));
        add(card(anatomy));
        add(Box.createVerticalStrut(8));
        add(card(explanationBox()));
        add(Box.createVerticalStrut(10));

        add(slider(AnatomyView.DETECT, "Detection distance", "%.0f px",
                20, 120, 1.0,
                () -> Parameters.DETECT_DISTANCE,
                value -> Parameters.DETECT_DISTANCE = value));

        add(slider(AnatomyView.CRUISE, "Cruise speed", "%.1f px/frame",
                5, 50, 0.1,
                () -> Parameters.CRUISE_SPEED,
                value -> Parameters.CRUISE_SPEED = value));

        add(slider(AnatomyView.STEER, "Steer gain", "%.1f",
                0, 30, 0.1,
                () -> Parameters.STEER_GAIN,
                value -> Parameters.STEER_GAIN = value));

        add(slider(AnatomyView.SPACING, "Sensor spacing", "%.0f px",
                10, 160, 1.0,
                () -> Parameters.SENSOR_SPACING,
                value -> Parameters.SENSOR_SPACING = value));

        add(slider(AnatomyView.PAUSE, "Pause when done", "%.1f s",
                5, 40, 0.1,
                () -> Parameters.PAUSE_SECONDS,
                value -> Parameters.PAUSE_SECONDS = value));

        // The number of threshold devices used to be a slider here. It is not a parameter any
        // more - the circuit editor under the world owns the wiring, and you add and delete
        // devices there. Two places to set the same thing is one place too many.

        add(Box.createVerticalGlue());
        add(resetButton());

        describe(null);
    }

    /** Fixed width, natural height: the window grows to fit the sliders, never clips them. */
    @Override
    public Dimension getPreferredSize() {
        return new Dimension(370, super.getPreferredSize().height);
    }

    // ------------------------------------------------------------------ pieces

    private JLabel title(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.TITLE);
        label.setForeground(Theme.MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    /** Wraps a component in a white rounded-ish card. */
    private JPanel card(Component content) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.CARD);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.LINE),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(content, BorderLayout.CENTER);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, content.getPreferredSize().height + 14));
        return panel;
    }

    private JLabel explanationBox() {
        explanation.setFont(Theme.LABEL);
        explanation.setForeground(Theme.TEXT);
        explanation.setVerticalAlignment(JLabel.TOP);
        explanation.setPreferredSize(new Dimension(300, 90));
        return explanation;
    }

    /**
     * One parameter: its name, its live value, and a slider.
     * The slider works in whole steps, so sliderValue * step = the real value.
     */
    private JPanel slider(String key, String name, String format,
                          int min, int max, double step,
                          DoubleSupplier getter, DoubleConsumer setter) {

        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(Theme.LABEL);
        nameLabel.setForeground(Theme.TEXT);

        JLabel valueLabel = new JLabel(String.format(Locale.US, format, getter.getAsDouble()));
        valueLabel.setFont(Theme.VALUE);
        valueLabel.setForeground(Theme.ACCENT);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(nameLabel, BorderLayout.WEST);
        header.add(valueLabel, BorderLayout.EAST);

        JSlider bar = new JSlider(min, max, (int) Math.round(getter.getAsDouble() / step));
        bar.setOpaque(false);
        bar.setFocusable(false);
        bar.addChangeListener(event -> {
            double value = bar.getValue() * step;
            setter.accept(value);
            valueLabel.setText(String.format(Locale.US, format, value));
            describe(key);
            anatomy.repaint();
        });

        // Lets "Reset to defaults" pull this slider back without rebuilding the panel.
        refreshers.add(() -> bar.setValue((int) Math.round(getter.getAsDouble() / step)));

        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        row.add(header, BorderLayout.NORTH);
        row.add(bar, BorderLayout.CENTER);

        // Pointing at a row is enough to see what it controls - no need to drag it.
        MouseAdapter hover = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                describe(key);
            }
        };
        row.addMouseListener(hover);
        bar.addMouseListener(hover);
        header.addMouseListener(hover);

        return row;
    }

    private JButton resetButton() {
        JButton button = Ui.button("Reset to defaults", () -> {
            // Only the vehicle's parameters. The circuit belongs to the editor, and wiping
            // someone's wiring because they wanted the cruise speed back would be a nasty
            // surprise.
            Parameters.resetToDefaults();
            for (Runnable refresher : refreshers) {
                refresher.run();  // each slider snaps back to its default
            }
            describe(null);
            anatomy.repaint();
        });
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        return button;
    }

    // ------------------------------------------------------------------ text

    /** Highlights the part of the drawing this parameter controls, and explains it. */
    private void describe(String key) {
        anatomy.highlight(key);
        explanation.setText(html(key));
    }

    private String html(String key) {
        if (key == null) {
            return wrap("<b>Point at a slider.</b> The picture above shows where that number "
                    + "acts on the vehicle, and this box shows the formula it goes into. "
                    + "Everything changes live - the vehicle never stops driving.");
        }
        switch (key) {
            case AnatomyView.DETECT:
                return wrap(String.format(Locale.US,
                        "<b>Detection distance.</b> How near a source must be to count at all. "
                        + "The brain only sees a reading, so the distance becomes a level:"
                        + "<br>&nbsp;&nbsp;level = %.0f / %.0f&sup2; = <b>%.2f</b>"
                        + "<br>The pulse fires at the <i>peak</i> of the reading - the closest "
                        + "approach - not when this level is crossed.",
                        Parameters.SENSOR_GAIN, Parameters.DETECT_DISTANCE, Parameters.detectLevel()));
            case AnatomyView.CRUISE:
                return wrap(String.format(Locale.US,
                        "<b>Cruise speed.</b> Top speed when no source is near. It brakes as the "
                        + "light grows and stops 22 px from a source."
                        + "<br>&nbsp;&nbsp;speed = %.1f &times; max(0, 1 &minus; reading)",
                        Parameters.CRUISE_SPEED));
            case AnatomyView.STEER:
                return wrap(String.format(Locale.US,
                        "<b>Steer gain.</b> How hard it veers <i>away</i> from the brighter sensor - "
                        + "that is why it grazes a source instead of parking on it."
                        + "<br>&nbsp;&nbsp;steer = %.1f &times; (right &minus; left)",
                        Parameters.STEER_GAIN));
            case AnatomyView.SPACING:
                return wrap(String.format(Locale.US,
                        "<b>Sensor spacing.</b> The only reason it can tell left from right: "
                        + "the steering signal is (right &minus; left). At %.0f px the two sensors "
                        + "read almost the same value and it can barely steer.",
                        Parameters.SENSOR_SPACING));
            case AnatomyView.PAUSE:
                return wrap(String.format(Locale.US,
                        "<b>Pause.</b> How long it stands still once the last threshold device has "
                        + "fired. Then the chain is cleared and it counts again."
                        + "<br>&nbsp;&nbsp;%.1f s &times; %d fps = <b>%d frames</b>",
                        Parameters.PAUSE_SECONDS, Parameters.FPS, Parameters.pauseFrames()));
            default:
                return "";
        }
    }

    /**
     * Swing's HTML ignores a CSS width on body and div, but it does respect the width
     * of a table cell - which is the only reliable way to make a JLabel wrap.
     */
    private String wrap(String body) {
        return "<html><table width='288' cellpadding='0'><tr><td>" + body + "</td></tr></table></html>";
    }
}
