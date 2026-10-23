package za.co.neroland.neroarcade.module.pack.hazard;

import java.util.List;
import java.util.Set;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.hook.HazardModule;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.runtime.Cell;
import za.co.neroland.neroarcade.runtime.Deflect;
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.EntityKind;
import za.co.neroland.neroarcade.runtime.MatchState;

/**
 * {@code neroarcade:hazard/bouncing_ball} — balls that bounce off walls and bats and score past a goal line.
 * <ul>
 *   <li>{@code count} — balls kept on the board; a ball removed by a goal or a shot is re-served.</li>
 *   <li>{@code speed} — each axis moves {@code speed × 64} units per tick once served (diagonal).</li>
 * </ul>
 * A serve puts ball {@code i} at {@code (w/2 + i − count/2, h/2)} (or the nearest non-blocking cell when that one
 * blocks, e.g. a pillar), holds it still for 40 ticks (aux {@code counter}), then launches it on one of the four
 * diagonals from the match RNG. It moves in half-cell sub-steps per axis; entering a blocking cell negates that
 * axis and stops it for the tick; wrap axes pass through. A deflection reflects (the default) and adds
 * <em>english</em> from the bat's motion, clamped to {@code ±speed×64}, keeping both the perpendicular and the
 * along component at least 64 (no flat shots, no dead vertical ball); a still bat adds none. On a goal every other alive seat scores 1 (the runtime has
 * charged the goal seat) and the ball is re-served. Declares {@code SCORES}. Odd combinations: with a
 * {@code cursor} or {@code serpent} it is simply a ball to dodge (touch = damage); on {@code ring_24} it wraps on
 * X and only scores top/bottom; with {@code score_target} and no paddle nobody scores. Stateless; pure.
 */
public final class BouncingBallHazard implements HazardModule {

    public static final BouncingBallHazard INSTANCE = new BouncingBallHazard();

    /** Ticks a served ball waits before it launches. */
    static final int SERVE_DELAY = 40;
    /** Added to the ball's along-bat component per deflection by a moving bat. */
    static final int ENGLISH = 32;
    /** The smallest perpendicular speed after a deflection. */
    static final int MIN_PERPENDICULAR = 64;
    /** The smallest along-bat speed after english (008b Q2): english never flattens the ball onto one axis. */
    static final int MIN_ALONG = 64;

    private static final int SUB_STEP = MatchState.FP / 2;

    private static final Identifier ID = Identifier.fromNamespaceAndPath("neroarcade", "hazard/bouncing_ball");

    private static final List<PropertySpec> PROPERTIES = List.of(
            PropertySpec.integer("count", 1, 1, 4),
            PropertySpec.integer("speed", 3, 1, 5));

    private static final Set<ModuleFlag> FLAGS = Set.of(ModuleFlag.LETHAL, ModuleFlag.SCORES);

