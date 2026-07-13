import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/**
 * The BODY of Vehicle 5: two sensors, two motors, and the wheels that move it.
 * The thinking happens in Brain; this file only senses and drives.
 *
 * One frame, in four steps:
 *
 *     sensors  ->  brain (counts)  ->  motors  ->  movement
 *
 * Every number used here is explained in Parameters.java.
 */
public class Vehicle {

    // Where it is and which way it points
    public double x, y, heading;

    // What it sensed and what the motors did on the last frame (also used by the display)
    public double readingLeft, readingRight, readingAverage;
    public double leftMotorSpeed, rightMotorSpeed;

    // Its memory
    public Brain brain;
    public String action = "";
    int pauseFramesLeft;

    // Where each pulse was counted, in world coordinates. One mark per source visited since
    // the circuit last cleared, so the trail shows what the brain is currently remembering.
    private final List<double[]> detectMarks = new ArrayList<>();

    public Vehicle(double x, double y, double heading, Circuit circuit) {
        this.x = x;
        this.y = y;
        this.heading = heading;
        this.brain = new Brain(circuit);
    }

    /**
     * Start the brain over on the SAME circuit. The editor hands the brain a circuit and then
     * keeps editing that very object, so this must not swap the circuit out - it only throws
     * away what the network currently remembers, which is what you want after the wiring
     * underneath it changed.
     */
    public void rebuildBrain() {
        brain = new Brain(brain.circuit());
        pauseFramesLeft = 0;
        detectMarks.clear();
    }

    public void update(Source[] sources) {
        // -------------------------------------------------- 1. SENSE
        double[][] sensors = sensorPositions();
        readingLeft = readSensor(sensors[0][0], sensors[0][1], sources);
        readingRight = readSensor(sensors[1][0], sensors[1][1], sources);
        readingAverage = (readingLeft + readingRight) / 2;

        // -------------------------------------------------- 2. PAUSE?
        // The chain filled up earlier, so we are standing still. Count down, then forget.
        if (pauseFramesLeft > 0) {
            pauseFramesLeft--;
            if (pauseFramesLeft == 0) {
                brain.reset(readingAverage);
                detectMarks.clear();
            }
            leftMotorSpeed = 0;
            rightMotorSpeed = 0;
            action = "PAUSED";
            return;
        }

        // -------------------------------------------------- 3. THINK
        // The brain turns the reading into pulses and counts them. It says "full"
        // on the single frame the last threshold device fires.
        boolean chainFull = brain.feed(readingAverage);
        if (brain.justPulsed()) {
            detectMarks.add(new double[] { x, y });
        }
        if (chainFull) {
            pauseFramesLeft = Parameters.pauseFrames();
        }

        // -------------------------------------------------- 4. DRIVE
        // Brake as the light gets stronger, and steer AWAY from the brighter sensor,
        // so the vehicle grazes a source instead of parking on it.
        double speed = Parameters.CRUISE_SPEED * Math.max(0, 1 - readingAverage);
        double steer = Parameters.STEER_GAIN * (readingRight - readingLeft);

        leftMotorSpeed = speed - steer;
        rightMotorSpeed = speed + steer;
        action = "DRIVING";

        // Differential drive: the two wheel speeds become one forward speed and one
        // turning rate. Equal speeds -> straight line. Faster left wheel -> turns right.
        //
        //     forwardSpeed = (left + right) / 2
        //     turningRate  = (left - right) / wheelBase        [radians per frame]
        double wheelBase = 2 * Parameters.BODY_RADIUS;
        double forwardSpeed = (leftMotorSpeed + rightMotorSpeed) / 2;
        double turningRate = (leftMotorSpeed - rightMotorSpeed) / wheelBase;

        heading += turningRate;
        x += forwardSpeed * Math.cos(heading);
        y += forwardSpeed * Math.sin(heading);

        // Leave one edge of the screen, come back in on the other.
        // (Java keeps the sign of a negative operand, so a plain % is not enough.)
        x = (x % Parameters.SCREEN_WIDTH + Parameters.SCREEN_WIDTH) % Parameters.SCREEN_WIDTH;
        y = (y % Parameters.SCREEN_HEIGHT + Parameters.SCREEN_HEIGHT) % Parameters.SCREEN_HEIGHT;
    }

    /**
     * Where the two sensors are, in world coordinates: SENSOR_FORWARD in front of the
     * centre, and half of SENSOR_SPACING to either side.
     * Returns { {leftX, leftY}, {rightX, rightY} }.
     *
     * On screen y grows DOWNWARD, so turning the forward vector by +90 degrees points
     * to the vehicle's RIGHT, not its left.
     */
    double[][] sensorPositions() {
        double forwardX = Math.cos(heading);
        double forwardY = Math.sin(heading);
        double rightX = -forwardY;  // forward, rotated by +90 degrees
        double rightY = forwardX;

        double half = Parameters.SENSOR_SPACING / 2;
        double ahead = Parameters.SENSOR_FORWARD;

        return new double[][] {
            { x + forwardX * ahead - rightX * half, y + forwardY * ahead - rightY * half },
            { x + forwardX * ahead + rightX * half, y + forwardY * ahead + rightY * half },
        };
    }

    /**
     * What one sensor reads at a point: the inverse-square law, added up over every
     * source, because light adds up.
     *
     *     reading = sum over sources of  SENSOR_GAIN / distance^2
     *
     * The max(..., 1.0) only stops a division by zero if the sensor lands exactly on
     * a source.
     */
    double readSensor(double pointX, double pointY, Source[] sources) {
        double total = 0;
        for (Source source : sources) {
            double dx = pointX - source.x;
            double dy = pointY - source.y;
            total += 1.0 / Math.max(dx * dx + dy * dy, 1.0);
        }
        return Parameters.SENSOR_GAIN * total;
    }

    public void draw(Graphics2D g) {
        // Where each source was counted: small dark dots, cleared with the brain.
        for (double[] mark : detectMarks) {
            Draw.disc(g, mark[0], mark[1], 4, Color.BLACK);
        }

        Draw.disc(g, x, y, Parameters.BODY_RADIUS, new Color(60, 90, 220));

        // Each sensor is colored by its OWN reading, not the average, so you can see
        // which side is more stimulated.
        double[][] sensors = sensorPositions();
        Draw.disc(g, sensors[0][0], sensors[0][1], Parameters.SENSOR_RADIUS, sensorColor(readingLeft));
        Draw.disc(g, sensors[1][0], sensors[1][1], Parameters.SENSOR_RADIUS, sensorColor(readingRight));
    }

    /**
     * Sensor color by how strongly it is stimulated, in fractions of detectLevel() -
     * the same level Brain uses to decide whether a visit counts at all. All shades
     * of green: pale when the source is far, darkening as it gets closer.
     *
     *     palest green : reading < 25% of detectLevel
     *     light green  : reading < 50% of detectLevel
     *     medium green : reading < 100% of detectLevel
     *     dark green   : reading >= detectLevel  (this is what Brain would count)
     */
    private Color sensorColor(double reading) {
        double level = Parameters.detectLevel();
        if (reading >= level) return new Color(20, 100, 45);
        if (reading >= level * 0.5) return new Color(90, 170, 100);
        if (reading >= level * 0.25) return new Color(170, 220, 170);
        return new Color(220, 245, 220);
    }
}
