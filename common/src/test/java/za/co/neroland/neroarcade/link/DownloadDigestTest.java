package za.co.neroland.neroarcade.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.link.LinkEvent;

import za.co.neroland.neroarcade.exchange.ExchangeTable;
import za.co.neroland.neroarcade.privacy.Tombstones;

/**
 * 007 §4 {@code CARTRIDGE_DOWNLOADED}: the digest runs once at {@code link.digestHour} (server-local), sends one event
 * per listing with downloads to its author, at most five per author (the rest folded into the fifth as
 * {@code more}), resets the per-listing counters, never names a downloader, and skips a tombstoned author. The
 * counter persists with the listing and is dropped with it.
 */
class DownloadDigestTest {

    @Test
    void digestAtTheHourPerAuthorCappedAndReset() {
        FakeLinkContext ctx = new FakeLinkContext();
        for (int i = 1; i <= 7; i++) {
            LinkFixtures.publish(ctx, LinkFixtures.game(LinkFixtures.id(i), "Game " + i, LinkFixtures.ALICE, null), i);
        }
        LinkFixtures.publish(ctx, LinkFixtures.game(LinkFixtures.id(20), "Bob Game", LinkFixtures.BOB, null), 20);
        LinkFixtures.publish(ctx, LinkFixtures.game(LinkFixtures.id(30), "Orphan", Tombstones.ERASED_AUTHOR, null), 30);
        for (int listing = 1; listing <= 9; listing++) {
            for (int n = 0; n < listing; n++) {
                assertTrue(ctx.exchange.countDownload(listing));
            }
        }
        ArcadeEvents.Pipeline events = LinkFixtures.pipeline(ctx);

        ctx.hour = 17;
        events.tick();
        assertTrue(ctx.published.isEmpty(), "not before the digest hour");

        ctx.hour = 18;
        events.tick();
        List<LinkEvent> alice = ctx.published.stream().filter(e -> LinkFixtures.ALICE.equals(e.playerId())).toList();
        assertEquals(5, alice.size(), "at most five per author");
        JsonObject busiest = alice.get(0).payload();
        assertEquals("CARTRIDGE_DOWNLOADED", busiest.get("type").getAsString());
        assertEquals("Game 7", busiest.get("title").getAsString());
        assertEquals(7, busiest.get("count").getAsInt());
        assertEquals("Game 7 was downloaded 7 times today",
                busiest.getAsJsonObject("_notify").get("body").getAsString());
        JsonObject last = alice.get(4).payload();
        assertEquals(2, last.get("more").getAsInt(), "the two quietest folded into the fifth");
        assertTrue(last.getAsJsonObject("_notify").get("body").getAsString().endsWith("(+2 more)"));
        assertEquals(1, ctx.published.stream().filter(e -> LinkFixtures.BOB.equals(e.playerId())).count());
        assertFalse(ctx.published.stream().anyMatch(e -> Tombstones.ERASED_AUTHOR.equals(e.playerId())),
                "a tombstoned author hears nothing");
        for (LinkEvent e : ctx.published) {
            assertFalse(e.payload().toString().contains(LinkFixtures.BOB.toString()));
            assertFalse(e.isBroadcast());
        }
        for (int listing = 1; listing <= 9; listing++) {
            assertEquals(0, ctx.exchange.downloadsPending(listing), "counters reset");
        }

        int sent = ctx.published.size();
        events.tick();
        assertEquals(sent, ctx.published.size(), "once a day");
        ctx.day++;
        ctx.exchange.countDownload(1);
        events.tick();
        assertEquals(sent + 1, ctx.published.size(), "the next day digests again");
        assertEquals("Game 1 was downloaded once today",
                ctx.published.get(sent).payload().getAsJsonObject("_notify").get("body").getAsString());
    }

    @Test
    void countersPersistAndGoWithTheListing() {
        ExchangeTable table = new ExchangeTable();
        table.publish(LinkFixtures.game(LinkFixtures.id(1), "A", LinkFixtures.ALICE, null), LinkFixtures.ALICE,
                java.util.Optional.empty(), LinkFixtures.TAGS, 1, 10);
        table.countDownload(1);
        table.countDownload(1);
        assertFalse(table.countDownload(99), "an unknown listing counts nothing");
        ExchangeTable loaded = ExchangeTable.load(table.save());
        assertEquals(2, loaded.downloadsPending(1));
        loaded.unpublish(1);
        assertTrue(loaded.drainDownloads().isEmpty(), "a gone listing's count is dropped");
    }
}
