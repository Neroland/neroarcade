package za.co.neroland.neroarcade.module.pack.pawn;

import java.util.List;
import java.util.Set;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.hook.Intent;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.hook.PawnModule;
import za.co.neroland.neroarcade.runtime.Cell;
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.EntityKind;
import za.co.neroland.neroarcade.runtime.MatchState;

/**
 * {@code neroarcade:pawn/serpent} — always moving on the grid; grows with each pickup and loses a life touching a
 * wall, a solid entity or itself. Flags {@code AUTO_MOVE, LETHAL_TO_PAWNS}.
 * <ul>
 *   <li>{@code speed} — grid-stepper rate: {@code acc += speed * 64} per tick, one cell per 256 (speed 4 = one cell
 *       per tick); SPEED adds 2, STUN stops it.</li>
 *   <li>{@code start_length} — cells at spawn, head included (1..8); the body unfolds behind the head.</li>
 * </ul>
 * The head is the PAWN (aux: acc, dir, counter = pending growth); the body is SEGMENTs owned by the seat with aux
 * {@code data} = order 1..n (1 next to the head). A step moves the head one cell; segment k takes segment k-1's
 * cell, segment 1 the old head cell; pending growth adds a segment on the vacated tail cell (at most
 * {@value #MAX_SEGMENTS} per seat — extra growth is dropped, as is growth when the entity budget is full).
 *
 * <p>Input: a direction turns the head unless it is the 180-degree turn of the current direction or leads into the
 * neck (segment 1), so two quick turns between steps cannot reverse it either. Odd combinations: a
 * {@code BUTTONS_ONLY} control turns it (A counter-clockwise, B clockwise); a one-axis control turns it on that
 * axis only. Pawn-vs-pawn hits are directional (001 A1): only a head that runs into a body is hit. It wraps where
 * the board wraps. Runtime opponent: the stock greedy one (it cannot reverse into itself). Sprite role
 * {@code serpent} (head f0, body f1). Stateless; pure.
 */
public final class SerpentPawn implements PawnModule {

    public static final SerpentPawn INSTANCE = new SerpentPawn();

    /** Most SEGMENTs a seat's serpent can have. */
    public static final int MAX_SEGMENTS = 48;

    private static final List<PropertySpec> PROPERTIES = List.of(
            PropertySpec.integer("speed", 3, 1, 5),
            PropertySpec.integer("start_length", 3, 1, 8));

    private static final Set<ModuleFlag> FLAGS = Set.of(ModuleFlag.AUTO_MOVE, ModuleFlag.LETHAL_TO_PAWNS);

    private SerpentPawn() {
    }

    @Override
    public Identifier id() {
        return Identifier.fromNamespaceAndPath("neroarcade", "pawn/serpent");
    }

    @Override
    public List<PropertySpec> properties() {
        return PROPERTIES;
    }

    @Override
    public Set<ModuleFlag> flags() {
        return FLAGS;
    }

    @Override
    public void spawn(ModuleContext ctx, int seat, int cellX, int cellY) {
        MatchState s = ctx.state();
        int toX = s.width() / 2 - cellX;
        int toY = s.height() / 2 - cellY;
        int dir = Math.abs(toX) >= Math.abs(toY) ? (toX >= 0 ? GridStep.RIGHT : GridStep.LEFT)
                : (toY > 0 ? GridStep.DOWN : GridStep.UP);
        int id = s.spawn(EntityKind.PAWN, GridStep.centre(cellX), GridStep.centre(cellY), seat, 0);
        s.setPawnId(seat, id);
        if (id < 0) {
            return;
        }
        s.setAux(id, GridStep.withDir(s.aux(id), dir));
        int lastX = cellX;
        int lastY = cellY;
        boolean unfolding = true;
        int length = ctx.props().getInt("start_length");
        for (int k = 1; k < length; k++) {
            if (unfolding) {
                int nx = GridStep.wrapCellX(s, lastX - GridStep.dx(dir));
                int ny = GridStep.wrapCellY(s, lastY - GridStep.dy(dir));
                byte c = s.cell(nx, ny);
                if (c == Cell.EMPTY || c == Cell.LANE) {
                    lastX = nx;
                    lastY = ny;
                } else {
                    unfolding = false;
                }
            }
            int seg = s.spawn(EntityKind.SEGMENT, GridStep.centre(lastX), GridStep.centre(lastY), seat, 0);
            if (seg < 0) {
                break;
            }
            s.setAux(seg, GridStep.withData(s.aux(seg), k));
        }
    }