    private BouncingBallHazard() {
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

    /** "Balls 1 · speed 3". */
    @Override
    public Object[] effectArgs(PropertyValues props) {
        return new Object[] {props.getInt("count"), props.getInt("speed")};
    }

    @Override
    public void onMatchStart(ModuleContext ctx) {
        int count = ctx.props().getInt("count");
        for (int i = 0; i < count; i++) {
            serve(ctx, i, count);
        }
    }

    /** Places ball {@code i} on the centre line, still, with the serve delay in its counter. */
    static void serve(ModuleContext ctx, int i, int count) {
        MatchState state = ctx.state();
        int w = state.width();
        int h = state.height();
        int cx = Math.max(0, Math.min(w - 1, w / 2 + (i - count / 2)));
        int cy = h / 2;
        int[] cell = nearestOpen(state, cx, cy);
        int id = state.spawn(EntityKind.HAZARD, HazardAux.centre(cell[0]), HazardAux.centre(cell[1]), -1,
                ctx.stackIndex());
        if (id < 0) {
            return;
        }
        state.set(id, Entities.FLAGS, Entities.F_LETHAL);
        state.set(id, Entities.HP, 1);
        state.setAux(id, HazardAux.withCounter(state.aux(id), SERVE_DELAY));
    }

    /** {@code (cx, cy)} when it neither blocks nor is a goal cell, else the nearest such cell by a ring scan. */
    private static int[] nearestOpen(MatchState state, int cx, int cy) {
        int max = Math.max(state.width(), state.height());
        for (int r = 0; r <= max; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) {
                        continue;
                    }
                    int x = cx + dx;
                    int y = cy + dy;
                    if (x < 0 || y < 0 || x >= state.width() || y >= state.height()) {
                        continue;
                    }
                    byte v = state.cell(x, y);
                    if (!Cell.blocks(v) && Cell.goalSeat(v) < 0) {
                        return new int[] {x, y};
                    }
                }
            }
        }
        return new int[] {cx, cy};
    }

    @Override
    public void tick(ModuleContext ctx) {
        MatchState state = ctx.state();
        int tag = ctx.stackIndex();
        int velocity = ctx.props().getInt("speed") * 64;
        int index = 0;
        while (index < state.entityCount()) {
            int id = state.entityIdAt(index);
            index++;
            if (state.get(id, Entities.KIND) != EntityKind.HAZARD.ordinal() || state.tag(id) != tag) {
                continue;
            }
            int aux = state.aux(id);
            int counter = HazardAux.counter(aux);
            if (counter > 0) {
                counter--;
                state.setAux(id, HazardAux.withCounter(aux, counter));
                if (counter == 0) {
                    int d = ctx.rng().nextInt(0, 3);
                    state.set(id, Entities.VX, (d & 1) != 0 ? velocity : -velocity);
                    state.set(id, Entities.VY, (d & 2) != 0 ? velocity : -velocity);
                }
                continue;
            }
            moveAxis(state, id, true);
            moveAxis(state, id, false);
        }
        int count = ctx.props().getInt("count");
        int live = HazardAux.countTagged(state, EntityKind.HAZARD, tag);
        if (live < count) {
            serve(ctx, live, count);
        }
    }

    /** Half-cell sub-steps along one axis; entering a blocking cell negates that axis and stops it this tick. */
    private static void moveAxis(MatchState state, int id, boolean horizontal) {
        int field = horizontal ? Entities.VX : Entities.VY;
        int v = state.get(id, field);
        int x = state.get(id, Entities.X);
        int y = state.get(id, Entities.Y);
        int sign = Integer.signum(v);
        int remaining = Math.abs(v);
        int pos = horizontal ? x : y;
        while (remaining > 0) {
            int step = Math.min(SUB_STEP, remaining);
            int next = horizontal ? state.wrapX(pos + sign * step) : state.wrapY(pos + sign * step);
            int cx = (horizontal ? next : x) >> MatchState.FP_SHIFT;
            int cy = (horizontal ? y : next) >> MatchState.FP_SHIFT;
            if (Cell.blocks(state.cell(cx, cy))) {
                state.set(id, field, -v);
                break;
            }
            pos = next;
            remaining -= step;
        }
        state.set(id, horizontal ? Entities.X : Entities.Y, pos);
    }

    /**
     * Reflect (the default), then english: the bat's non-zero velocity component (VX first) adds
     * {@code sign × 32} to the ball's matching component, clamped to {@code ±speed×64}, then kept at a magnitude
     * of at least {@link #MIN_ALONG} (its own sign, or the bat's when it is zero); the other component is kept at a
     * magnitude of at least 64, pointing away from the bat when it was zero.
     */
    @Override
    public void onDeflected(ModuleContext ctx, int seat, int hazardEntityId, int pawnEntityId) {
        MatchState state = ctx.state();
        Deflect.reflect(state, hazardEntityId);
        if (!state.exists(hazardEntityId) || pawnEntityId < 0 || !state.exists(pawnEntityId)) {
            return;
        }
        int pvx = state.get(pawnEntityId, Entities.VX);
        int pvy = state.get(pawnEntityId, Entities.VY);
        boolean alongX;
        int pawnV;
        if (pvx != 0) {
            alongX = true;
            pawnV = pvx;
        } else if (pvy != 0) {
            alongX = false;
            pawnV = pvy;
        } else {
            return;
        }
        int max = ctx.props().getInt("speed") * 64;
        int alongField = alongX ? Entities.VX : Entities.VY;
        int perpField = alongX ? Entities.VY : Entities.VX;
        int along = state.get(hazardEntityId, alongField) + Integer.signum(pawnV) * ENGLISH;
        along = Math.max(-max, Math.min(max, along));
        if (Math.abs(along) < MIN_ALONG) {
            along = (along == 0 ? Integer.signum(pawnV) : Integer.signum(along)) * MIN_ALONG;
        }
        state.set(hazardEntityId, alongField, along);
        int perp = state.get(hazardEntityId, perpField);
        if (Math.abs(perp) < MIN_PERPENDICULAR) {
            int sign = Integer.signum(perp);
            if (sign == 0) {
                int coordField = alongX ? Entities.Y : Entities.X;
                int away = state.get(hazardEntityId, coordField) - state.get(pawnEntityId, coordField);
                sign = away < 0 ? -1 : 1;
            }
            state.set(hazardEntityId, perpField, sign * MIN_PERPENDICULAR);
        }
    }

    /** Every other alive seat scores 1, then the ball is removed (the next {@code tick} re-serves). */
    @Override
    public void onGoal(ModuleContext ctx, int hazardEntityId, int goalSeat) {
        MatchState state = ctx.state();
        for (int s = 0; s < state.seatCount(); s++) {
            if (s != goalSeat && state.seatAlive(s)) {
                state.addScore(s, 1);
            }
        }
        state.remove(hazardEntityId);
    }
}
