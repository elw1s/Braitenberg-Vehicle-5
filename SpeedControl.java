import java.awt.Color;
import java.awt.FlowLayout;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

/**
 * The row of 1x / 2x / 4x / 8x buttons under the canvas.
 *
 * Nothing here belongs to Braitenberg - it only makes the demo watchable. It does
 * NOT change any physics: the simulation still advances in the same small steps,
 * we just run several of them per drawn frame. So 8x means "8 update() calls per
 * repaint", not "8 times the speed inside update()", and the vehicle behaves
 * exactly as it does at 1x.
 */
public class SpeedControl extends JPanel {

    private static final int[] FACTORS = { 1, 2, 4, 8 };

    private int stepsPerFrame = 1;

    public SpeedControl() {
        setLayout(new FlowLayout(FlowLayout.CENTER, 8, 6));
        setBackground(new Color(238, 238, 238));
        add(new JLabel("Speed:"));

        ButtonGroup group = new ButtonGroup();
        for (int factor : FACTORS) {
            JToggleButton button = new JToggleButton(factor + "x", factor == 1);
            button.setFocusable(false);
            button.addActionListener(event -> stepsPerFrame = factor);
            group.add(button);
            add(button);
        }
    }

    /** How many simulation steps the main loop should run before drawing. */
    public int stepsPerFrame() {
        return stepsPerFrame;
    }
}
