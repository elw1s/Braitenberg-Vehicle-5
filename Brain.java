/**
 * The brain of Vehicle 5: a chain of threshold devices that COUNTS.
 *
 * This is the whole point of Braitenberg's chapter 5. A vehicle whose sensors are
 * wired straight to its motors can only react to what it senses right now. Put
 * threshold devices in between, chain them, and the vehicle gains MEMORY - here,
 * the memory of how many sources it has already visited.
 *
 * Two stages:
 *
 *   1. PEAK DETECTOR. The reading stays high the whole time we are next to a source,
 *      so we need ONE event per source, not one per frame. Visiting a source means
 *      passing its point of CLOSEST APPROACH, and that is exactly where the reading
 *      stops rising and starts to fall. So: one pulse at each peak that is above the
 *      detection level.
 *
 *          reading  ..0.05  0.20  0.45 [0.52] 0.40  0.31 [0.38] 0.22  0.05..
 *          above    ..no    no    yes   yes   yes   yes   yes   no    no
 *          rising   ..yes   yes   yes   no    no    no    yes   no    no
 *          pulse    ..no    no    no   [YES]  no    no   [YES]  no    no
 *                                        |                 |
 *                                    source A          source B
 *
 *      Note the second peak: the vehicle went from source A straight into source B
 *      without the reading ever dropping below the detection level. A detector that
 *      waited for the reading to cross the level upwards would have missed B
 *      completely - the two sources look like one long "above" stretch to it.
 *
 *      "Is it still rising?" is just another threshold device, this one fed by the
 *      change in the reading:  RISING = threshold(reading - previousReading, 0).
 *
 *   2. COUNTING CHAIN. Each pulse pushes the chain one device further:
 *
 *          pulse 1:  [ON ] [OFF] [OFF] [OFF]     1 source seen
 *          pulse 2:  [ON ] [ON ] [OFF] [OFF]     2 sources seen
 *          pulse 3:  [ON ] [ON ] [ON ] [OFF]     3 sources seen
 *          pulse 4:  [ON ] [ON ] [ON ] [ON ]     full -> the vehicle pauses
 *
 * There is no "if (count == 4)" anywhere. The counting falls out of the thresholds.
 */
public class Brain {

    private final ThresholdDevice[] devices;

    // What the peak detector remembers from one frame to the next.
    // previousReading starts impossibly high so that the very first frame can never
    // look like a peak: on frame one there is no "before" to compare against yet.
    private double previousReading = Double.MAX_VALUE;
    private boolean wasRising;

    public Brain() {
        devices = new ThresholdDevice[Parameters.CHAIN_LENGTH];
        for (int i = 0; i < devices.length; i++) {
            devices[i] = new ThresholdDevice(Parameters.counterThreshold(i));
        }
    }

    /**
     * Feed one frame's sensor reading in.
     * Returns true on the frame the LAST device fires - the chain is now full.
     */
    public boolean feed(double averageReading) {
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

        wasRising = rising;
        previousReading = averageReading;

        if (!pulse) {
            return false;
        }

        // Stage 2: let the pulse walk one step down the chain.
        //
        // The old states are read from a snapshot first. Without it, device 0 would
        // switch on and device 1 would immediately see it in the same frame, and the
        // pulse would ripple through the entire chain at once.
        boolean[] previous = new boolean[devices.length];
        for (int i = 0; i < devices.length; i++) {
            previous[i] = devices[i].active;
        }

        for (int i = 0; i < devices.length; i++) {
            int inputSum = 1;                    // the pulse itself
            if (i > 0 && previous[i - 1]) {
                inputSum += 1;                   // the device before this one
            }
            // Devices latch: once active, they stay active until reset().
            devices[i].active = previous[i] || devices[i].wouldFire(inputSum);
        }

        int last = devices.length - 1;
        return devices[last].active && !previous[last];
    }

    /**
     * Forget everything and start counting from zero.
     *
     * currentReading re-arms the peak detector. The vehicle stood still all through
     * the pause, so it is still sitting next to the source it just counted; starting
     * from "not rising" means driving away from it cannot produce another peak.
     */
    public void reset(double currentReading) {
        for (ThresholdDevice device : devices) {
            device.active = false;
        }
        previousReading = currentReading;
        wasRising = false;
    }

    /** How many sources have been counted so far. */
    public int count() {
        int active = 0;
        for (ThresholdDevice device : devices) {
            if (device.active) active++;
        }
        return active;
    }

    public int size() {
        return devices.length;
    }

    public boolean isActive(int index) {
        return devices[index].active;
    }
}
