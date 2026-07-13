import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Locale;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;

/**
 * BRAITENBERG VEHICLE 5 - "Logic"
 *
 * A vehicle drives among four light sources. It brakes and veers away whenever it passes one.
 * A network of threshold devices counts the sources it has visited; when that network fires,
 * the vehicle stops for a moment, forgets, and starts over.
 *
 * Run it with:   run.bat          (or: javac -d out *.java  &&  java -cp out Vehicle5)
 *
 * Do NOT use "java Vehicle5.java". That single-file launcher compiles only this one file and
 * loads the other classes with a different class loader, which fails with an IllegalAccessError.
 * The project is more than one file.
 *
 * THE SIMULATION - read these to understand Vehicle 5:
 *
 *     Parameters.java       every number in the project, with its formula and examples
 *     ThresholdDevice.java  the one building block: fires when its inputs reach a threshold
 *     Circuit.java          threshold devices wired into a network -> the vehicle's MEMORY.
 *                           The rules that step it are all in here, and nothing else.
 *     Brain.java            the peak detector that turns light into pulses, feeding the circuit
 *     Vehicle.java          the body: sensors -> brain -> motors -> movement
 *     Source.java           a light source
 *     Vehicle5.java         this file: the window, the loop, the numbers on screen
 *
 * THE INTERFACE - no logic in here, only knobs and paint:
 *
 *     CircuitEditor.java    the board under the world: draws the brain, and rewires it
 *     ControlPanel.java     the sliders on the right; they write straight into Parameters
 *     AnatomyView.java      the drawing that shows WHERE each parameter acts
 *     SpeedControl.java     the 1x/2x/4x/8x buttons and the fullscreen toggle
 *     Theme.java            colours and fonts
 *     Draw.java             a helper for drawing circles
 */
public class Vehicle5 extends JPanel implements ActionListener {

    // The world. Four sources, one for each device in the default counting circuit.
    Source[] sources = {
        new Source(300, 300, new Color(255, 80, 80)),
        new Source(120, 180, new Color(255, 190, 60)),
        new Source(480, 250, new Color(90, 210, 120)),
        new Source(170, 470, new Color(120, 160, 255)),
    };

    /**
     * The brain's wiring. The editor holds on to THIS object and edits it in place, so the
     * vehicle is always running whatever the board is showing.
     */
    final Circuit circuit = Circuit.counter(Parameters.CHAIN_LENGTH);

    Vehicle vehicle = new Vehicle(300, 500, Math.toRadians(-35), circuit);

    final SpeedControl speedControl = new SpeedControl();
    final ControlPanel controlPanel = new ControlPanel();

    /** Adding or deleting a device changes the shape of the network, so the brain restarts. */
    final CircuitEditor editor = new CircuitEditor(vehicle, () -> vehicle.rebuildBrain());

    // Fullscreen (F11, Escape, or the button) remembers the windowed bounds to come back to.
    private boolean fullscreen;
    private Rectangle windowedBounds;

    public static void main(String[] args) {
        Vehicle5 canvas = new Vehicle5();

        // The canvas keeps its exact 600x600 size - the vehicle wraps around at those edges - so
        // it is centred rather than stretched.
        JPanel world = new JPanel(new GridBagLayout());
        world.setBackground(Theme.BG);
        world.add(canvas);

        // The editor runs the FULL width of the window, under the world and the sliders both.
        // Tucking it under the 600 px world would leave its toolbar fighting for room while an
        // empty strip sat beside it - and a circuit wants width more than anything else.
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(canvas.editor, BorderLayout.CENTER);
        bottom.add(canvas.speedControl, BorderLayout.SOUTH);

        JFrame frame = new JFrame("Braitenberg Vehicle 5");
        frame.setLayout(new BorderLayout());
        frame.add(world, BorderLayout.CENTER);
        frame.add(canvas.controlPanel, BorderLayout.EAST);
        frame.add(bottom, BorderLayout.SOUTH);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setMinimumSize(frame.getSize());
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        canvas.speedControl.onFullscreen(() -> canvas.toggleFullscreen(frame));
        canvas.bindFullscreenKeys(frame);
    }

    Vehicle5() {
        setPreferredSize(new Dimension(Parameters.SCREEN_WIDTH, Parameters.SCREEN_HEIGHT));
        setBackground(Color.WHITE);
        setAlignmentX(Component.CENTER_ALIGNMENT);

        // This timer is the "while running:" loop: it fires FPS times per second.
        new Timer(1000 / Parameters.FPS, this).start();
    }

    /** One frame: advance the simulation, then redraw. */
    @Override
    public void actionPerformed(ActionEvent event) {
        for (int step = 0; step < speedControl.stepsPerFrame(); step++) {
            vehicle.update(sources);
        }
        repaint();
        editor.tick();
    }

    // ------------------------------------------------------------------ fullscreen

    /** F11 toggles fullscreen from anywhere in the window; Escape only leaves it. */
    private void bindFullscreenKeys(JFrame frame) {
        JComponent root = frame.getRootPane();

        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("F11"), "toggleFullscreen");
        root.getActionMap().put("toggleFullscreen", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) {
                toggleFullscreen(frame);
            }
        });

        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("ESCAPE"), "exitFullscreen");
        root.getActionMap().put("exitFullscreen", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) {
                if (fullscreen) {
                    toggleFullscreen(frame);
                }
            }
        });
    }

    /**
     * Real OS-level fullscreen (undecorated + GraphicsDevice), not just a maximised window -
     * this hides the title bar and the taskbar. Swing will only let setUndecorated() through
     * on a frame that is not displayable, hence the dispose()/setVisible() pair.
     */
    void toggleFullscreen(JFrame frame) {
        GraphicsDevice device = frame.getGraphicsConfiguration().getDevice();

        frame.dispose();
        if (!fullscreen) {
            windowedBounds = frame.getBounds();
            frame.setUndecorated(true);
            device.setFullScreenWindow(frame);
        } else {
            device.setFullScreenWindow(null);
            frame.setUndecorated(false);
            frame.setBounds(windowedBounds);
        }
        frame.setVisible(true);

        fullscreen = !fullscreen;
        speedControl.setFullscreen(fullscreen);
    }

    // ------------------------------------------------------------------ painting

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (Source source : sources) {
            source.draw(g);
        }
        vehicle.draw(g);
        drawReadout(g);
    }

    /** The live numbers in the corner: what it senses, what it does, what it remembers. */
    void drawReadout(Graphics2D g) {
        StringBuilder devices = new StringBuilder();
        for (int i = 0; i < vehicle.brain.size(); i++) {
            devices.append(vehicle.brain.isActive(i) ? "[ON ] " : "[OFF] ");
        }

        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        g.drawString(String.format(Locale.US, "sensors  left=%.2f  right=%.2f  (detect above %.2f)",
                vehicle.readingLeft, vehicle.readingRight, Parameters.detectLevel()), 10, 20);
        g.drawString(String.format(Locale.US, "motors   left=%.2f  right=%.2f",
                vehicle.leftMotorSpeed, vehicle.rightMotorSpeed), 10, 40);
        g.drawString("brain    " + devices, 10, 60);
        g.drawString(String.format(Locale.US, "%-7s  devices on = %d / %d",
                vehicle.action, vehicle.brain.count(), vehicle.brain.size()), 10, 80);
    }
}
