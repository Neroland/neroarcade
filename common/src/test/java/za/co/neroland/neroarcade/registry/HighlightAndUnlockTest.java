package za.co.neroland.neroarcade.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.TestFiles;

/**
 * Two ecosystem conventions (005a P2, P9): every NeroArcade item sits in exactly one Core item-highlight tag
 * ({@code neroland:highlight/*}: machines, tools, upgrades, materials), and every recipe has a recipe-book unlock
 * advancement that rewards exactly it.
 */
class HighlightAndUnlockTest {

    private static Path data() {
        return TestFiles.repoRoot().resolve("common/src/main/resources/data");
    }

    @Test
    void everyItemIsInExactlyOneHighlightTag() throws IOException {
        Map<String, List<String>> seen = new HashMap<>();
        Path dir = data().resolve("neroland/tags/item/highlight");
        for (String tag : List.of("machines", "tools", "upgrades", "materials")) {
            Path file = dir.resolve(tag + ".json");
            assertTrue(Files.isRegularFile(file), file.toString());
            JsonObject json = JsonParser.parseString(TestFiles.read(file)).getAsJsonObject();
            assertEquals(false, json.get("replace").getAsBoolean(), tag + ": never replaces Core's tag");
            for (JsonElement value : json.getAsJsonArray("values")) {
                JsonObject entry = value.getAsJsonObject();
                assertEquals(false, entry.get("required").getAsBoolean(), "optional entries, like the siblings");
                seen.computeIfAbsent(entry.get("id").getAsString(), k -> new ArrayList<>()).add(tag);
            }
        }
        for (String item : ArcadeIds.ITEMS) {
            List<String> tags = seen.getOrDefault("neroarcade:" + item, List.of());
            assertEquals(1, tags.size(), item + " highlight tags: " + tags);
        }
        assertEquals(ArcadeIds.ITEMS.size(), seen.size(), "no stray ids: " + seen.keySet());
        assertEquals(List.of("upgrades"), seen.get("neroarcade:module_chip"));
    }

    @Test
    void everyRecipeHasAnUnlockAdvancement() throws IOException {
        Path recipes = data().resolve("neroarcade/recipe");
        Path unlocks = data().resolve("neroarcade/advancement/recipes");
        Map<String, String> rewardedBy = new HashMap<>();
        try (Stream<Path> files = Files.walk(unlocks)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject json = JsonParser.parseString(TestFiles.read(file)).getAsJsonObject();
                assertEquals("minecraft:recipes/root", json.get("parent").getAsString());
                for (JsonElement r : json.getAsJsonObject("rewards").getAsJsonArray("recipes")) {
                    rewardedBy.put(r.getAsString(), file.getFileName().toString());
                }
            }
        }
        int count = 0;
        try (Stream<Path> files = Files.list(recipes)) {
            for (Path recipe : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String id = "neroarcade:" + recipe.getFileName().toString().replace(".json", "");
                assertEquals(recipe.getFileName().toString(), rewardedBy.get(id), id + " has no unlock");
                count++;
            }
        }
        assertEquals(count, rewardedBy.size(), "no unlock for a recipe that does not exist");
    }
}
