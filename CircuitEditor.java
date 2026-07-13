import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

/**
 * The circuit BOARD: draws the brain's network live, and lets you rewire it while the vehicle
 * drives. Everything in this file is interface. The network itself - what a wire means, what a
 * threshold does, how a frame is stepped - lives in Circuit.java, and this file never decides
 * any of it.
 *
 * HOW YOU USE IT
 *
 *   + Device        adds a threshold device.
 *   click a device  selects it. Click a second one and you pick the kind of wire to run between
 *                   them: excitatory or inhibitory.
 *   click a WIRE    selects the signal that wire is carrying - that is, its source. Click a
 *                   device next and the same signal BRANCHES to it as well. This is the junction
 *                   dot in Braitenberg's drawings: one output feeding several inputs. Click
 *                   another wire instead and the signal branches to wherever that wire goes.
 *   click the rail  selects the incoming pulse as the signal, the same way.
 *   empty space     cancels a half-drawn wire.
 *   drag a device   moves it.
 *   right-click     a device: change its threshold, make it the OUTPUT, or delete it.
 *                   a wire: flip it between excitatory and inhibitory, or delete it.
 *   Test            runs your circuit on a train of pulses and says which ones it fires on,
 *                   right there in the bar - no waiting for the vehicle to find that many
 *                   sources, and no window to dismiss.
 *   Save            writes the circuit to circuits/<name>.circuit, and it turns up in Load.
 *
 * WHAT THE DRAWING MEANS
 *
 *   green ring            the device is ON (it has latched)
 *   double ring, "out"    the OUTPUT device: when this switches on, the vehicle pauses
 *   grey wire, arrow      excitatory - it pushes the target towards firing
 *   red wire, bar end     inhibitory - while it is on, the target is forced OFF
 *   arc over the top      the wire runs BACKWARDS: feedback. It reads last frame's value,
 *                         which is why a loop cannot hang. See Circuit.
 */
public class CircuitEditor extends JPanel {

    private static final long FLASH_MS = 450;
    private static final double RADIUS = 20;
    private static final double GRAB = 24;      // how close a click counts as "on" a device
    private static final double WIRE_GRAB = 6;  // ... or "on" a wire

    private final Vehicle vehicle;
    private final Runnable onStructureChanged;
    private final Board board = new Board();

    private final JLabel status = new JLabel();
    private final JTextArea result = new JTextArea();
    private final JTextField saveName = Ui.field("circuit name", 130, this::save);

    /**
     * The half-drawn wire: the SIGNAL the next click will run a wire from. It is a device index,
     * or Circuit.INPUT for the pulse rail, or Circuit.NONE when nothing is selected.
     *
     * Clicking a wire puts that wire's SOURCE in here - not the wire, because a wire is not a
     * thing a signal can come out of. A wire is only ever "some source, feeding some target",
     * so branching off a wire is branching off its source.
     */
    private int signal = Circuit.NONE;

    private int dragging = Circuit.NONE;

    // Live-display bookkeeping: when each device last switched on, and when the last pulse
    // arrived, so both can be shown lit for FLASH_MS rather than for one invisible frame.
    private boolean[] wasActive = new boolean[0];
    private long[] activatedAt = new long[0];
    private long lastPulseAt;      // 0, not Long.MIN_VALUE: "now - MIN_VALUE" overflows negative
    private long seenPulseCount;

