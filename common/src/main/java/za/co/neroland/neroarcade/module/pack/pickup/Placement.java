package za.co.neroland.neroarcade.module.pack.pickup;

import java.util.Set;

import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.hook.ArenaModule;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.runtime.Cell;
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.EntityKind;
import za.co.neroland.neroarcade.runtime.MatchState;

/**
 * Shared, static, pure helpers of the pickup chips: random free-cell placement, per-tag counts and the respawn
 * recipe (a hidden MARKER with a TTL per collected pickup; {@code live = pickups + markers} of the tag is topped
 * up to {@code count}). Pickup markers carry aux {@code data} {@link #MARKER_DATA} so a hazard stack with the same
 * index never counts them. No state; integer-only.
 */
final class Placement {

    /** {@code data} (aux bits 20–27) of a pickup respawn MARKER. */
    static final int MARKER_DATA = 0xB2;
    /** Pawn clearance of a respawned pickup and of the start placement. */
    static final int PAWN_CLEARANCE = 3;

    private static final int DATA_SHIFT = 20;
    private static final int BYTE = 0xFF;
    private static final int PLACEMENT_TRIES = 16;

    private Placement() {
    }

    static int data(int aux) {
        return (aux >>> DATA_SHIFT) & BYTE;
    }

    /** Live PICKUP entities of {@code tag}. */
    static int countPickups(MatchState state, int tag) {
        return count(state, EntityKind.PICKUP, tag);
    }

    /** Live pickup respawn MARKERs of {@code tag}. */
    static int countMarkers(MatchState state, int tag) {
        return count(state, EntityKind.MARKER, tag);
    }

    private static int count(MatchState state, EntityKind kind, int tag) {
        int n = 0;
        for (int i = 0; i < state.entityCount(); i++) {
            int id = state.entityIdAt(i);
            if (state.get(id, Entities.KIND) != kind.ordinal()) {
                continue;
            }
            int aux = state.aux(id);
            if ((aux & 3) == tag && (kind != EntityKind.MARKER || data(aux) == MARKER_DATA)) {
                n++;
            }
        }
        return n;
    }

    /** The respawn timer of one collected pickup: a hidden MARKER of {@code tag}, removed after {@code delay}. */
    static void spawnMarker(MatchState state, int tag, int delay) {
        int id = state.spawn(EntityKind.MARKER, 0, 0, -1, tag);
        if (id < 0) {
            return;
        }
        state.set(id, Entities.FLAGS, Entities.F_HIDDEN);
        state.set(id, Entities.TTL, delay);
        state.setAux(id, (state.aux(id) & ~(BYTE << DATA_SHIFT)) | (MARKER_DATA << DATA_SHIFT));
    }

    /** One PICKUP of the context's stack at {@code cell}; returns its id or -1. */
    static int spawnPickup(ModuleContext ctx, int[] cell) {
        return ctx.state().spawn(EntityKind.PICKUP, (cell[0] << MatchState.FP_SHIFT) + MatchState.FP / 2,
                (cell[1] << MatchState.FP_SHIFT) + MatchState.FP / 2, -1, ctx.stackIndex());
    }

    /** Places up to {@code n} pickups at match start ({@code minPawnDist} from every seat's spawn cell). */
    static void placeAtStart(ModuleContext ctx, int n, int minPawnDist) {
        for (int i = 0; i < n; i++) {
            int[] cell = randomFreeCell(ctx, minPawnDist, true);
            if (cell == null || spawnPickup(ctx, cell) < 0) {
                return;
            }
        }
    }

    /** The respawn recipe's tick: while pickups + markers of this stack are fewer than {@code count}, place one. */
    static void topUp(ModuleContext ctx, int count) {
        MatchState state = ctx.state();
        int tag = ctx.stackIndex();
        int live = countPickups(state, tag) + countMarkers(state, tag);
        while (live < count) {
            int[] cell = randomFreeCell(ctx, PAWN_CLEARANCE, false);
            if (cell == null || spawnPickup(ctx, cell) < 0) {
                return;
            }
            live++;
        }
    }

    /** True when the RULE chip declares {@code flag} (capability query, never an id). */
    static boolean ruleHas(ModuleContext ctx, ModuleFlag flag) {
        if (ctx.game() == null) {
            return false;
        }
        Set<ModuleFlag> flags = ctx.game().flags(ModuleType.RULE);
        return flags.contains(flag);
    }

    private static int delta(int from, int to, int size, boolean wraps) {
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
     * start ({@code atStart}) the pawns do not exist yet (002 §6 order), so every seat's arena spawn cell stands in
     * for them; otherwise every alive seat's pawn cell counts. Null after 16 misses (skip this tick).
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
