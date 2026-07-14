import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
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

    /** The colours new sources are given, in turn. Cosmetic - every source shines the same. */
    private static final Color[] PALETTE = {
        new Color(255, 80, 80),
        new Color(255, 190, 60),
        new Color(90, 210, 120),
        new Color(120, 160, 255),
        new Color(190, 120, 220),
        new Color(80, 200, 210),
    };

    /**
     * The world. Drag a source to move it, right-click to add or delete one - see the mouse
     * handling below. Four to start with, one for each device in the default circuit.
     */
    final List<Source> sources = new ArrayList<>(List.of(
        new Source(300, 300, PALETTE[0]),
        new Source(120, 180, PALETTE[1]),
        new Source(480, 250, PALETTE[2]),
        new Source(170, 470, PALETTE[3])));

    /** The source being dragged, or null. */
    private Source dragging;

    /**
     * The brain's wiring. The editor holds on to THIS object and edits it in place, so the
     * vehicle is always running whatever the board is showing.
     *
     * A RING, not the plain counter, because nothing outside the circuit clears it any more. The
     * ring clears itself, so it pauses at every fourth source, over and over. Load the plain
     * Counter from the menu to see the other half of the lesson: it fills up, fires once, and can
     * never fire again - and that is not a bug, it is what a chain with no feedback in it is.
     */
    final Circuit circuit = Circuit.ringCounter(Parameters.CHAIN_LENGTH);

    Vehicle vehicle = new Vehicle(300, 500, Math.toRadians(-35), circuit);

    final SpeedControl speedControl = new SpeedControl();
    final ControlPanel controlPanel = new ControlPanel();

    /**
     * The editor gets two lines back to the simulation: rebuild the brain when the shape of the
     * network changes, and HOLD THE WORLD STILL the moment anyone touches the wiring. Rewiring a
     * brain while its owner is driving around is no way to see what you have done - and the
     * vehicle would be somewhere else by the time you looked up.
     */
    final CircuitEditor editor = new CircuitEditor(vehicle, vehicle::rebuildBrain, this::pause);

    /** Whether the world is moving. Everything still draws while it is not. */
    private boolean running = true;

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

        // Swing hands the focus to the first thing that will take it, which is the editor's name
        // box - so every key you press lands in it before you have asked to save anything. Park
        // the focus on the world instead: the name box gets it when you click it, and not before.
        canvas.requestFocusInWindow();

        canvas.speedControl.onFullscreen(() -> canvas.toggleFullscreen(frame));
        canvas.speedControl.onPlayPause(() -> canvas.setRunning(!canvas.isRunning()));
        canvas.bindFullscreenKeys(frame);
    }

    Vehicle5() {
        setPreferredSize(new Dimension(Parameters.SCREEN_WIDTH, Parameters.SCREEN_HEIGHT));
        setBackground(Color.WHITE);
        setAlignmentX(Component.CENTER_ALIGNMENT);
        setFocusable(true);   // so the focus has somewhere harmless to sit at startup

        installSourceMouse();

        // This timer is the "while running:" loop: it fires FPS times per second.
        new Timer(1000 / Parameters.FPS, this).start();
    }

    // ------------------------------------------------------------------ the world's sources

    /**
     * Drag a source to move it; right-click to add one or take one away.
     *
     * Deliberately NOT paused while you do it. Rewiring a brain mid-drive is useless, so the
     * editor stops the world - but dragging a light around while the vehicle is chasing it is the
     * single most convincing thing this simulation does, and freezing that would be a shame. It
     * works either way: while paused, the readout still updates, so you can place a source and see
     * what the sensors make of it before letting go.
     */
    private void installSourceMouse() {
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                Source under = sourceUnder(event.getX(), event.getY());

                if (SwingUtilities.isRightMouseButton(event)) {
                    sourceMenu(under, event.getX(), event.getY()).show(Vehicle5.this,
                            event.getX(), event.getY());
                    return;
                }
                dragging = under;
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (dragging == null) {
                    return;
                }
                // Kept inside the world, or a source could be dropped where nothing can reach it.
                dragging.x = clamp(event.getX(), 0, Parameters.SCREEN_WIDTH);
                dragging.y = clamp(event.getY(), 0, Parameters.SCREEN_HEIGHT);
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                dragging = null;
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                setCursor(Cursor.getPredefinedCursor(
                        sourceUnder(event.getX(), event.getY()) != null
                                ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    private JPopupMenu sourceMenu(Source under, int x, int y) {
        JPopupMenu menu = new JPopupMenu();

        if (under != null) {
            JMenuItem delete = new JMenuItem("Delete this source");
            delete.setFont(Theme.LABEL);
            delete.addActionListener(event -> {
                sources.remove(under);
                repaint();
            });
            menu.add(delete);
        } else {
            JMenuItem add = new JMenuItem("Add a source here");
            add.setFont(Theme.LABEL);
            add.addActionListener(event -> {
                sources.add(new Source(x, y, PALETTE[sources.size() % PALETTE.length]));
                repaint();
            });
            menu.add(add);
        }
        return menu;
    }

    private Source sourceUnder(int x, int y) {
        // Backwards: the last one drawn is the one on top, so it is the one you meant.
        for (int i = sources.size() - 1; i >= 0; i--) {
            if (sources.get(i).contains(x, y)) {
                return sources.get(i);
            }
        }
        return null;
    }

    private double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }

    /** One frame: advance the simulation if it is running, then redraw either way. */
    @Override
    public void actionPerformed(ActionEvent event) {
        if (running) {
            for (int step = 0; step < speedControl.stepsPerFrame(); step++) {
                vehicle.update(sources);
            }
        }
        repaint();
        editor.tick();
    }

    // ------------------------------------------------------------------ running and not

    /** Hold the world still. Called by the editor on every edit, and by the Play/Pause button. */
    void pause() {
        setRunning(false);
    }

    void setRunning(boolean run) {
        running = run;
        speedControl.setRunning(run);
        repaint();
    }

    boolean isRunning() {
        return running;
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

        if (!running) {
            drawPausedOverlay(g);
        }
    }

    /** Unmissable, because a still picture of a simulation looks exactly like a broken one. */
    void drawPausedOverlay(Graphics2D g) {
        int w = getWidth(), h = getHeight();

        g.setColor(new Color(255, 255, 255, 190));
        g.fillRect(0, 0, w, h);

        String title = "PAUSED";
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 64));
        int titleWidth = g.getFontMetrics().stringWidth(title);
        g.setColor(new Color(0x1B, 0x21, 0x2C));
        g.drawString(title, (w - titleWidth) / 2, h / 2);

        String hint = "Build the circuit below, then press Play";
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        int hintWidth = g.getFontMetrics().stringWidth(hint);
        g.setColor(new Color(0x77, 0x80, 0x92));
        g.drawString(hint, (w - hintWidth) / 2, h / 2 + 30);
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

        // Nobody discovers a right-click menu they were not told about.
        g.setFont(Theme.SMALL);
        g.setColor(Theme.MUTED);
        g.drawString("drag a light to move it   -   right-click to add or delete one",
                10, getHeight() - 12);
    }
}
