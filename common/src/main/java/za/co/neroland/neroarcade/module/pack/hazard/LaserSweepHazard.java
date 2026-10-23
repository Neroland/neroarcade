package za.co.neroland.neroarcade.module.pack.hazard;

import java.util.List;
import java.util.Set;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.hook.HazardModule;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.runtime.Cell;
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.EntityKind;
import za.co.neroland.neroarcade.runtime.MatchState;

/**
 * {@code neroarcade:hazard/laser_sweep} — a beam that sweeps the board back and forth.
 * <ul>
 *   <li>{@code period} — ticks for one sweep across the qualifying lines (floored at 2 ticks per cell).</li>
 *   <li>{@code axis} — {@code rows}: a horizontal beam sweeping over the rows; {@code columns}: a vertical beam
 *       sweeping over the columns; {@code both}: one of each.</li>
 * </ul>
 * A beam is a line of HAZARD entities, one per cell (aux {@code data} = beam 0 horizontal / 1 vertical,
 * {@code counter} = cell index), spawned once and moved every tick — no churn. A line qualifies when it has a
 * non-blocking, non-GOAL cell; {@code lo..hi} are the first and last qualifying lines, {@code span = hi−lo+1},
 * {@code step = max(2, period / span)}; at tick {@code t}: {@code k = t / step}, the beam sits on
 * {@code lo + (k/span odd ? span−1−k%span : k%span)} (ping-pong, never a teleport, never faster than half a cell
 * per tick). A beam cell over a blocking or GOAL cell is {@code F_HIDDEN} and parked on the first open cell of the
 * same line (a duplicate touch is harmless), so beams never fire through walls or goal rows. Beams are
 * {@code F_PERSISTENT} and ignore shots, deflections and goals. Budget: {@code both} on 32×32 is 64 entities per
 * stack; a spawn refused by the budget just shortens the beam. Odd combinations: {@code cursor speed 1} with
 * {@code period 20} is legal and brutal; paddle duels are unaffected (goal rows are never touched). Stateless; pure.
 */
public final class LaserSweepHazard implements HazardModule {

    public static final LaserSweepHazard INSTANCE = new LaserSweepHazard();

    /** Fairness floor: ticks per cell. */
    static final int MIN_STEP = 2;
    /** Beam line index of the horizontal beam (sweeps over rows). */
    static final int HORIZONTAL = 0;
    /** Beam line index of the vertical beam (sweeps over columns). */
    static final int VERTICAL = 1;

    private static final Identifier ID = Identifier.fromNamespaceAndPath("neroarcade", "hazard/laser_sweep");

    private static final List<PropertySpec> PROPERTIES = List.of(
            PropertySpec.integer("period", 80, 20, 200),
            PropertySpec.enumeration("axis", "rows", "rows", "columns", "both"));

    private static final Set<ModuleFlag> FLAGS = Set.of(ModuleFlag.LETHAL);

    private LaserSweepHazard() {
    }

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public List<PropertySpec> properties() {
        return PROPERTIES;
    }

    @Override
    public Set<ModuleFlag> flags() {
        return FLAGS;
    }

    /** "Rows · 80-tick sweep". */
    @Override
    public Object[] effectArgs(PropertyValues props) {
        return new Object[] {Component.translatable(PROPERTIES.get(1).constantLangKey(this, props.getEnum("axis"))),
                props.getInt("period")};
    }

    private static boolean horizontalOn(String axis) {
        return !"columns".equals(axis);
    }

    private static boolean verticalOn(String axis) {
        return !"rows".equals(axis);
    }

    @Override
    public void onMatchStart(ModuleContext ctx) {
        MatchState state = ctx.state();
        String axis = ctx.props().getEnum("axis");
        if (horizontalOn(axis)) {
            spawnBeam(ctx, HORIZONTAL, state.width());
        }
        if (verticalOn(axis)) {
            spawnBeam(ctx, VERTICAL, state.height());
        }
        place(ctx);
    }

    private static void spawnBeam(ModuleContext ctx, int line, int length) {
        MatchState state = ctx.state();
        for (int i = 0; i < length; i++) {
            int x = line == HORIZONTAL ? HazardAux.centre(i) : HazardAux.centre(0);
            int y = line == HORIZONTAL ? HazardAux.centre(0) : HazardAux.centre(i);
            int id = state.spawn(EntityKind.HAZARD, x, y, -1, ctx.stackIndex());
            if (id < 0) {
                return;
            }
            state.set(id, Entities.HP, 1);
            state.set(id, Entities.FLAGS, Entities.F_LETHAL | Entities.F_PERSISTENT);
            state.setAux(id, HazardAux.withCounter(HazardAux.withData(state.aux(id), line), i));
        }
    }

