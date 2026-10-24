package za.co.neroland.neroarcade.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The creative gallery's floor plan as pure numbers (004 §9): every piece is an {@link Offset} from the invoking
 * operator's block position (north is {@code -z}, the floor layer is {@code dy = 0}), so the plan is checked in a
 * plain JVM and the builder only places what it is told.
 *
 * <pre>
 *   dz
 *   -31   ##### back wall ######  link cables + hub (4) in the wall line
 *   -30         [L][L][L][L]  four-cabinet link row, Marquee Screen (scale 3) above, facing the hall
 *   -27..-29  dais: bench, duplicator, exchange (west side, facing the aisle)
 *   -4..-25   W cabinets (22, facing east) | aisle: cyan cable strip under a glass line at x 0 | E cabinets (21)
 *             a floor sign in front of each cabinet: cartridge title + chip name in the slot colour
 *   -3    entrance (open)
 *    0    operator; the capture camera stands at dz +9, 3 up, looking down the aisle
 *         dx -7                   0                    +7
 * </pre>
 *
 * Walls and ceiling (dy 6, five blocks of headroom so the scale-3 marquee fits) are dark stone; the light comes from
 * the cabinets and hidden light blocks under the ceiling.
 */
public final class GalleryLayout {

    public static final int MIN_DX = -7;
    public static final int MAX_DX = 7;
    public static final int MIN_DZ = -31;
    public static final int MAX_DZ = -3;
    public static final int CEILING_DY = 6;
    /** The aisle strip runs one layer under the floor, seen through a glass line in it (nobody trips on it). */
    public static final int STRIP_DY = -1;

    /** First and last cabinet row positions (front to back). */
    public static final int ROW_FRONT_DZ = -4;
    public static final int WEST_DX = -5;
    public static final int EAST_DX = 5;
    public static final int WEST_COUNT = 22;
    public static final int EAST_COUNT = 21;
    /** Every chip's cabinet: one per shipped chip. */
    public static final int CHIP_CABINETS = WEST_COUNT + EAST_COUNT;

    public static final int LINK_DZ = -30;
    public static final int LINK_CABLE_DZ = -31;
    public static final int[] LINK_DX = {-1, 0, 1, 2};
    public static final int MARQUEE_SCALE = 3;

    /** The light level of the hidden light blocks. */
    public static final int LIGHT_LEVEL = 11;

    /** A horizontal facing, kept free of Minecraft types. */
    public enum Facing {
        NORTH,
        SOUTH,
        WEST,
        EAST
    }

    /** An integer offset from the operator's position. */
    public record Offset(int dx, int dy, int dz) {
    }

    /** One chip's cabinet: where its lower half stands, which way it faces, where its sign goes. */
    public record ChipCabinet(int index, Offset lower, Facing facing, Offset sign) {
    }

    /** One cable piece: its position and which neighbours it connects to (n, s, e, w, up, down). */
    public record Cable(Offset at, boolean north, boolean south, boolean east, boolean west, boolean up, boolean down) {
    }

    private GalleryLayout() {
    }

    // --- the shell -------------------------------------------------------------------------------------

    public static boolean inFootprint(int dx, int dy, int dz) {
        return dx >= MIN_DX && dx <= MAX_DX && dz >= MIN_DZ && dz <= MAX_DZ && dy >= STRIP_DY && dy <= CEILING_DY;
    }

    /** A floor cell over the aisle strip: glass, so the cyan cable below shows. */
    public static boolean stripWindow(int dx, int dz) {
        return dx == 0 && dz <= ROW_FRONT_DZ && dz >= ROW_FRONT_DZ - (WEST_COUNT - 1);
    }

    /** A wall cell: the two side walls and the back wall, between floor and ceiling. */
    public static boolean wall(int dx, int dy, int dz) {
        return dy >= 1 && dy < CEILING_DY && (dx == MIN_DX || dx == MAX_DX || dz == MIN_DZ);
    }

    // --- the chip cabinets -----------------------------------------------------------------------------

    /** The 43 cabinet stands: the west row front to back, then the east row front to back. */
    public static List<ChipCabinet> chipCabinets() {
        List<ChipCabinet> out = new ArrayList<>();
        for (int i = 0; i < WEST_COUNT; i++) {
            out.add(new ChipCabinet(out.size(), new Offset(WEST_DX, 1, ROW_FRONT_DZ - i), Facing.EAST,
                    new Offset(WEST_DX + 1, 1, ROW_FRONT_DZ - i)));
        }
        for (int i = 0; i < EAST_COUNT; i++) {
            out.add(new ChipCabinet(out.size(), new Offset(EAST_DX, 1, ROW_FRONT_DZ - i), Facing.WEST,
                    new Offset(EAST_DX - 1, 1, ROW_FRONT_DZ - i)));
        }
        return out;
    }