    @Override
    public void applyInput(ModuleContext ctx, int seat, Intent intent) {
        MatchState s = ctx.state();
        int id = s.pawnId(seat);
        int aux = s.aux(id);
        int dir = GridStep.dir(aux);
        if (GridStep.buttonsOnly(ctx)) {
            if (intent.aPressed()) {
                dir = turn(s, seat, id, dir, GridStep.ccw(dir));
            }
            if (intent.bPressed()) {
                dir = turn(s, seat, id, dir, GridStep.cw(dir));
            }
        } else {
            int mx = intent.moveX();
            int my = intent.moveY();
            int want = -1;
            if (mx != 0 && my != 0) {
                want = GridStep.horizontal(dir) ? (my > 0 ? GridStep.DOWN : GridStep.UP)
                        : (mx > 0 ? GridStep.RIGHT : GridStep.LEFT);
            } else if (mx != 0) {
                want = mx > 0 ? GridStep.RIGHT : GridStep.LEFT;
            } else if (my != 0) {
                want = my > 0 ? GridStep.DOWN : GridStep.UP;
            }
            if (want >= 0) {
                dir = turn(s, seat, id, dir, want);
            }
        }
        s.setAux(id, GridStep.withDir(s.aux(id), dir));
    }

    /** {@code want} unless it reverses {@code dir} or points into the neck; then {@code dir}. */
    private static int turn(MatchState s, int seat, int head, int dir, int want) {
        if (want == GridStep.reverse(dir)) {
            return dir;
        }
        int hx = s.get(head, Entities.X) >> MatchState.FP_SHIFT;
        int hy = s.get(head, Entities.Y) >> MatchState.FP_SHIFT;
        int nx = GridStep.wrapCellX(s, hx + GridStep.dx(want));
        int ny = GridStep.wrapCellY(s, hy + GridStep.dy(want));
        int[] body = segments(s, seat);
        if (body.length > 0 && body[0] >= 0) {
            int sx = s.get(body[0], Entities.X) >> MatchState.FP_SHIFT;
            int sy = s.get(body[0], Entities.Y) >> MatchState.FP_SHIFT;
            boolean onHead = sx == hx && sy == hy;
            if (!onHead && sx == nx && sy == ny) {
                return dir;
            }
        }
        return want;
    }