    @Override
    public void tick(ModuleContext ctx) {
        place(ctx);
    }

    /** Moves every beam entity of this stack to the beam's position for {@code ctx.tick()}. */
    private static void place(ModuleContext ctx) {
        MatchState state = ctx.state();
        int period = ctx.props().getInt("period");
        String axis = ctx.props().getEnum("axis");
        int rowPos = horizontalOn(axis) ? position(state, HORIZONTAL, period, ctx.tick()) : -1;
        int colPos = verticalOn(axis) ? position(state, VERTICAL, period, ctx.tick()) : -1;
        int rowPark = rowPos >= 0 ? firstOpen(state, HORIZONTAL, rowPos) : -1;
        int colPark = colPos >= 0 ? firstOpen(state, VERTICAL, colPos) : -1;
        int tag = ctx.stackIndex();
        for (int index = 0; index < state.entityCount(); index++) {
            int id = state.entityIdAt(index);
            if (state.get(id, Entities.KIND) != EntityKind.HAZARD.ordinal() || state.tag(id) != tag) {
                continue;
            }
            int aux = state.aux(id);
            int line = HazardAux.data(aux);
            int along = HazardAux.counter(aux);
            int pos = line == HORIZONTAL ? rowPos : colPos;
            int park = line == HORIZONTAL ? rowPark : colPark;
            int flags = state.get(id, Entities.FLAGS);
            if (pos < 0) {
                state.set(id, Entities.FLAGS, flags | Entities.F_HIDDEN);
                continue;
            }
            int cx = line == HORIZONTAL ? along : pos;
            int cy = line == HORIZONTAL ? pos : along;
            if (open(state.cell(cx, cy))) {
                flags &= ~Entities.F_HIDDEN;
            } else {
                flags |= Entities.F_HIDDEN;
                if (park >= 0) {
                    cx = line == HORIZONTAL ? park : pos;
                    cy = line == HORIZONTAL ? pos : park;
                }
            }
            state.set(id, Entities.X, HazardAux.centre(cx));
            state.set(id, Entities.Y, HazardAux.centre(cy));
            state.set(id, Entities.FLAGS, flags);
        }
    }

    private static boolean open(byte cell) {
        return !Cell.blocks(cell) && Cell.goalSeat(cell) < 0;
    }

    /** True when line {@code index} (a row for the horizontal beam, a column for the vertical) has an open cell. */
    private static boolean qualifies(MatchState state, int line, int index) {
        return firstOpen(state, line, index) >= 0;
    }

    /** The first open cell along line {@code index}, or -1. */
    private static int firstOpen(MatchState state, int line, int index) {
        int length = line == HORIZONTAL ? state.width() : state.height();
        for (int i = 0; i < length; i++) {
            byte cell = line == HORIZONTAL ? state.cell(i, index) : state.cell(index, i);
            if (open(cell)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The line the beam sits on at {@code tick}: ping-pong over the qualifying range with
     * {@code step = max(2, period / span)} ticks per line; -1 when no line qualifies.
     */
    static int position(MatchState state, int line, int period, int tick) {
        int lines = line == HORIZONTAL ? state.height() : state.width();
        int lo = -1;
        int hi = -1;
        for (int i = 0; i < lines; i++) {
            if (qualifies(state, line, i)) {
                if (lo < 0) {
                    lo = i;
                }
                hi = i;
            }
        }
        if (lo < 0) {
            return -1;
        }
        int span = hi - lo + 1;
        int step = Math.max(MIN_STEP, period / span);
        int k = Math.max(0, tick) / step;
        int pass = k / span;
        int p = k % span;
        return lo + ((pass & 1) != 0 ? span - 1 - p : p);
    }

    /** Beams ignore shots. */
    @Override
    public void onShot(ModuleContext ctx, int ownerSeat, int hazardEntityId, int projectileEntityId) {
    }

    /** Beams are never moved by a bat. */
    @Override
    public void onDeflected(ModuleContext ctx, int seat, int hazardEntityId, int pawnEntityId) {
    }

    /** Beams never sit on goal cells (they are parked); belt and braces: nothing. */
    @Override
    public void onGoal(ModuleContext ctx, int hazardEntityId, int goalSeat) {
    }
}
