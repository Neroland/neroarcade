package za.co.neroland.neroarcade;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/** Repository files the tests read: the merged lang file and the example cartridges. */
public final class TestFiles {

    private TestFiles() {
    }

    public static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("tools").resolve("lang"))
                    && Files.isDirectory(dir.resolve("common").resolve("src").resolve("main"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("repo root not found above " + Path.of("").toAbsolutePath());
    }

    /** The shipped {@code en_us.json} as key → text. */
    public static Map<String, String> lang() {
        Path file = repoRoot().resolve("common/src/main/resources/assets/neroarcade/lang/en_us.json");
        Map<String, String> out = new TreeMap<>();
        for (Map.Entry<String, JsonElement> entry : JsonParser.parseString(read(file)).getAsJsonObject().entrySet()) {
            out.put(entry.getKey(), entry.getValue().getAsString());
        }
        return out;
    }

    public static Path testResources() {
        return repoRoot().resolve("common/src/test/resources");
    }

    /** Every {@code cartridges/*.json}, sorted by name. */
    public static List<Path> cartridges() {
        try (Stream<Path> files = Files.list(testResources().resolve("cartridges"))) {
            return files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String cartridge(String name) {
        return read(testResources().resolve("cartridges").resolve(name));
    }
}
