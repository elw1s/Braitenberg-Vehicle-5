import java.util.ArrayList;
import java.util.List;

/**
 * A NETWORK of threshold devices - the brain's wiring, and the rules that run it.
 *
 * Braitenberg draws these as circles (the devices, labelled with their threshold) sitting on
 * a horizontal input rail, with wires between them. This class is that drawing as data: a
 * list of devices, a list of wires, and which device is the output.
 *
 * There is NO Swing in this file, and no drawing. CircuitEditor is the picture of a Circuit;
 * this is the thing itself. If you want to understand the vehicle's brain, read only this.
 *
 *
 * THE RULES. There are three, and step() is all three of them.
 *
 *   1. THE CLOCK IS THE PULSE. The network takes exactly one step each time a pulse arrives on
 *      the input rail, and does nothing at all in between. Everything else here follows from
 *      that, so it is worth being concrete:
 *
 *          pulse 1:  the first device fires. Its output has NOT gone anywhere yet.
 *          pulse 2:  now the first device's output arrives at the second one, which therefore
 *                    sees the new pulse AND the first device at the same moment - the pair that
 *                    reaches its threshold of 2. The first device's own inhibition arrives too,
 *                    and switches it off.
 *          pulse 3:  the second device's output reaches the third, which fires.
 *
 *      That is Braitenberg's fig. 10b: a pulse out for every third pulse in.
 *
 *   2. ONE STEP OF DELAY. A device reads the state the network had at the PREVIOUS pulse, never
 *      the one being computed. So a signal takes one pulse to travel one device, which is what
 *      spreads a burst of three pulses across three devices instead of collapsing it into one.
 *      It is also why a FEEDBACK LOOP cannot hang - a wire running backwards reads the last
 *      step's value, and there is nothing to chase.
 *
 *   3. EXCITATION SUMS, INHIBITION VETOES. Add up the excitatory wires whose source was on. The
 *      device fires if that reaches its threshold - unless an inhibitory wire's source was on,
 *      in which case it is off, whatever the excitation says.
 *
 * In one line:
 *
 *      on = vetoed ? false : (excitation >= threshold)
 *
 * NOTHING LATCHES. A device is on for exactly as long as its inputs say so, and its state at
 * the last pulse is the only memory in the system. That is enough: the plain counter remembers
 * because the pulse rail keeps re-firing each device it has already reached, and fig. 10b's ring
 * remembers because the token it is passing along has to be somewhere. Devices that latched on
 * for good could count, but they could never count TWICE.
 *
 * The circuit FIRES on the step its output device switches from off to on. That is the signal
 * that makes the vehicle pause.
 */
public class Circuit {

    /** A wire can come from the pulse rail instead of from a device. */
    public static final int INPUT = -1;

    /** No device chosen (as an output, or as the source of a wire). */
    public static final int NONE = -2;

    public static final int EXCITE = +1;
    public static final int INHIBIT = -1;

    /** One wire. from is INPUT or a device index; to is always a device index. */
    public static final class Edge {
        public int from, to;
        public final int weight;   // EXCITE or INHIBIT

        /**
         * DRAWING ONLY, and step() never once looks at it.
         *
         * When you tap a wire and run the signal somewhere new, the new wire carries the same
         * source - electrically it makes no difference at all where the junction sits. But it
         * makes every difference to a reader, so the point you tapped is remembered here (as a
         * fraction of the board, so it survives resizing) and the wire is drawn leaving from
         * there, with a junction dot on it. -1 means "no junction: draw it from the device".
         */
        public double branchX = -1, branchY = -1;

        Edge(int from, int to, int weight) {
            this.from = from;
            this.to = to;
            this.weight = weight;
        }

        public boolean inhibitory() {
            return weight < 0;
        }

        public boolean branched() {
            return branchX >= 0 && branchY >= 0;
        }

        public void branchAt(double x, double y) {
            branchX = x;
            branchY = y;
        }
    }

    private final List<ThresholdDevice> devices = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();
    private int output = NONE;

    /** What to call this circuit. The editor sets it to "Custom" as soon as you touch it. */
    public String name = "Empty";

    // =====================================================================
    // RUNNING IT - the only part that matters for the vehicle's behaviour
    // =====================================================================

