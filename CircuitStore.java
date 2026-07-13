import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Saving and loading circuits, as plain text you can open and read.
 *
 * A saved circuit is one file in the "circuits" folder next to the project, and it is exactly
 * the data in Circuit - the devices, the wires, and which device is the output:
 *
 *     name    my counter
 *     device  1  0.14  0.50          threshold, then where it sits on the board (0..1)
 *     device  2  0.38  0.50
 *     edge    -1  0   1              from, to, weight.  from -1 is the pulse rail (Circuit.INPUT)
 *     edge     0  1   1              weight  1 is excitatory, -1 is inhibitory
 *     edge     1  0  -1
 *     output   1
 *
 * No Swing here either. This file only knows about Circuit.
 */
public class CircuitStore {

    private static final Path FOLDER = Paths.get("circuits");
    private static final String SUFFIX = ".circuit";

    /** The names of every saved circuit, alphabetically. Empty if nothing has been saved yet. */
    public static List<String> list() {
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(FOLDER)) {
            return names;
        }
        try (var files = Files.list(FOLDER)) {
            files.map(path -> path.getFileName().toString())
                 .filter(file -> file.endsWith(SUFFIX))
                 .map(file -> file.substring(0, file.length() - SUFFIX.length()))
                 .forEach(names::add);
        } catch (IOException ignored) {
            // An unreadable folder is the same as an empty one, as far as the menu is concerned.
        }
        Collections.sort(names);
        return names;
    }

    public static void save(String name, Circuit circuit) throws IOException {
        Files.createDirectories(FOLDER);

        StringBuilder text = new StringBuilder();
        text.append("name ").append(name).append('\n');

        // Locale.ROOT, not the machine's locale. On a German or Turkish Windows, "%.4f" writes
        // "0,1400" with a comma - and Double.parseDouble only ever reads a dot, so the file
        // would save fine and then fail to load. A saved file has to mean the same thing on
        // every machine, so it is written in one fixed format.
        for (int i = 0; i < circuit.size(); i++) {
            ThresholdDevice device = circuit.device(i);
            text.append(String.format(Locale.ROOT, "device %d %.4f %.4f%n",
                    device.threshold, device.x, device.y));
        }
        for (Circuit.Edge edge : circuit.edges()) {
            text.append(String.format(Locale.ROOT, "edge %d %d %d%n",
                    edge.from, edge.to, edge.weight));
        }
        text.append("output ").append(circuit.output()).append('\n');

        Files.writeString(fileFor(name), text.toString());
    }

    public static Circuit load(String name) throws IOException {
        Circuit circuit = new Circuit();
        circuit.name = name;

        for (String line : Files.readAllLines(fileFor(name))) {
            String[] word = line.trim().split("\\s+");
            if (word.length == 0 || word[0].isEmpty()) {
                continue;
            }
            switch (word[0]) {
                case "device":
                    circuit.addDevice(Integer.parseInt(word[1]),
                            Double.parseDouble(word[2]), Double.parseDouble(word[3]));
                    break;
                case "edge":
                    circuit.connect(Integer.parseInt(word[1]),
                            Integer.parseInt(word[2]), Integer.parseInt(word[3]));
                    break;
                case "output":
                    circuit.setOutput(Integer.parseInt(word[1]));
                    break;
                default:
                    break;   // "name", and anything else, is just a comment as far as loading goes
            }
        }
        return circuit;
    }

    /**
     * A save name becomes a file name, so it must not be able to escape the folder or carry
     * anything the file system will choke on. Everything that is not a letter, a digit, a space,
     * a dash or an underscore is dropped - "../../etc/passwd" saves as "etcpasswd".
     */
    public static String cleanName(String name) {
        String cleaned = name.trim().replaceAll("[^A-Za-z0-9 _-]", "");
        return cleaned.length() > 40 ? cleaned.substring(0, 40).trim() : cleaned;
    }

    private static Path fileFor(String name) {
        return FOLDER.resolve(cleanName(name) + SUFFIX);
    }
}
