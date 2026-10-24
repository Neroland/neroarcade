package za.co.neroland.neroarcade.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.bus.BusNode;
import za.co.neroland.neroarcade.bus.BusTopology;
import za.co.neroland.neroarcade.command.GalleryLayout.Cable;
import za.co.neroland.neroarcade.command.GalleryLayout.ChipCabinet;
import za.co.neroland.neroarcade.command.GalleryLayout.Offset;
import za.co.neroland.neroarcade.module.ArcadeModule;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.runtime.ShippedReplays;

/**
 * The gallery's floor plan: everything inside the footprint and nothing on top of anything else; one cabinet per
 * shipped chip, each with an example cartridge that uses it; the link row really is one bus of four cabinets and a
 * marquee (discovery run on the plan), and the aisle strip joins nothing; the capture poses look into the hall.
 */
class GalleryLayoutTest {

    @AfterEach
    void tearDown() {
        TestModules.reset();
    }

    private static long key(Offset o) {
        return BlockPos.asLong(o.dx(), o.dy(), o.dz());
    }

    /** Every exhibit cell the plan places (cabinets count both halves, the kiosk both halves). */
    private static Map<Long, String> exhibits() {
        Map<Long, String> cells = new HashMap<>();
        for (ChipCabinet c : GalleryLayout.chipCabinets()) {
            put(cells, c.lower(), "cabinet " + c.index());
            put(cells, new Offset(c.lower().dx(), c.lower().dy() + 1, c.lower().dz()), "top of cabinet " + c.index());
            put(cells, c.sign(), "sign " + c.index());
        }
        for (Offset o : GalleryLayout.linkCabinets()) {
            put(cells, o, "link cabinet");
            put(cells, new Offset(o.dx(), o.dy() + 1, o.dz()), "top of link cabinet");
        }
        for (Cable c : GalleryLayout.linkCables()) {
            put(cells, c.at(), "link cable");
        }
        for (Cable c : GalleryLayout.aisleStrip()) {
            put(cells, c.at(), "strip");
        }
        put(cells, GalleryLayout.HUB, "hub");
        put(cells, GalleryLayout.MARQUEE, "marquee");
        put(cells, GalleryLayout.BENCH, "bench");
        put(cells, GalleryLayout.DUPLICATOR, "duplicator");
        put(cells, GalleryLayout.EXCHANGE, "exchange");
        Offset ex = GalleryLayout.EXCHANGE;
        put(cells, new Offset(ex.dx(), ex.dy() + 1, ex.dz()), "top of exchange");
        for (Offset o : GalleryLayout.lights()) {
            put(cells, o, "light");
        }
        return cells;
    }

    private static void put(Map<Long, String> cells, Offset at, String what) {
        assertTrue(GalleryLayout.inFootprint(at.dx(), at.dy(), at.dz()), what + " outside the footprint: " + at);
        String before = cells.put(key(at), what);
        assertEquals(null, before, what + " placed on top of " + before + " at " + at);
    }

    @Test
    void everythingFitsAndNothingOverlaps() {
        Map<Long, String> cells = exhibits();
        for (Map.Entry<Long, String> e : cells.entrySet()) {
            BlockPos p = BlockPos.of(e.getKey());
            boolean wallLine = GalleryLayout.wall(p.getX(), p.getY(), p.getZ());
            boolean allowedInWall = e.getValue().equals("link cable") || e.getValue().equals("hub");
            assertTrue(!wallLine || allowedInWall, e.getValue() + " inside a wall at " + p);
            assertTrue(p.getY() < GalleryLayout.CEILING_DY, e.getValue() + " in the ceiling");
            assertFalse(GalleryLayout.dais(p.getX(), p.getY(), p.getZ()), e.getValue() + " inside the dais at " + p);
        }
        assertEquals(GalleryLayout.CHIP_CABINETS, GalleryLayout.chipCabinets().size());
        // the workshop stands on the dais
        for (Offset o : List.of(GalleryLayout.BENCH, GalleryLayout.DUPLICATOR, GalleryLayout.EXCHANGE)) {
            assertTrue(GalleryLayout.dais(o.dx(), o.dy() - 1, o.dz()), "on the dais: " + o);
        }
        // the scale-3 marquee's surface (2 px up, two blocks tall) stays under the ceiling
        double top = GalleryLayout.MARQUEE.dy() + 2.0 / 16.0 + GalleryLayout.MARQUEE_SCALE * 2.0 / 3.0;
        assertTrue(top <= GalleryLayout.CEILING_DY, "marquee top " + top);
    }