    public CircuitEditor(Vehicle vehicle, Runnable onStructureChanged) {
        this.vehicle = vehicle;
        this.onStructureChanged = onStructureChanged;

        setLayout(new BorderLayout());
        setBackground(Theme.CARD);
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.LINE));

        // The buttons across the top, and the test result on its own full-width line under them.
        // The result needs the whole width: sharing a row with the Save box would clip it, and a
        // clipped explanation is worse than none.
        JPanel bars = new JPanel(new BorderLayout());
        bars.setOpaque(false);
        bars.add(buttonBar(), BorderLayout.NORTH);
        bars.add(resultBox(), BorderLayout.CENTER);

        add(bars, BorderLayout.NORTH);
        add(board, BorderLayout.CENTER);

        refresh();
    }

    private Circuit circuit() {
        return vehicle.brain.circuit();
    }

    /** The circuit changed shape, so the brain's state array has to be rebuilt around it. */
    private void structureChanged() {
        circuit().name = "Custom";
        onStructureChanged.run();
        refresh();
    }

    /** A rewire that does not change the device count - no rebuild needed, it applies live. */
    private void wiringChanged() {
        circuit().name = "Custom";
        refresh();
    }

    private void refresh() {
        Circuit circuit = circuit();
        String text;

        if (signal != Circuit.NONE) {
            String from = signal == Circuit.INPUT ? "the pulse input" : "device " + signal;
            text = "Wiring from " + from + " - click a device or a wire. Empty space cancels.";
        } else if (circuit.size() == 0) {
            text = "Empty board. Press \"+ Device\", or load something.";
        } else if (!circuit.hasOutput()) {
            text = "No OUTPUT device - the vehicle can never pause. Right-click a device to set one.";
        } else {
            text = circuit.name + " - " + circuit.size() + " devices, "
                    + circuit.edges().size() + " wires";
        }

        status.setText(text);
        board.repaint();
    }

    // ------------------------------------------------------------------ the two bars

    /**
     * BorderLayout, not BoxLayout, and that is the whole trick to making this responsive: the
     * controls go EAST at their natural size and are never squeezed, and the summary goes CENTER
     * and gets whatever is left. Narrow the window and the summary gives way; the buttons stay
     * whole. A BoxLayout shares the pain out evenly instead, which is how "+ Device" ended up
     * sliced in half.
     */
    private JPanel buttonBar() {
        JPanel bar = new JPanel(new BorderLayout(14, 0));
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder(10, 14, 6, 14));

        status.setFont(Theme.LABEL);
        status.setForeground(Theme.MUTED);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        controls.setOpaque(false);
        controls.add(saveName);
        controls.add(Ui.button("Save", this::save));
        controls.add(Box.createHorizontalStrut(10));
        controls.add(Ui.menu("Load", this::fillLoadMenu));
        controls.add(Ui.button("Test", this::test));
        controls.add(Ui.primary("+ Device", this::addDevice));

        bar.add(Ui.heading("Brain circuit"), BorderLayout.WEST);
        bar.add(status, BorderLayout.CENTER);
        bar.add(controls, BorderLayout.EAST);
        return bar;
    }

    /**
     * One message line, under the controls: what Test found, what Save did, or how to finish the
     * wire you have half-drawn. It wraps, because in fullscreen this line is very wide and these
     * are sentences, not labels.
     */
    private JPanel resultBox() {
        result.setFont(Theme.LABEL);
        result.setForeground(Theme.MUTED);
        result.setEditable(false);
        result.setFocusable(false);
        result.setOpaque(false);
        result.setLineWrap(true);
        result.setWrapStyleWord(true);
        result.setRows(2);

        JPanel box = new JPanel(new BorderLayout());
        box.setOpaque(false);
        box.setBorder(BorderFactory.createEmptyBorder(0, 14, 8, 14));
        box.add(result, BorderLayout.CENTER);
        return box;
    }

    // ------------------------------------------------------------------ load and save

    /**
     * The presets from Circuit.java, then everything saved in the circuits folder. Built fresh
     * each time the menu opens, so a circuit you saved a moment ago is already in it.
     */
    private void fillLoadMenu(JPopupMenu menu) {
        offer(menu, "Counter", () -> Circuit.counter(Parameters.CHAIN_LENGTH));
        offer(menu, "Self-resetting counter",
                () -> Circuit.selfResettingCounter(Parameters.CHAIN_LENGTH));
        offer(menu, "Empty board", Circuit::new);

        List<String> saved = CircuitStore.list();
        if (saved.isEmpty()) {
            return;
        }
        menu.addSeparator();
        for (String name : saved) {
            offer(menu, name, () -> {
                try {
                    return CircuitStore.load(name);
                } catch (IOException failure) {
                    say("Could not read \"" + name + "\": " + failure.getMessage(), true);
                    return null;
                }
            });
        }
    }

    private void offer(JPopupMenu menu, String label, Supplier<Circuit> loader) {
        JMenuItem item = new JMenuItem(label);
        item.setFont(Theme.LABEL);
        item.addActionListener(event -> load(loader));
        menu.add(item);
    }

    private void load(Supplier<Circuit> loader) {
        Circuit loaded = loader.get();
        if (loaded == null) {
            return;    // the loader has already said what went wrong
        }
        copyInto(loaded, circuit());
        signal = Circuit.NONE;
        onStructureChanged.run();
        say("Loaded " + loaded.name + ".", false);
        refresh();
    }

    /**
     * Overwrite the live circuit with another one, IN PLACE. The Brain holds a reference to this
     * exact object, so it has to be refilled rather than swapped out.
     */
    private void copyInto(Circuit from, Circuit into) {
        into.clear();
        for (int i = 0; i < from.size(); i++) {
            ThresholdDevice device = from.device(i);
            into.addDevice(device.threshold, device.x, device.y);
        }
        for (Circuit.Edge edge : from.edges()) {
            into.connect(edge.from, edge.to, edge.weight);
        }
        into.setOutput(from.output());
        into.name = from.name;
    }

    private void save() {
        String name = CircuitStore.cleanName(saveName.getText());
        if (name.isEmpty()) {
            say("Type a name in the box first, then press Save.", true);
            return;
        }
        if (circuit().size() == 0) {
            say("There is nothing on the board to save.", true);
            return;
        }
        try {
            CircuitStore.save(name, circuit());
            circuit().name = name;
            saveName.setText("");
            say("Saved as \"" + name + "\". It is in the Load menu now.", false);
            refresh();
        } catch (IOException failure) {
            say("Could not save: " + failure.getMessage(), true);
        }
    }

    // ------------------------------------------------------------------ the buttons

    private void addDevice() {
        // Drop it in a free-ish spot: march across the board, then wrap to a second row.
        int n = circuit().size();
        circuit().addDevice(1, 0.14 + 0.12 * (n % 6), n < 6 ? 0.35 : 0.75);
        structureChanged();
    }

    /**
     * Run the circuit on a clean train of pulses and say which ones it fired on. This is the
     * answer to "does the thing I just built actually work", without driving the vehicle around
     * for two minutes to find out.
     */
    private void test() {
        Circuit circuit = circuit();

        if (circuit.size() == 0) {
            say("The board is empty. Add a device first.", true);
            return;
        }
        if (!circuit.hasOutput()) {
            say("No OUTPUT device. Right-click a device and choose \"Make output\" - "
                + "without one, nothing can tell the vehicle to pause.", true);
            return;
        }

        List<Integer> firings = circuit.firingPulses(20, 3);

        if (firings.isEmpty()) {
            say("Fed it 20 pulses and the output never fired. Its threshold is out of reach - "
                + "check that enough wires run into it, and that nothing is inhibiting it "
                + "for good.", true);
            return;
        }

        StringBuilder text = new StringBuilder("Fires on source ");
        for (int i = 0; i < firings.size(); i++) {
            text.append(i > 0 ? ", " : "").append(firings.get(i));
        }
        text.append(" of 20.  ").append(rhythmOf(firings));
        say(text.toString(), false);
    }

    /** What the firings add up to, in a sentence. */
    private String rhythmOf(List<Integer> firings) {
        if (firings.size() < 2) {
            return "Once, and then never again: nothing in the circuit clears it, so the vehicle's"
                 + " own pause has to - which is exactly what the plain Counter relies on.";
        }
        int gap = firings.get(1) - firings.get(0);
        for (int i = 2; i < firings.size(); i++) {
            if (firings.get(i) - firings.get(i - 1) != gap) {
                return "The gaps are uneven, so it never settles into a rhythm.";
            }
        }
        return gap == 1
                ? "It pauses at every source."
                : "It pauses every " + gap + ordinal(gap) + " source, clearing itself each time.";
    }

    private String ordinal(int n) {
        return n == 2 ? "nd" : n == 3 ? "rd" : "th";
    }

    private void say(String message, boolean bad) {
        result.setForeground(bad ? Theme.INHIBIT : Theme.TEXT);
        result.setText(message);
    }

    // ==================================================================================
    // The board itself: drawing, and the mouse
    // ==================================================================================

    private class Board extends JPanel {

        Board() {
            setBackground(Theme.WELL);   // a shade off white, so the board reads as a workspace
            setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.LINE));
            setPreferredSize(new Dimension(Parameters.SCREEN_WIDTH, 190));

            MouseAdapter mouse = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { pressed(e); }
                @Override public void mouseDragged(MouseEvent e) { dragged(e); }
                @Override public void mouseReleased(MouseEvent e) { dragging = Circuit.NONE; }
                @Override public void mouseMoved(MouseEvent e) { moved(e); }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        // ----------------------------------------------------------------- geometry

        double railY() {
            return getHeight() - 24;
        }

        Point2D deviceAt(int index) {
            ThresholdDevice device = circuit().device(index);
            return new Point2D.Double(device.x * getWidth(), device.y * (railY() - 34) + 17);
        }

        int deviceUnder(Point point) {
            for (int i = circuit().size() - 1; i >= 0; i--) {
                if (deviceAt(i).distance(point) <= GRAB) {
                    return i;
                }
            }
            return Circuit.NONE;
        }

        boolean onRail(Point point) {
            return Math.abs(point.y - railY()) <= 8;
        }

        /**
         * The points a wire is drawn through. Drawing and hit-testing walk this same list, so
         * what you can click is exactly what you can see - they cannot drift apart.
         */
        List<Point2D> wirePath(Circuit.Edge edge) {
            List<Point2D> path = new ArrayList<>();
            Point2D to = deviceAt(edge.to);

            if (edge.from == Circuit.INPUT) {
                path.add(new Point2D.Double(to.getX(), railY()));
                path.add(new Point2D.Double(to.getX(), to.getY() + RADIUS));
                return path;
            }

            Point2D from = deviceAt(edge.from);

            if (edge.from == edge.to) {
                // A self-inhibiting device: a little loop out of the top and back in.
                for (double a = 20; a <= 340; a += 12) {
                    double rad = Math.toRadians(a - 90);
                    path.add(new Point2D.Double(
                            from.getX() + 14 * Math.cos(rad),
                            from.getY() - RADIUS - 11 + 11 * Math.sin(rad)));
                }
                return path;
            }

            if (from.getX() <= to.getX()) {
                // Rim to rim, not centre to centre, so the line does not run underneath either
                // circle and the head lands where the head belongs.
                double dx = to.getX() - from.getX();
                double dy = to.getY() - from.getY();
                double length = Math.max(Math.hypot(dx, dy), 1);
                dx /= length;
                dy /= length;
                path.add(new Point2D.Double(
                        from.getX() + dx * RADIUS, from.getY() + dy * RADIUS));
                path.add(new Point2D.Double(
                        to.getX() - dx * RADIUS, to.getY() - dy * RADIUS));
                return path;
            }

            // Backwards: FEEDBACK. Arc it over the top, so it cannot be mistaken for a forward
            // wire. The longer the jump back, the higher the arc - otherwise a device that
            // inhibits the whole chain draws four arcs stacked on top of each other, and you
            // cannot see which one ends where.
            double lift = 20 + 13 * Math.abs(edge.from - edge.to);
            double x1 = from.getX(), y1 = from.getY() - RADIUS;
            double x2 = to.getX(), y2 = to.getY() - RADIUS;
            for (double t = 0; t <= 1.0001; t += 0.04) {
                double u = 1 - t;
                double x = u*u*u*x1 + 3*u*u*t*x1 + 3*u*t*t*x2 + t*t*t*x2;
                double y = u*u*u*y1 + 3*u*u*t*(y1 - lift) + 3*u*t*t*(y2 - lift) + t*t*t*y2;
                path.add(new Point2D.Double(x, y));
            }
            return path;
        }

        Circuit.Edge wireUnder(Point point) {
            for (Circuit.Edge edge : circuit().edges()) {
                List<Point2D> path = wirePath(edge);
                for (int i = 0; i < path.size() - 1; i++) {
                    if (Line2D.ptSegDist(
                            path.get(i).getX(), path.get(i).getY(),
                            path.get(i + 1).getX(), path.get(i + 1).getY(),
                            point.x, point.y) <= WIRE_GRAB) {
                        return edge;
                    }
                }
            }
            return null;
        }

        // ----------------------------------------------------------------- the mouse

        void pressed(MouseEvent event) {
            Point point = event.getPoint();
            int device = deviceUnder(point);
            Circuit.Edge wire = device == Circuit.NONE ? wireUnder(point) : null;

            if (SwingUtilities.isRightMouseButton(event)) {
                if (device != Circuit.NONE) {
                    deviceMenu(device).show(this, point.x, point.y);
                } else if (wire != null) {
                    wireMenu(wire).show(this, point.x, point.y);
                }
                return;
            }

            if (device != Circuit.NONE) {
                if (signal == Circuit.NONE || signal == device) {
                    // Pick this device up: it is either the start of a wire, or a drag.
                    signal = signal == device ? Circuit.NONE : device;
                    dragging = device;
                } else {
                    askWireKind(signal, device, point);
                }

            } else if (wire != null) {
                if (signal == Circuit.NONE) {
                    // Tap the signal this wire carries. A wire is not itself a source, so what
                    // gets selected is the thing feeding it - and the next click BRANCHES that
                    // same signal somewhere new, which is the junction dot in the book's figures.
                    signal = wire.from;
                } else {
                    // Branch the held signal into wherever this wire already goes.
                    askWireKind(signal, wire.to, point);
                }

            } else if (onRail(point) && signal == Circuit.NONE) {
                signal = Circuit.INPUT;

            } else {
                signal = Circuit.NONE;   // empty space cancels
            }
            refresh();
        }

        void dragged(MouseEvent event) {
            if (dragging == Circuit.NONE) {
                return;
            }
            signal = Circuit.NONE;   // it turned out to be a drag, not the start of a wire

            ThresholdDevice device = circuit().device(dragging);
            device.x = clamp((double) event.getX() / getWidth(), 0.04, 0.96);
            device.y = clamp((event.getY() - 17) / (railY() - 34), 0.0, 1.0);
            refresh();
        }

        void moved(MouseEvent event) {
            boolean overSomething = deviceUnder(event.getPoint()) != Circuit.NONE
                    || wireUnder(event.getPoint()) != null
                    || onRail(event.getPoint());
            setCursor(Cursor.getPredefinedCursor(
                    overSomething ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        }

        double clamp(double value, double low, double high) {
            return Math.max(low, Math.min(high, value));
        }

        // ----------------------------------------------------------------- the menus

        /** The two kinds of wire. This is the choice the user makes for every connection. */
        void askWireKind(int from, int to, Point at) {
            JPopupMenu menu = new JPopupMenu();

            JMenuItem excite = new JMenuItem("Excitatory  -->    pushes it towards firing");
            excite.addActionListener(e -> connect(from, to, Circuit.EXCITE));

            JMenuItem inhibit = new JMenuItem("Inhibitory  --|    forces it OFF");
            inhibit.setForeground(Theme.INHIBIT);
            inhibit.addActionListener(e -> connect(from, to, Circuit.INHIBIT));

            // A device already latches itself on, so exciting itself would mean nothing.
            if (from != to) {
                menu.add(excite);
            }
            menu.add(inhibit);

            // Escaping the menu drops the half-drawn wire, so the board is never left in a state
            // the status line is not describing.
            menu.addPopupMenuListener(new PopupMenuListener() {
                @Override public void popupMenuWillBecomeVisible(PopupMenuEvent e) { }
                @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent e) { }
                @Override public void popupMenuCanceled(PopupMenuEvent e) {
                    signal = Circuit.NONE;
                    refresh();
                }
            });

            menu.show(this, at.x, at.y);
        }

        void connect(int from, int to, int weight) {
            circuit().connect(from, to, weight);
            signal = Circuit.NONE;
            wiringChanged();
        }

        JPopupMenu deviceMenu(int index) {
            Circuit circuit = circuit();
            JPopupMenu menu = new JPopupMenu();

            JMenuItem header = new JMenuItem("Threshold");
            header.setEnabled(false);
            menu.add(header);

            for (int t = 1; t <= 4; t++) {
                final int threshold = t;
                JMenuItem item = new JMenuItem(
                        (circuit.device(index).threshold == t ? "* " : "   ") + t);
                item.addActionListener(e -> {
                    circuit.device(index).threshold = threshold;
                    wiringChanged();
                });
                menu.add(item);
            }

            menu.addSeparator();

            JMenuItem output = new JMenuItem(index == circuit.output()
                    ? "Already the output"
                    : "Make output  (fires -> vehicle pauses)");
            output.setEnabled(index != circuit.output());
            output.addActionListener(e -> {
                circuit.setOutput(index);
                wiringChanged();
            });
            menu.add(output);

            JMenuItem delete = new JMenuItem("Delete device");
            delete.addActionListener(e -> {
                circuit.removeDevice(index);
                signal = Circuit.NONE;
                structureChanged();
            });
            menu.add(delete);

            return menu;
        }

        JPopupMenu wireMenu(Circuit.Edge wire) {
            JPopupMenu menu = new JPopupMenu();

            JMenuItem flip = new JMenuItem(wire.inhibitory()
                    ? "Make excitatory  -->"
                    : "Make inhibitory  --|");
            flip.addActionListener(e -> {
                circuit().connect(wire.from, wire.to,
                        wire.inhibitory() ? Circuit.EXCITE : Circuit.INHIBIT);
                wiringChanged();
            });

            JMenuItem delete = new JMenuItem("Delete wire");
            delete.addActionListener(e -> {
                circuit().disconnect(wire.from, wire.to);
                wiringChanged();
            });

            // A self-wire can only ever be inhibitory - see Circuit.connect().
            if (wire.from != wire.to) {
                menu.add(flip);
            }
            menu.add(delete);
            return menu;
        }

        // ----------------------------------------------------------------- drawing

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Brain brain = vehicle.brain;
            Circuit circuit = circuit();
            long now = System.currentTimeMillis();
            noteTransitions(brain, circuit.size(), now);

            drawRail(g, now);
            for (Circuit.Edge edge : circuit.edges()) {
                drawWire(g, brain, edge, now);
            }
            for (int i = 0; i < circuit.size(); i++) {
                drawDevice(g, circuit, brain, i, now);
            }
        }

        void drawRail(Graphics2D g, long now) {
            boolean lit = pulseLit(now);
            boolean held = signal == Circuit.INPUT;
            double y = railY();

            g.setStroke(lit || held ? Theme.BOLD : Theme.THIN);
            g.setColor(lit ? Theme.FLASH : held ? Theme.ACCENT : Theme.FAINT);
            g.draw(new Line2D.Double(12, y, getWidth() - 12, y));

            g.setFont(Theme.SMALL);
            g.setColor(lit ? Theme.FLASH : held ? Theme.ACCENT : Theme.MUTED);
            g.drawString("pulse in", 14, (float) y + 14);
        }

        void drawWire(Graphics2D g, Brain brain, Circuit.Edge edge, long now) {
            boolean fromInput = edge.from == Circuit.INPUT;

            // A wire is live while its source is on. The pulse rail's "on" is stretched to
            // FLASH_MS so the eye can catch what is really a single frame.
            boolean live = fromInput ? pulseLit(now) : brain.isActive(edge.from);

            // Every wire out of the held signal is highlighted, so it is obvious what clicking
            // one of them would branch.
            boolean held = signal != Circuit.NONE && edge.from == signal;

            Color color = edge.inhibitory()
                    ? (live ? Theme.INHIBIT : Theme.INHIBIT_FAINT)
                    : (live ? Theme.FLASH : held ? Theme.ACCENT : Theme.FAINT);

            g.setColor(color);
            g.setStroke(live || held ? Theme.BOLD : Theme.THIN);

            List<Point2D> path = wirePath(edge);
            Path2D shape = new Path2D.Double();
            shape.moveTo(path.get(0).getX(), path.get(0).getY());
            for (int i = 1; i < path.size(); i++) {
                shape.lineTo(path.get(i).getX(), path.get(i).getY());
            }
            g.draw(shape);

            // The head sits at the end of the path, pointing the way the path was going. Every
            // path already stops at the target's rim, so there is nothing to back off by - and
            // backing off anyway is what used to leave the inhibitory bars floating in mid-air
            // above the devices they belong to.
            Point2D last = path.get(path.size() - 1);
            Point2D prev = path.get(path.size() - 2);
            double dx = last.getX() - prev.getX();
            double dy = last.getY() - prev.getY();
            double length = Math.hypot(dx, dy);
            if (length < 0.001) {
                return;
            }
            dx /= length;
            dy /= length;

            if (edge.inhibitory()) {
                bar(g, last.getX(), last.getY(), dx, dy);
            } else {
                arrowHead(g, last.getX(), last.getY(), dx, dy);
            }
        }

        void drawDevice(Graphics2D g, Circuit circuit, Brain brain, int index, long now) {
            Point2D at = deviceAt(index);
            double cx = at.getX(), cy = at.getY();

            boolean active = brain.isActive(index);
            boolean justLit = active && now - activatedAt[index] < FLASH_MS;
            boolean held = index == signal;

            // The held signal wears a halo, so it is obvious what the next click will connect.
            if (held) {
                g.setColor(Theme.ACCENT_BG);
                g.fill(new Ellipse2D.Double(cx - RADIUS - 7, cy - RADIUS - 7,
                        2 * RADIUS + 14, 2 * RADIUS + 14));
            }

            // The output device wears a second ring, drawn UNDER the device so the two rings
            // read as one badge rather than as a collision.
            if (index == circuit.output()) {
                double r = RADIUS + 5;
                g.setStroke(Theme.THIN);
                g.setColor(active ? Theme.ACTIVE : Theme.FAINT);
                g.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
            }

            Ellipse2D circle = new Ellipse2D.Double(cx - RADIUS, cy - RADIUS, 2 * RADIUS, 2 * RADIUS);
            g.setColor(justLit ? Theme.FLASH_BG : active ? Theme.ACTIVE_BG : Theme.CARD);
            g.fill(circle);

            g.setStroke(active || held ? Theme.BOLD : Theme.THIN);
            g.setColor(justLit ? Theme.FLASH
                     : active ? Theme.ACTIVE
                     : held ? Theme.ACCENT
                     : Theme.BUTTON_LINE);
            g.draw(circle);

            String label = String.valueOf(circuit.device(index).threshold);
            g.setFont(Theme.DEVICE);
            g.setColor(active ? Theme.TEXT : Theme.MUTED);
            int w = g.getFontMetrics().stringWidth(label);
            g.drawString(label, (float) (cx - w / 2.0), (float) (cy + 5));

            if (index == circuit.output()) {
                g.setFont(Theme.SMALL);
                g.setColor(active ? Theme.ACTIVE : Theme.MUTED);
                int ow = g.getFontMetrics().stringWidth("out");
                g.drawString("out", (float) (cx - ow / 2.0), (float) (cy + RADIUS + 19));
            }
        }

        void arrowHead(Graphics2D g, double x, double y, double dx, double dy) {
            double size = 7;
            double px = -dy, py = dx;
            Path2D head = new Path2D.Double();
            head.moveTo(x, y);
            head.lineTo(x - dx * size + px * size * 0.5, y - dy * size + py * size * 0.5);
            head.lineTo(x - dx * size - px * size * 0.5, y - dy * size - py * size * 0.5);
            head.closePath();
            g.fill(head);
        }

        /** The blunt end of an inhibitory wire - the standard "this shuts you off" symbol. */
        void bar(Graphics2D g, double x, double y, double dx, double dy) {
            double px = -dy, py = dx;
            g.setStroke(new BasicStroke(3f));
            g.draw(new Line2D.Double(x - px * 6, y - py * 6, x + px * 6, y + py * 6));
        }

        boolean pulseLit(long now) {
            return now - lastPulseAt < FLASH_MS;
        }

        /** Timestamps new pulses and new latches, so both can be shown lit for FLASH_MS. */
        void noteTransitions(Brain brain, int n, long now) {
            if (wasActive.length != n) {
                wasActive = new boolean[n];
                activatedAt = new long[n];
            }

            long pulses = brain.pulseCount();
            if (pulses > seenPulseCount) {
                lastPulseAt = now;
            }
            seenPulseCount = pulses;

            for (int i = 0; i < n; i++) {
                boolean active = brain.isActive(i);
                if (active && !wasActive[i]) {
                    activatedAt[i] = now;
                }
                wasActive[i] = active;
            }
        }
    }

    /** The main loop calls this every frame. */
    public void tick() {
        board.repaint();
    }
}
