package za.co.neroland.neroarcade.runtime;

import java.util.Set;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.hook.HazardModule;
import za.co.neroland.neroarcade.module.hook.ModuleContext;

/**
 * The stock runtime opponent: {@code PawnModule.opponentInput}'s default. A pure function of the context (no
 * RNG draw, no field), so a replay re-derives every decision from the same start.
 *
 * <p>{@link #greedy}, in priority order:
 * <ol>
 *   <li><b>Flee.</b> If a lethal hazard or another seat's projectile is within {@link #THREAT_RADIUS} cells
 *       (Chebyshev), step in the free direction that leaves the largest Chebyshev distance to it (ties: the
 *       direction with more free cells ahead, then UP, DOWN, LEFT, RIGHT), and press A.</li>
 *   <li><b>Collect.</b> Else step toward the nearest PICKUP along the axis of larger distance (the other axis
 *       when that one is blocked).</li>
 *   <li><b>Hunt.</b> Else, when the PAWN has {@code LETHAL_TO_PAWNS} or {@code FIRES}, step toward the nearest
 *       other seat's pawn the same way, pressing A when aligned on an axis.</li>
 *   <li><b>Wander.</b> Else hold the pawn's current direction of travel, reversing at a wall; from rest, take
 *       the direction with the most free cells.</li>
 * </ol>
 * Entities are scanned in id order and seats 0→3; distance ties keep the lower id / seat. Distances wrap where
 * the board wraps.
 *
 * <p><b>Target vs direction</b> (orchestrator decision 2026-10-21): a decision is split into {@link #target} (which
 * of the four goals above, and for collect/hunt the cell aimed at) and {@link #steer} (the buttons toward that
 * target from where the pawn is <i>now</i>). For a pawn that keeps the default {@code opponentInput},
 * {@code ArcadeRuntime} re-chooses the target every {@code OPPONENT_CADENCE} simulated ticks and steers every
 * simulated tick, so the opponent can stop on any cell instead of moving in cadence-sized hops. The held target is
 * an input latch like the held {@code SeatInput} (002 R5): both functions are pure, a replay re-derives it.
 * {@link #greedy} is {@code steer(target(..))}, for chips that call it from their own override.
 */
public final class RuntimeOpponent {

    /** Cells (Chebyshev) within which a threat makes the opponent flee. */
    public static final int THREAT_RADIUS = 3;

    /** {@link #target} result when there is nothing to do (no pawn). */
    public static final int NO_TARGET = 0;

    private static final int MODE_FLEE = 1;
    private static final int MODE_COLLECT = 2;
    private static final int MODE_HUNT = 3;
    private static final int MODE_WANDER = 4;

    private static final int NONE = -1;
    private static final int UP = 0;
    private static final int DOWN = 1;
    private static final int LEFT = 2;
    private static final int RIGHT = 3;

    private RuntimeOpponent() {
    }

    /** One full decision: {@code steer(ctx, seat, target(ctx, seat))}. Pure. */
    public static SeatInput greedy(ModuleContext ctx, int seat) {
        return steer(ctx, seat, target(ctx, seat));
    }

