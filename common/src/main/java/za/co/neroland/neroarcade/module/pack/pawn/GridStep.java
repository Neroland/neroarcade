package za.co.neroland.neroarcade.module.pack.pawn;

import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.hook.Intent;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.runtime.Cell;
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.EntityKind;
import za.co.neroland.neroarcade.runtime.MatchState;
import za.co.neroland.neroarcade.runtime.SeatState;

/**
 * Shared, static, pure helpers of the pawn chips: the aux-word layout, directions, the smooth mover, the VOID
 * check and the projectile duty.
 *
 * <p><b>Aux word layout</b> (32 bits): bits 0-1 <b>tag</b> (stack index, written by {@code spawn}, never touched
 * here — every setter below keeps every bit it does not own); bits 2-9 <b>acc</b> (0..255, grid steppers); bits
 * 10-11 <b>dir</b> (0 up, 1 down, 2 left, 3 right); bits 12-19 <b>counter</b> (0..255); bits 20-27 <b>data</b>
 * (0..255).
 */
final class GridStep {

    static final int UP = 0;
    static final int DOWN = 1;
    static final int LEFT = 2;
    static final int RIGHT = 3;

    /** Fixed-point units per speed point (speed 4 = one cell per tick). */
    static final int UNITS_PER_SPEED = 64;

    private static final int SUB_STEP = MatchState.FP / 2;

    private GridStep() {
    }

    // ---- aux fields ------------------------------------------------------------------------------------

    static int acc(int aux) {
        return (aux >>> 2) & 0xFF;
    }

    static int withAcc(int aux, int value) {
        return (aux & ~(0xFF << 2)) | ((value & 0xFF) << 2);
    }

    static int dir(int aux) {
        return (aux >>> 10) & 3;
    }

    static int withDir(int aux, int value) {
        return (aux & ~(3 << 10)) | ((value & 3) << 10);
    }

    static int counter(int aux) {
        return (aux >>> 12) & 0xFF;
    }

    static int withCounter(int aux, int value) {
        return (aux & ~(0xFF << 12)) | ((value & 0xFF) << 12);
    }

    static int data(int aux) {
        return (aux >>> 20) & 0xFF;
    }

    static int withData(int aux, int value) {
        return (aux & ~(0xFF << 20)) | ((value & 0xFF) << 20);
    }

    // ---- directions ------------------------------------------------------------------------------------

    static int dx(int dir) {
        return dir == LEFT ? -1 : dir == RIGHT ? 1 : 0;
    }

    static int dy(int dir) {
        return dir == UP ? -1 : dir == DOWN ? 1 : 0;
    }

    /** The 180-degree turn: up/down and left/right swap. */
    static int reverse(int dir) {
        return dir ^ 1;
    }

    /** A quarter turn counter-clockwise (screen coordinates, y down). */
    static int ccw(int dir) {
        return switch (dir) {
            case UP -> LEFT;
            case LEFT -> DOWN;
            case DOWN -> RIGHT;
            default -> UP;
        };
    }

    /** A quarter turn clockwise (screen coordinates, y down). */
    static int cw(int dir) {
        return switch (dir) {
            case UP -> RIGHT;
            case RIGHT -> DOWN;
            case DOWN -> LEFT;
            default -> UP;
        };
    }

    static boolean horizontal(int dir) {
        return dir == LEFT || dir == RIGHT;
    }

    // ---- cells -----------------------------------------------------------------------------------------

    /** Fixed-point centre of cell {@code c}. */
    static int centre(int c) {
        return (c << MatchState.FP_SHIFT) + MatchState.FP / 2;
    }

    /** A cell coordinate wrapped where the board wraps (unchanged otherwise). */
    static int wrapCellX(MatchState s, int cx) {
        return s.wrapsX() ? Math.floorMod(cx, s.width()) : cx;
    }

    static int wrapCellY(MatchState s, int cy) {
        return s.wrapsY() ? Math.floorMod(cy, s.height()) : cy;
    }

