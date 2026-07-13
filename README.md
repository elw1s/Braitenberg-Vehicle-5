# Braitenberg Vehicle 5

This project is a small Java Swing simulation of a Braitenberg vehicle. A vehicle moves around a 600x600 world, senses four light sources, and uses a chain of threshold devices as memory. When the chain fills up, the vehicle pauses briefly, resets, and starts counting again.

## What the project contains

- `Vehicle5.java` creates the window, world, animation loop, and on-screen readout.
- `Vehicle.java` handles sensing, steering, braking, movement, and pausing.
- `Brain.java` and `ThresholdDevice.java` implement the counting chain.
- `Parameters.java` stores every numeric constant used by the simulation.
- `ControlPanel.java` provides the live sliders on the right side of the window.
- `AnatomyView.java` shows where each parameter acts on the vehicle.
- `SpeedControl.java` adjusts simulation speed.
- `Theme.java` and `Draw.java` handle UI styling and drawing helpers.
- `Source.java` defines the light sources.

## Requirements

- Windows, macOS, or Linux with a Java runtime and JDK installed.
- Java 17 or newer is recommended.

## How to run

### Windows

Run `run.bat` from the project folder.

The script compiles all `.java` files into `out/` and then launches `Vehicle5`:

```bat
run.bat
```

### Manual build

If you want to run it yourself from a terminal, use:

```bash
javac -d out *.java
java -cp out Vehicle5
```

## Controls

- Use the sliders in the control panel to change the simulation live.
- `Reset to defaults` restores the original parameter values.
- The speed buttons at the bottom let you run the simulation faster or slower.
- The readout in the top-left corner shows the current sensor values, motor speeds, brain state, and how many sources have been counted.

## Simulation behavior

- The vehicle has two sensors mounted in front of its body.
- It reads light using an inverse-square falloff, so closer sources have a stronger effect.
- The vehicle slows down as the average reading increases.
- It steers away from the brighter sensor, which makes it graze sources instead of stopping on top of them.
- The brain counts source visits with a chain of threshold devices.
- When the last device fires, the vehicle pauses for a short time and then forgets the count.

## Parameter summary

The tunable values in `Parameters.java` are:

- `DETECT_DISTANCE` - how close a source must be before it counts.
- `CRUISE_SPEED` - the vehicle's top speed when no source is nearby.
- `STEER_GAIN` - how strongly the vehicle turns away from the brighter sensor.
- `SENSOR_SPACING` - the distance between the left and right sensors.
- `PAUSE_SECONDS` - how long the vehicle pauses after the chain fills.
- `CHAIN_LENGTH` - how many threshold devices are chained together.

## Notes

- Compiled `.class` files belong in `out/` and are generated, not source.
- Do not use `java Vehicle5.java`; this project uses multiple classes and should be compiled normally first.