    /**
     * Chooses what the opponent is doing: flee (with the escape direction), collect or hunt (with the target cell),
     * or wander. Packed as {@code mode << 24 | y << 8 | x} (flee: {@code x} = direction); {@link #NO_TARGET} without
     * a pawn. Pure.
     */
    public static int target(ModuleContext ctx, int seat) {
        MatchState s = ctx.state();
        int pawn = s.pawnId(seat);
        if (pawn < 0 || !s.exists(pawn)) {
            return NO_TARGET;
        }
        int px = s.get(pawn, Entities.X) >> MatchState.FP_SHIFT;
        int py = s.get(pawn, Entities.Y) >> MatchState.FP_SHIFT;

        // 1. flee
        int threatDx = 0;
        int threatDy = 0;
        int threatDist = Integer.MAX_VALUE;
        // 2. collect
        int pickupDx = 0;
        int pickupDy = 0;
        int pickupDist = Integer.MAX_VALUE;
        for (int i = 0; i < s.entityCount(); i++) {
            int id = s.entityIdAt(i);
            int kind = s.get(id, Entities.KIND);
            boolean threat = kind == EntityKind.HAZARD.ordinal() ? lethalHazard(ctx, id)
                    : kind == EntityKind.PROJECTILE.ordinal() && s.get(id, Entities.OWNER) != seat;
            boolean pickup = kind == EntityKind.PICKUP.ordinal();
            if (!threat && !pickup) {
                continue;
            }
            int dx = deltaX(s, px, s.get(id, Entities.X) >> MatchState.FP_SHIFT);
            int dy = deltaY(s, py, s.get(id, Entities.Y) >> MatchState.FP_SHIFT);
            int d = Math.max(Math.abs(dx), Math.abs(dy));
            if (threat && d < threatDist) {
                threatDist = d;
                threatDx = dx;
                threatDy = dy;
            } else if (pickup && d < pickupDist) {
                pickupDist = d;
                pickupDx = dx;
                pickupDy = dy;
            }
        }
        if (threatDist <= THREAT_RADIUS) {
            int best = NONE;
            int bestDist = -1;
            int bestFree = -1;
            for (int dir = UP; dir <= RIGHT; dir++) {
                if (blocked(s, px + dx(dir), py + dy(dir))) {
                    continue;
                }
                int ndx = wrapDelta(threatDx - dx(dir), s.width(), s.wrapsX());
                int ndy = wrapDelta(threatDy - dy(dir), s.height(), s.wrapsY());
                int dist = Math.max(Math.abs(ndx), Math.abs(ndy));
                int free = freeRun(s, px, py, dir);
                if (dist > bestDist || (dist == bestDist && free > bestFree)) {
                    best = dir;
                    bestDist = dist;
                    bestFree = free;
                }
            }
            return pack(MODE_FLEE, best, 0);
        }
        if (pickupDist != Integer.MAX_VALUE && toward(s, px, py, pickupDx, pickupDy) != NONE) {
            return pack(MODE_COLLECT, cellX(s, px + pickupDx), cellY(s, py + pickupDy));
        }

        // 3. hunt
        Set<ModuleFlag> pawnFlags = ctx.game() == null ? Set.of() : ctx.game().flags(ModuleType.PAWN);
        if (pawnFlags.contains(ModuleFlag.LETHAL_TO_PAWNS) || pawnFlags.contains(ModuleFlag.FIRES)) {
            int huntDx = 0;
            int huntDy = 0;
            int huntDist = Integer.MAX_VALUE;
            for (int other = 0; other < s.seatCount(); other++) {
                int id = s.pawnId(other);
                if (other == seat || !s.seatAlive(other) || id < 0 || !s.exists(id)) {
                    continue;
                }
                int dx = deltaX(s, px, s.get(id, Entities.X) >> MatchState.FP_SHIFT);
                int dy = deltaY(s, py, s.get(id, Entities.Y) >> MatchState.FP_SHIFT);
                int d = Math.max(Math.abs(dx), Math.abs(dy));
                if (d < huntDist) {
                    huntDist = d;
                    huntDx = dx;
                    huntDy = dy;
                }
            }
            if (huntDist != Integer.MAX_VALUE) {
                boolean aligned = huntDx == 0 || huntDy == 0;
                if (toward(s, px, py, huntDx, huntDy) != NONE || aligned) {
                    return pack(MODE_HUNT, cellX(s, px + huntDx), cellY(s, py + huntDy));
                }
            }
        }

        // 4. wander
        return pack(MODE_WANDER, 0, 0);
    }

    /**
     * The buttons for this tick toward {@code target} (from {@link #target}) from the pawn's current cell. Collect
     * and hunt stop on the target cell; a target that has become unreachable falls back to wandering. Pure.
     */
    public static SeatInput steer(ModuleContext ctx, int seat, int target) {
        MatchState s = ctx.state();
        int pawn = s.pawnId(seat);
        int mode = target >>> 24;
        if (pawn < 0 || !s.exists(pawn) || mode == 0) {
            return SeatInput.NONE;
        }
        int px = s.get(pawn, Entities.X) >> MatchState.FP_SHIFT;
        int py = s.get(pawn, Entities.Y) >> MatchState.FP_SHIFT;
        int tx = target & 0xFF;
        int ty = (target >> 8) & 0xFF;
        switch (mode) {
            case MODE_FLEE -> {
                return input(tx == 0xFF ? NONE : tx, true);
            }
            case MODE_COLLECT, MODE_HUNT -> {
                int tdx = deltaX(s, px, tx);
                int tdy = deltaY(s, py, ty);
                boolean hunt = mode == MODE_HUNT;
                if (tdx == 0 && tdy == 0) {
                    return input(NONE, hunt);
                }
                int dir = toward(s, px, py, tdx, tdy);
                boolean aligned = hunt && (tdx == 0 || tdy == 0);
                if (dir != NONE || aligned) {
                    return input(dir, aligned);
                }
            }
            default -> {
            }
        }
        // 4. wander
        int vx = s.get(pawn, Entities.VX);
        int vy = s.get(pawn, Entities.VY);
        int dir = vx > 0 ? RIGHT : vx < 0 ? LEFT : vy > 0 ? DOWN : vy < 0 ? UP : NONE;
        if (dir != NONE && blocked(s, px + dx(dir), py + dy(dir))) {
            dir = reverse(dir);
        }
        if (dir == NONE || blocked(s, px + dx(dir), py + dy(dir))) {
            dir = mostFree(s, px, py);
        }
        return input(dir, false);
    }

