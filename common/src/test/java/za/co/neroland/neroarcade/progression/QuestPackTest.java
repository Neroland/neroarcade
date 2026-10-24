package za.co.neroland.neroarcade.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.TestFiles;
import za.co.neroland.neroarcade.registry.ArcadeIds;

/**
 * The NeroQuests pack NeroArcade ships as inert data ({@code data/neroarcade/neroquests/}, the "Insert Coin" chapter),
 * checked against the sibling pack contract as far as it can be without the quest mod (its {@code SiblingPacksTest}
 * runs the real validator once {@code "neroarcade"} is added there): well-formed JSON; chapter → quests that exist;
 * prerequisites inside the pack; objective and reward shapes the siblings use; {@code gate_open} only on NeroArcade's
 * own player gates; {@code custom_event} only on NeroArcade's channels and only in {@code "scope":"server"} quests;
 * {@code mod_hook} only on NeroArcade's own {@code hook/} gates; items are NeroArcade's or vanilla; every title and
 * description is a shipped lang key; every gate file has the player-gate shape.
 */
class QuestPackTest {

    private static final Path DATA = TestFiles.repoRoot().resolve("common/src/main/resources/data/neroarcade");
    private static final Pattern EVENT_SCOPE = Pattern.compile("[A-Za-z0-9_.:/#-]{1,64}");
    private static final Set<String> OBJECTIVES = Set.of("neroquests:craft_item", "neroquests:collect_item",
            "neroquests:gate_open", "neroquests:custom_event", "neroquests:quest_complete");
    private static final Set<String> REWARDS = Set.of("neroquests:xp", "neroquests:currency", "neroquests:item",
            "neroquests:mod_hook");

    private static JsonObject read(Path file) {
        JsonElement element = JsonParser.parseString(TestFiles.read(file));
        assertTrue(element.isJsonObject(), file + " is an object");
        return element.getAsJsonObject();
    }

    private static List<Path> json(Path dir) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
    }

    private static void ownItem(String id) {
        Identifier item = Identifier.parse(id);
        assertTrue(item.getNamespace().equals("minecraft")
                || item.getNamespace().equals("neroarcade") && ArcadeIds.ITEMS.contains(item.getPath()), id);
    }

    @Test
    void gatesHaveThePlayerShape() throws IOException {
        Set<String> gates = new HashSet<>();
        for (Path file : json(DATA.resolve("neroland_gates"))) {
            JsonObject gate = read(file);
            assertEquals("player", gate.get("scope").getAsString(), file.toString());
            assertFalse(gate.get("title").getAsString().isBlank());
            String rel = DATA.resolve("neroland_gates").relativize(file).toString().replace('\\', '/');
            gates.add("neroarcade:" + rel.substring(0, rel.length() - ".json".length()));
        }
        for (Identifier g : ArcadeMilestones.gates()) {
            assertTrue(gates.contains(g.toString()), "gate file for " + g);
        }
        assertTrue(gates.contains(QuestHooks.BLANK_CARTRIDGES.toString()));
    }

    @Test
    void questPackJsonIsWellFormed() throws IOException {
        Map<String, String> lang = TestFiles.lang();
        Path quests = DATA.resolve("neroquests/quests");
        List<Path> chapters = json(DATA.resolve("neroquests/chapters"));
        assertEquals(1, chapters.size());
        JsonObject chapter = read(chapters.get(0));
        assertTrue(lang.containsKey(chapter.get("title").getAsString()));
        ownItem(chapter.get("icon").getAsString());
        assertTrue(chapter.get("allow_server_scope").getAsBoolean(), "the chapter holds server-scope quests");
        Set<String> listed = new HashSet<>();
        for (JsonElement entry : chapter.getAsJsonArray("quests")) {
            JsonObject q = entry.getAsJsonObject();
            listed.add(q.get("quest").getAsString());
            q.get("x").getAsInt();
            q.get("y").getAsInt();
        }
        Set<String> files = new HashSet<>();
        for (Path file : json(quests)) {
            String rel = quests.relativize(file).toString().replace('\\', '/');
            files.add("neroarcade:" + rel.substring(0, rel.length() - ".json".length()));
        }
        assertEquals(files, listed, "the chapter lists exactly the pack's quests");

        Set<String> gates = new HashSet<>();
        ArcadeMilestones.gates().forEach(g -> gates.add(g.toString()));
        Set<String> channels = new HashSet<>();
        ArcadeMilestones.channels().forEach(c -> channels.add(c.toString()));

        for (Path file : json(quests)) {
            JsonObject q = read(file);
            String where = file.getFileName().toString();
            assertTrue(lang.containsKey(q.get("title").getAsString()), where + " title");
            assertTrue(lang.containsKey(q.get("description").getAsString()), where + " description");
            ownItem(q.get("icon").getAsString());
            String scope = q.get("scope").getAsString();
            assertTrue(scope.equals("player") || scope.equals("server"), where);
            for (JsonElement pre : q.getAsJsonArray("prerequisites")) {
                assertTrue(listed.contains(pre.getAsString()), where + " prerequisite " + pre);
            }
            JsonArray objectives = q.getAsJsonArray("objectives");
            assertFalse(objectives.isEmpty(), where);
            for (JsonElement e : objectives) {
                JsonObject o = e.getAsJsonObject();
                String type = o.get("type").getAsString();
                assertTrue(OBJECTIVES.contains(type), where + " " + type);
                switch (type) {
                    case "neroquests:gate_open" -> {
                        assertEquals("player", scope, where + ": a gate is a personal milestone");
                        assertTrue(gates.contains(o.get("gate").getAsString()), where);
                    }
                    case "neroquests:custom_event" -> {
                        assertEquals("server", scope, where + ": a channel can never credit one player");
                        assertTrue(channels.contains(o.get("channel").getAsString()), where);
                        assertEquals("rising", o.get("direction").getAsString());
                        assertEquals("world", o.get("audience").getAsString());
                        assertTrue(o.get("min_value").getAsLong() >= 1);
                        if (o.has("event_scope")) {
                            assertTrue(EVENT_SCOPE.matcher(o.get("event_scope").getAsString()).matches());
                        }
                    }
                    case "neroquests:craft_item", "neroquests:collect_item" -> {
                        ownItem(o.get("item").getAsString());
                        assertTrue(o.get("count").getAsInt() >= 1);
                    }
                    default -> {
                    }
                }
            }
            for (JsonElement e : q.getAsJsonArray("rewards")) {
                JsonObject r = e.getAsJsonObject();
                String type = r.get("type").getAsString();
                assertTrue(REWARDS.contains(type), where + " " + type);
                if (type.equals("neroquests:currency")) {
                    assertTrue(r.get("amount").getAsInt() <= 1000);
                }
                if (type.equals("neroquests:mod_hook")) {
                    assertTrue(r.get("gate").getAsString().startsWith("neroarcade:hook/"), where);
                    assertEquals("player", scope);
                }
            }
        }
    }
}
