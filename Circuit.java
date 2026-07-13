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
 * THE RULES. Every device is evaluated once per FRAME - not once per pulse:
 *
 *   0. THE CLOCK IS THE FRAME. The pulse (INPUT) is a wire like any other: on during the
 *      frames the peak detector fires, off the rest of the time. Stepping every frame, and
 *      not only on pulses, is what gives the network a sense of TIME - a device can react to
 *      the ABSENCE of pulses. A latching device sits still through the quiet frames, so a
 *      plain counter behaves the same either way; but a circuit that resets itself in the gap
 *      after a pulse is only expressible this way.
 *
 *   1. SNAPSHOT. Every device reads the state the network had BEFORE this frame. Nothing sees
 *      a neighbour's new value in the same step. This is what makes it a synchronous (clocked)
 *      circuit - and it is why a FEEDBACK LOOP cannot hang: a wire running backwards reads
 *      last frame's value, not this frame's.
 *
 *   2. EXCITATION. Add up the excitatory wires whose source is on. A device fires when that
 *      sum reaches its threshold.
 *
 *   3. INHIBITION IS A VETO. If any inhibitory wire into a device has an active source, that
 *      device is forced OFF this frame no matter what the excitation says, and its latch is
 *      cleared. This is the only way to switch a latched device back off from inside the
 *      network - it is how a circuit resets itself (Braitenberg's fig. 10b).
 *
 * All of which is one line, in step():
 *
 *      on = vetoed ? false : (was on || excitation >= threshold)
 *
 * The circuit FIRES on the frame its output device switches from off to on. That is the
 * signal that makes the vehicle pause.
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

        Edge(int from, int to, int weight) {
            this.from = from;
            this.to = to;
            this.weight = weight;
        }

        public boolean inhibitory() {
            return weight < 0;
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
     * One frame of the network. Reads the state before this frame, returns the state after.
     * inputOn is whether the peak detector pulsed on this frame. See THE RULES above.
     */
    public boolean[] step(boolean[] before, boolean inputOn) {
        boolean[] after = new boolean[devices.size()];

        for (int i = 0; i < devices.size(); i++) {
            int excitation = 0;
            boolean vetoed = false;

            for (Edge edge : edges) {
                if (edge.to != i) {
                    continue;
                }
                boolean sourceOn = edge.from == INPUT ? inputOn : before[edge.from];
                if (!sourceOn) {
                    continue;
                }
                if (edge.inhibitory()) {
                    vetoed = true;
                } else {
                    excitation += edge.weight;
                }
            }

            after[i] = !vetoed && (before[i] || devices.get(i).wouldFire(excitation));
        }
        return after;
    }

    /** Did the output device just switch on? A circuit with no output never fires. */
    public boolean fired(boolean[] before, boolean[] after) {
        return hasOutput() && after[output] && !before[output];
    }

    /**
     * Run the circuit on a train of pulses and report which pulses it fired on. Pure
     * bookkeeping on top of step() - the editor's "Test" button uses this to tell you what
     * the thing you just wired up actually does, without waiting for the vehicle to find
     * that many sources.
     *
     * quiet is how many empty frames sit between two pulses. It matters: a self-resetting
     * circuit does its clearing in exactly those frames.
     */
    public List<Integer> firingPulses(int pulses, int quiet) {
        List<Integer> firings = new ArrayList<>();
        boolean[] state = new boolean[devices.size()];

        for (int p = 1; p <= pulses; p++) {
            state = advance(state, true, firings, p);
            for (int q = 0; q < quiet; q++) {
                state = advance(state, false, firings, p);
            }
        }
        return firings;
    }

    private boolean[] advance(boolean[] state, boolean inputOn, List<Integer> firings, int pulse) {
        boolean[] after = step(state, inputOn);
        if (fired(state, after) && !firings.contains(pulse)) {
            firings.add(pulse);
        }
        return after;
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
    public void connect(int from, int to, int weight) {
        if (to < 0 || to >= devices.size()) return;
        if (from == to && weight > 0) return;

        disconnect(from, to);
        edges.add(new Edge(from, to, weight));
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
     * The same counter, but it CLEARS ITSELF: the last device inhibits every device in the
     * chain, itself included. No outside reset - the network remembers and forgets on its own.
     * This is the shape of Braitenberg's fig. 10b, and the reason inhibition exists here.
     *
     * It only comes out clean because the network is stepped every frame. The veto lands on
     * the very next frame, in the quiet just after the pulse, so the chain is already empty
     * when the next pulse arrives and every pulse gets counted. Step it only on pulses instead
     * and the reset has to eat a pulse to do its work, which drifts the count to one-in-(n+1).
     */
    public static Circuit selfResettingCounter(int n) {
        Circuit circuit = counter(n);
        circuit.name = "Self-resetting counter";

        for (int i = 0; i < n; i++) {
            circuit.connect(n - 1, i, INHIBIT);
        }
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
