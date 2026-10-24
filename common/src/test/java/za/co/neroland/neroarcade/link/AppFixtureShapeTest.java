package za.co.neroland.neroarcade.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.link.LinkActionResult;

import za.co.neroland.neroarcade.TestFiles;
import za.co.neroland.neroarcade.board.RecordOutcome;
import za.co.neroland.neroarcade.cabinet.BusMatchResult;

/**
 * The JSON the module sends against the companion app's fixtures (copied into {@code test/resources/link/}): every
 * key a fixture's {@code data} carries is present in ours with the same JSON type (optional keys may be absent;
 * {@code null} matches anything), and times use the same units ({@code asOf} and event {@code at} in milliseconds,
 * board and best {@code at} in seconds). The exported {@code gameDef} is the cartridge's own canonical form; the app
 * reads only {@code title}, {@code revision} and {@code slots} from it.
 */
class AppFixtureShapeTest {

    /** Keys a row may leave out (absent in some states, or only present when there is something to say). */
    private static final Set<String> OPTIONAL = Set.of("best", "rank", "name", "more", "_notify", "plays", "hearts",
            "at");

    private static JsonObject fixture(String name) {
        String text = TestFiles.read(TestFiles.testResources().resolve("link").resolve(name));
        JsonObject env = JsonParser.parseString(text).getAsJsonObject();
        assertTrue(env.get("ok").getAsBoolean());
        return env.getAsJsonObject("data");
    }

    private static FakeLinkContext seeded() {
        FakeLinkContext ctx = new FakeLinkContext();
        String pub = LinkFixtures.id(1);
        LinkFixtures.publish(ctx, LinkFixtures.game(pub, "Neon Dodge", LinkFixtures.ALICE, null), 1_791_000_000L);
        ctx.exchange.addDraft(LinkFixtures.ALICE, LinkFixtures.game(LinkFixtures.id(2), "Serpent Chase",
                LinkFixtures.ALICE, null), 1, 8);
        ctx.boards.put(pub, LinkFixtures.board(pub, LinkFixtures.KESSLER, 12400, "StarPilot", LinkFixtures.BOB, 10950,
                null, LinkFixtures.ALICE, 9120, null));
        String other = LinkFixtures.id(3);
        ctx.boards.put(other, LinkFixtures.board(other, LinkFixtures.ALICE, 2019, null));
        ctx.cabinets.put(LinkFixtures.ALICE, new ArcadeLinkContext.CabinetInfo("minecraft:overworld", 3, true, 0,
                "PLAYING", "versus", 3, pub, pub, "Neon Dodge"));
        return ctx;
    }

    @Test
    void library() {
        FakeLinkContext ctx = seeded();
        JsonObject ours = LinkFixtures.provider(ctx, LinkFixtures.pipeline(ctx)).snapshot(LinkFixtures.ALICE, "library",
                Map.of());
        JsonObject fx = fixture("neroarcade_library.json");
        assertShape(fx, ours, "library");
        JsonArray rows = ours.getAsJsonArray("cartridges");
        assertEquals(List.of("draft", "published", "best_only"),
                rows.asList().stream().map(r -> r.getAsJsonObject().get("state").getAsString()).toList());
        for (JsonElement fr : fx.getAsJsonArray("cartridges")) {
            String state = fr.getAsJsonObject().get("state").getAsString();
            JsonObject mine = rows.asList().stream().map(JsonElement::getAsJsonObject)
                    .filter(r -> r.get("state").getAsString().equals(state)).findFirst().orElseThrow();
            assertShape(fr.getAsJsonObject(), mine, "library." + state);
        }
        assertSeconds(rows.get(1).getAsJsonObject().getAsJsonObject("best").get("at"));
        assertMillis(ours.get("asOf"));
    }

    @Test
    void leaderboard() {
        FakeLinkContext ctx = seeded();
        JsonObject ours = LinkFixtures.provider(ctx, LinkFixtures.pipeline(ctx)).snapshot(LinkFixtures.ALICE,
                "leaderboard", Map.of("cartridge", LinkFixtures.id(1)));
        JsonObject fx = fixture("neroarcade_leaderboard.json");
        assertShape(fx, ours, "leaderboard");
        assertShape(fx.getAsJsonArray("top").get(0).getAsJsonObject(), ours.getAsJsonArray("top").get(0)
                .getAsJsonObject(), "leaderboard.top");
        assertShape(fx.getAsJsonObject("you"), ours.getAsJsonObject("you"), "leaderboard.you");
        assertSeconds(ours.getAsJsonArray("top").get(0).getAsJsonObject().get("at"));
    }

    @Test
    void cabinets() {
        FakeLinkContext ctx = seeded();
        JsonObject ours = LinkFixtures.provider(ctx, LinkFixtures.pipeline(ctx)).snapshot(LinkFixtures.ALICE,
                "cabinets", Map.of());
        JsonObject fx = fixture("neroarcade_cabinets.json");
        assertShape(fx, ours, "cabinets");
        assertShape(fx.getAsJsonArray("cabinets").get(0).getAsJsonObject(),
                ours.getAsJsonArray("cabinets").get(0).getAsJsonObject(), "cabinets[0]");
        assertEquals(fx.getAsJsonArray("cabinets").get(0).getAsJsonObject().get("label"),
                ours.getAsJsonArray("cabinets").get(0).getAsJsonObject().get("label"), "the D6 label, exactly");
    }