    /**
     * Which example cartridge each chip's cabinet runs: the lowest-numbered example that contains the chip (008 §4
     * gallery labels). {@code examples} is slug → the chip ids it uses, in 008 order; {@code chips} the chips in
     * gallery order. A chip no example contains is left out.
     */
    public static Map<String, String> cartridgeFor(List<String> chips, Map<String, Set<String>> examples) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String chip : chips) {
            for (Map.Entry<String, Set<String>> example : examples.entrySet()) {
                if (example.getValue().contains(chip)) {
                    out.put(chip, example.getKey());
                    break;
                }
            }
        }
        return out;
    }

    // --- the workshop dais -----------------------------------------------------------------------------

    public static final Offset BENCH = new Offset(-5, 2, -27);
    public static final Offset DUPLICATOR = new Offset(-5, 2, -28);
    public static final Offset EXCHANGE = new Offset(-5, 2, -29);
    public static final Facing WORKSHOP_FACING = Facing.EAST;

    /** The raised step under the bench, duplicator and exchange. */
    public static boolean dais(int dx, int dy, int dz) {
        return dy == 1 && dx >= -6 && dx <= -4 && dz >= -29 && dz <= -27;
    }

    // --- the link row ----------------------------------------------------------------------------------

    public static final Offset HUB = new Offset(4, 1, LINK_CABLE_DZ);
    public static final Offset MARQUEE = new Offset(1, 3, LINK_DZ);
    /** The link-row cabinet that holds the cartridge (the bus host). */
    public static final int LINK_HOST = 0;
    /** The four-seat example the link row runs. */
    public static final String LINK_CARTRIDGE = "four_way_rally";

    public static List<Offset> linkCabinets() {
        List<Offset> out = new ArrayList<>();
        for (int dx : LINK_DX) {
            out.add(new Offset(dx, 1, LINK_DZ));
        }
        return out;
    }

    /** The link-row cables in the back-wall line: a run from the first cabinet to the hub, and a riser to the marquee. */
    public static List<Cable> linkCables() {
        List<Cable> out = new ArrayList<>();
        int first = LINK_DX[0];
        for (int dx = first; dx < HUB.dx(); dx++) {
            boolean riser = dx == MARQUEE.dx();
            out.add(new Cable(new Offset(dx, 1, LINK_CABLE_DZ), false, dx <= LINK_DX[LINK_DX.length - 1], true,
                    dx > first, riser, false));
        }
        out.add(new Cable(new Offset(MARQUEE.dx(), 2, LINK_CABLE_DZ), false, false, false, false, true, true));
        out.add(new Cable(new Offset(MARQUEE.dx(), 3, LINK_CABLE_DZ), false, true, false, false, false, true));
        return out;
    }

    // --- the aisle strip and the lights ----------------------------------------------------------------

    /** The cyan strip down the aisle: active Link Cable under the floor's glass line, never touching a hub. */
    public static List<Cable> aisleStrip() {
        List<Cable> out = new ArrayList<>();
        int last = ROW_FRONT_DZ - (WEST_COUNT - 1);
        for (int dz = ROW_FRONT_DZ; dz >= last; dz--) {
            // north is -z: the next piece back; south the piece in front
            out.add(new Cable(new Offset(0, STRIP_DY, dz), dz > last, dz < ROW_FRONT_DZ, false, false, false, false));
        }
        return out;
    }

    /** Hidden light blocks under the ceiling: down the aisle every four blocks and over the dais. */
    public static List<Offset> lights() {
        List<Offset> out = new ArrayList<>();
        for (int dz = -4; dz >= -28; dz -= 4) {
            out.add(new Offset(-2, CEILING_DY - 1, dz));
            out.add(new Offset(2, CEILING_DY - 1, dz));
        }
        out.add(new Offset(-5, CEILING_DY - 1, -28));
        out.add(new Offset(1, CEILING_DY - 1, -29));
        return out;
    }

    // --- capture poses ---------------------------------------------------------------------------------

    /** A camera pose relative to the origin block's centre: where it stands and what it looks at. */
    public record Shot(String name, double x, double y, double z, double tx, double ty, double tz) {
    }

    /** The capture's shots; the first is the hero frame (004 §9: 12 back, 3 up, down the aisle). */
    public static final List<Shot> SHOTS = List.of(
            new Shot("hero", 0, 3, ROW_FRONT_DZ + 13, 0, 1.5, -20),
            new Shot("aisle_west", 1.5, 2.6, -6, WEST_DX, 2, -14),
            new Shot("aisle_east", -1.5, 2.6, -6, EAST_DX, 2, -14),
            new Shot("link_row", 0.5, 3.2, -24, 0.5, 3, LINK_DZ),
            new Shot("workshop", -1, 3.4, -24, BENCH.dx(), 2.5, DUPLICATOR.dz()));

    /** The pose the budget frame is measured from (the hero frame: the most screens in view). */
    public static final String BUDGET_SHOT = "hero";
}
