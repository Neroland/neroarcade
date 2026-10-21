package za.co.neroland.neroarcade.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.TestChips;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.runtime.Budgets;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.MatchState;

/**
 * The pixel renderer is deterministic and allocation-free: a known match state draws a pinned picture, the four
 * status screens draw, arenas scale by whole pixels (16 → 4 px, 32 → 2 px, strip_9 → 7 x 2 px), and the scanline
 * option darkens odd texel rows only.
 */
class ScreenRendererTest {

    /** FNV-1a 64 of the indexed 64x64 frame below; changes only when the drawing rules or the sheets change. */
    private static final long PINNED_MATCH_FRAME = 0xe12261c18861c255L;

    private static SpriteSheets sheets;
    private static ArcadeFont font;

    @BeforeAll
    static void load() {
        sheets = ScreenTestAssets.sheets(ThemePalettes.Theme.NEON_VOID);
        font = ScreenTestAssets.font();
    }

    private static MatchState knownState(ResolvedGame game) {
        return HeadlessMatch.of(game, 1, 77L).run(Budgets.COUNTDOWN_TICKS + 40, HeadlessMatch.InputScript.randomWalk())
                .state();
    }

    @Test
    void shippedSheetsAreComplete() {
        for (ThemePalettes.Theme theme : ThemePalettes.Theme.values()) {
            SpriteSheets s = ScreenTestAssets.sheets(theme);
            for (SpriteSheets.Sheet sheet : SpriteSheets.Sheet.values()) {
                for (int sprite = 0; sprite < sheet.sprites(); sprite++) {
                    if (sheet == SpriteSheets.Sheet.TILES && sprite == SpriteSheets.TILE_VOID) {
                        continue; // the void tile is index 0 by design
                    }
                    assertTrue(s.has(sheet, sprite), theme + " " + sheet + " sprite " + sprite + " is empty");
                }
            }
        }
        assertTrue(font.lit('A', 0, 6) || font.lit('A', 2, 0), "the font's A has lit pixels");
    }

    @Test
    void knownMatchRendersAPinnedFrame() {
        ResolvedGame game = TestModules.resolvedOf(TestChips.Bouncer.INSTANCE, TestChips.Orb.INSTANCE);
        MatchState state = knownState(game);
        ScreenRenderer a = new ScreenRenderer(sheets, font);
        a.drawMatch(state, SpriteMap.of(game), EntityPositions.AUTHORITATIVE, 0);
        ScreenRenderer b = new ScreenRenderer(sheets, font);
        b.drawMatch(knownState(game), SpriteMap.of(game), EntityPositions.AUTHORITATIVE, 0);
        assertEquals(a.checksum(), b.checksum(), "same state, same picture");
        assertEquals(PINNED_MATCH_FRAME, a.checksum(), "pinned frame changed: 0x" + Long.toHexString(a.checksum()));
        byte[] before = a.pixels();
        a.drawMatch(state, SpriteMap.of(game), EntityPositions.AUTHORITATIVE, 0);
        assertTrue(before == a.pixels(), "the buffer is reused, never reallocated");
    }

    @Test
    void entitiesDrawOverTheBoard() {
        ResolvedGame game = TestModules.resolvedOf(TestChips.Bouncer.INSTANCE, TestChips.Orb.INSTANCE);
        MatchState state = knownState(game);
        ScreenRenderer r = new ScreenRenderer(sheets, font);
        r.drawMatch(state, SpriteMap.of(game), EntityPositions.AUTHORITATIVE, 0);
        long withEntities = r.checksum();
        EntityPositions offscreen = new EntityPositions() {
            @Override
            public int x(MatchState s, int id) {
                return -100_000;
            }
        };
        r.drawMatch(state, SpriteMap.of(game), offscreen, 0);
        assertNotEquals(withEntities, r.checksum(), "moving every entity off the board changes the picture");
    }

    @Test
    void everyStatusScreenDraws() {
        ScreenRenderer r = new ScreenRenderer(sheets, font);
        for (ScreenStatus status : ScreenStatus.values()) {
            r.drawStatus(status, "MISSING CHIP", 0);
            assertTrue(litPixels(r) > 20, status + " draws text");
        }
        r.drawStatus(ScreenStatus.NO_POWER, "", 20); // the off half of the 0.5 Hz blink
        assertEquals(0, litPixels(r), "NO POWER blinks off");
        r.drawStatus(ScreenStatus.UNREADABLE, "MISSING CHIP", 0);
        assertEquals(ThemePalettes.HAZARD_HOT, firstLit(r, 0, 30), "UNREADABLE HERE in palette 9");
        assertEquals(ThemePalettes.TEXT_DIM, firstLit(r, 34, 60), "the reason in palette 13");
    }