    @Test
    void events() {
        FakeLinkContext ctx = seeded();
        ArcadeEvents.Pipeline events = LinkFixtures.pipeline(ctx);
        String id = LinkFixtures.id(1);
        ctx.exchange.countDownload(1);
        ctx.hour = 18;
        events.tick(); // CARTRIDGE_DOWNLOADED for Alice
        events.onMatchEnd(new ArcadeEvents.MatchEndInfo(id, id, "Neon Dodge", "minecraft:overworld", 3,
                List.of(new BusMatchResult.SeatResult(LinkFixtures.ALICE, 0, 8650, 2, false),
                        new BusMatchResult.SeatResult(LinkFixtures.BOB, 1, 9000, 1, false)), "WIN",
                new RecordOutcome(0, 0, 1, 0, List.of(new RecordOutcome.Displaced(LinkFixtures.ALICE, 7))), false));
        JsonObject ours = LinkFixtures.provider(ctx, events).snapshot(LinkFixtures.ALICE, "events", Map.of());
        JsonObject fx = fixture("neroarcade_events.json");
        assertShape(fx, ours, "events");
        for (JsonElement fe : fx.getAsJsonArray("events")) {
            String type = fe.getAsJsonObject().getAsJsonObject("payload").get("type").getAsString();
            JsonObject mine = ours.getAsJsonArray("events").asList().stream().map(JsonElement::getAsJsonObject)
                    .filter(e -> e.getAsJsonObject("payload").get("type").getAsString().equals(type)).findFirst()
                    .orElseThrow(() -> new AssertionError("no " + type));
            assertShape(fe.getAsJsonObject(), mine, "events." + type);
            assertShape(fe.getAsJsonObject().getAsJsonObject("payload"), mine.getAsJsonObject("payload"),
                    "events." + type + ".payload");
            assertMillis(mine.get("at"));
            // the section row never carries the push copy (security review L2); the bus copy does
            assertTrue(!mine.getAsJsonObject("payload").has("_notify"), "events." + type + " echoes _notify");
            if (fe.getAsJsonObject().getAsJsonObject("payload").has("_notify")) {
                JsonObject bus = ctx.published.stream().map(ev -> ev.payload())
                        .filter(p -> p.get("type").getAsString().equals(type)).findFirst()
                        .orElseThrow(() -> new AssertionError("no published " + type));
                assertShape(fe.getAsJsonObject().getAsJsonObject("payload").getAsJsonObject("_notify"),
                        bus.getAsJsonObject("_notify"), "events." + type + "._notify");
            }
        }
    }

    @Test
    void exportAndImportResults() {
        FakeLinkContext ctx = seeded();
        LinkActionResult export = LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE, "export_cartridge",
                LinkFixtures.exportParams("e1", LinkFixtures.id(1), LinkFixtures.id(1)));
        assertTrue(export.ok());
        JsonObject fx = fixture("neroarcade_action_export_cartridge.json");
        assertShape(fx, export.state(), "export");
        JsonObject def = export.state().getAsJsonObject("gameDef");
        assertTrue(def.has("title") && def.has("revision") && def.has("slots"), def.toString());

        JsonObject imported = LinkFixtures.validGameDef();
        LinkActionResult result = LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE, "import_cartridge",
                LinkFixtures.importParams("i1", imported));
        assertTrue(result.ok(), LinkFixtures.describe(result));
        assertShape(fixture("neroarcade_action_import_cartridge.json"), result.state(), "import");
    }

    @Test
    void anExportReimports() {
        FakeLinkContext ctx = new FakeLinkContext();
        LinkActionResult first = LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE, "import_cartridge",
                LinkFixtures.importParams("i1", LinkFixtures.validGameDef()));
        String id = first.state().get("draftId").getAsString();
        LinkActionResult export = LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE, "export_cartridge",
                LinkFixtures.exportParams("e1", id, id));
        assertTrue(export.ok(), LinkFixtures.describe(export));
        JsonObject def = export.state().getAsJsonObject("gameDef");
        def.remove("authorUuid"); // what the app strips into a .nac file
        LinkActionResult again = LinkFixtures.handler(ctx).execute(LinkFixtures.BOB, "import_cartridge",
                LinkFixtures.importParams("i2", def));
        assertTrue(again.ok(), "a .nac round trip imports: " + LinkFixtures.describe(again));
    }

    // --- shape ------------------------------------------------------------------------------------

    private static void assertShape(JsonObject fixture, JsonObject ours, String path) {
        for (Map.Entry<String, JsonElement> e : fixture.entrySet()) {
            String key = e.getKey();
            JsonElement theirs = e.getValue();
            JsonElement mine = ours.get(key);
            if (mine == null) {
                if (OPTIONAL.contains(key)) {
                    continue;
                }
                fail(path + "." + key + " is missing from " + ours);
            }
            if (theirs.isJsonNull() || mine.isJsonNull()) {
                continue;
            }
            assertEquals(kind(theirs), kind(mine), path + "." + key + " type");
            if (theirs.isJsonPrimitive()) {
                assertEquals(theirs.getAsJsonPrimitive().isNumber(), mine.getAsJsonPrimitive().isNumber(), path + "." + key);
                assertEquals(theirs.getAsJsonPrimitive().isBoolean(), mine.getAsJsonPrimitive().isBoolean(),
                        path + "." + key);
            }
            if (theirs.isJsonObject() && !"gameDef".equals(key) && !"payload".equals(key)) {
                assertShape(theirs.getAsJsonObject(), mine.getAsJsonObject(), path + "." + key);
            }
        }
    }

    private static String kind(JsonElement el) {
        return el.isJsonObject() ? "object" : el.isJsonArray() ? "array" : el.isJsonPrimitive() ? "primitive" : "null";
    }

    private static void assertMillis(JsonElement el) {
        assertTrue(el.getAsLong() >= 100_000_000_000L, "milliseconds: " + el);
    }

    private static void assertSeconds(JsonElement el) {
        assertTrue(el.getAsLong() < 100_000_000_000L, "seconds: " + el);
    }
}
