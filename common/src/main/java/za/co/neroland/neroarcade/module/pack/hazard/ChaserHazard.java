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
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.EntityKind;
import za.co.neroland.neroarcade.runtime.MatchState;

/**
 * {@code neroarcade:hazard/chaser} — drones that walk cell by cell toward the nearest pawn through the maze.
 * <ul>
 *   <li>{@code count} — drones kept on the board (a destroyed one returns 60 ticks later).</li>
 *   <li>{@code speed} — grid stepper: {@code acc += speed × 64} per tick, one cell per 256 (speed 4 = a cell a
 *       tick; STUN never applies).</li>
 * </ul>
 * Per step the target is the nearest alive pawn (Chebyshev, wrap-aware, lower seat on ties); of the four
 * neighbours (skipping blocking, {@code F_SOLID}, GOAL cells and cells holding another drone of this stack) it
 * takes the lowest wrap-aware Manhattan distance to the target, never the 180° reversal unless that is the only
 * open cell; ties prefer continuing straight, then up, down, left, right. No open cell or no target → it stays.
 * Spawned {@code HP 2}, five cells clear of the pawns. A shot costs 1 hp; the second destroys it and gives the
 * shooter 5. A paddle bats it back (the default reflect of its render velocity; the next step re-centres it).
 * A destroyed drone (shot or goal) leaves a hidden MARKER (TTL 60); while drones + markers of this stack are
 * fewer than {@code count} a drone is placed. Odd combinations: wrap arenas → it takes the shorter way round;
 * {@code shield} → the touch is absorbed by the seat. Cost: ≤ 12 drones × 2 steps × 4 cells × (1 cell read +
 * ≤ 256 solid reads + ≤ 256 occupancy reads) ≈ 50 k int reads per tick, no allocation per step. Stateless; pure.
 */
public final class ChaserHazard implements HazardModule {

    public static final ChaserHazard INSTANCE = new ChaserHazard();

    /** Ticks a destroyed drone stays away. */
    static final int RESPAWN_TICKS = 60;
    /** Hit points of a drone. */
    static final int HP = 2;
    /** Score for destroying a drone. */
    static final int KILL_SCORE = 5;
    /** Placement clearance from the pawns. */
    static final int PAWN_CLEARANCE = 5;

    private static final Identifier ID = Identifier.fromNamespaceAndPath("neroarcade", "hazard/chaser");

    private static final List<PropertySpec> PROPERTIES = List.of(
            PropertySpec.integer("count", 1, 1, 4),
            PropertySpec.integer("speed", 2, 1, 5));

    private static final Set<ModuleFlag> FLAGS = Set.of(ModuleFlag.LETHAL);

    /** Tie order after "continue straight": up, down, left, right. */
    private static final int[] ORDER = {HazardAux.UP, HazardAux.DOWN, HazardAux.LEFT, HazardAux.RIGHT};

