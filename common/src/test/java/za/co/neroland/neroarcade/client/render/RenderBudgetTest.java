package za.co.neroland.neroarcade.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.bench.BenchSlots;
import za.co.neroland.neroarcade.client.bench.BenchModel;
import za.co.neroland.neroarcade.duplicator.DuplicatorRules;
import za.co.neroland.neroarcade.module.ModuleType;

/**
 * The Stage 5 render budget (the family BER gates: at most 60 000 vertices and 1.5 ms of BER time a frame): the
 * vertices each renderer emits at each distance, the 004 scene (20 awake cabinets and 2 marquees in view, plus a
 * bench, a duplicator and a kiosk) far under the gate, the shared texture pool's cap, the counters, and the small
 * pure pieces the renderers lean on (marquee text, glyph cells, the copy clock, the slider step, the rack mask).
 */
class RenderBudgetTest {

    @AfterEach
    void reset() {
        ArcadeBerStats.reset();
    }

    @Test
    void perRendererVertexCountsAtEachDistance() {
        double near = 4 * 4;
        double far = 30 * 30;
        assertEquals(8 + 12 * 4, RenderPlan.cabinetVertices(near, "ABCDEFGHIJKLMNOP"), "12 glyphs, title cut to 12");
        assertEquals(8 + 10 * 4, RenderPlan.cabinetVertices(near, ""), "NEROARCADE");
        assertEquals(8, RenderPlan.cabinetVertices(far, "SERPENT GARDEN"), "past the text range: the screen only");
        assertTrue(RenderPlan.cabinetVertices(near, "A B C D E F") <= RenderPlan.CABINET_MAX, "spaces draw nothing");
        assertEquals(56, RenderPlan.CABINET_MAX);
        assertEquals(16, RenderPlan.MARQUEE_MAX);
        assertEquals(88, RenderPlan.BENCH_MAX);
        assertEquals(12, RenderPlan.DUPLICATOR_MAX);
        assertEquals(24, RenderPlan.EXCHANGE_MAX);
    }

    @Test
    void theBudgetSceneIsFarUnderTheGate() {
        int scene = 20 * RenderPlan.CABINET_MAX + 2 * RenderPlan.MARQUEE_MAX + RenderPlan.BENCH_MAX
                + RenderPlan.DUPLICATOR_MAX + RenderPlan.EXCHANGE_MAX;
        System.out.println("budget scene, worst case: " + scene + " vertices/frame (gate "
                + ArcadeBerStats.VERTEX_BUDGET + ")");
        assertEquals(1276, scene);
        assertTrue(scene * 20 < ArcadeBerStats.VERTEX_BUDGET, "even twenty such scenes fit the gate");
        // the whole gallery hall (47 cabinets, a marquee, the workshop) at its worst
        int gallery = 47 * RenderPlan.CABINET_MAX + RenderPlan.MARQUEE_MAX + RenderPlan.BENCH_MAX
                + RenderPlan.DUPLICATOR_MAX + RenderPlan.EXCHANGE_MAX;
        assertTrue(gallery < 5_000, "gallery: " + gallery);
    }

    @Test
    void theScreenPoolCoversTheBudgetSceneAndNoMore() {
        TextureLru lru = new TextureLru(TextureLru.DEFAULT_CAP);
        // 20 cabinets, 2 marquees (main + strip each), a bench preview and a kiosk screen
        int screens = 20 + 2 * 2 + 1 + 1;
        int granted = 0;
        for (long key = 0; key < screens; key++) {
            granted += lru.acquire(key, 1) >= 0 ? 1 : 0;
        }
        assertEquals(TextureLru.DEFAULT_CAP, granted, "the pool is the cap; the rest show the ink face");
        assertTrue(AttractReplays.MAX_PLAYERS <= 16, "one demo per shipped replay at most");
    }