    /**
     * ONE PULSE. Takes the state the network was left in by the previous pulse, and returns the
     * state this one puts it in. Called once per pulse and never in between - see THE RULES.
     *
     * The pulse itself is on for the whole of this step, by definition: a step IS a pulse. So a
     * wire from INPUT is simply a wire whose source is on.
     */
    public boolean[] step(boolean[] before) {
        boolean[] after = new boolean[devices.size()];

        for (int i = 0; i < devices.size(); i++) {
            int excitation = 0;
            boolean vetoed = false;

            for (Edge edge : edges) {
                if (edge.to != i) {
                    continue;
                }
                // INPUT is the pulse, and we are in one. Everything else is read from the state
                // the network was in BEFORE this pulse - never from what we are computing now.
                boolean sourceOn = edge.from == INPUT || before[edge.from];
                if (!sourceOn) {
                    continue;
                }
                if (edge.inhibitory()) {
                    vetoed = true;
                } else {
                    excitation += edge.weight;
                }
            }

            after[i] = !vetoed && devices.get(i).wouldFire(excitation);
        }
        return after;
    }

    /** Did the output device just switch on? A circuit with no output never fires. */
    public boolean fired(boolean[] before, boolean[] after) {
        return hasOutput() && after[output] && !before[output];
    }

    /** What a trial run of the circuit turned up. */
    public static final class Trace {
        /** Which pulses the output fired on. */
        public final List<Integer> firings = new ArrayList<>();
        /** Whether each device switched on even once. A device that never does is dead wiring. */
        public boolean[] everOn;
    }

    /**
     * Run the circuit on a train of pulses and report what happened. Pure bookkeeping on top of
     * step() - the editor's "Test" button uses this to tell you what the thing you just wired up
     * actually does, without waiting for the vehicle to find that many sources.
     *
     * There is no gap to model between two pulses. The network only ever moves when a pulse
     * arrives, so a run is just that many steps.
     */
    public Trace run(int pulses) {
        Trace trace = new Trace();
        trace.everOn = new boolean[devices.size()];

        boolean[] state = new boolean[devices.size()];
        for (int pulse = 1; pulse <= pulses; pulse++) {
            boolean[] after = step(state);

            if (fired(state, after)) {
                trace.firings.add(pulse);
            }
            for (int i = 0; i < after.length; i++) {
                if (after[i]) {
                    trace.everOn[i] = true;
                }
            }
            state = after;
        }
        return trace;
    }

    // =====================================================================
    // EDITING IT - what the editor calls. None of this runs while a frame is stepping.
    // =====================================================================

    public int size() {
        return devices.size();
    }

    public ThresholdDevice device(int index) {
        return devices.get(index);
    }

    public List<Edge> edges() {
        return edges;
    }

    public int output() {
        return output;
    }

    public boolean hasOutput() {
        return output >= 0 && output < devices.size();
    }

    public void setOutput(int index) {
        output = index;
    }

    /** Returns the index of the new device. */
    public int addDevice(int threshold, double x, double y) {
        devices.add(new ThresholdDevice(threshold, x, y));
        int index = devices.size() - 1;
        if (!hasOutput()) {
            output = index;   // the first device you place is the output until you say otherwise
        }
        return index;
    }

    /**
     * Removing a device is the one fiddly edit, because every wire and the output are stored
     * as INDEXES into the device list - so deleting device 2 silently turns "wire into 3" into
     * "wire into the wrong device". Everything that has to shift, shifts here, in one place.
     *
     * INPUT is -1 and every real index is >= 0, so "> index" never touches an input wire.
     */
    public void removeDevice(int index) {
        devices.remove(index);
        edges.removeIf(edge -> edge.from == index || edge.to == index);

        for (Edge edge : edges) {
            if (edge.from > index) edge.from--;
            if (edge.to > index) edge.to--;
        }

        if (output == index) {
            output = devices.isEmpty() ? NONE : 0;
        } else if (output > index) {
            output--;
        }
    }

    /**
     * Wire two things together. A pair can carry only ONE wire: connecting an already
     * connected pair replaces it, so you can flip a wire from excitatory to inhibitory just
     * by drawing it again. A device cannot excite itself (it already latches), but it CAN
     * inhibit itself - that is how a device gives a single-frame pulse instead of latching on.
     */
    /** Returns the new wire, or null if the pair cannot carry one. */
    public Edge connect(int from, int to, int weight) {
        if (to < 0 || to >= devices.size()) return null;
        if (from == to && weight > 0) return null;

        disconnect(from, to);
        Edge edge = new Edge(from, to, weight);
        edges.add(edge);
        return edge;
    }

