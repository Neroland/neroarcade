package za.co.neroland.neroarcade.module.pack.pawn;

import java.util.List;
import java.util.Set;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.hook.HazardModule;
import za.co.neroland.neroarcade.module.hook.HitOutcome;
import za.co.neroland.neroarcade.module.hook.Intent;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.hook.PawnModule;
import za.co.neroland.neroarcade.runtime.Cell;
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.EntityKind;
import za.co.neroland.neroarcade.runtime.MatchState;
import za.co.neroland.neroarcade.runtime.SeatInput;

/**
 * {@code neroarcade:pawn/paddle} — a slide-along bar that bounces hazards and shots back. Flag {@code ONE_AXIS}.
 * <ul>
 *   <li>{@code length} — cells in the bar (1..8): the PAWN at the centre and SEGMENTs at offsets -1, +1, -2, +2, …
 *       (aux {@code data} = offset + 8).</li>
 *   <li>{@code speed} — {@code speed * 64} units per tick along the bar's line; SPEED adds 2, STUN stops it.</li>
 *   <li>{@code axis} — seats 0 and 1 take it, seats 2 and 3 the other one. A horizontal bar: seat 0 (or 2) on row
 *       {@code h - 3} with goal row {@code h - 2}, seat 1 (or 3) on row 2 with goal row 1; vertical likewise on
 *       columns {@code w - 3}/{@code w - 2} and 2/1.</li>
 * </ul>
 * {@code spawn} keeps the given cell's coordinate along the line, centres the bar on it and clamps it so every cell
 * is inside {@code 1..side-2}, then paints every non-WALL cell of its goal line {@code Cell.goal(seat)} (nothing
 * else writes goal cells in 0.1.0). It stops when either end cell would enter a blocking cell or a solid entity.
 * {@code onHit}: DEFLECT for HAZARD and PROJECTILE (the runtime reflects the toucher), IGNORE otherwise.
 *
 * <p>Odd combinations: with a {@code BUTTONS_ONLY} control it slides on its own (horizontal: starts right,
 * vertical: down); A reverses, B held stops it. A one-axis rule input of the wrong axis still moves it. On
 * {@code ring_24} the bars sit on rows 5 / 2; on {@code strip_9} the goal line is all nine cells. Runtime opponent
 * (override): tracks the nearest lethal hazard moving toward its line to its projected crossing, else the board
 * centre, with a one-cell dead zone and no buttons. Sprite role {@code paddle} (segments too). Stateless; pure.
 */
public final class PaddlePawn implements PawnModule {

    public static final PaddlePawn INSTANCE = new PaddlePawn();

    private static final int SUB_STEP = MatchState.FP / 2;

    private static final List<PropertySpec> PROPERTIES = List.of(
            PropertySpec.integer("length", 3, 1, 8),
            PropertySpec.integer("speed", 3, 1, 5),
            PropertySpec.enumeration("axis", "horizontal", "horizontal", "vertical"));

    private static final Set<ModuleFlag> FLAGS = Set.of(ModuleFlag.ONE_AXIS);

    private PaddlePawn() {
    }

    @Override
    public Identifier id() {
        return Identifier.fromNamespaceAndPath("neroarcade", "pawn/paddle");
    }

    @Override
    public List<PropertySpec> properties() {
        return PROPERTIES;
    }

    @Override
    public Set<ModuleFlag> flags() {
        return FLAGS;
    }

    // ---- geometry ---------------------------------------------------------------------------------------

    /** True when {@code seat}'s bar slides along X (row-shaped). */
    static boolean horizontal(PropertyValues props, int seat) {
        boolean base = "horizontal".equals(props.getEnum("axis"));
        return (seat & 3) < 2 ? base : !base;
    }

    /** Even seats sit near the far edge (h-3 / w-3), odd seats near the origin (2). */
    private static boolean near(int seat) {
        return (seat & 1) == 0;
    }

    /** The body line (row for a horizontal bar, column for a vertical one). */
    static int bodyLine(MatchState s, boolean horizontal, int seat) {
        int side = horizontal ? s.height() : s.width();
        return near(seat) ? side - 3 : 2;
    }

    /** The goal line, directly behind the body line. */
    static int goalLine(MatchState s, boolean horizontal, int seat) {
        int side = horizontal ? s.height() : s.width();
        return near(seat) ? side - 2 : 1;
    }

    /** Offset of the k-th segment (k = 1..length-1): -1, +1, -2, +2, … */
    static int offset(int k) {
        return (k & 1) == 1 ? -((k + 1) / 2) : k / 2;
    }

