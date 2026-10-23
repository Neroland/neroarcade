package za.co.neroland.neroarcade.module.pack.pawn;

import java.util.List;
import java.util.Set;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.hook.HitOutcome;
import za.co.neroland.neroarcade.module.hook.Intent;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.hook.PawnModule;
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.EntityKind;
import za.co.neroland.neroarcade.runtime.MatchState;

/**
 * {@code neroarcade:pawn/runner} — a lane runner. Flags {@code AUTO_MOVE, ONE_AXIS}.
 * <ul>
 *   <li>{@code speed} — it runs along Y on its own at {@code speed * 64} units per tick (smooth mover), starting
 *       up, and reverses when the next cell blocks; SPEED adds 2, STUN stops it.</li>
 *   <li>{@code jump} — a hop (A, while grounded) lasts {@code T = max(1, 128 * jump / gravity)} ticks (8 per 2
 *       cells at default gravity, twice that at half gravity) and carries it {@code jump} extra cells along its
 *       run: {@code jump * 256 / T} extra units per airborne tick. Walls still stop it.</li>
 * </ul>
 * Its one input axis is X (one-axis rule, X first): each new press shifts it one whole column, if that cell does
 * not block, with a 4-tick lockout; no lane change while airborne or with a {@code BUTTONS_ONLY} control. While
 * airborne {@code onHit} returns IGNORE (hazards and shots pass under it) and the VOID check is skipped (a hop
 * clears a VOID gap).
 *
 * <p>Aux: dir (up/down), counter = airborne ticks left, data bits 0-3 = lane lockout, bits 4-5 = last lane input
 * (0 none, 1 right, 2 left). Odd combinations: on {@code grid_16} it shuttles up and down its column (legal);
 * {@code reach_exit} puts the exit on its column. Runtime opponent: the stock greedy one (it presses A near a
 * threat, which is a hop). Sprite role {@code runner}. Stateless; pure.
 */
public final class RunnerPawn implements PawnModule {

    public static final RunnerPawn INSTANCE = new RunnerPawn();

    /** Ticks after a lane change before the next one. */
    static final int LANE_LOCKOUT = 4;

    private static final List<PropertySpec> PROPERTIES = List.of(
            PropertySpec.integer("speed", 3, 1, 5),
            PropertySpec.integer("jump", 2, 1, 4));

    private static final Set<ModuleFlag> FLAGS = Set.of(ModuleFlag.AUTO_MOVE, ModuleFlag.ONE_AXIS);

    private RunnerPawn() {
    }

    @Override
    public Identifier id() {
        return Identifier.fromNamespaceAndPath("neroarcade", "pawn/runner");
    }

    @Override
    public List<PropertySpec> properties() {
        return PROPERTIES;
    }

    @Override
    public Set<ModuleFlag> flags() {
        return FLAGS;
    }

    /** Airborne ticks of a hop: {@code max(1, 128 * jump / gravity)}, capped to the counter's 255. */
    static int hopTicks(int jump, int gravity) {
        return Math.min(255, Math.max(1, (128 * jump) / Math.max(1, gravity)));
    }

    @Override
    public void spawn(ModuleContext ctx, int seat, int cellX, int cellY) {
        MatchState s = ctx.state();
        int id = s.spawn(EntityKind.PAWN, GridStep.centre(cellX), GridStep.centre(cellY), seat, 0);
        s.setPawnId(seat, id);
        // dir 0 = up: the fresh aux word already says so
    }

    @Override
    public void applyInput(ModuleContext ctx, int seat, Intent intent) {
        MatchState s = ctx.state();
        int id = s.pawnId(seat);
        int aux = s.aux(id);
        int airborne = GridStep.counter(aux);
        if (airborne > 0) {
            airborne--;
        }
        int data = GridStep.data(aux);
        int lockout = data & 15;
        if (lockout > 0) {
            lockout--;
        }
        int lastM = (data >> 4) & 3;
        int m = GridStep.oneAxis(intent, true);
        int code = m > 0 ? 1 : m < 0 ? 2 : 0;
        if (airborne == 0 && m != 0 && code != lastM && lockout == 0 && !GridStep.buttonsOnly(ctx)) {
            int cx = s.get(id, Entities.X) >> MatchState.FP_SHIFT;
            int cy = s.get(id, Entities.Y) >> MatchState.FP_SHIFT;
            int nx = GridStep.wrapCellX(s, cx + m);
            if (!GridStep.blocked(s, nx, cy)) {
                s.set(id, Entities.X, GridStep.centre(nx));
                lockout = LANE_LOCKOUT;
            }
        }
        if (airborne == 0 && (intent.aPressed() || intent.a())) {
            airborne = hopTicks(ctx.props().getInt("jump"), s.gravity());
        }
        aux = GridStep.withCounter(aux, airborne);
        aux = GridStep.withData(aux, (lockout & 15) | (code << 4));
        s.setAux(id, aux);
    }

    @Override
    public void tick(ModuleContext ctx, int seat) {
        MatchState s = ctx.state();
        int id = s.pawnId(seat);
        int aux = s.aux(id);
        int dir = GridStep.dir(aux) == GridStep.DOWN ? GridStep.DOWN : GridStep.UP;
        boolean airborne = GridStep.counter(aux) > 0;
        int speed = GridStep.speed(s, seat, ctx.props().getInt("speed"));
        int v = speed * GridStep.UNITS_PER_SPEED;
        if (airborne) {
            int jump = ctx.props().getInt("jump");
            v += (jump * MatchState.FP) / hopTicks(jump, s.gravity());
        }
        int x = s.get(id, Entities.X);
        int y0 = s.get(id, Entities.Y);
        int velocity = GridStep.dy(dir) * v;
        int y1 = GridStep.move(s, x, y0, velocity, false);
        if (v > 0 && y1 != s.wrapY(y0 + velocity)) {
            dir = GridStep.reverse(dir);
            s.setAux(id, GridStep.withDir(aux, dir));
        }
        s.set(id, Entities.Y, y1);
        s.set(id, Entities.VX, 0);
        s.set(id, Entities.VY, GridStep.dy(dir) * speed * GridStep.UNITS_PER_SPEED);
        GridStep.moveProjectiles(s, seat);
        if (!airborne) {
            GridStep.voidCheck(s, seat);
        }
    }

    /** Airborne: IGNORE (it passes over). Grounded: DAMAGE. */
    @Override
    public HitOutcome onHit(ModuleContext ctx, int seat, int entityId) {
        MatchState s = ctx.state();
        int id = s.pawnId(seat);
        if (id >= 0 && s.exists(id) && GridStep.counter(s.aux(id)) > 0) {
            return HitOutcome.IGNORE;
        }
        return HitOutcome.DAMAGE;
    }

    /** "Runs · speed 3 · hop 2". */
    @Override
    public Object[] effectArgs(PropertyValues props) {
        return new Object[] {props.getInt("speed"), props.getInt("jump")};
    }
}
