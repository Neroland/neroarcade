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
 * {@code pushNotifications.enabled=false} (the kill switch, Stage 7 security review M1): <b>nothing is published on
 * the link bus</b> — NeroLink 1.x turns every published player-scoped event, with or without {@code _notify}, into a
 * phone notification. Record falls, window summaries and digests are kept in the {@code events} section only, and
 * the persisted alert is still raised (leading edge and summary).
 */
class PushKillSwitchTest {

    @Test
    void nothingIsPublishedWhileKilled() {
        FakeLinkContext ctx = new FakeLinkContext();
        ctx.settings = LinkSettings.DEFAULTS.withPush(false);
        String root = LinkFixtures.id(3);
        LinkFixtures.publish(ctx, LinkFixtures.game(root, "Neon Dodge", LinkFixtures.ALICE, null), 1);
        ctx.exchange.countDownload(1);
        ArcadeEvents.Pipeline events = LinkFixtures.pipeline(ctx);

        for (int i = 0; i < 2; i++) {
            events.onMatchEnd(new ArcadeEvents.MatchEndInfo(root, root, "Neon Dodge", "minecraft:overworld", 1,
                    List.of(new BusMatchResult.SeatResult(LinkFixtures.BOB, 0, 99, 1, false)), "WIN",
                    new RecordOutcome(1, 1, 0, 0, List.of(new RecordOutcome.Displaced(LinkFixtures.ALICE, 2 + i))),
                    false));
            ctx.now += 30 * 60_000L;
        }
        ctx.now += 60 * 60_000L;
        ctx.hour = 18;
        events.tick();

        assertTrue(ctx.published.isEmpty(), "nothing reaches the bridge: " + ctx.published);
        assertEquals(2, ctx.raises, "the alert is still raised (leading edge and summary)");

        List<JsonObject> ring = events.events(LinkFixtures.ALICE, 20);
        assertEquals(3, count(ring, ArcadeEvents.RECORD_BEATEN), "leading edge, in-window fall, summary: " + ring);
        assertEquals(1, count(ring, ArcadeEvents.CARTRIDGE_DOWNLOADED), "the digest row");
        for (JsonObject row : ring) {
            assertFalse(row.getAsJsonObject("payload").has(ArcadePushCopy.FIELD), row.toString());
        }
        assertEquals(2, events.events(LinkFixtures.BOB, 20).size(), "Bob's two match results, section only");
    }

    @Test
    void withTheSwitchOnTheSameRunPublishesRichEvents() {
        FakeLinkContext ctx = new FakeLinkContext();
        String root = LinkFixtures.id(3);
        ArcadeEvents.Pipeline events = LinkFixtures.pipeline(ctx);
        events.onMatchEnd(new ArcadeEvents.MatchEndInfo(root, root, "Neon Dodge", "minecraft:overworld", 1,
                List.of(new BusMatchResult.SeatResult(LinkFixtures.BOB, 0, 99, 1, false)), "WIN",
                new RecordOutcome(1, 1, 0, 0, List.of(new RecordOutcome.Displaced(LinkFixtures.ALICE, 2))), false));
        assertEquals(1, ctx.published.size());
        assertTrue(ctx.published.get(0).payload().has(ArcadePushCopy.FIELD));
    }

    private static long count(List<JsonObject> ring, String type) {
        return ring.stream().filter(r -> type.equals(r.getAsJsonObject("payload").get("type").getAsString())).count();
    }
}