    /** Lowest offset of a bar of {@code length}. */
    static int low(int length) {
        return -(length / 2);
    }

    /** Highest offset of a bar of {@code length}. */
    static int high(int length) {
        return (length - 1) / 2;
    }

    // ---- hooks ------------------------------------------------------------------------------------------

    @Override
    public void spawn(ModuleContext ctx, int seat, int cellX, int cellY) {
        MatchState s = ctx.state();
        boolean horiz = horizontal(ctx.props(), seat);
        int length = ctx.props().getInt("length");
        int lo = low(length);
        int hi = high(length);
        int line = bodyLine(s, horiz, seat);
        int side = horiz ? s.width() : s.height();
        int along = Math.max(1 - lo, Math.min(side - 2 - hi, horiz ? cellX : cellY));
        int cx = horiz ? along : line;
        int cy = horiz ? line : along;
        int goal = goalLine(s, horiz, seat);
        for (int i = 0; i < side; i++) {
            int gx = horiz ? i : goal;
            int gy = horiz ? goal : i;
            if (s.cell(gx, gy) != Cell.WALL) {
                s.setCell(gx, gy, Cell.goal(seat));
            }
        }
        int id = s.spawn(EntityKind.PAWN, GridStep.centre(cx), GridStep.centre(cy), seat, 0);
        s.setPawnId(seat, id);
        if (id < 0) {
            return;
        }
        if (GridStep.buttonsOnly(ctx)) {
            s.setAux(id, GridStep.withDir(s.aux(id), horiz ? GridStep.RIGHT : GridStep.DOWN));
        }
        for (int k = 1; k < length; k++) {
            int off = offset(k);
            int sx = horiz ? s.wrapX(GridStep.centre(cx + off)) : GridStep.centre(cx);
            int sy = horiz ? GridStep.centre(cy) : s.wrapY(GridStep.centre(cy + off));
            int seg = s.spawn(EntityKind.SEGMENT, sx, sy, seat, 0);
            if (seg < 0) {
                break;
            }
            s.setAux(seg, GridStep.withData(s.aux(seg), off + 8));
        }
    }

    @Override
    public void applyInput(ModuleContext ctx, int seat, Intent intent) {
        MatchState s = ctx.state();
        int id = s.pawnId(seat);
        boolean horiz = horizontal(ctx.props(), seat);
        int speed = GridStep.speed(s, seat, ctx.props().getInt("speed"));
        int v;
        if (GridStep.buttonsOnly(ctx)) {
            int aux = s.aux(id);
            int dir = GridStep.dir(aux);
            if (intent.aPressed()) {
                dir = GridStep.reverse(dir);
                s.setAux(id, GridStep.withDir(aux, dir));
            }
            int sign = horiz ? GridStep.dx(dir) : GridStep.dy(dir);
            v = intent.b() ? 0 : sign * speed * GridStep.UNITS_PER_SPEED;
        } else {
            v = GridStep.oneAxis(intent, horiz) * speed * GridStep.UNITS_PER_SPEED;
        }
        s.set(id, Entities.VX, horiz ? v : 0);
        s.set(id, Entities.VY, horiz ? 0 : v);
    }

    @Override
    public void tick(ModuleContext ctx, int seat) {
        MatchState s = ctx.state();
        int id = s.pawnId(seat);
        boolean horiz = horizontal(ctx.props(), seat);
        int length = ctx.props().getInt("length");
        int lo = low(length);
        int hi = high(length);
        int x = s.get(id, Entities.X);
        int y = s.get(id, Entities.Y);
        int velocity = horiz ? s.get(id, Entities.VX) : s.get(id, Entities.VY);
        int lineCell = (horiz ? y : x) >> MatchState.FP_SHIFT;
        int pos = horiz ? x : y;
        int sign = Integer.signum(velocity);
        int remaining = Math.abs(velocity);
        while (remaining > 0) {
            int step = Math.min(SUB_STEP, remaining);
            int next = horiz ? s.wrapX(pos + sign * step) : s.wrapY(pos + sign * step);
            int cNew = next >> MatchState.FP_SHIFT;
            if (cNew != (pos >> MatchState.FP_SHIFT)) {
                int lead = cNew + (sign > 0 ? hi : lo);
                int cx = horiz ? lead : lineCell;
                int cy = horiz ? lineCell : lead;
                if (GridStep.blocked(s, cx, cy)) {
                    break;
                }
            }
            pos = next;
            remaining -= step;
        }
        if (horiz) {
            s.set(id, Entities.X, pos);
        } else {
            s.set(id, Entities.Y, pos);
        }
        int px = s.get(id, Entities.X);
        int py = s.get(id, Entities.Y);
        s.forEach(EntityKind.SEGMENT, seg -> {
            if (s.get(seg, Entities.OWNER) != seat) {
                return;
            }
            int off = GridStep.data(s.aux(seg)) - 8;
            s.set(seg, Entities.X, horiz ? s.wrapX(px + off * MatchState.FP) : px);
            s.set(seg, Entities.Y, horiz ? py : s.wrapY(py + off * MatchState.FP));
        });
        GridStep.moveProjectiles(s, seat);
        GridStep.voidCheck(s, seat);
    }

