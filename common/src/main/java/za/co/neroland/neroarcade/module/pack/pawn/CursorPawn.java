package za.co.neroland.neroarcade.module.pack.pawn;

import java.util.List;
import java.util.Set;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.module.ModuleDefaults;
import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.hook.Intent;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.hook.PawnModule;
import za.co.neroland.neroarcade.runtime.Cell;
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.EntityKind;
import za.co.neroland.neroarcade.runtime.MatchState;
import za.co.neroland.neroarcade.runtime.SeatState;

/**
 * {@code neroarcade:pawn/cursor} — the default pawn: a free 4-way cursor. {@code speed N} moves
 * {@code N * 64} fixed-point units per simulated tick (speed 4 = one cell per tick); a SPEED status adds 2,
 * STUN stops it. Motion is integrated in half-cell sub-steps per axis so it never tunnels through a wall.
 * Stateless; pure.
 */
public final class CursorPawn implements PawnModule {

    public static final CursorPawn INSTANCE = new CursorPawn();

    /** Fixed-point units per speed point. */
    public static final int UNITS_PER_SPEED = 64;

    private static final int SUB_STEP = MatchState.FP / 2;

    private static final List<PropertySpec> PROPERTIES = List.of(PropertySpec.integer("speed", 3, 1, 5));

    private CursorPawn() {
    }

    @Override
    public Identifier id() {
        return ModuleDefaults.PAWN;
    }

    @Override
    public List<PropertySpec> properties() {
        return PROPERTIES;
    }

    @Override
    public Set<ModuleFlag> flags() {
        return Set.of();
    }

    @Override
    public void spawn(ModuleContext ctx, int seat, int cellX, int cellY) {
        MatchState state = ctx.state();
        int id = state.spawn(EntityKind.PAWN, (cellX << MatchState.FP_SHIFT) + MatchState.FP / 2,
                (cellY << MatchState.FP_SHIFT) + MatchState.FP / 2, seat, 0);
        state.setPawnId(seat, id);
    }

    @Override
    public void applyInput(ModuleContext ctx, int seat, Intent intent) {
        MatchState state = ctx.state();
        int id = state.pawnId(seat);
        int speed = ctx.props().getInt("speed");
        if ((state.statusFlags(seat) & SeatState.SPEED) != 0) {
            speed += 2;
        }
        if ((state.statusFlags(seat) & SeatState.STUN) != 0) {
            speed = 0;
        }
        state.set(id, Entities.VX, intent.moveX() * speed * UNITS_PER_SPEED);
        state.set(id, Entities.VY, intent.moveY() * speed * UNITS_PER_SPEED);
    }

    @Override
    public void tick(ModuleContext ctx, int seat) {
        MatchState state = ctx.state();
        int id = state.pawnId(seat);
        int x = state.get(id, Entities.X);
        int y = state.get(id, Entities.Y);
        x = move(state, x, y, state.get(id, Entities.VX), true);
        y = move(state, x, y, state.get(id, Entities.VY), false);
        state.set(id, Entities.X, x);
        state.set(id, Entities.Y, y);
    }

    /**
     * Moves one axis in half-cell sub-steps, stopping before a blocking cell or before entering a cell holding an
     * {@code F_SOLID} entity (a pawn already sharing a cell with one can still leave it). Returns the new
     * coordinate.
     */
    private static int move(MatchState state, int x, int y, int velocity, boolean horizontal) {
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
}
