package za.co.neroland.neroarcade.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.TestFiles;
import za.co.neroland.neroarcade.module.ArcadeModule;
import za.co.neroland.neroarcade.module.ModuleRegistry;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.TestModules;

/**
 * Every recipe, loot table and tag under {@code data/} (both the shared resources and the 26.3 overlay) parses,
 * and every {@code neroarcade:} id it names is registered: items and blocks against {@link ArcadeIds}, components
 * against {@link ArcadeIds#COMPONENTS}, module ids against the shipped modules. Every block drops itself on every
 * line, and every shipped chip can be crafted.
 */
class RecipeFilesTest {

    private static final List<String> RESOURCE_ROOTS = List.of("common/src/main/resources", "common/src/main/resources-26.3");

    @BeforeEach
    void registry() {
        TestModules.installShipped();
    }

    @AfterEach
    void tearDown() {
        TestModules.reset();
    }

    private static List<Path> jsonUnder(String relative) {
        List<Path> out = new ArrayList<>();
        for (String root : RESOURCE_ROOTS) {
            Path dir = TestFiles.repoRoot().resolve(root).resolve(relative);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(dir)) {
                files.filter(p -> p.toString().endsWith(".json")).sorted().forEach(out::add);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return out;
    }

    @Test
    void everyDataFileParsesAndNamesOnlyRegisteredIds() {
        List<Path> files = jsonUnder("data");
        assertTrue(files.size() >= 30, "found " + files.size());
        Set<String> problems = new TreeSet<>();
        for (Path file : files) {
            String where = file.toString().replace('\\', '/');
            if (where.contains("/neroquests/") || where.contains("/neroland_gates/")) {
                continue; // quest, gate and channel ids, checked by progression.QuestPackTest
            }
            if (where.contains("/arcade_replays/") || where.contains("/advancement/recipes/")) {
                continue; // cartridges (ShippedReplaysTest) and recipe unlocks (HighlightAndUnlockTest)
            }
            JsonElement json = JsonParser.parseString(TestFiles.read(file));
            walk(json, null, file.getFileName().toString(), problems);
        }
        assertTrue(problems.isEmpty(), "unregistered ids: " + problems);
    }

    private void walk(JsonElement element, String key, String file, Set<String> problems) {
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                if (entry.getKey().startsWith("neroarcade:")) {
                    String component = entry.getKey().substring("neroarcade:".length());
                    if (!ArcadeIds.COMPONENTS.contains(component)) {
                        problems.add(file + ": component " + entry.getKey());
                    }
                }
                walk(entry.getValue(), entry.getKey(), file, problems);
            }
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> walk(child, key, file, problems));
        } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String value = element.getAsString();
            if (!value.startsWith("neroarcade:")) {
                return;
            }
            String path = value.substring("neroarcade:".length());
            if ("neroarcade:module_id".equals(key)) {
                if (ModuleRegistry.get(Identifier.parse(value)).isEmpty()) {
                    problems.add(file + ": module " + value);
                }
            } else if ("random_sequence".equals(key)) {
                if (!path.startsWith("blocks/") || !ArcadeIds.BLOCKS.contains(path.substring("blocks/".length()))) {
                    problems.add(file + ": sequence " + value);
                }
            } else if ("block".equals(key) || "blocks".equals(key)) {
                if (!ArcadeIds.BLOCKS.contains(path)) {
                    problems.add(file + ": block " + value);
                }
            } else if (!ArcadeIds.ITEMS.contains(path)) {
                problems.add(file + ": item " + value);
            }
        }
    }

    @Test
    void everyBlockDropsItselfOnEveryLine() {
        for (String root : RESOURCE_ROOTS) {
            for (String block : ArcadeIds.BLOCKS) {
                Path table = TestFiles.repoRoot().resolve(root).resolve("data/neroarcade/loot_table/blocks/" + block + ".json");
                assertTrue(Files.isRegularFile(table), "missing " + table);
                String text = TestFiles.read(table);
                assertTrue(text.contains("\"neroarcade:" + block + "\""), table + " must drop the block");
                if (block.equals(ArcadeIds.ARCADE_CABINET) || block.equals(ArcadeIds.ARCADE_EXCHANGE)) {
                    assertTrue(text.contains("\"lower\""), table + ": only the lower half drops");
                }
            }
        }
    }

    @Test
    void everyShippedChipHasARecipeWithTheRightModel() {
        Path recipes = TestFiles.repoRoot().resolve("common/src/main/resources/data/neroarcade/recipe");
        for (ArcadeModule module : ModuleRegistry.all()) {
            Path file = recipes.resolve("module_chip_" + module.id().getPath().replace('/', '_') + ".json");
            assertTrue(Files.isRegularFile(file), "no recipe for chip " + module.id());
            JsonObject result = JsonParser.parseString(TestFiles.read(file)).getAsJsonObject().getAsJsonObject("result");
            assertEquals("neroarcade:module_chip", result.get("id").getAsString());
            JsonObject components = result.getAsJsonObject("components");
            assertEquals(module.id().toString(), components.get("neroarcade:module_id").getAsString());
            JsonObject cmd = components.getAsJsonObject("minecraft:custom_model_data");
            ModuleType type = module.type();
            assertEquals(type.key(), cmd.getAsJsonArray("strings").get(0).getAsString());
            assertEquals(type.colour, cmd.getAsJsonArray("colors").get(0).getAsInt());
        }
    }

    @Test
    void cartridgesAreUntradeable() {
        Path tag = TestFiles.repoRoot().resolve("common/src/main/resources/data/neroeconomy/tags/item/untradeable.json");
        assertTrue(TestFiles.read(tag).contains("\"neroarcade:cartridge\""), "a cartridge carries an author UUID");
    }
}