    @Test
    void theLinkRowIsOneBusAndTheAisleStripJoinsNothing() {
        Map<Long, BusNode> nodes = new HashMap<>();
        nodes.put(key(GalleryLayout.HUB), BusNode.HUB);
        nodes.put(key(GalleryLayout.MARQUEE), BusNode.MARQUEE);
        for (Cable c : GalleryLayout.linkCables()) {
            nodes.put(key(c.at()), BusNode.CABLE);
        }
        for (Cable c : GalleryLayout.aisleStrip()) {
            nodes.put(key(c.at()), BusNode.CABLE);
        }
        for (Offset o : GalleryLayout.linkCabinets()) {
            nodes.put(key(o), BusNode.CABINET);
        }
        for (ChipCabinet c : GalleryLayout.chipCabinets()) {
            nodes.put(key(c.lower()), BusNode.CABINET);
        }
        Offset h = GalleryLayout.HUB;
        BusTopology bus = BusTopology.discover(p -> nodes.getOrDefault(p.asLong(), BusNode.NONE),
                new BlockPos(h.dx(), h.dy(), h.dz()), p -> false);
        Set<Long> cabinets = new HashSet<>();
        bus.cabinets().forEach(p -> cabinets.add(p.asLong()));
        Set<Long> expected = new HashSet<>();
        GalleryLayout.linkCabinets().forEach(o -> expected.add(key(o)));
        assertEquals(expected, cabinets, "the hub finds exactly the four link-row cabinets");
        assertEquals(1, bus.marquees().size());
        assertTrue(bus.cables().size() <= BusTopology.MAX_CABLES);
        assertTrue(bus.overflow().isEmpty() && bus.conflicts().isEmpty());
        Set<Long> strip = new HashSet<>();
        GalleryLayout.aisleStrip().forEach(c -> strip.add(key(c.at())));
        for (BlockPos cable : bus.cables()) {
            assertFalse(strip.contains(cable.asLong()), "the bus walked into the aisle strip");
        }
    }

    @Test
    void cableArmsMatchTheirNeighbours() {
        Map<Long, String> cells = exhibits();
        List<Cable> cables = new ArrayList<>(GalleryLayout.linkCables());
        cables.addAll(GalleryLayout.aisleStrip());
        for (Cable c : cables) {
            Offset o = c.at();
            assertEquals(joins(cells, o.dx(), o.dy(), o.dz() - 1), c.north(), "north of " + o);
            assertEquals(joins(cells, o.dx(), o.dy(), o.dz() + 1), c.south(), "south of " + o);
            assertEquals(joins(cells, o.dx() + 1, o.dy(), o.dz()), c.east(), "east of " + o);
            assertEquals(joins(cells, o.dx() - 1, o.dy(), o.dz()), c.west(), "west of " + o);
            assertEquals(joins(cells, o.dx(), o.dy() + 1, o.dz()), c.up(), "up of " + o);
            assertEquals(joins(cells, o.dx(), o.dy() - 1, o.dz()), c.down(), "down of " + o);
        }
    }

    /** What a cable connects to (LinkCableBlock.connects): cables, the hub, a marquee, a cabinet's lower half. */
    private static boolean joins(Map<Long, String> cells, int x, int y, int z) {
        String what = cells.get(BlockPos.asLong(x, y, z));
        return what != null && (what.equals("link cable") || what.equals("strip") || what.equals("hub")
                || what.equals("marquee") || what.startsWith("cabinet ") || what.equals("link cabinet"));
    }

    @Test
    void oneCabinetPerShippedChipEachWithAnExampleThatUsesIt() {
        TestModules.installShipped();
        List<ArcadeModule> chips = GalleryCommands.chips();
        assertEquals(GalleryLayout.CHIP_CABINETS, chips.size(), "one stand per shipped chip");
        Map<String, Set<String>> sets = GalleryCommands.chipSets(ShippedReplays.all());
        Map<String, String> assignment = GalleryLayout.cartridgeFor(
                chips.stream().map(m -> m.id().toString()).toList(), sets);
        assertEquals(chips.size(), assignment.size(), "every chip has a demo cartridge: " + assignment);
        assignment.forEach((chip, slug) -> assertTrue(sets.get(slug).contains(chip), chip + " in " + slug));
        assertEquals(4, ShippedReplays.bySlug(GalleryLayout.LINK_CARTRIDGE).orElseThrow().seats(),
                "the link row runs a four-seat demo");
    }

    @Test
    void theCapturePosesLookIntoTheHall() {
        assertEquals("hero", GalleryLayout.SHOTS.get(0).name());
        Set<String> names = new HashSet<>();
        for (GalleryLayout.Shot shot : GalleryLayout.SHOTS) {
            assertTrue(shot.name().matches("[a-z0-9_]+") && names.add(shot.name()), shot.name());
            assertTrue(GalleryLayout.inFootprint((int) Math.floor(shot.tx()), 1, (int) Math.floor(shot.tz())),
                    shot.name() + " looks at the hall");
        }
        GalleryLayout.Shot hero = GalleryLayout.SHOTS.get(0);
        assertEquals(12, hero.z() - GalleryLayout.MAX_DZ, 1e-9, "004 §9: twelve blocks back from the entrance");
        assertEquals(3, hero.y(), 1e-9, "three up");
        assertTrue(names.contains(GalleryLayout.BUDGET_SHOT));
    }
}
