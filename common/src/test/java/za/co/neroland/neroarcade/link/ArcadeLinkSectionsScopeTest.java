package za.co.neroland.neroarcade.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.board.RecordOutcome;
import za.co.neroland.neroarcade.cabinet.BusMatchResult;

/**
 * 007 §8.1–8.2 / §9: with two players seeded, nothing of B's appears in any of A's sections (B's draft, B's seat,
 * B's events, B's UUID), no section carries a key named {@code uuid}, {@code x}, {@code y}, {@code z} or {@code pos},
 * {@code cabinets} lists only the requester's own seat with a coordinate-free label, and {@code library} pages are
 * capped at 50 with a working cursor.
 */
class ArcadeLinkSectionsScopeTest {

    private static FakeLinkContext seeded() {
        FakeLinkContext ctx = new FakeLinkContext();
        // A: one published game, one draft; B: one draft with a secret title and a published game
        LinkFixtures.publish(ctx, LinkFixtures.game(LinkFixtures.id(1), "Alice Pub", LinkFixtures.ALICE, null), 10);
        ctx.exchange.addDraft(LinkFixtures.ALICE, LinkFixtures.game(LinkFixtures.id(2), "Alice Draft",
                LinkFixtures.ALICE, null), 1, 8);
        ctx.exchange.addDraft(LinkFixtures.BOB, LinkFixtures.game(LinkFixtures.id(3), "Bob Secret", LinkFixtures.BOB,
                null), 1, 8);
        LinkFixtures.publish(ctx, LinkFixtures.game(LinkFixtures.id(4), "Bob Pub", LinkFixtures.BOB, null), 11);
        // a shared board both have bests on; Bob's own board
        ctx.boards.put(LinkFixtures.id(1), LinkFixtures.board(LinkFixtures.id(1), LinkFixtures.BOB, 80, null,
                LinkFixtures.ALICE, 70, null));
        ctx.boards.put(LinkFixtures.id(5), LinkFixtures.board(LinkFixtures.id(5), LinkFixtures.BOB, 10, null));
        ctx.cabinets.put(LinkFixtures.BOB, new ArcadeLinkContext.CabinetInfo("minecraft:the_nether", 3, true, 0,
                "PLAYING", "versus", 3, LinkFixtures.id(4), LinkFixtures.id(4), "Bob Pub"));
        ctx.cabinets.put(LinkFixtures.ALICE, new ArcadeLinkContext.CabinetInfo("minecraft:overworld", 1, true, 0,
                "LOBBY", "solo", 1, LinkFixtures.id(1), LinkFixtures.id(1), "Alice Pub"));
        return ctx;
    }

    @Test
    void bobNeverAppearsInAlicesSections() {
        FakeLinkContext ctx = seeded();
        ArcadeEvents.Pipeline events = LinkFixtures.pipeline(ctx);
        events.onMatchEnd(new ArcadeEvents.MatchEndInfo(LinkFixtures.id(5), LinkFixtures.id(5), "Bob Only", "minecraft:overworld", 1,
                List.of(new BusMatchResult.SeatResult(LinkFixtures.BOB, 0, 10, 1, false)), "WIN",
                new RecordOutcome(1, 1, 0, 0), false));
        ArcadeSnapshotProvider provider = LinkFixtures.provider(ctx, events);

        for (String section : List.of("library", "cabinets", "events")) {
            JsonObject out = provider.snapshot(LinkFixtures.ALICE, section, Map.of());
            String text = out.toString();
            assertFalse(text.contains("Bob Secret"), section + ": " + text);
            assertFalse(text.contains("Bob Only"), section + ": " + text);
            assertFalse(text.contains(LinkFixtures.BOB.toString()), section);
            assertFalse(text.contains(LinkFixtures.ALICE.toString()), section + ": not even the requester's own id");
            assertFalse(text.contains("Nether"), section + ": Bob's seat");
            RecordBeatenPushMinimisationTest.assertNoKeys(out, "uuid", "x", "y", "z", "pos", "player");
        }
        JsonObject board = provider.snapshot(LinkFixtures.ALICE, "leaderboard", Map.of("cartridge", LinkFixtures.id(1)));
        RecordBeatenPushMinimisationTest.assertNoKeys(board, "uuid", "x", "y", "z", "pos", "player");
        assertFalse(board.toString().contains(LinkFixtures.BOB.toString()));
    }

    @Test
    void libraryIsWhatTheServerKnowsAboutYou() {
        FakeLinkContext ctx = seeded();
        JsonObject lib = LinkFixtures.provider(ctx, LinkFixtures.pipeline(ctx)).snapshot(LinkFixtures.ALICE, "library",
                Map.of());
        JsonArray rows = lib.getAsJsonArray("cartridges");
        assertEquals(2, rows.size(), rows.toString());
        assertEquals("draft", rows.get(0).getAsJsonObject().get("state").getAsString(), "drafts first");
        JsonObject pub = rows.get(1).getAsJsonObject();
        assertEquals("published", pub.get("state").getAsString());
        assertEquals(70, pub.getAsJsonObject("best").get("score").getAsInt());
        assertEquals(2, pub.getAsJsonObject("best").get("rank").getAsInt());
        assertEquals(1, lib.getAsJsonObject("limits").get("draftsUsed").getAsInt());
        assertTrue(lib.getAsJsonObject("actions").get("import_cartridge").getAsBoolean());
        assertTrue(lib.get("nextCursor").isJsonNull());

        ctx.settings = LinkSettings.DEFAULTS.withActions(false, true, true);
        JsonObject off = LinkFixtures.provider(ctx, LinkFixtures.pipeline(ctx)).snapshot(LinkFixtures.ALICE, "library",
                Map.of());
        assertFalse(off.getAsJsonObject("actions").get("export_cartridge").getAsBoolean(),
                "the section is how the app learns an action is off");
    }

