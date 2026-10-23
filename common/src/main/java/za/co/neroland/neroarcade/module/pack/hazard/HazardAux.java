package za.co.neroland.neroarcade.module.pack.hazard;

import za.co.neroland.neroarcade.module.hook.ArenaModule;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.runtime.Cell;
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.EntityKind;
import za.co.neroland.neroarcade.runtime.MatchState;

/**
 * Shared, static, pure helpers of the hazard chips: the aux-word layout (bits 0–1 tag, never overwritten; 2–9
 * {@code acc}; 10–11 {@code dir}; 12–19 {@code counter}; 20–27 {@code data}), per-tag entity counts, the hazard
 * respawn MARKER and the random free-cell placement (the same rule as the pickups' {@code Placement}; duplicated
 * because each package keeps its helpers private). No state; integer-only.
 */
final class HazardAux {

    /** Directions stored in the {@code dir} bits. */
    static final int UP = 0;
    static final int DOWN = 1;
    static final int LEFT = 2;
    static final int RIGHT = 3;

    /** {@code data} value that marks a MARKER as a hazard chip's respawn timer (pickup markers use another). */
    static final int MARKER_DATA = 0xA1;

    private static final int ACC_SHIFT = 2;
    private static final int DIR_SHIFT = 10;
    private static final int COUNTER_SHIFT = 12;
    private static final int DATA_SHIFT = 20;
    private static final int BYTE = 0xFF;
    private static final int PLACEMENT_TRIES = 16;

    private HazardAux() {
    }

    static int acc(int aux) {
        return (aux >>> ACC_SHIFT) & BYTE;
    }

    static int withAcc(int aux, int value) {
        return (aux & ~(BYTE << ACC_SHIFT)) | ((value & BYTE) << ACC_SHIFT);
    }

    static int dir(int aux) {
        return (aux >>> DIR_SHIFT) & 3;
    }

    static int withDir(int aux, int value) {
        return (aux & ~(3 << DIR_SHIFT)) | ((value & 3) << DIR_SHIFT);
    }

    static int counter(int aux) {
        return (aux >>> COUNTER_SHIFT) & BYTE;
    }

    static int withCounter(int aux, int value) {
        return (aux & ~(BYTE << COUNTER_SHIFT)) | ((value & BYTE) << COUNTER_SHIFT);
    }

    static int data(int aux) {
        return (aux >>> DATA_SHIFT) & BYTE;
    }

    static int withData(int aux, int value) {
        return (aux & ~(BYTE << DATA_SHIFT)) | ((value & BYTE) << DATA_SHIFT);
    }

    static int dx(int dir) {
        return dir == LEFT ? -1 : dir == RIGHT ? 1 : 0;
    }

    static int dy(int dir) {
        return dir == UP ? -1 : dir == DOWN ? 1 : 0;
    }

    /** UP↔DOWN, LEFT↔RIGHT. */
    static int opposite(int dir) {
        return dir ^ 1;
    }

    /** Centre of cell {@code c} in fixed point. */
    static int centre(int c) {
        return (c << MatchState.FP_SHIFT) + MatchState.FP / 2;
    }

    /** Live entities of {@code kind} carrying stack tag {@code tag} (MARKERs: only hazard respawn markers). */
    static int countTagged(MatchState state, EntityKind kind, int tag) {
        int n = 0;
        for (int i = 0; i < state.entityCount(); i++) {
            int id = state.entityIdAt(i);
            if (state.get(id, Entities.KIND) != kind.ordinal()) {
                continue;
            }
            int aux = state.aux(id);
            if ((aux & 3) != tag) {
                continue;
            }
            if (kind == EntityKind.MARKER && data(aux) != MARKER_DATA) {
                continue;
            }
            n++;
        }
        return n;
    }

    /** A hidden hazard respawn MARKER of {@code tag} that housekeeping removes after {@code ttl} ticks. */
    static void spawnMarker(MatchState state, int tag, int ttl) {
        int id = state.spawn(EntityKind.MARKER, 0, 0, -1, tag);
        if (id < 0) {
            return;
        }
        state.set(id, Entities.FLAGS, Entities.F_HIDDEN);
        state.set(id, Entities.TTL, ttl);
        state.setAux(id, withData(state.aux(id), MARKER_DATA));
    }

    /** Signed shortest delta {@code to - from} on an axis of {@code size} cells (wrap-aware when it wraps). */
    static int delta(int from, int to, int size, boolean wraps) {
        int d = to - from;
        if (wraps) {
            d = Math.floorMod(d, size);
            if (d > size / 2) {
                d -= size;
            }
        }
        return d;
    }

    /**
     * Up to 16 RNG draws of {@code (nextInt(0, w-1), nextInt(0, h-1))}; the first cell that is EMPTY or LANE, holds
     * no entity (hidden MARKERs excepted) and is at least {@code minPawnDist} Chebyshev from every pawn. At match
     * start ({@code atStart}) the pawns do not exist yet, so every seat's arena spawn cell stands in for them;
     * otherwise every alive seat's pawn cell counts. Returns null after 16 misses (the caller skips this tick).
     */
    static int[] randomFreeCell(ModuleContext ctx, int minPawnDist, boolean atStart) {
        MatchState state = ctx.state();
        int w = state.width();
        int h = state.height();
        int seats = state.seatCount();
        int[] px = new int[seats];
        int[] py = new int[seats];
        int pawns = 0;
        if (atStart) {
            if (ctx.game() != null) {
                ArenaModule arena = ctx.game().arena();
                ModuleContext arenaCtx = ctx.withProps(ctx.game().arenaProps(), 0);
                for (int s = 0; s < seats; s++) {
                    int[] cell = arena.spawnCell(arenaCtx, s);
                    px[pawns] = cell[0];
                    py[pawns] = cell[1];
                    pawns++;
                }
            }
        } else {
            for (int s = 0; s < seats; s++) {
                int id = state.pawnId(s);
                if (state.seatAlive(s) && id >= 0 && state.exists(id)) {
                    px[pawns] = state.get(id, Entities.X) >> MatchState.FP_SHIFT;
                    py[pawns] = state.get(id, Entities.Y) >> MatchState.FP_SHIFT;
                    pawns++;
                }
            }
        }
        for (int attempt = 0; attempt < PLACEMENT_TRIES; attempt++) {
            int x = ctx.rng().nextInt(0, w - 1);
            int y = ctx.rng().nextInt(0, h - 1);
            byte cell = state.cell(x, y);
            if (cell != Cell.EMPTY && cell != Cell.LANE) {
                continue;
            }
            boolean ok = true;
            for (int p = 0; p < pawns && ok; p++) {
                int ddx = Math.abs(delta(px[p], x, w, state.wrapsX()));
                int ddy = Math.abs(delta(py[p], y, h, state.wrapsY()));
                ok = Math.max(ddx, ddy) >= minPawnDist;
            }
            for (int i = 0; i < state.entityCount() && ok; i++) {
                int id = state.entityIdAt(i);
                if (state.get(id, Entities.KIND) != EntityKind.MARKER.ordinal() && state.occupies(id, x, y)) {
                    ok = false;
                }
            }
            if (ok) {
                return new int[] {x, y};
            }
        }
        return null;
    }
}
