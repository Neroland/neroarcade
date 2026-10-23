package za.co.neroland.neroarcade.module.pack.pawn;

import java.util.List;
import java.util.Set;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.module.ModuleDefaults;
import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.hook.Intent;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.hook.PawnModule;
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.EntityKind;
import za.co.neroland.neroarcade.runtime.MatchState;

/**
 * {@code neroarcade:pawn/cursor} — the default pawn: a free 4-way cursor.
 * <ul>
 *   <li>{@code speed} — {@code N * 64} fixed-point units per simulated tick (speed 4 = one cell per tick); a SPEED
 *       status adds 2, STUN stops it.</li>
 * </ul>
 * Motion is integrated in half-cell sub-steps per axis so it never tunnels through a wall or into an
 * {@code F_SOLID} entity's cell.
 *
 * <p>Odd combinations: with a {@code BUTTONS_ONLY} control (one button) it auto-moves, starting right; A reverses,
 * B held stops it. It loses a life standing on a VOID cell at the end of its tick, and moves the projectiles its
 * seat owns (a deflected shot changes owner). Sprite role {@code cursor}. Stateless; pure.
 */
public final class CursorPawn implements PawnModule {

    public static final CursorPawn INSTANCE = new CursorPawn();

    /** Fixed-point units per speed point. */
    public static final int UNITS_PER_SPEED = GridStep.UNITS_PER_SPEED;

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
        int id = state.spawn(EntityKind.PAWN, GridStep.centre(cellX), GridStep.centre(cellY), seat, 0);
        state.setPawnId(seat, id);
        if (id >= 0 && GridStep.buttonsOnly(ctx)) {
            state.setAux(id, GridStep.withDir(state.aux(id), GridStep.RIGHT));
        }
    }

    @Override
    public void applyInput(ModuleContext ctx, int seat, Intent intent) {
        MatchState state = ctx.state();
        int id = state.pawnId(seat);
        int speed = GridStep.speed(state, seat, ctx.props().getInt("speed"));
        if (GridStep.buttonsOnly(ctx)) {
            int aux = state.aux(id);
            int dir = GridStep.dir(aux);
            if (intent.aPressed()) {
                dir = GridStep.reverse(dir);
                state.setAux(id, GridStep.withDir(aux, dir));
            }
            int v = intent.b() ? 0 : speed * UNITS_PER_SPEED;
            state.set(id, Entities.VX, GridStep.dx(dir) * v);
            state.set(id, Entities.VY, GridStep.dy(dir) * v);
            return;
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
        x = GridStep.move(state, x, y, state.get(id, Entities.VX), true);
        y = GridStep.move(state, x, y, state.get(id, Entities.VY), false);
        state.set(id, Entities.X, x);
        state.set(id, Entities.Y, y);
        GridStep.moveProjectiles(state, seat);
        GridStep.voidCheck(state, seat);
    }

    /** "Moves 4 ways · speed 3". */
    @Override
    public Object[] effectArgs(PropertyValues props) {
        return new Object[] {props.getInt("speed")};
    }
}
