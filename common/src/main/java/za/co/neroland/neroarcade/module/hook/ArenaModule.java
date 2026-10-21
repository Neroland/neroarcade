package za.co.neroland.neroarcade.module.hook;

import za.co.neroland.neroarcade.module.ArcadeModule;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.runtime.Cell;
import za.co.neroland.neroarcade.runtime.MatchState;

/**
 * ARENA: the board. Decides its size and topology and fills the cells at match start. Pure (see
 * {@link ArcadeModule}); {@link #build} runs before every other match-start hook.
 */
public interface ArenaModule extends ArcadeModule {

    @Override
    default ModuleType type() {
        return ModuleType.ARENA;
    }

    /** Cells, 4..{@code Budgets.maxArenaSide()} (the runtime clamps; the validator refuses oversize). */
    int width(PropertyValues props);

    int height(PropertyValues props);

    /** Ring/tube topology: true; grid: false. A MODIFIER may override via {@code MatchState.setWrap}. */
    boolean wraps(PropertyValues props);

    /** Fills the cells (walls, floor, pillars) at match start. */
    void build(ModuleContext ctx);

    /**
     * Step 1b: runs right after PACE on every simulated tick (never on a paced-out one), before any input is
     * read, so every other hook sees this tick's board. May shift cells, write new rows from {@code ctx.rng()}
     * and {@code damageSeat} a seat whose pawn now sits in a blocking cell. Must not touch entities it did not
     * spawn. Default: nothing.
     */
    default void tick(ModuleContext ctx) {
    }

    /**
     * The seat's spawn cell {@code {x, y}}. Default: a distinct inset corner per seat (top-left, bottom-right,
     * top-right, bottom-left), moved to the nearest EMPTY cell in a fixed scan order. Deterministic.
     */
    default int[] spawnCell(ModuleContext ctx, int seat) {
        MatchState state = ctx.state();
        int w = state.width();
        int h = state.height();
        int ax = switch (seat & 3) {
            case 0, 3 -> 1;
            default -> w - 2;
        };
        int ay = switch (seat & 3) {
            case 0, 2 -> 1;
            default -> h - 2;
        };
        int max = Math.max(w, h);
        for (int r = 0; r <= max; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) {
                        continue;
                    }
                    int x = ax + dx;
                    int y = ay + dy;
                    if (x >= 0 && y >= 0 && x < w && y < h && state.cell(x, y) == Cell.EMPTY) {
                        return new int[] {x, y};
                    }
                }
            }
        }
        return new int[] {Math.max(0, Math.min(w - 1, ax)), Math.max(0, Math.min(h - 1, ay))};
    }
}
