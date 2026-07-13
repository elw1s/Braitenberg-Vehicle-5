/**
 * A Braitenberg threshold device.
 *
 * It sums its inputs and fires only if that sum reaches its threshold.
 * Once it has fired it latches: it stays active until it is switched off.
 * This single element is all Vehicle 5 is built from.
 */
public class ThresholdDevice {

    final int threshold;
    boolean active;

    public ThresholdDevice(int threshold) {
        this.threshold = threshold;
    }

    public boolean wouldFire(int inputSum) {
        return inputSum >= threshold;
    }
}
