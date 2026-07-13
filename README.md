# Braitenberg Vehicle 5

A Java Swing simulation of a Braitenberg vehicle. The vehicle moves around a 600x600 world, senses four light sources, and uses a network of threshold devices as memory. When that network fires, the vehicle pauses briefly, forgets, and starts counting again.

The network is not hard-coded. There is a circuit editor under the world, and you can rewire the vehicle's brain while it drives.

## What the project contains

The simulation - read these in this order to understand Vehicle 5:

- `Parameters.java` - every number used anywhere, with the formula it appears in.
- `ThresholdDevice.java` - the one building block: it fires when its inputs reach a threshold.
- `Circuit.java` - threshold devices wired into a network. **The whole logic of the brain is in this one file**, including the rules for stepping it. No Swing, no drawing.
- `Brain.java` - the peak detector that turns a light reading into pulses, feeding the circuit.
- `Vehicle.java` - the body: sensing, steering, braking, movement, pausing.
- `Source.java` - a light source.
- `Vehicle5.java` - the window, the animation loop, the on-screen readout.

The interface - no logic in here, only knobs and paint:

- `CircuitEditor.java` - the board under the world: draws the brain live, and lets you rewire it.
- `ControlPanel.java` - the sliders on the right.
- `AnatomyView.java` - shows where each parameter acts on the vehicle.
- `SpeedControl.java` - simulation speed, and the fullscreen toggle.
- `Theme.java`, `Draw.java` - styling and drawing helpers.

## Requirements

- Windows, macOS, or Linux with a JDK installed. Java 17 or newer is recommended.

## How to run

On Windows, run `run.bat` from the project folder. Otherwise:

```bash
javac -d out *.java
java -cp out Vehicle5
```

Do not use `java Vehicle5.java`. That single-file launcher compiles only that one file and loads the rest with a different class loader, which fails with an `IllegalAccessError`.

## How the vehicle behaves

- Two sensors sit at the front of the body.
- Each reads light with an inverse-square falloff, summed over every source, so closer sources dominate.
- The vehicle slows down as the average reading rises, and stops dead 22 px from a source.
- It steers *away* from the brighter sensor. That is deliberate: a vehicle that turned towards a source would brake, park on it, and never reach the other three. Turning away makes it graze them.
- A peak detector turns "I am next to a source" into a single **pulse** at the closest approach. That pulse drives the circuit.
- When the circuit's output device fires, the vehicle pauses, then forgets and starts over.

**At the default settings the first source takes about 20 seconds to reach.** Press 2x/4x/8x - the speed buttons only run more simulation steps per frame, they do not change any physics.

## The circuit editor

The board under the world is the vehicle's actual brain, drawn live and editable while it drives.

| Do this | To get this |
| --- | --- |
| `+ Device` | add a threshold device |
| click a device | select it, then click a second one to run a wire between them |
| **click a wire** | select the signal that wire carries, and branch it somewhere else too |
| click the `pulse in` rail | wire *from* the incoming pulse |
| click empty space | cancel a half-drawn wire |
| drag a device | move it |
| right-click a device | change its threshold, make it the OUTPUT, or delete it |
| right-click a wire | flip it between excitatory and inhibitory, or delete it |
| `Test` | run the circuit on 20 pulses and say which ones it fires on, in the bar |
| `Load` | replace the board with a preset, or with a circuit you saved |
| name + `Save` | write the circuit to `circuits/<name>.circuit` |

**Clicking a wire branches it.** A wire is not a thing a signal can come *out* of - it is only ever "some source, feeding some target". So clicking one selects its **source**, and the next thing you click gets fed by that same source as well. That is the junction dot in Braitenberg's drawings: one output feeding several inputs, which is exactly what the `pulse in` rail does.

Saved circuits are plain text and worth opening. `edge -1 0 1` is a wire from the pulse rail (`-1`) into device 0, excitatory (`1`); `-1` in the last column would make it inhibitory.

When you connect two devices you are asked which kind of wire you want:

- **Excitatory** (grey, arrow) - it pushes the target towards firing.
- **Inhibitory** (red, bar end) - while it is on, the target is forced **off**, and its latch is cleared. This is the only way to switch a latched device back off from inside the network, so it is how a circuit resets itself.

The **double ring** marks the OUTPUT device: when it switches on, the vehicle pauses. A circuit with no output can never pause the vehicle, and `Test` will tell you so.

A wire drawn **backwards** is feedback, and is arced over the top so you can see it. A feedback loop cannot hang: every device reads the state the network had *before* this frame, so a backwards wire reads last frame's value. That is what makes it a synchronous circuit.

## The rules the network runs on

All of this lives in `Circuit.step()`, and nowhere else:

1. **The clock is the frame.** Every device is evaluated once per frame, not once per pulse. The pulse is just a wire that happens to be on during a pulse frame. Stepping every frame is what lets a circuit react to the *gaps* between pulses - which is where inhibition does its work.
2. **Snapshot.** Every device reads the state from before this frame. Nothing sees a neighbour's new value in the same step.
3. **Excitation.** Add up the excitatory wires whose source is on. The device fires if that sum reaches its threshold.
4. **Inhibition is a veto.** Any active inhibitory wire forces the device off, whatever the excitation says.
5. **Devices latch.** Once on, a device stays on until something inhibits it.

In one line: `on = vetoed ? false : (was on || excitation >= threshold)`

## A note on Braitenberg's figure 10a

The book's fig. 10a is captioned as firing on "a burst of 3 pulses, preceded and followed by a pause". Its devices (1, 1, 2, 2, 1) are easy to read off the drawing, but it is **not** offered as a preset, because the caption needs the network to measure *elapsed time*: what separates a burst from three lone pulses is how long the gaps are, and a plain threshold device has no clock in it. It can say "it is quiet right now", never "it has been quiet for a while".

Wire it up from the picture alone and you get a circuit that fires on the very first pulse. Build it in the editor and press `Test` to see that for yourself. Making it work needs a *device* that can hold a signal for a few frames after its input drops - a change to `ThresholdDevice`, not to the wiring.

## Tunable parameters

The sliders on the right write straight into `Parameters.java`:

- `DETECT_DISTANCE` - how close a source must be before a visit counts at all.
- `CRUISE_SPEED` - top speed when no source is near.
- `STEER_GAIN` - how hard it veers away from the brighter sensor.
- `SENSOR_SPACING` - the distance between the two sensors; the only reason it can tell left from right.
- `PAUSE_SECONDS` - how long it stands still once the circuit fires.

The number of threshold devices used to be a slider. It is not a parameter any more - the circuit editor owns the wiring, and you add and delete devices there.

## Notes

- Compiled `.class` files belong in `out/` and are generated, not source.
- The source is ASCII only, so it compiles the same whatever default encoding `javac` picks up.
