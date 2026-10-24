package za.co.neroland.neroarcade.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.board.RecordOutcome;
import za.co.neroland.neroarcade.cabinet.BusMatchResult;

/**
 * 007 §4: at most one notification per (player, board) per cooldown. A second fall inside the hour sends nothing to
 * the bus (so the bridge sends no push for it) and is visible in the {@code events} section; when the window closes
 * the newest rank goes out once (one rich notification, the alert updated to that rank). Different boards have
 * separate windows.
 */
class RecordBeatenCoalesceTest {

    private static final String ROOT = LinkFixtures.id(5);
    private static final String OTHER = LinkFixtures.id(6);
    private static final long MINUTE = 60_000L;

    private static ArcadeEvents.MatchEndInfo fall(String root, Integer newRank) {
        return new ArcadeEvents.MatchEndInfo(root, root, "Neon Dodge", "minecraft:overworld", 1,
                List.of(new BusMatchResult.SeatResult(LinkFixtures.BOB, 0, 99, 1, false)), "WIN",
                new RecordOutcome(1, 1, 1, 1, List.of(new RecordOutcome.Displaced(LinkFixtures.ALICE, newRank))), false);
    }

    @Test
    void twoFallsInAnHourAreOnePush() {
        FakeLinkContext ctx = new FakeLinkContext();
        ArcadeEvents.Pipeline events = LinkFixtures.pipeline(ctx);

        events.onMatchEnd(fall(ROOT, 3));
        ctx.now += 10 * MINUTE;
        events.onMatchEnd(fall(ROOT, 5));
        events.tick();

        assertEquals(1, ctx.pushesFor(LinkFixtures.ALICE), "one push inside the hour");
        assertEquals(1, ctx.published.stream().filter(e -> LinkFixtures.ALICE.equals(e.playerId())).count(),
                "the second fall is not on the bus (the bridge would turn it into a generic push)");
        List<JsonObject> section = events.events(LinkFixtures.ALICE, 20);
        assertEquals(2, section.size(), "both falls are in the events section");
        assertEquals(5, section.get(0).getAsJsonObject("payload").get("newRank").getAsInt(), "newest first");
        assertTrue(alertText(ctx).endsWith("#3"), alertText(ctx));
        int raisesInWindow = ctx.raises;

        ctx.now += 51 * MINUTE; // the window (60 min from the first fall) has closed
        events.tick();
        assertEquals(2, ctx.pushesFor(LinkFixtures.ALICE), "one summary when the window closes");
        assertTrue(alertText(ctx).endsWith("#5"), "the alert now carries the newest rank: " + alertText(ctx));
        assertEquals(raisesInWindow + 1, ctx.raises);

        ctx.now += 30 * MINUTE;
        events.onMatchEnd(fall(ROOT, 6));
        assertEquals(2, ctx.pushesFor(LinkFixtures.ALICE), "the summary opened a new window");

        events.onMatchEnd(fall(OTHER, 1));
        assertEquals(3, ctx.pushesFor(LinkFixtures.ALICE), "another board has its own window");
    }

    @Test
    void fallingOffTheBoardSaysSo() {
        FakeLinkContext ctx = new FakeLinkContext();
        ArcadeEvents.Pipeline events = LinkFixtures.pipeline(ctx);
        events.onMatchEnd(fall(ROOT, null));
        JsonObject payload = ctx.published.stream().filter(e -> LinkFixtures.ALICE.equals(e.playerId())).findFirst()
                .orElseThrow().payload();
        assertTrue(payload.get("newRank").isJsonNull());
        assertTrue(payload.getAsJsonObject("_notify").get("body").getAsString().contains("off the top 10"));
    }

    @Test
    void matchResultsNeverNotify() {
        FakeLinkContext ctx = new FakeLinkContext();
        ArcadeEvents.Pipeline events = LinkFixtures.pipeline(ctx);
        events.onMatchEnd(new ArcadeEvents.MatchEndInfo(ROOT, ROOT, "Neon Dodge", "minecraft:overworld", 3,
                List.of(new BusMatchResult.SeatResult(LinkFixtures.BOB, 0, 99, 1, false)), "WIN",
                new RecordOutcome(1, 1, 0, 0), false));
        assertTrue(ctx.published.isEmpty(), "a match result is never on the bus");
        JsonObject row = events.events(LinkFixtures.BOB, 20).get(0).getAsJsonObject("payload");
        assertEquals("MATCH_RESULT", row.get("type").getAsString());
        assertEquals("Overworld · Linked hall (3 cabinets)", row.get("label").getAsString());
        assertTrue(row.get("newBest").getAsBoolean());
        assertFalse(row.has("_notify"));
    }

    private static String alertText(FakeLinkContext ctx) {
        return ctx.alerts.get(LinkFixtures.ALICE).get("neroarcade." + ROOT + ".record").text();
    }
}