    private ChaserHazard() {
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

    /** "Drones 1 · speed 2". */
    @Override
    public Object[] effectArgs(PropertyValues props) {
        return new Object[] {props.getInt("count"), props.getInt("speed")};
    }

    @Override
    public void onMatchStart(ModuleContext ctx) {
        int count = ctx.props().getInt("count");
        for (int i = 0; i < count; i++) {
            place(ctx, true);
        }
    }

    /** One drone at a random free cell clear of the pawns; nothing when no cell is found or the budget is full. */
    private static void place(ModuleContext ctx, boolean atStart) {
        int[] cell = HazardAux.randomFreeCell(ctx, PAWN_CLEARANCE, atStart);
        if (cell == null) {
            return;
        }
        MatchState state = ctx.state();
        int id = state.spawn(EntityKind.HAZARD, HazardAux.centre(cell[0]), HazardAux.centre(cell[1]), -1,
                ctx.stackIndex());
        if (id < 0) {
            return;
        }
        state.set(id, Entities.HP, HP);
        state.set(id, Entities.FLAGS, Entities.F_LETHAL);
        state.setAux(id, HazardAux.withDir(state.aux(id), HazardAux.DOWN));
    }

    @Override
    public void tick(ModuleContext ctx) {
        MatchState state = ctx.state();
        int tag = ctx.stackIndex();
        int speed = ctx.props().getInt("speed");
        for (int index = 0; index < state.entityCount(); index++) {
            int id = state.entityIdAt(index);
            if (state.get(id, Entities.KIND) != EntityKind.HAZARD.ordinal() || state.tag(id) != tag) {
                continue;
            }
            int aux = state.aux(id);
            int acc = HazardAux.acc(aux) + speed * 64;
            while (acc >= MatchState.FP) {
                acc -= MatchState.FP;
                step(state, id, tag);
            }
            aux = HazardAux.withAcc(state.aux(id), acc);
            state.setAux(id, aux);
            int dir = HazardAux.dir(aux);
            state.set(id, Entities.VX, HazardAux.dx(dir) * speed * 64);
            state.set(id, Entities.VY, HazardAux.dy(dir) * speed * 64);
        }
        int count = ctx.props().getInt("count");
        int live = HazardAux.countTagged(state, EntityKind.HAZARD, tag)
                + HazardAux.countTagged(state, EntityKind.MARKER, tag);
        if (live < count) {
            place(ctx, false);
        }
    }

    /** One grid step of drone {@code id} toward the nearest pawn (see the class comment). */
    static void step(MatchState state, int id, int tag) {
        int w = state.width();
        int h = state.height();
        boolean wx = state.wrapsX();
        boolean wy = state.wrapsY();
        int cx = state.get(id, Entities.X) >> MatchState.FP_SHIFT;
        int cy = state.get(id, Entities.Y) >> MatchState.FP_SHIFT;
        int tx = 0;
        int ty = 0;
        int best = Integer.MAX_VALUE;
        for (int s = 0; s < state.seatCount(); s++) {
            int pawn = state.pawnId(s);
            if (!state.seatAlive(s) || pawn < 0 || !state.exists(pawn)) {
                continue;
            }
            int px = state.get(pawn, Entities.X) >> MatchState.FP_SHIFT;
            int py = state.get(pawn, Entities.Y) >> MatchState.FP_SHIFT;
            int d = Math.max(Math.abs(HazardAux.delta(cx, px, w, wx)), Math.abs(HazardAux.delta(cy, py, h, wy)));
            if (d < best) {
                best = d;
                tx = px;
                ty = py;
            }
        }
        if (best == Integer.MAX_VALUE) {
            return;
        }
        int aux = state.aux(id);
        int dir = HazardAux.dir(aux);
        int reverse = HazardAux.opposite(dir);
        int chosen = -1;
        int chosenDist = Integer.MAX_VALUE;
        int open = 0;
        boolean reverseOpen = false;
        for (int k = -1; k < ORDER.length; k++) {
            int d = k < 0 ? dir : ORDER[k];
            if (k >= 0 && d == dir) {
                continue;
            }
            int nx = cx + HazardAux.dx(d);
            int ny = cy + HazardAux.dy(d);
            if (wx) {
                nx = Math.floorMod(nx, w);
            }
            if (wy) {
                ny = Math.floorMod(ny, h);
            }
            if (!enterable(state, id, tag, nx, ny)) {
                continue;
            }
            open++;
            if (d == reverse) {
                reverseOpen = true;
                continue;
            }
            int dist = Math.abs(HazardAux.delta(nx, tx, w, wx)) + Math.abs(HazardAux.delta(ny, ty, h, wy));
            if (dist < chosenDist) {
                chosenDist = dist;
                chosen = d;
            }
        }
        if (chosen < 0 && reverseOpen && open == 1) {
            chosen = reverse;
        }
        if (chosen < 0) {
            return;
        }
        int nx = cx + HazardAux.dx(chosen);
        int ny = cy + HazardAux.dy(chosen);
        state.set(id, Entities.X, state.wrapX(HazardAux.centre(nx)));
        state.set(id, Entities.Y, state.wrapY(HazardAux.centre(ny)));
        state.setAux(id, HazardAux.withDir(state.aux(id), chosen));
    }

    /** Not blocking, no {@code F_SOLID} entity, not a GOAL cell, no other drone of this stack. */
    private static boolean enterable(MatchState state, int self, int tag, int nx, int ny) {
        byte cell = state.cell(nx, ny);
        if (Cell.blocks(cell) || Cell.goalSeat(cell) >= 0 || state.solidAt(nx, ny)) {
            return false;
        }
        for (int i = 0; i < state.entityCount(); i++) {
            int other = state.entityIdAt(i);
            if (other != self && state.get(other, Entities.KIND) == EntityKind.HAZARD.ordinal()
                    && state.tag(other) == tag && state.occupies(other, nx, ny)) {
                return false;
            }
        }
        return true;
    }

    /** hp −1; at 0 the drone is destroyed, the shooter scores 5 and the respawn timer starts. */
    @Override
    public void onShot(ModuleContext ctx, int ownerSeat, int hazardEntityId, int projectileEntityId) {
        MatchState state = ctx.state();
        if (state.damageEntity(hazardEntityId, 1)) {
            state.addScore(ownerSeat, KILL_SCORE);
            HazardAux.spawnMarker(state, ctx.stackIndex(), RESPAWN_TICKS);
        }
    }

    /** Removed (default) and the respawn timer starts. Drones avoid goal cells, so only a bat can push one there. */
    @Override
    public void onGoal(ModuleContext ctx, int hazardEntityId, int goalSeat) {
        ctx.state().remove(hazardEntityId);
        HazardAux.spawnMarker(ctx.state(), ctx.stackIndex(), RESPAWN_TICKS);
    }
}
