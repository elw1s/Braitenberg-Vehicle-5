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

## The lights

The world is yours to rearrange, and it keeps running while you do it:

- **Drag** a light to move it. Watching the vehicle react to a light you are dragging is the fastest way to understand what it is doing.
- **Right-click** empty space to add a light, or a light to delete it.
- There is no rule about how many. Take them all away and the vehicle simply drives in a straight line for ever; crowd forty of them in and it still works.

Drag a light right on top of a sensor and the vehicle will whirl on the spot. That is not a bug - the reading is `SENSOR_GAIN / distance^2`, so at nearly zero distance it is enormous, and the steering goes with it. It cannot run away to infinity (`readSensor` caps a reading at `SENSOR_GAIN`), it never moves forward while doing it, and it rights itself the moment the light is moved. The same hard veer, in a milder form, is exactly what makes the vehicle graze a source and swing away rather than parking on it.

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
| `Reset` | put the vehicle back on the start line with an empty head |
| `Load` | replace the board with a preset, or with a circuit you saved |
| name + `Save` | write the circuit to `circuits/<name>.circuit` |

**Editing anything stops the world.** The vehicle freezes, a large `PAUSED` appears over it, and it stays that way until you press `Play`. You cannot judge a circuit you are halfway through building, and a vehicle that kept driving while you wired would be somewhere else, thinking with a half-finished brain, by the time you looked up. `Play` / `Pause` is also just a button - stop it whenever you like.

**Clicking a wire branches it.** A wire is not a thing a signal can come *out* of - it is only ever "some source, feeding some target". So clicking one selects its **source**, and the next thing you click gets fed by that same source as well. That is the junction dot in Braitenberg's drawings: one output feeding several inputs, which is exactly what the `pulse in` rail does.

**A device can be wired back into itself.** Click a wire coming out of a device, then click that same device: you get the little hook Braitenberg draws in fig. 10b. It can only be inhibitory (a device already latches itself on, so exciting itself would mean nothing), and what it does is stop the device latching: it fires for one frame and then switches itself off. There is also a `Inhibit itself` item in a device's right-click menu, which does the same thing.

Saving needs a name - the `Save` button stays dead until you type one.

Saved circuits are plain text and worth opening. `edge -1 0 1` is a wire from the pulse rail (`-1`) into device 0, excitatory (`1`); `-1` in the last column would make it inhibitory.

When you connect two devices you are asked which kind of wire you want:

- **Excitatory** (grey, arrow) - it pushes the target towards firing.
- **Inhibitory** (red, bar end) - while it is on, the target is forced **off**, and its latch is cleared. This is the only way to switch a latched device back off from inside the network, so it is how a circuit resets itself.

The **double ring** marks the OUTPUT device: when it switches on, the vehicle pauses. A circuit with no output can never pause the vehicle, and `Test` will tell you so.

A wire drawn **backwards** is feedback, and is arced over the top so you can see it. A feedback loop cannot hang: every device reads the state the network had *before* this frame, so a backwards wire reads last frame's value. That is what makes it a synchronous circuit.

## The rules the network runs on

All of this lives in `Circuit.step()`, and nowhere else:

1. **The clock is the pulse.** The network takes exactly one step each time a pulse arrives, and does *nothing at all* in between.
2. **One step of delay.** A device reads the state the network was in at the *previous* pulse, never the one being computed. A signal takes one pulse to travel one device.
3. **Excitation sums, inhibition vetoes.** Add up the excitatory wires whose source was on; the device fires if that reaches its threshold - unless an inhibitory wire's source was on, in which case it is off regardless.

In one line: `on = vetoed ? false : (excitation >= threshold)`

**Nothing latches.** A device is on exactly as long as its inputs say so, and the state at the last pulse is the only memory in the system. That is enough: the counter remembers because the pulse rail keeps re-firing every device it has already reached, and the ring remembers because the token it is passing along has to be *somewhere*. Devices that latched on for good could count - but they could never count twice.

This is what makes the book's figures work, and it is worth seeing why. A device with threshold 2 needs the pulse **and** another device *at the same moment*. Its neighbour's signal arrives one pulse later. So:

```
pulse 1:  the first device fires. Its output has not gone anywhere yet.
pulse 2:  the output arrives at the second device, which therefore sees the new
          pulse AND the first device together - the pair that reaches threshold 2.
          The first device's own inhibition arrives too, and switches it off.
pulse 3:  the second device's output reaches the third, which fires.
```

**Whenever the output device fires, the motors stop** - that is the pause, and it is true of every circuit you can build here.

## Why the plain counter only ever pauses once

The pause used to clear the circuit when it ended. It does not any more, and that one change is worth understanding, because it is the whole difference between the two kinds of network:

| | | |
| --- | --- | --- |
| **Ring counter** | clears itself | pauses at every 4th source, for ever |
| **Plain counter** | cannot clear itself | pauses **once**, and never again |

Both behave identically up to their first pause - and then they part company. The plain counter's devices are all on, the output is stuck on, and nothing in the circuit can switch them off, so the vehicle simply drives on for ever. That is not a bug. It is what a chain with no feedback in it *is*, and it is exactly why Braitenberg draws the feedback.

Clearing the circuit from outside hid all of this: it made the vehicle's pause quietly do the circuit's job, so a network that could reset itself and one that could not looked the same from the outside.

Note also what *cannot* work: making the vehicle stop for ever when the output fires. The circuit's clock **is** the vehicle's movement - stop the vehicle and it meets no more sources, so there are no more pulses, so the network never steps again and the output stays stuck on. Nothing could ever start it again. Every circuit would end in the same permanent freeze, which erases the very difference above.

## Braitenberg's figures 10a and 10b

**Fig. 10b works, and is in the `Load` menu.** The book captions it "a network of threshold devices that emits a pulse for every third pulse in a row in the input", and that is exactly what it does - press `Test` and it reports firing on pulses 3, 6, 9, 12.

It is a **ring**: three devices (1, 2, 2), and a single token that walks along them, one device per pulse.

```
pulse 1:  [ON ] [   ] [   ]
pulse 2:  [   ] [ON ] [   ]     device 1 has inhibited itself and stood down
pulse 3:  [   ] [   ] [ON ]     the output fires -> the motors stop
pulse 4:  [ON ] [   ] [   ]     nothing holds device 1 down now; it re-arms
```

The two hooks the book draws going back into the first device are what make it a ring rather than a chain that fills up once. The **self-inhibiting hook** makes device 1 fire once and hand the token on instead of sitting there on for good; the **wire from device 2 back to device 1** holds it down while the token is still travelling. Note that the *last* device deliberately does not hold device 1 down - if it did, the pulse after it fired would be spent re-arming rather than counting, and the ring would quietly become a divide-by-four.

`Ring counter` in the `Load` menu is the same thing for any length.

**Fig. 10a is not offered, on purpose.** Its caption - a signal on "a burst of 3 pulses, preceded and followed by a pause" - needs the network to measure *elapsed time*: what separates a burst from three lone pulses is how long the gaps are, and a plain threshold device has no clock in it. It can say "it is quiet right now", never "it has been quiet for a while". Wire its devices (1, 1, 2, 2, 1) up from the picture alone and you get a circuit that fires on the very first pulse; build it in the editor and press `Test` to see that for yourself. Making it work needs a *device* that can hold a signal for a few frames after its input drops - a change to `ThresholdDevice`, not to the wiring.

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