    private static int pack(int mode, int x, int y) {
        return mode << 24 | (y & 0xFF) << 8 | (x & 0xFF);
    }

    /** A cell x on the board (wrapped where the board wraps; arenas are at most 32 wide). */
    private static int cellX(MatchState s, int x) {
        return s.wrapsX() ? Math.floorMod(x, s.width()) : x;
    }

    private static int cellY(MatchState s, int y) {
        return s.wrapsY() ? Math.floorMod(y, s.height()) : y;
    }

    private static boolean lethalHazard(ModuleContext ctx, int id) {
        MatchState s = ctx.state();
        ResolvedGame game = ctx.game();
        int tag = s.tag(id);
        if (game != null && tag < game.hazards().size()) {
            HazardModule hazard = game.hazards().get(tag);
            return hazard.lethal(game.hazardProps().get(tag));
        }
        return (s.get(id, Entities.FLAGS) & Entities.F_LETHAL) != 0;
    }

    /** Toward a target at {@code (tdx, tdy)}: the axis of larger distance (ties x), else the other one. */
    private static int toward(MatchState s, int px, int py, int tdx, int tdy) {
        int xDir = tdx > 0 ? RIGHT : tdx < 0 ? LEFT : NONE;
        int yDir = tdy > 0 ? DOWN : tdy < 0 ? UP : NONE;
        int first = Math.abs(tdx) >= Math.abs(tdy) ? xDir : yDir;
        int second = first == xDir ? yDir : xDir;
        if (first != NONE && !blocked(s, px + dx(first), py + dy(first))) {
            return first;
        }
        if (second != NONE && !blocked(s, px + dx(second), py + dy(second))) {
            return second;
        }
        return NONE;
    }

    private static int mostFree(MatchState s, int px, int py) {
        int best = NONE;
        int bestFree = 0;
        for (int dir = UP; dir <= RIGHT; dir++) {
            int free = freeRun(s, px, py, dir);
            if (free > bestFree) {
                best = dir;
                bestFree = free;
            }
        }
        return best;
    }

    /** Free cells ahead in {@code dir}, capped at the board's longer side. */
    private static int freeRun(MatchState s, int px, int py, int dir) {
        int max = Math.max(s.width(), s.height());
        int n = 0;
        int x = px;
        int y = py;
        while (n < max) {
            x += dx(dir);
            y += dy(dir);
            if (blocked(s, x, y)) {
                break;
            }
            n++;
        }
        return n;
    }

    private static boolean blocked(MatchState s, int cx, int cy) {
        return Cell.blocks(s.cell(cx, cy)) || s.solidAt(cx, cy);
    }

    private static int deltaX(MatchState s, int from, int to) {
        return wrapDelta(to - from, s.width(), s.wrapsX());
    }

    private static int deltaY(MatchState s, int from, int to) {
        return wrapDelta(to - from, s.height(), s.wrapsY());
    }

    /** The shortest signed delta on a wrapping axis; unchanged otherwise. */
    private static int wrapDelta(int d, int side, boolean wraps) {
        if (!wraps) {
            return d;
        }
        d = Math.floorMod(d, side);
        return d > side / 2 ? d - side : d;
    }

    private static int dx(int dir) {
        return dir == LEFT ? -1 : dir == RIGHT ? 1 : 0;
    }

    private static int dy(int dir) {
        return dir == UP ? -1 : dir == DOWN ? 1 : 0;
    }

    private static int reverse(int dir) {
        return switch (dir) {
            case UP -> DOWN;
            case DOWN -> UP;
            case LEFT -> RIGHT;
            case RIGHT -> LEFT;
            default -> NONE;
        };
    }

    private static SeatInput input(int dir, boolean pressA) {
        int buttons = switch (dir) {
            case UP -> SeatInput.UP;
            case DOWN -> SeatInput.DOWN;
            case LEFT -> SeatInput.LEFT;
            case RIGHT -> SeatInput.RIGHT;
            default -> 0;
        };
        return SeatInput.of(buttons | (pressA ? SeatInput.A : 0), 0, 0);
    }
}
