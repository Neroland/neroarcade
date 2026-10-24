package za.co.neroland.neroarcade.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.link.LinkActionResult;

import za.co.neroland.neroarcade.game.GameDef;

/**
 * 007 §3.1: the exported JSON is checked against 12 KiB before answering; a larger one (impossible for a stored
 * cartridge, which is at most 8 KiB of NBT) answers {@code INTERNAL "cartridge too large to export"}.
 */
class ExportSizeCapTest {

    @Test
    void overTwelveKibIsRefused() {
        FakeLinkContext ctx = new FakeLinkContext();
        String id = LinkFixtures.id(1);
        GameDef huge = new GameDef(1, id, "x".repeat(13 * 1024), Optional.of(LinkFixtures.ALICE), Optional.empty(),
                List.of(), Optional.empty(), Map.of(), 1L, 1);
        ctx.exchange.addDraft(LinkFixtures.ALICE, huge, 1, 8);
        LinkActionResult r = LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE, "export_cartridge",
                LinkFixtures.exportParams("r1", id, id));
        assertEquals(LinkActionResult.Error.INTERNAL, r.error());
        assertEquals("cartridge too large to export", r.message());
    }

    @Test
    void aNormalCartridgeFits() {
        FakeLinkContext ctx = new FakeLinkContext();
        String id = LinkFixtures.id(2);
        ctx.exchange.addDraft(LinkFixtures.ALICE, LinkFixtures.game(id, "Small", LinkFixtures.ALICE, null), 1, 8);
        LinkActionResult r = LinkFixtures.handler(ctx).execute(LinkFixtures.ALICE, "export_cartridge",
                LinkFixtures.exportParams("r1", id, id));
        assertTrue(r.ok());
        assertTrue(r.state().get("bytes").getAsInt() <= ArcadeActionHandler.MAX_GAME_DEF_BYTES);
    }
}