    @Test
    void bestOnlyRowsForBoardsYouDidNotAuthor() {
        FakeLinkContext ctx = seeded();
        JsonObject lib = LinkFixtures.provider(ctx, LinkFixtures.pipeline(ctx)).snapshot(LinkFixtures.BOB, "library",
                Map.of());
        JsonArray rows = lib.getAsJsonArray("cartridges");
        JsonObject last = rows.get(rows.size() - 1).getAsJsonObject();
        boolean sawBestOnly = false;
        for (int i = 0; i < rows.size(); i++) {
            JsonObject r = rows.get(i).getAsJsonObject();
            if ("best_only".equals(r.get("state").getAsString())) {
                sawBestOnly = true;
                assertFalse(r.get("authored").getAsBoolean());
                assertFalse(r.has("plays"));
            }
        }
        assertTrue(sawBestOnly, rows.toString());
        assertEquals("best_only", last.get("state").getAsString(), "bests last");
    }

    @Test
    void cabinetsOnlyYourOwnSeatNoCoordinates() {
        FakeLinkContext ctx = seeded();
        JsonObject out = LinkFixtures.provider(ctx, LinkFixtures.pipeline(ctx)).snapshot(LinkFixtures.ALICE, "cabinets",
                Map.of());
        JsonArray list = out.getAsJsonArray("cabinets");
        assertEquals(1, list.size());
        JsonObject c = list.get(0).getAsJsonObject();
        assertEquals("Overworld · Solo cabinet", c.get("label").getAsString());
        assertEquals("host", c.get("role").getAsString());
        assertEquals("Alice Pub", c.getAsJsonObject("cartridge").get("title").getAsString());
        JsonObject nobody = LinkFixtures.provider(ctx, LinkFixtures.pipeline(ctx)).snapshot(LinkFixtures.KESSLER,
                "cabinets", Map.of());
        assertEquals(0, nobody.getAsJsonArray("cabinets").size(), "not seated: empty");
    }

    @Test
    void libraryPagesAreCappedWithACursor() {
        FakeLinkContext ctx = new FakeLinkContext();
        for (int i = 0; i < 60; i++) {
            LinkFixtures.publish(ctx, LinkFixtures.game(LinkFixtures.id(200 + i), "G" + i, LinkFixtures.ALICE, null), i);
        }
        ArcadeSnapshotProvider provider = LinkFixtures.provider(ctx, LinkFixtures.pipeline(ctx));
        JsonObject first = provider.snapshot(LinkFixtures.ALICE, "library", Map.of("limit", "500"));
        assertEquals(50, first.getAsJsonArray("cartridges").size());
        String cursor = first.get("nextCursor").getAsString();
        assertTrue(ArcadeSnapshotProvider.bytes(first) <= ArcadeSnapshotProvider.LIBRARY_BUDGET);
        JsonObject second = provider.snapshot(LinkFixtures.ALICE, "library", Map.of("cursor", cursor));
        assertEquals(10, second.getAsJsonArray("cartridges").size());
        assertTrue(second.get("nextCursor").isJsonNull());
        JsonObject bogus = provider.snapshot(LinkFixtures.ALICE, "library", Map.of("cursor", "zzzzzzzzzzzzzzzz"));
        assertEquals(0, bogus.getAsJsonArray("cartridges").size(), "an unknown cursor never restarts");
        JsonObject small = provider.snapshot(LinkFixtures.ALICE, "library", Map.of("limit", "5"));
        assertEquals(5, small.getAsJsonArray("cartridges").size());
    }

    @Test
    void theEventsSectionNeverEchoesTheNotificationCopy() {
        // Stage 7 security review L2: only the bus copy carries _notify; the ring (section and WS snapshot) never
        FakeLinkContext ctx = seeded();
        ArcadeEvents.Pipeline events = LinkFixtures.pipeline(ctx);
        events.onMatchEnd(new ArcadeEvents.MatchEndInfo(LinkFixtures.id(1), LinkFixtures.id(1), "Alice Pub",
                "minecraft:overworld", 1, List.of(new BusMatchResult.SeatResult(LinkFixtures.BOB, 0, 99, 1, false)),
                "WIN", new RecordOutcome(1, 1, 0, 0, List.of(new RecordOutcome.Displaced(LinkFixtures.ALICE, 2))),
                false));
        assertEquals(1, ctx.pushesFor(LinkFixtures.ALICE), "the bus copy is rich");
        JsonObject out = LinkFixtures.provider(ctx, events).snapshot(LinkFixtures.ALICE, "events", Map.of());
        assertTrue(out.toString().contains("RECORD_BEATEN"), out.toString());
        RecordBeatenPushMinimisationTest.assertNoKeys(out, ArcadePushCopy.FIELD);
        for (JsonObject row : events.events(LinkFixtures.ALICE, 20)) {
            assertFalse(row.getAsJsonObject("payload").has(ArcadePushCopy.FIELD), row.toString());
        }
    }
}