    @Test
    void attractLoopShowsTitleArenaAndScores() {
        ResolvedGame game = TestModules.resolvedOf();
        MatchState arena = knownState(game);
        ScreenRenderer r = new ScreenRenderer(sheets, font);
        r.drawAttract("Neon Dodge", arena, SpriteMap.of(game), 0);
        long title = r.checksum();
        r.drawAttract("Neon Dodge", arena, SpriteMap.of(game), 50);
        long demo = r.checksum();
        r.drawAttract("Neon Dodge", arena, SpriteMap.of(game), 110);
        long scores = r.checksum();
        assertNotEquals(title, demo);
        assertNotEquals(demo, scores);
        r.drawAttract("Neon Dodge", null, SpriteMap.of(game), 50);
        assertTrue(litPixels(r) > 0, "no arena: the title card stays up");
    }

    @Test
    void arenasScaleByWholePixels() {
        ArenaLayout sixteen = ArenaLayout.of(16, 16);
        assertEquals(4, sixteen.sx());
        assertEquals(4, sixteen.sy());
        assertEquals(0, sixteen.ox());
        ArenaLayout thirtyTwo = ArenaLayout.of(32, 32);
        assertEquals(2, thirtyTwo.sx());
        assertEquals(0, thirtyTwo.oy());
        ArenaLayout strip = ArenaLayout.of(9, 32);
        assertEquals(7, strip.sx(), "strip_9: 7 px across");
        assertEquals(2, strip.sy(), "strip_9: 2 px along");
        assertEquals(0, strip.ox(), "63 px wide, 1 px letterbox");
        ArenaLayout shortStrip = ArenaLayout.of(9, 16);
        assertEquals(7, shortStrip.sx());
        assertEquals(2, shortStrip.sy());
        assertEquals(16, shortStrip.oy(), "a 16-long strip is letterboxed top and bottom");
        ArenaLayout twenty = ArenaLayout.of(20, 20);
        assertEquals(3, twenty.sx());
        assertEquals(2, twenty.ox());
        assertEquals(256 * 3 >> 8, twenty.px(256) - twenty.ox(), "one cell = sx pixels");
    }

    @Test
    void scanlinesDarkenOddRowsOnly() {
        ScreenRenderer r = new ScreenRenderer(sheets, font);
        r.clear(ThemePalettes.TEXT);
        int even = ScreenPixels.texel(r.pixels(), ThemePalettes.Theme.NEON_VOID, true, 3, 2);
        int odd = ScreenPixels.texel(r.pixels(), ThemePalettes.Theme.NEON_VOID, true, 3, 3);
        int off = ScreenPixels.texel(r.pixels(), ThemePalettes.Theme.NEON_VOID, false, 3, 3);
        assertEquals(ThemePalettes.Theme.NEON_VOID.colour(ThemePalettes.TEXT), even);
        assertEquals(even, off, "without scanlines odd rows are untouched");
        assertEquals(((0xE8 * 200) >> 8), (odd >> 16) & 0xFF, "odd rows at 0.78");
        int[] texture = new int[ScreenPixels.TEXTURE * ScreenPixels.TEXTURE];
        r.clear(0);
        ScreenPixels.expand(r.pixels(), ThemePalettes.Theme.NEON_VOID, false, texture);
        assertEquals(ThemePalettes.Theme.NEON_VOID.colour(ThemePalettes.BACKGROUND), texture[0],
                "index 0 shows the background, never a hole");
    }

    @Test
    void themesResolveFromThemeSpecIds() {
        assertEquals(ThemePalettes.Theme.HANGAR, ThemePalettes.themeOf("sprites/hangar"));
        assertEquals(ThemePalettes.Theme.NEON_VOID, ThemePalettes.themeOf("palette/neon_void_2"));
        assertEquals(ThemePalettes.Theme.NEON_VOID, ThemePalettes.themeOf("something/else"));
        assertEquals(ThemePalettes.Theme.NEON_VOID, SpriteMap.of(TestModules.resolvedOf()).theme());
    }

    private static int litPixels(ScreenRenderer r) {
        int n = 0;
        for (byte b : r.pixels()) {
            if (b != ThemePalettes.BACKGROUND) {
                n++;
            }
        }
        return n;
    }

    private static int firstLit(ScreenRenderer r, int y0, int y1) {
        for (int y = y0; y < y1; y++) {
            for (int x = 0; x < ScreenRenderer.SIZE; x++) {
                if (r.pixel(x, y) != ThemePalettes.BACKGROUND) {
                    return r.pixel(x, y);
                }
            }
        }
        return -1;
    }
}