    public void disconnect(int from, int to) {
        edges.removeIf(edge -> edge.from == from && edge.to == to);
    }

    public Edge edgeBetween(int from, int to) {
        for (Edge edge : edges) {
            if (edge.from == from && edge.to == to) return edge;
        }
        return null;
    }

    public void clear() {
        devices.clear();
        edges.clear();
        output = NONE;
    }

    // =====================================================================
    // The circuits the editor can load for you
    // =====================================================================

    /**
     * THE DEFAULT. A plain counter: thresholds 1, then 2, 2, 2 ...
     *
     * Device 0 needs only the pulse. Every later device needs the pulse AND the device before
     * it, so each pulse walks the chain exactly one step further. It never clears itself - the
     * vehicle's pause does that from outside.
     */
    public static Circuit counter(int n) {
        Circuit circuit = new Circuit();
        circuit.name = "Counter";

        for (int i = 0; i < n; i++) {
            circuit.addDevice(i == 0 ? 1 : 2, spread(i, n), 0.5);
            circuit.connect(INPUT, i, EXCITE);
            if (i > 0) {
                circuit.connect(i - 1, i, EXCITE);
            }
        }
        circuit.setOutput(n - 1);
        return circuit;
    }

    /**
     * BRAITENBERG'S FIG. 10b, generalised: a RING that emits a pulse for every n-th pulse in.
     *
     * The plain counter above fills up and stays full. This one passes a single token along the
     * chain instead, and only one device is ever on at a time:
     *
     *     pulse 1:  [ON ] [   ] [   ]
     *     pulse 2:  [   ] [ON ] [   ]      device 0 has inhibited itself and stood down
     *     pulse 3:  [   ] [   ] [ON ]      the last device fires -> the vehicle pauses
     *     pulse 4:  [ON ] [   ] [   ]      nothing is inhibiting device 0 any more; it re-arms
     *
     * Two kinds of inhibitory wire do it, and both are in the book's drawing:
     *
     *     device 0 inhibits ITSELF          so it fires once and hands the token on rather than
     *                                       sitting there on for good.
     *     devices 1..n-2 inhibit device 0   so it stays down while the token is still travelling,
     *                                       and only re-arms once the token has left the chain.
     *
     * The last device is deliberately NOT one of them. It is still on during the step after it
     * fires, and if it held device 0 down as well, that pulse would be spent re-arming instead of
     * counting - and the ring would quietly become a divide-by-(n+1).
     */
    public static Circuit ringCounter(int n) {
        Circuit circuit = counter(n);
        circuit.name = "Ring counter";

        circuit.connect(0, 0, INHIBIT);
        for (int i = 1; i <= n - 2; i++) {
            circuit.connect(i, 0, INHIBIT);
        }
        return circuit;
    }

    /**
     * BRAITENBERG'S FIG. 10b: "a network of threshold devices that emits a pulse for every third
     * pulse in a row in the input."
     *
     * Three devices (1, 2, 2), the pulse rail into each, a chain between them, and the two hooks
     * the book draws going back into the first device. It is ringCounter(3), and Test will tell
     * you it fires on 3, 6, 9, 12 - exactly what the caption promises.
     */
    public static Circuit figure10b() {
        Circuit circuit = ringCounter(3);
        circuit.name = "Figure 10b";
        return circuit;
    }

    /** Evenly spaced across the board, left to right. */
    private static double spread(int i, int n) {
        return n == 1 ? 0.5 : 0.14 + 0.72 * i / (n - 1.0);
    }

    // Braitenberg's fig. 10a - "a signal when a burst of 3 pulses arrives, preceded and
    // followed by a pause" - is deliberately not offered as a preset. Its devices (1,1,2,2,1)
    // are easy to read off the drawing, but the caption needs the network to measure ELAPSED
    // TIME: what separates a burst from three lone pulses is how long the gaps are, and a
    // plain threshold device has no clock in it. Nothing in this file can express "it has been
    // quiet for a while" - only "it is quiet right now". Wire it up from the picture alone and
    // you get a circuit that fires on the very first pulse. Build it in the editor and press
    // Test if you want to see that for yourself.
}
