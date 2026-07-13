/**
 * The brain of Vehicle 5: a peak detector feeding a network of threshold devices.
 *
 * This is the whole point of Braitenberg's chapter 5. A vehicle whose sensors are wired
 * straight to its motors can only react to what it senses right now. Put threshold devices in
 * between, wire them together, and the vehicle gains MEMORY - here, the memory of how many
 * sources it has already visited.
 *
 * Two stages:
 *
 *   1. PEAK DETECTOR. The reading stays high the whole time we are next to a source, so we
 *      need ONE event per source, not one per frame. Visiting a source means passing its point
 *      of CLOSEST APPROACH, and that is exactly where the reading stops rising and starts to
 *      fall. So: one pulse at each peak that is above the detection level.
 *
 *          reading  ..0.05  0.20  0.45 [0.52] 0.40  0.31 [0.38] 0.22  0.05..
 *          above    ..no    no    yes   yes   yes   yes   yes   no    no
 *          rising   ..yes   yes   yes   no    no    no    yes   no    no
 *          pulse    ..no    no    no   [YES]  no    no   [YES]  no    no
 *                                        |                 |
 *                                    source A          source B
 *
 *      Note the second peak: the vehicle went from source A straight into source B without the
 *      reading ever dropping below the detection level. A detector that waited for the reading
 *      to cross the level upwards would have missed B completely - the two sources look like
 *      one long "above" stretch to it.
 *
 *      "Is it still rising?" is just another threshold device, this one fed by the CHANGE in
 *      the reading:  RISING = threshold(reading - previousReading, 0).
 *
 *   2. THE NETWORK. Each frame is one step of a circuit of threshold devices, with the pulse
 *      as its input wire. What that circuit DOES is not decided here - see Circuit.java, which
 *      holds both the rules and the wirings. The default one counts:
 *
 *          pulse 1:  [ON ] [OFF] [OFF] [OFF]     1 source seen
 *          pulse 2:  [ON ] [ON ] [OFF] [OFF]     2 sources seen
 *          pulse 3:  [ON ] [ON ] [ON ] [OFF]     3 sources seen
 *          pulse 4:  [ON ] [ON ] [ON ] [ON ]     full -> the vehicle pauses
 *
 * There is no "if (count == 4)" anywhere. The counting falls out of the thresholds.
 */
public class Brain {

    private final Circuit circuit;
    private boolean[] state;

    // What the peak detector remembers from one frame to the next. previousReading starts
    // impossibly high so the very first frame can never look like a peak: on frame one there
    // is no "before" to compare against yet.
    private double previousReading = Double.MAX_VALUE;
    private boolean wasRising;

    private boolean pulsedThisFeed;

    // How many pulses this brain has seen. A pulse lasts a single frame, and at 2x-8x the main
    // loop runs several frames per repaint, so a display that only asked "did the last frame
    // pulse?" would blink for 16 ms at best and miss the pulse entirely at worst. A running
    // total cannot be missed: the display just watches it change. See CircuitEditor.
    private long pulseCount;

    public Brain(Circuit circuit) {
        this.circuit = circuit;
        this.state = new boolean[circuit.size()];
    }

    public Circuit circuit() {
        return circuit;
    }

    /**
     * Feed one frame's sensor reading in.
     * Returns true on the frame the circuit's OUTPUT device switches on.
     */
    public boolean feed(double averageReading) {
        // The editor can add or delete a device between two frames, which leaves this array
        // the wrong length for the circuit it belongs to. Rather than trust every caller to
        // rebuild the brain, notice it here and start the network from empty.
        if (state.length != circuit.size()) {
            state = new boolean[circuit.size()];
        }

        // Stage 1: a pulse at the peak of the reading, i.e. at the closest approach.
        //
        // Two threshold devices decide it:
        //   above  : is a source near enough to count at all?
        //   rising : is the reading still growing?  (a threshold on the CHANGE)
        //
        // The peak is the frame where it was rising and now no longer is.
        boolean above = averageReading > Parameters.detectLevel();
        boolean rising = averageReading > previousReading;
        boolean pulse = above && wasRising && !rising;

        pulsedThisFeed = pulse;
        if (pulse) {
            pulseCount++;
        }

        wasRising = rising;
        previousReading = averageReading;

        // Stage 2: one step of the network - EVERY frame, with the pulse as its input wire,
        // not just on the frames that pulse. A latching device sits still through the quiet
        // frames, so this costs the counter nothing; what it buys is that a circuit can also
        // react to the GAPS between pulses, which is where inhibition does its work.
        boolean[] before = state;
        state = circuit.step(before, pulse);
        return circuit.fired(before, state);
    }

    /**
     * Forget everything and start counting from zero.
     *
     * currentReading re-arms the peak detector. The vehicle stood still all through the pause,
     * so it is still sitting next to the source it just counted; starting from "not rising"
     * means driving away from it cannot produce another peak.
     */
    public void reset(double currentReading) {
        state = new boolean[circuit.size()];
        previousReading = currentReading;
        wasRising = false;
        pulsedThisFeed = false;
    }

    public boolean justPulsed() {
        return pulsedThisFeed;
    }

    /** How many pulses in total. Only ever grows - reset() does not clear it. */
    public long pulseCount() {
        return pulseCount;
    }

    /** How many devices are currently on. For the default counter, that is the count. */
    public int count() {
        int active = 0;
        for (boolean on : state) {
            if (on) active++;
        }
        return active;
    }

    public int size() {
        return state.length;
    }

    public boolean isActive(int index) {
        return index >= 0 && index < state.length && state[index];
    }
}
