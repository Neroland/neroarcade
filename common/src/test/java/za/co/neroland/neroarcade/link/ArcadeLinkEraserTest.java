package za.co.neroland.neroarcade.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.board.RecordOutcome;
import za.co.neroland.neroarcade.cabinet.BusMatchResult;
import za.co.neroland.neroarcade.data.NeroArcadeData;

/**
 * 007 §8.8: the link module's transient per-player state (the events ring, the notification windows, request ids and
 * rate-limit windows) joins the composite eraser as store {@code "link"}; after an erasure none of it remains, other
 * players' state is untouched, and a server-less erasure (Core's conformance probe shape) does not throw.
 */
class ArcadeLinkEraserTest {

    @Test
    void erasureForgetsEverythingTransient() {
        FakeLinkContext ctx = new FakeLinkContext();
        ArcadeEvents.Pipeline events = LinkFixtures.pipeline(ctx);
        String root = LinkFixtures.id(1);
        events.onMatchEnd(new ArcadeEvents.MatchEndInfo(root, root, "Neon Dodge", "minecraft:overworld", 1,
                List.of(new BusMatchResult.SeatResult(LinkFixtures.BOB, 0, 99, 1, false)), "WIN",
                new RecordOutcome(1, 1, 0, 0, List.of(new RecordOutcome.Displaced(LinkFixtures.ALICE, 2))), false));
        assertTrue(events.holdsAnyFor(LinkFixtures.ALICE));
        assertTrue(events.holdsAnyFor(LinkFixtures.BOB));

        NeroArcadeData.StoreEraser eraser = NeroArcadeLinkModule.eraser(events);
        assertEquals("link", eraser.name());
        eraser.erase(null, LinkFixtures.ALICE);
        eraser.backupNow(null);
        assertFalse(events.holdsAnyFor(LinkFixtures.ALICE));
        assertTrue(events.events(LinkFixtures.ALICE, 20).isEmpty());
        assertTrue(events.holdsAnyFor(LinkFixtures.BOB), "only the erased player goes");
        eraser.erase(null, LinkFixtures.KESSLER); // unknown player: no-op
        eraser.erase(null, null);
    }

    @Test
    void moduleProbeCoversDedupAndLimiter() {
        // the module-level probe answers false for a player it never saw
        assertFalse(NeroArcadeLinkModule.holdsAnyFor(LinkFixtures.KESSLER));
    }

    @Test
    void dedupAndLimiterForgetOnErasure() {
        LinkRequestDedup dedup = new LinkRequestDedup();
        LinkRateLimiter limiter = LinkRateLimiter.standard(() -> 10);
        dedup.remember(LinkFixtures.ALICE, "export_cartridge", "r",
                za.co.neroland.nerolandcore.link.LinkActionResult.ok(null), 0L);
        limiter.tryAcquire(LinkFixtures.ALICE, "export_cartridge", 0L);
        dedup.forget(LinkFixtures.ALICE);
        limiter.forget(LinkFixtures.ALICE);
        assertFalse(dedup.holdsAnyFor(LinkFixtures.ALICE));
        assertFalse(limiter.holdsAnyFor(LinkFixtures.ALICE));
    }
}
