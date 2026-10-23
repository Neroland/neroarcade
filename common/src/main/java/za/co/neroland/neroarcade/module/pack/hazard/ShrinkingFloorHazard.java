package za.co.neroland.neroarcade.module.pack.hazard;

import java.util.List;
import java.util.Set;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.hook.HazardModule;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.runtime.Cell;
import za.co.neroland.neroarcade.runtime.MatchState;

/**
 * {@code neroarcade:hazard/shrinking_floor} — the outer ring of the floor falls away at a steady interval.
 * <ul>
 *   <li>{@code interval} — ticks between rings (the first falls at tick {@code interval}; no telegraph, the
 *       20-tick minimum is the warning).</li>
 *   <li>{@code min_size} — a ring {@code r} only falls while {@code w − 2r > min_size} and
 *       {@code h − 2r > min_size}.</li>
 * </ul>
 * No entities. At every tick {@code t > 0} with {@code t % interval == 0} the ring that falls is the outermost
 * ring {@code r} (cells with {@code min(x, y, w−1−x, h−1−y) == r}) that still holds an EMPTY or LANE cell — on a
 * bordered arena the all-WALL border ring is skipped, so the first interval already takes the floor's edge. Its
 * EMPTY and LANE cells become VOID; WALL, EXIT, EXIT_LOCKED, GOAL and BLOCK cells stay. Pawns die standing in VOID
 * (their own end-of-tick check, 001 A7). A pickup left on VOID stays there, unreachable: {@code clear_board} then
 * ends by its clock. Lethal (it kills through VOID) but never touches a pawn itself. Stateless; pure.
 */
public final class ShrinkingFloorHazard implements HazardModule {

    public static final ShrinkingFloorHazard INSTANCE = new ShrinkingFloorHazard();

    private static final Identifier ID = Identifier.fromNamespaceAndPath("neroarcade", "hazard/shrinking_floor");

    private static final List<PropertySpec> PROPERTIES = List.of(
            PropertySpec.integer("interval", 100, 20, 400),
            PropertySpec.integer("min_size", 4, 2, 16));

    private static final Set<ModuleFlag> FLAGS = Set.of(ModuleFlag.LETHAL);

    private ShrinkingFloorHazard() {
    }

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public List<PropertySpec> properties() {
        return PROPERTIES;
    }

    @Override
    public Set<ModuleFlag> flags() {
        return FLAGS;
    }

    /** "Shrink 100 t · min 4". */
    @Override
    public Object[] effectArgs(PropertyValues props) {
        return new Object[] {props.getInt("interval"), props.getInt("min_size")};
    }

    @Override
    public void onMatchStart(ModuleContext ctx) {
    }

    @Override
    public void tick(ModuleContext ctx) {
        int t = ctx.tick();
        int interval = ctx.props().getInt("interval");
        if (t <= 0 || t % interval != 0) {
            return;
        }
        MatchState state = ctx.state();
        int w = state.width();
        int h = state.height();
        int rings = (Math.min(w, h) + 1) / 2;
        for (int r = 0; r < rings; r++) {
            if (!hasFloor(state, r)) {
                continue;
            }
            int minSize = ctx.props().getInt("min_size");
            if (w - 2 * r > minSize && h - 2 * r > minSize) {
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        if (ring(x, y, w, h) == r && floor(state.cell(x, y))) {
                            state.setCell(x, y, Cell.VOID);
                        }
                    }
                }
            }
            return;
        }
    }

    static int ring(int x, int y, int w, int h) {
        return Math.min(Math.min(x, y), Math.min(w - 1 - x, h - 1 - y));
    }

    private static boolean floor(byte cell) {
        return cell == Cell.EMPTY || cell == Cell.LANE;
    }

    private static boolean hasFloor(MatchState state, int r) {
        int w = state.width();
        int h = state.height();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (ring(x, y, w, h) == r && floor(state.cell(x, y))) {
                    return true;
                }
            }
        }
        return false;
    }
}