    /** DEFLECT for hazards and shots; anything else (no pawn-pawn hits without LETHAL_TO_PAWNS) is IGNORE. */
    @Override
    public HitOutcome onHit(ModuleContext ctx, int seat, int entityId) {
        MatchState s = ctx.state();
        if (!s.exists(entityId)) {
            return HitOutcome.IGNORE;
        }
        int kind = s.get(entityId, Entities.KIND);
        return kind == EntityKind.HAZARD.ordinal() || kind == EntityKind.PROJECTILE.ordinal()
                ? HitOutcome.DEFLECT : HitOutcome.IGNORE;
    }

    /**
     * The nearest lethal hazard moving toward this bar's line is projected to its crossing coordinate
     * {@code c = pos + vAlong * (dist / |vPerp|)}; the bar moves toward {@code c} (no hazard: the board centre) with
     * a one-cell dead zone. No buttons. Pure.
     */
    @Override
    public SeatInput opponentInput(ModuleContext ctx, int seat) {
        MatchState s = ctx.state();
        int pawn = s.pawnId(seat);
        if (pawn < 0 || !s.exists(pawn)) {
            return SeatInput.NONE;
        }
        boolean horiz = horizontal(ctx.props(), seat);
        int pos = horiz ? s.get(pawn, Entities.X) : s.get(pawn, Entities.Y);
        int linePos = horiz ? s.get(pawn, Entities.Y) : s.get(pawn, Entities.X);
        int extent = (horiz ? s.width() : s.height()) << MatchState.FP_SHIFT;
        boolean wraps = horiz ? s.wrapsX() : s.wrapsY();
        int target = extent / 2;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < s.entityCount(); i++) {
            int id = s.entityIdAt(i);
            if (s.get(id, Entities.KIND) != EntityKind.HAZARD.ordinal() || !lethal(ctx, id)) {
                continue;
            }
            int bAlong = horiz ? s.get(id, Entities.X) : s.get(id, Entities.Y);
            int bPerp = horiz ? s.get(id, Entities.Y) : s.get(id, Entities.X);
            int vAlong = horiz ? s.get(id, Entities.VX) : s.get(id, Entities.VY);
            int vPerp = horiz ? s.get(id, Entities.VY) : s.get(id, Entities.VX);
            if (vPerp == 0 || Integer.signum(vPerp) != Integer.signum(linePos - bPerp)) {
                continue;
            }
            int dist = Math.abs(linePos - bPerp);
            if (dist < bestDist) {
                bestDist = dist;
                target = bAlong + vAlong * (dist / Math.abs(vPerp));
            }
        }
        target = wraps ? Math.floorMod(target, extent) : Math.max(0, Math.min(extent - 1, target));
        int diff = target - pos;
        if (wraps) {
            diff = Math.floorMod(diff + extent / 2, extent) - extent / 2;
        }
        if (Math.abs(diff) <= MatchState.FP) {
            return SeatInput.NONE;
        }
        int button = horiz ? (diff > 0 ? SeatInput.RIGHT : SeatInput.LEFT) : (diff > 0 ? SeatInput.DOWN : SeatInput.UP);
        return SeatInput.of(button, 0, 0);
    }

    /** As the stock opponent decides it: the owning chip's {@code lethal(props)}, else the entity's F_LETHAL bit. */
    private static boolean lethal(ModuleContext ctx, int id) {
        MatchState s = ctx.state();
        ResolvedGame game = ctx.game();
        int tag = s.tag(id);
        if (game != null && tag < game.hazards().size()) {
            HazardModule hazard = game.hazards().get(tag);
            return hazard.lethal(game.hazardProps().get(tag));
        }
        return (s.get(id, Entities.FLAGS) & Entities.F_LETHAL) != 0;
    }

    /** "Bar of 3 · speed 3". */
    @Override
    public Object[] effectArgs(PropertyValues props) {
        return new Object[] {props.getInt("length"), props.getInt("speed")};
    }
}
