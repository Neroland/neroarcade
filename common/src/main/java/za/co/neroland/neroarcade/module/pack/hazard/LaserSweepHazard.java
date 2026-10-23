package za.co.neroland.neroarcade.module.pack.hazard;

import java.util.List;
import java.util.Set;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.hook.ArenaModule;
import za.co.neroland.neroarcade.module.hook.HazardModule;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.runtime.Cell;
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.EntityKind;
import za.co.neroland.neroarcade.runtime.MatchState;

/**
 * {@code neroarcade:hazard/laser_sweep} — a beam with one gap that sweeps the board back and forth.
 * <ul>
 *   <li>{@code period} — ticks for one sweep across the qualifying lines (floored at 2 ticks per cell).</li>
 *   <li>{@code axis} — {@code rows}: a horizontal beam sweeping over the rows; {@code columns}: a vertical beam
 *       sweeping over the columns; {@code both}: one of each.</li>
 * </ul>
 * A beam is a line of HAZARD entities, one per cell (aux {@code data} = {@code line | gStart << 1} with line 0
 * horizontal / 1 vertical, {@code counter} = cell index, {@code acc} = the start phase {@code k0}), spawned once and
 * moved every tick — no churn. A line qualifies when it has a non-blocking, non-GOAL cell; {@code lo..hi} are the
 * first and last qualifying lines, {@code span = hi−lo+1}, {@code step = max(2, period / span)}; at tick {@code t}:
 * {@code k = t / step + k0}, the beam sits on {@code lo + (k/span odd ? span−1−k%span : k%span)} (back and forth,
 * never a teleport, never faster than half a cell per tick).
 *
 * <p><b>Start.</b> At match start the beam begins on the qualifying line farthest from every seat's arena spawn
 * line (ties: the lower line); {@code k0 = start − lo} is frozen in every beam entity for the whole match, so a
 * later cell change never moves the phase. <b>Gap.</b> Each beam carries a hole of {@code gap = max(2, length/4)}
 * cells starting at {@code gStart}, drawn {@code rng.nextInt(2, length−2−gap)} at match start and again when every
 * new pass begins (no gap when {@code length−2−gap < 2}). Beam cells inside the gap, or over a blocking or GOAL cell,
 * are {@code F_HIDDEN} <i>in place</i>: hidden entities never collide (001 A15.c), so beams never fire through walls
 * or goal rows and a body that lines up in the hole survives. Beams are {@code F_PERSISTENT} and ignore shots,
 * deflections and goals. Budget: {@code both} on 32×32 is 64 entities per stack; a spawn refused by the budget just
 * shortens the beam. Odd combinations: {@code cursor speed 1} with {@code period 20} is legal and brutal; paddle
 * duels are unaffected (goal rows are never touched). Stateless; pure.
 */
public final class LaserSweepHazard implements HazardModule {

    public static final LaserSweepHazard INSTANCE = new LaserSweepHazard();

    /** Fairness floor: ticks per cell. */
    static final int MIN_STEP = 2;
    /** Beam line index of the horizontal beam (sweeps over rows). */
    static final int HORIZONTAL = 0;
    /** Beam line index of the vertical beam (sweeps over columns). */
    static final int VERTICAL = 1;
    /** Smallest gap, cells. */
    static final int MIN_GAP = 2;

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

    // ---- aux helpers ------------------------------------------------------------------------------------

    /** The beam line (0 horizontal, 1 vertical) of a beam entity's {@code data}. */
    static int line(int aux) {
        return HazardAux.data(aux) & 1;
    }

    /** The gap's first cell index of a beam entity's {@code data} (0 = no gap). */
    static int gapStart(int aux) {
        return HazardAux.data(aux) >>> 1;
    }

    /** The frozen start phase of a beam entity. */
    static int startPhase(int aux) {
        return HazardAux.acc(aux);
    }

    /** {@code max(2, length / 4)} cells. */
    static int gapLength(int length) {
        return Math.max(MIN_GAP, length / 4);
    }

    private static int beamLength(MatchState state, int line) {
        return line == HORIZONTAL ? state.width() : state.height();
    }

