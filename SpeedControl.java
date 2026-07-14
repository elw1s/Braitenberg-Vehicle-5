import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JPanel;

/**
 * The strip along the bottom: how fast to run, and the fullscreen toggle.
 *
 * Nothing here belongs to Braitenberg - it only makes the demo watchable. The speed buttons do
 * NOT change any physics: the simulation still advances in the same small steps, we just run
 * several of them per drawn frame. So 8x means "8 update() calls per repaint", not "8 times the
 * speed inside update()", and the vehicle behaves exactly as it does at 1x.
 *
 * Worth knowing while you watch it: at 1x, and at the default detection distance, the vehicle
 * takes about twenty seconds to find its first source. Press 4x.
 */
public class SpeedControl extends JPanel {

    private static final int[] FACTORS = { 1, 2, 4, 8 };

    private final List<Ui.Segment> segments = new ArrayList<>();
    private final JButton playPause;
    private final JButton fullscreen;

    private Runnable onFullscreen = () -> { };
    private Runnable onPlayPause = () -> { };

    private int stepsPerFrame = 1;

    public SpeedControl() {
        setLayout(new BorderLayout());
        setBackground(Theme.BG);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.LINE),
                BorderFactory.createEmptyBorder(8, 14, 8, 14)));

        JPanel speed = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        speed.setOpaque(false);

        playPause = Ui.primary("Pause", () -> onPlayPause.run());
        speed.add(playPause);
        speed.add(Box.createHorizontalStrut(14));
        speed.add(Ui.caption("Speed"));

        for (int factor : FACTORS) {
            final int steps = factor;
            Ui.Segment segment = Ui.segment(factor + "x", () -> choose(steps));
            segments.add(segment);
            speed.add(segment);
        }
        choose(1);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);
        fullscreen = Ui.button("Fullscreen", () -> onFullscreen.run());
        right.add(fullscreen);

        add(speed, BorderLayout.WEST);
        add(right, BorderLayout.EAST);

        setFullscreen(false);
    }

    private void choose(int steps) {
        stepsPerFrame = steps;
        for (int i = 0; i < segments.size(); i++) {
            segments.get(i).setChosen(FACTORS[i] == steps);
        }
    }

    /** How many simulation steps the main loop should run before drawing. */
    public int stepsPerFrame() {
        return stepsPerFrame;
    }

    /** What the fullscreen button does. Vehicle5 owns the window, so it owns the toggling. */
    public void onFullscreen(Runnable action) {
        this.onFullscreen = action;
    }

    /** What the Play/Pause button does. Vehicle5 owns the clock, so it owns the running. */
    public void onPlayPause(Runnable action) {
        this.onPlayPause = action;
    }

    /**
     * Keeps the button honest however the world got stopped - the button, or an edit in the
     * circuit editor. It says what pressing it will DO, not what the state currently is.
     */
    public void setRunning(boolean running) {
        playPause.setText(running ? "Pause" : "Play");
    }

    /** Keeps the button honest whichever way fullscreen was toggled - the button, or F11. */
    public void setFullscreen(boolean on) {
        fullscreen.setText(on ? "Exit fullscreen  (Esc)" : "Fullscreen  (F11)");
    }
}