    @Override
    public void tick(ModuleContext ctx, int seat) {
        MatchState s = ctx.state();
        int id = s.pawnId(seat);
        int aux = s.aux(id);
        int dir = GridStep.dir(aux);
        int pending = GridStep.counter(aux);
        int speed = GridStep.speed(s, seat, ctx.props().getInt("speed"));
        int acc = GridStep.acc(aux) + speed * GridStep.UNITS_PER_SPEED;
        while (acc >= MatchState.FP) {
            acc -= MatchState.FP;
            int hx = s.get(id, Entities.X) >> MatchState.FP_SHIFT;
            int hy = s.get(id, Entities.Y) >> MatchState.FP_SHIFT;
            int nx = GridStep.wrapCellX(s, hx + GridStep.dx(dir));
            int ny = GridStep.wrapCellY(s, hy + GridStep.dy(dir));
            if (GridStep.blocked(s, nx, ny)) {
                s.damageSeat(seat, 1);
                if (s.pawnId(seat) < 0) {
                    return;
                }
                acc = 0; // shielded: it waits in front of the obstacle
                break;
            }
            s.set(id, Entities.X, GridStep.centre(nx));
            s.set(id, Entities.Y, GridStep.centre(ny));
            int[] body = segments(s, seat);
            int n = body.length;
            int tailX = hx;
            int tailY = hy;
            if (n > 0 && body[n - 1] >= 0) {
                tailX = s.get(body[n - 1], Entities.X) >> MatchState.FP_SHIFT;
                tailY = s.get(body[n - 1], Entities.Y) >> MatchState.FP_SHIFT;
            }
            for (int k = n - 1; k >= 1; k--) {
                if (body[k] >= 0 && body[k - 1] >= 0) {
                    s.set(body[k], Entities.X, s.get(body[k - 1], Entities.X));
                    s.set(body[k], Entities.Y, s.get(body[k - 1], Entities.Y));
                }
            }
            if (n > 0 && body[0] >= 0) {
                s.set(body[0], Entities.X, GridStep.centre(hx));
                s.set(body[0], Entities.Y, GridStep.centre(hy));
            }
            if (pending > 0) {
                pending--;
                if (n < MAX_SEGMENTS) {
                    int seg = s.spawn(EntityKind.SEGMENT, GridStep.centre(tailX), GridStep.centre(tailY), seat, 0);
                    if (seg >= 0) {
                        s.setAux(seg, GridStep.withData(s.aux(seg), n + 1));
                    }
                }
            }
            if (bitesItself(s, seat, nx, ny)) {
                s.damageSeat(seat, 1);
                if (s.pawnId(seat) < 0) {
                    return;
                }
            }
        }
        aux = s.aux(id);
        aux = GridStep.withAcc(aux, acc);
        aux = GridStep.withCounter(aux, pending);
        s.setAux(id, aux);
        int v = speed * GridStep.UNITS_PER_SPEED;
        s.set(id, Entities.VX, GridStep.dx(dir) * v);
        s.set(id, Entities.VY, GridStep.dy(dir) * v);
        GridStep.moveProjectiles(s, seat);
        GridStep.voidCheck(s, seat);
    }

    private static boolean bitesItself(MatchState s, int seat, int hx, int hy) {
        for (int i = 0; i < s.entityCount(); i++) {
            int e = s.entityIdAt(i);
            if (s.get(e, Entities.KIND) == EntityKind.SEGMENT.ordinal() && s.get(e, Entities.OWNER) == seat
                    && s.occupies(e, hx, hy)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The seat's SEGMENT ids indexed by order - 1 (array length = segment count; a slot whose order is missing or
     * out of range holds -1).
     */
    static int[] segments(MatchState s, int seat) {
        int n = 0;
        for (int i = 0; i < s.entityCount(); i++) {
            int e = s.entityIdAt(i);
            if (s.get(e, Entities.KIND) == EntityKind.SEGMENT.ordinal() && s.get(e, Entities.OWNER) == seat) {
                n++;
            }
        }
        int[] out = new int[n];
        java.util.Arrays.fill(out, -1);
        for (int i = 0; i < s.entityCount(); i++) {
            int e = s.entityIdAt(i);
            if (s.get(e, Entities.KIND) == EntityKind.SEGMENT.ordinal() && s.get(e, Entities.OWNER) == seat) {
                int order = GridStep.data(s.aux(e));
                if (order >= 1 && order <= n) {
                    out[order - 1] = e;
                }
            }
        }
        return out;
    }

    /** Pending growth +1 (capped at 255); the segment appears on the next step. */
    @Override
    public void onCollect(ModuleContext ctx, int seat, int pickupEntityId) {
        MatchState s = ctx.state();
        int id = s.pawnId(seat);
        if (id < 0 || !s.exists(id)) {
            return;
        }
        int aux = s.aux(id);
        s.setAux(id, GridStep.withCounter(aux, Math.min(255, GridStep.counter(aux) + 1)));
    }

    /** "Speed 3 · length 3". */
    @Override
    public Object[] effectArgs(PropertyValues props) {
        return new Object[] {props.getInt("speed"), props.getInt("start_length")};
    }
}