    // ---- hooks ------------------------------------------------------------------------------------------

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
        int k0 = startPhase(ctx, line);
        int gStart = drawGap(ctx, length);
        for (int i = 0; i < length; i++) {
            int x = line == HORIZONTAL ? HazardAux.centre(i) : HazardAux.centre(0);
            int y = line == HORIZONTAL ? HazardAux.centre(0) : HazardAux.centre(i);
            int id = state.spawn(EntityKind.HAZARD, x, y, -1, ctx.stackIndex());
            if (id < 0) {
                return;
            }
            state.set(id, Entities.HP, 1);
            state.set(id, Entities.FLAGS, Entities.F_LETHAL | Entities.F_PERSISTENT);
            int aux = HazardAux.withCounter(HazardAux.withData(state.aux(id), line | (gStart << 1)), i);
            state.setAux(id, HazardAux.withAcc(aux, k0));
        }
    }

    /**
     * {@code start − lo}: the start is the qualifying line whose smallest distance to every seat's arena spawn line
     * (the row for the horizontal beam, the column for the vertical) is largest; ties go to the lower line. 0 when
     * there is no game (bare unit contexts) or no line qualifies.
     */
    static int startPhase(ModuleContext ctx, int line) {
        MatchState state = ctx.state();
        int[] range = range(state, line);
        if (range == null || ctx.game() == null) {
            return 0;
        }
        int seats = state.seatCount();
        int[] spawn = new int[seats];
        ArenaModule arena = ctx.game().arena();
        ModuleContext arenaCtx = ctx.withProps(ctx.game().arenaProps(), 0);
        for (int s = 0; s < seats; s++) {
            int[] cell = arena.spawnCell(arenaCtx, s);
            spawn[s] = line == HORIZONTAL ? cell[1] : cell[0];
        }
        int best = range[0];
        int bestScore = -1;
        for (int l = range[0]; l <= range[1]; l++) {
            if (!qualifies(state, line, l)) {
                continue;
            }
            int score = Integer.MAX_VALUE;
            for (int s = 0; s < seats; s++) {
                score = Math.min(score, Math.abs(l - spawn[s]));
            }
            if (score > bestScore) {
                bestScore = score;
                best = l;
            }
        }
        return best - range[0];
    }

    /** One draw {@code nextInt(2, length−2−gap)}, or 0 (no gap, no draw) when that range is empty. */
    private static int drawGap(ModuleContext ctx, int length) {
        int max = length - 2 - gapLength(length);
        if (max < MIN_GAP) {
            return 0;
        }
        return ctx.rng().nextInt(MIN_GAP, max);
    }

    @Override
    public void tick(ModuleContext ctx) {
        MatchState state = ctx.state();
        int tick = ctx.tick();
        if (tick > 0) {
            int period = ctx.props().getInt("period");
            String axis = ctx.props().getEnum("axis");
            int previous = tick - Math.max(1, state.paceDivisor());
            for (int line = HORIZONTAL; line <= VERTICAL; line++) {
                if (line == HORIZONTAL ? !horizontalOn(axis) : !verticalOn(axis)) {
                    continue;
                }
                int k0 = beamPhase(ctx, line);
                if (k0 < 0) {
                    continue;
                }
                int now = pass(state, line, period, tick, k0);
                if (now >= 0 && now != pass(state, line, period, previous, k0)) {
                    redrawGap(ctx, line);
                }
            }
        }
        place(ctx);
    }

    /** The frozen {@code k0} of this stack's {@code line} beam, or -1 when the beam has no entity. */
    private static int beamPhase(ModuleContext ctx, int line) {
        MatchState state = ctx.state();
        int tag = ctx.stackIndex();
        for (int index = 0; index < state.entityCount(); index++) {
            int id = state.entityIdAt(index);
            if (state.get(id, Entities.KIND) == EntityKind.HAZARD.ordinal() && state.tag(id) == tag
                    && line(state.aux(id)) == line) {
                return startPhase(state.aux(id));
            }
        }
        return -1;
    }

    /** A new pass has begun: one draw for the beam, written into every entity of it. */
    private static void redrawGap(ModuleContext ctx, int line) {
        MatchState state = ctx.state();
        int gStart = drawGap(ctx, beamLength(state, line));
        int tag = ctx.stackIndex();
        for (int index = 0; index < state.entityCount(); index++) {
            int id = state.entityIdAt(index);
            int aux = state.aux(id);
            if (state.get(id, Entities.KIND) == EntityKind.HAZARD.ordinal() && state.tag(id) == tag
                    && line(aux) == line) {
                state.setAux(id, HazardAux.withData(aux, line | (gStart << 1)));
            }
        }
    }

    /** Moves every beam entity of this stack to the beam's position for {@code ctx.tick()}; hides in place. */
    private static void place(ModuleContext ctx) {
        MatchState state = ctx.state();
        int period = ctx.props().getInt("period");
        String axis = ctx.props().getEnum("axis");
        int rowK0 = horizontalOn(axis) ? beamPhase(ctx, HORIZONTAL) : -1;
        int colK0 = verticalOn(axis) ? beamPhase(ctx, VERTICAL) : -1;
        int rowPos = rowK0 >= 0 ? position(state, HORIZONTAL, period, ctx.tick(), rowK0) : -1;
        int colPos = colK0 >= 0 ? position(state, VERTICAL, period, ctx.tick(), colK0) : -1;
        int tag = ctx.stackIndex();
        for (int index = 0; index < state.entityCount(); index++) {
            int id = state.entityIdAt(index);
            if (state.get(id, Entities.KIND) != EntityKind.HAZARD.ordinal() || state.tag(id) != tag) {
                continue;
            }
            int aux = state.aux(id);
            int line = line(aux);
            int along = HazardAux.counter(aux);
            int pos = line == HORIZONTAL ? rowPos : colPos;
            int flags = state.get(id, Entities.FLAGS);
            if (pos < 0) {
                state.set(id, Entities.FLAGS, flags | Entities.F_HIDDEN);
                continue;
            }
            int cx = line == HORIZONTAL ? along : pos;
            int cy = line == HORIZONTAL ? pos : along;
            int gStart = gapStart(aux);
            boolean inGap = gStart > 0 && along >= gStart && along < gStart + gapLength(beamLength(state, line));
            if (open(state.cell(cx, cy)) && !inGap) {
                flags &= ~Entities.F_HIDDEN;
            } else {
                flags |= Entities.F_HIDDEN;
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
        int length = beamLength(state, line);
        for (int i = 0; i < length; i++) {
            byte cell = line == HORIZONTAL ? state.cell(i, index) : state.cell(index, i);
            if (open(cell)) {
                return true;
            }
        }
        return false;
    }

    /** {@code {lo, hi}} of the qualifying lines, or null when none qualifies. */
    private static int[] range(MatchState state, int line) {
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
        return lo < 0 ? null : new int[] {lo, hi};
    }

    /** The pass index {@code (t / step + k0) / span} at {@code tick}, or -1 when no line qualifies. */
    static int pass(MatchState state, int line, int period, int tick, int k0) {
        int[] range = range(state, line);
        if (range == null) {
            return -1;
        }
        int span = range[1] - range[0] + 1;
        int step = Math.max(MIN_STEP, period / span);
        return (Math.max(0, tick) / step + k0) / span;
    }

    /** {@link #position(MatchState, int, int, int, int)} with start phase 0. */
    static int position(MatchState state, int line, int period, int tick) {
        return position(state, line, period, tick, 0);
    }

    /**
     * The line the beam sits on at {@code tick}: back and forth over the qualifying range with
     * {@code step = max(2, period / span)} ticks per line, phase-shifted by {@code k0}; -1 when no line qualifies.
     */
    static int position(MatchState state, int line, int period, int tick, int k0) {
        int[] range = range(state, line);
        if (range == null) {
            return -1;
        }
        int lo = range[0];
        int span = range[1] - lo + 1;
        int step = Math.max(MIN_STEP, period / span);
        int k = Math.max(0, tick) / step + k0;
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

    /** Beams over goal cells are hidden and never collide (001 A15.c); belt and braces: nothing. */
    @Override
    public void onGoal(ModuleContext ctx, int hazardEntityId, int goalSeat) {
    }
}