    @Test
    void theCountersAverageAndJudgeTheWindow() {
        for (int f = 0; f < 10; f++) {
            ArcadeBerStats.frame(f, 0f);
            ArcadeBerStats.submitted();
            ArcadeBerStats.vertices(1276);
            ArcadeBerStats.nanos(200_000);
            ArcadeBerStats.culled();
            ArcadeBerStats.upload();
        }
        ArcadeBerStats.endFrame();
        ArcadeBerStats.Snapshot a = ArcadeBerStats.average();
        assertEquals(10, a.frames());
        assertEquals(1276, a.vertices(), 0.001);
        assertEquals(0.2, a.millis(), 0.0001);
        assertEquals(1, a.uploads(), 0.0001);
        assertTrue(ArcadeBerStats.withinBudget());
        String report = ArcadeBerStats.report(22, TextureLru.DEFAULT_CAP, 3);
        assertTrue(report.contains("budget 60000") && report.contains("22 of 24"), report);
        ArcadeBerStats.frame(99, 0f);
        ArcadeBerStats.vertices(70_000);
        ArcadeBerStats.endFrame();
        assertFalse(ArcadeBerStats.withinBudget(), "a peak over the gate fails the record");
    }

    @Test
    void marqueeTextIsTheFontsUpperCaseCutToTwelve() {
        assertEquals("NEROARCADE", RenderPlan.marqueeText(null));
        assertEquals("NEROARCADE", RenderPlan.marqueeText("   "));
        assertEquals("SERPENT GARD", RenderPlan.marqueeText("Serpent Garden"));
        assertEquals("CAF   BAR", RenderPlan.marqueeText("Caf\u00e9  bar"), "a character the font lacks is a gap");
        assertNull(RenderPlan.glyphUv(' '));
        assertNull(RenderPlan.glyphUv('a'), "no lower case in the font");
        float[] a = RenderPlan.glyphUv('A');
        // 'A' = 65 -> index 33 -> column 1, row 2 of the 16 x 4 sheet (80 x 28 px)
        assertEquals(5f / 80f, a[0], 1e-6);
        assertEquals(14f / 28f, a[1], 1e-6);
        assertEquals(10f / 80f, a[2], 1e-6);
        assertEquals(21f / 28f, a[3], 1e-6);
    }

    @Test
    void theCopyClockFillsOverACopyAndFlashesOnlyAFinishedOne() {
        CopyClock clock = new CopyClock();
        assertEquals(0f, clock.observe(false, 0));
        assertTrue(clock.idle(0));
        clock.observe(true, 100);
        assertEquals(0.5f, clock.observe(true, 100 + DuplicatorRules.COPY_TICKS / 2), 1e-6);
        assertEquals(1f, clock.observe(true, 100 + DuplicatorRules.COPY_TICKS + 5), 1e-6);
        clock.observe(false, 100 + DuplicatorRules.COPY_TICKS + 6);
        assertTrue(clock.flashing(100 + DuplicatorRules.COPY_TICKS + 7));
        assertFalse(clock.flashing(100 + DuplicatorRules.COPY_TICKS + 6 + CopyClock.FLASH_TICKS));

        CopyClock cancelled = new CopyClock();
        cancelled.observe(true, 0);
        cancelled.observe(false, 20);
        assertFalse(cancelled.flashing(21), "a copy cut short does not flash");
        assertTrue(cancelled.idle(21));
    }

    @Test
    void animationsLoop() {
        assertEquals(0f, RenderPlan.platterDegrees(0), 1e-6);
        assertEquals(180f, RenderPlan.platterDegrees(20), 1e-4);
        assertEquals(0f, RenderPlan.platterDegrees(40), 1e-4);
        assertEquals(1f, RenderPlan.bob(10), 1e-5);
        assertEquals(0f, RenderPlan.bob(40), 1e-5);
    }

    @Test
    void sliderWheelStepsBySixtiethsAndDragCommitsAreCapped() {
        assertEquals(1, BenchModel.wheelStep(0));
        assertEquals(1, BenchModel.wheelStep(59));
        assertEquals(9, BenchModel.wheelStep(590), "Seconds (10..600) steps by about ten");
        assertTrue(BenchModel.DRAG_COMMIT_HZ <= 10, "005a P4: at most 10 commits a second while dragging");
    }

    @Test
    void theRackMaskLightsOneChipPerSlotKind() {
        assertEquals(0, BenchSlots.rackMask(i -> false));
        int hazards = BenchSlots.rackMask(i -> i == BenchSlots.index(ModuleType.HAZARD, 2));
        assertEquals(1 << ModuleType.HAZARD.ordinal(), hazards, "any stack position lights its kind");
        assertEquals((1 << ModuleType.values().length) - 1, BenchSlots.rackMask(i -> true));
        assertEquals(RenderPlan.RACK_CHIPS, ModuleType.values().length);
    }
}
