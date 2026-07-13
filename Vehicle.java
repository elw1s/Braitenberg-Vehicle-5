import java.awt.Color;
import java.awt.Graphics2D;

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
    public Brain brain = new Brain();
    public String action = "";
    int pauseFramesLeft;

    public Vehicle(double x, double y, double heading) {
        this.x = x;
        this.y = y;
        this.heading = heading;
    }

    /**
     * Build a fresh brain. Needed when CHAIN_LENGTH changes, because the number of
     * threshold devices is fixed the moment a Brain is created.
     */
    public void rebuildBrain() {
        brain = new Brain();
        pauseFramesLeft = 0;
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
        Draw.disc(g, x, y, Parameters.BODY_RADIUS, new Color(60, 90, 220));

        // The sensors light up red while they are close enough to count a source.
        double[][] sensors = sensorPositions();
        Color color = readingAverage > Parameters.detectLevel() ? Color.RED : new Color(60, 200, 90);
        Draw.disc(g, sensors[0][0], sensors[0][1], Parameters.SENSOR_RADIUS, color);
        Draw.disc(g, sensors[1][0], sensors[1][1], Parameters.SENSOR_RADIUS, color);
    }
}