    /** A blocking cell or a cell holding an {@code F_SOLID} entity. */
    static boolean blocked(MatchState s, int cx, int cy) {
        return Cell.blocks(s.cell(cx, cy)) || s.solidAt(cx, cy);
    }

    // ---- input ------------------------------------------------------------------------------------------

    /** True when the CONTROL chip produces buttons only (001 §6 row 5: the pawn moves on its own). */
    static boolean buttonsOnly(ModuleContext ctx) {
        return ctx.game() != null && ctx.game().flags(ModuleType.CONTROL).contains(ModuleFlag.BUTTONS_ONLY);
    }

    /** {@code base} with SPEED (+2) and STUN (0) applied. */
    static int speed(MatchState s, int seat, int base) {
        int speed = base;
        if ((s.statusFlags(seat) & SeatState.SPEED) != 0) {
            speed += 2;
        }
        if ((s.statusFlags(seat) & SeatState.STUN) != 0) {
            speed = 0;
        }
        return speed;
    }

    /** The one-axis input rule: the pawn's own axis first, the other axis when it is zero. */
    static int oneAxis(Intent intent, boolean axisIsX) {
        if (axisIsX) {
            return intent.moveX() != 0 ? intent.moveX() : intent.moveY();
        }
        return intent.moveY() != 0 ? intent.moveY() : intent.moveX();
    }

    // ---- motion -----------------------------------------------------------------------------------------

    /**
     * The smooth mover ({@code CursorPawn}'s): moves one axis in half-cell sub-steps, stopping before a blocking
     * cell or before entering a cell holding an {@code F_SOLID} entity. Returns the new coordinate.
     */
    static int move(MatchState state, int x, int y, int velocity, boolean horizontal) {
        int sign = Integer.signum(velocity);
        int remaining = Math.abs(velocity);
        int pos = horizontal ? x : y;
        while (remaining > 0) {
            int step = Math.min(SUB_STEP, remaining);
            int next = horizontal ? state.wrapX(pos + sign * step) : state.wrapY(pos + sign * step);
            int cx = (horizontal ? next : x) >> MatchState.FP_SHIFT;
            int cy = (horizontal ? y : next) >> MatchState.FP_SHIFT;
            boolean entering = (next >> MatchState.FP_SHIFT) != (pos >> MatchState.FP_SHIFT);
            if (Cell.blocks(state.cell(cx, cy)) || (entering && state.solidAt(cx, cy))) {
                break;
            }
            pos = next;
            remaining -= step;
        }
        return pos;
    }

    /**
     * The VOID check (001 A7): a seat whose pawn stands on a VOID cell takes one damage. Returns true when the
     * seat's body is gone afterwards (the caller must stop; SHIELD keeps the body and returns false).
     */
    static boolean voidCheck(MatchState s, int seat) {
        int id = s.pawnId(seat);
        if (id < 0 || !s.exists(id)) {
            return true;
        }
        if (s.cell(s.get(id, Entities.X) >> MatchState.FP_SHIFT, s.get(id, Entities.Y) >> MatchState.FP_SHIFT) == Cell.VOID) {
            s.damageSeat(seat, 1);
        }
        return s.pawnId(seat) < 0;
    }

    /**
     * Projectile duty: every PROJECTILE whose owner is {@code seat} moves by its velocity (wrapping where the board
     * wraps); one entering a blocking cell is removed.
     */
    static void moveProjectiles(MatchState s, int seat) {
        s.forEach(EntityKind.PROJECTILE, id -> {
            if (s.get(id, Entities.OWNER) != seat) {
                return;
            }
            int x = s.wrapX(s.get(id, Entities.X) + s.get(id, Entities.VX));
            int y = s.wrapY(s.get(id, Entities.Y) + s.get(id, Entities.VY));
            if (Cell.blocks(s.cell(x >> MatchState.FP_SHIFT, y >> MatchState.FP_SHIFT))) {
                s.remove(id);
                return;
            }
            s.set(id, Entities.X, x);
            s.set(id, Entities.Y, y);
        });
    }
}
