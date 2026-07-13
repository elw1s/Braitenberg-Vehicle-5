import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Locale;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * BRAITENBERG VEHICLE 5 - "Logic"
 *
 * A vehicle drives among four light sources. It brakes and veers away whenever it
 * passes one. A chain of threshold devices counts the sources it has visited; when
 * the fourth one fires, the vehicle stops for a moment, forgets, and starts over.
 *
 * Run it with:   run.bat          (or: javac -d out *.java  &&  java -cp out Vehicle5)
 *
 * Do NOT use "java Vehicle5.java". That single-file launcher compiles only this one
 * file and loads the other classes with a different class loader, which fails with
 * an IllegalAccessError. The project is more than one file now.
 *
 * THE SIMULATION - read these to understand Vehicle 5:
 *
 *     Parameters.java       every number in the project, with its formula and examples
 *     ThresholdDevice.java  the one building block: fires when its inputs reach a threshold
 *     Brain.java            threshold devices chained into a counter -> the vehicle's MEMORY
 *     Vehicle.java          the body: sensors -> brain -> motors -> movement
 *     Source.java           a light source
 *     Vehicle5.java         this file: the window, the loop, the numbers on screen
 *
 * THE INTERFACE - no physics in here, only knobs and paint:
 *
 *     ControlPanel.java     the sliders on the right; they write straight into Parameters
 *     AnatomyView.java      the drawing that shows WHERE each parameter acts
 *     SpeedControl.java     the 1x/2x/4x/8x buttons at the bottom
 *     Theme.java            colours and fonts
 *     Draw.java             a helper for drawing circles
 */
public class Vehicle5 extends JPanel implements ActionListener {

    // The world. Four sources, one for each threshold device in the counting chain.
    Source[] sources = {
        new Source(300, 300, new Color(255, 80, 80)),
        new Source(120, 180, new Color(255, 190, 60)),
        new Source(480, 250, new Color(90, 210, 120)),
        new Source(170, 470, new Color(120, 160, 255)),
    };

    Vehicle vehicle = new Vehicle(300, 500, Math.toRadians(-35));

    final SpeedControl speedControl = new SpeedControl();

    /** The sliders write into Parameters; a new chain length needs a new brain. */
    final ControlPanel controlPanel = new ControlPanel(() -> vehicle.rebuildBrain());

    public static void main(String[] args) {
        Vehicle5 canvas = new Vehicle5();

        // The canvas keeps its exact 600x600 size (the vehicle wraps around at those
        // edges), so it is centred inside a filler panel rather than stretched.
        JPanel stage = new JPanel(new GridBagLayout());
        stage.setBackground(Theme.BG);
        stage.add(canvas);

        JFrame frame = new JFrame("Braitenberg Vehicle 5");
        frame.setLayout(new BorderLayout());
        frame.add(stage, BorderLayout.CENTER);              // the world, never covered up
        frame.add(canvas.controlPanel, BorderLayout.EAST);  // the knobs, beside it
        frame.add(canvas.speedControl, BorderLayout.SOUTH);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    Vehicle5() {
        setPreferredSize(new Dimension(Parameters.SCREEN_WIDTH, Parameters.SCREEN_HEIGHT));
        setBackground(Color.WHITE);

        // This timer is the "while running:" loop: it fires FPS times per second.
        new Timer(1000 / Parameters.FPS, this).start();
    }

    /** One frame: advance the simulation, then redraw. */
    @Override
    public void actionPerformed(ActionEvent event) {
        for (int step = 0; step < speedControl.stepsPerFrame(); step++) {
            vehicle.update(sources);
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (Source source : sources) {
            source.draw(g);
        }
        vehicle.draw(g);
        drawReadout(g);
    }

    /** The live numbers in the corner: what it senses, what it does, what it remembers. */
    void drawReadout(Graphics2D g) {
        StringBuilder chain = new StringBuilder();
        for (int i = 0; i < vehicle.brain.size(); i++) {
            chain.append(vehicle.brain.isActive(i) ? "[ON ] " : "[OFF] ");
        }

        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        g.drawString(String.format(Locale.US, "sensors  left=%.2f  right=%.2f  (detect above %.2f)",
                vehicle.readingLeft, vehicle.readingRight, Parameters.detectLevel()), 10, 20);
        g.drawString(String.format(Locale.US, "motors   left=%.2f  right=%.2f",
                vehicle.leftMotorSpeed, vehicle.rightMotorSpeed), 10, 40);
        g.drawString("brain    " + chain, 10, 60);
        g.drawString(String.format(Locale.US, "%-7s  sources counted = %d / %d",
                vehicle.action, vehicle.brain.count(), vehicle.brain.size()), 10, 80);
    }
}
