/**
 * EVERY number in this project lives here. Nothing else has magic values.
 *
 * The six values in the TUNABLE section are the ones the control panel on the right
 * of the window changes while the simulation runs. Everything else is fixed.
 *
 * For each one: the formula it appears in, why it has this value, and an example.
 */
public class Parameters {

    // =====================================================================
    // FIXED - the world and the physics
    // =====================================================================

    public static final int SCREEN_WIDTH = 600;
    public static final int SCREEN_HEIGHT = 600;

    /** Frames per second. Every speed below is "per frame", so 60 fps is the clock. */
    public static final int FPS = 60;

    /**
     * How strongly a sensor responds to light. Used in Vehicle.readSensor():
     *
     *     reading = SENSOR_GAIN / distance^2          (added up over all sources)
     *
     * The inverse-square law, exactly like real light. Turned around, a reading tells
     * you a distance:
     *
     *     distance = sqrt(SENSOR_GAIN / reading)
     *
     * With SENSOR_GAIN = 500:
     *
     *     distance  |  22 px   41 px   50 px   100 px   200 px
     *     reading   |  1.00    0.30    0.20    0.05     0.0125
     *
     * The value 500 is not sacred. It simply puts the interesting readings in the
     * range 0..1, which is what makes the brake in Vehicle.update() readable:
     * a reading of 1.0 (= 22 px away) brings the vehicle to a complete stop.
     */
    public static final double SENSOR_GAIN = 500;

    public static final double BODY_RADIUS = 30;
    public static final double SENSOR_RADIUS = 10;

    /** The sensors sit right at the front of the body, like antennae. */
    public static final double SENSOR_FORWARD = BODY_RADIUS + SENSOR_RADIUS;  // 40 px

    /** How big a source is drawn. Cosmetic only - it does not affect the physics. */
    public static final double SOURCE_RADIUS = 24;

    // =====================================================================
    // TUNABLE - the control panel writes to these while the vehicle drives
    // =====================================================================

    /**
     * DETECTION DISTANCE, in pixels. How near a source has to be for a visit to count
     * at all. Used through detectLevel() in Brain.feed().
     *
     * The brain cannot measure distance - it only sees a reading. So the distance is
     * converted into a reading level with the inverse-square law:
     *
     *     detectLevel = SENSOR_GAIN / DETECT_DISTANCE^2 = 500 / 41^2 = 0.30
     *
     * A source is drawn with a radius of 24 px, so at 41 px the pulse fires just as
     * the sensors touch the glowing disc.
     *
     *     20 px -> level 1.25 : it has to bump into a source to count it
     *    120 px -> level 0.03 : it counts sources it only drives past
     *
     * This is a floor, not the trigger: the pulse itself happens at the peak of the
     * reading (the closest approach), not the moment this level is crossed. See Brain.
     */
    public static double DETECT_DISTANCE = 41;

    /**
     * TOP SPEED, in pixels per frame, when no source is near. Used in Vehicle.update():
     *
     *     speed = CRUISE_SPEED * max(0, 1 - averageReading)
     *
     * The vehicle BRAKES as the light gets stronger and stops dead once the reading
     * reaches 1.0, which is 22 px from a source (see SENSOR_GAIN):
     *
     *     averageReading   |  0.0    0.3    0.5    0.75   1.0
     *     speed (px/frame) |  2.0    1.4    1.0    0.5    0.0
     *
     * 2.0 px/frame at 60 fps = 120 px/s: it crosses the 600 px window in 5 seconds.
     */
    public static double CRUISE_SPEED = 2.0;

    /**
     * How hard the vehicle veers AWAY from a source. Used in Vehicle.update():
     *
     *     steer      = STEER_GAIN * (rightReading - leftReading)
     *     leftMotor  = speed - steer
     *     rightMotor = speed + steer
     *
     * If the right sensor reads more, steer is positive, the right wheel spins faster,
     * and the vehicle turns LEFT - away from the light. Turning away is deliberate: a
     * vehicle that turned towards a source would brake, park on it, and never reach
     * the other three.
     *
     * The differential drive turns the wheel difference into an angle:
     *
     *     turningRate = (leftMotor - rightMotor) / wheelBase = -steer / BODY_RADIUS
     *
     * Example: right reads 0.5, left reads 0.2  ->  steer = 0.3
     *          turningRate = -0.3 / 30 = -0.01 rad/frame = 0.57 deg/frame = 34 deg/s
     *
     * Set it to 0 and the vehicle drives straight through every source.
     */
    public static double STEER_GAIN = 1.0;

    /**
     * DISTANCE BETWEEN THE TWO SENSORS, in pixels. This is what lets the vehicle tell
     * LEFT from RIGHT at all: the steering signal is (rightReading - leftReading), and
     * two sensors sitting on top of each other would always read the same thing.
     *
     * Source 60 px to the right of the vehicle's nose:
     *
     *     spacing 80 px  ->  left sensor 100 px away, right 20 px  ->  big difference
     *     spacing 10 px  ->  left sensor  65 px away, right 55 px  ->  almost none
     */
    public static double SENSOR_SPACING = 80;

    /**
     * How long the vehicle stands still once the last threshold device has fired,
     * in seconds. Converted to frames by pauseFrames(): 1.5 s * 60 fps = 90 frames.
     * Then the chain is cleared and it starts counting again.
     */
    public static double PAUSE_SECONDS = 1.5;

    /**
     * How many threshold devices the PRESET counters are built with - four devices count to
     * four, one per source in the world.
     *
     * There is no slider for this any more, and it is not the length of the brain's chain:
     * the circuit editor at the bottom of the window owns the wiring now, and you add or
     * delete devices there. This is only the size of the circuits the "Load preset" menu
     * hands you to start from. See Circuit.counter().
     */
    public static final int CHAIN_LENGTH = 4;

    // =====================================================================
    // Derived values - never stored, always recomputed from the above
    // =====================================================================

    /** The reading level that means "a source is DETECT_DISTANCE px away". */
    public static double detectLevel() {
        return SENSOR_GAIN / (DETECT_DISTANCE * DETECT_DISTANCE);
    }

    /** PAUSE_SECONDS expressed in frames. */
    public static int pauseFrames() {
        return (int) Math.round(PAUSE_SECONDS * FPS);
    }

    public static void resetToDefaults() {
        DETECT_DISTANCE = 41;
        CRUISE_SPEED = 2.0;
        STEER_GAIN = 1.0;
        SENSOR_SPACING = 80;
        PAUSE_SECONDS = 1.5;
    }
}
