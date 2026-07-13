/**
 * A Braitenberg threshold device: the one building block the whole brain is made of.
 *
 * It sums the wires coming into it and fires only if that sum reaches its threshold.
 * Every device here LATCHES: once it fires it stays on, and the only thing that can switch
 * it off again is an inhibitory wire (see Circuit). That latch is what turns a circuit of
 * these into a MEMORY rather than a reflex - it is the whole point of Braitenberg's chapter 5.
 *
 * threshold is editable, because the editor lets you click a device and change it.
 * x and y are 0..1 - a fraction of the board, not pixels - so the circuit keeps its shape
 * when the window is resized or goes fullscreen. They say nothing about behaviour.
 */
public class ThresholdDevice {

    public int threshold;
    public double x, y;

    public ThresholdDevice(int threshold, double x, double y) {
        this.threshold = threshold;
        this.x = x;
        this.y = y;
    }

    public boolean wouldFire(int inputSum) {
        return inputSum >= threshold;
    }
}
