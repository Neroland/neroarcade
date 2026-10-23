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
 * {@code neroarcade:hazard/falling_blocks} — crates drop from the top of a random column and fall straight down.
 * <ul>
 *   <li>{@code rate} — spawn chance per tick is {@code rate / 32} (a crate per 8 ticks at the default 4).</li>
 *   <li>{@code speed} — fall speed: {@code VY = clamp((speed × 64 × gravity) >> 5, 16, 256)} units/tick, so
 *       {@code low_gravity} slows the fall.</li>
 * </ul>
 * A crate spawns on the first non-blocking row of its column (skipped when that cell is occupied), ignores
 * {@code F_SOLID} entities and other crates, and is removed when it would enter a blocking cell or leave the
 * board. At most 48 live crates per stack. A paddle that deflects a crate <em>catches</em> it (removed, +1 to the
 * catcher); a crate reaching a goal line costs that seat a life (the runtime) and is removed (default
 * {@code onGoal}), which is the catch game's loss. Shots use the default (hp 1, destroyed). Stateless; pure.
 */
public final class FallingBlocksHazard implements HazardModule {

    public static final FallingBlocksHazard INSTANCE = new FallingBlocksHazard();

    /** Live crates per stack. */
    static final int CAP = 48;

    private static final Identifier ID = Identifier.fromNamespaceAndPath("neroarcade", "hazard/falling_blocks");

    private static final List<PropertySpec> PROPERTIES = List.of(
            PropertySpec.integer("rate", 4, 1, 10),
            PropertySpec.integer("speed", 2, 1, 5));

    private static final Set<ModuleFlag> FLAGS = Set.of(ModuleFlag.LETHAL);

    private FallingBlocksHazard() {
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

    /** "Crates 4/32 · speed 2". */
    @Override
    public Object[] effectArgs(PropertyValues props) {
        return new Object[] {props.getInt("rate"), props.getInt("speed")};
    }

    @Override
    public void onMatchStart(ModuleContext ctx) {
    }

    /** The fall speed in units per tick for {@code speed} under {@code gravity}. */
    static int fallSpeed(int speed, int gravity) {
        return Math.max(16, Math.min(256, (speed * 64 * gravity) >> 5));
    }

    @Override
    public void tick(ModuleContext ctx) {
        MatchState state = ctx.state();
        int tag = ctx.stackIndex();
        int w = state.width();
        int h = state.height();
        if (ctx.rng().chance(ctx.props().getInt("rate"), 32)
                && HazardAux.countTagged(state, EntityKind.HAZARD, tag) < CAP) {
            int x = ctx.rng().nextInt(0, w - 1);
            int y = 0;
            while (y < h && Cell.blocks(state.cell(x, y))) {
                y++;
            }
            if (y < h && !occupied(state, x, y)) {
                int id = state.spawn(EntityKind.HAZARD, HazardAux.centre(x), HazardAux.centre(y), -1, tag);
                if (id >= 0) {
                    state.set(id, Entities.VY, fallSpeed(ctx.props().getInt("speed"), state.gravity()));
                    state.set(id, Entities.HP, 1);
                    state.set(id, Entities.FLAGS, Entities.F_LETHAL);
                }
            }
        }
        int index = 0;
        while (index < state.entityCount()) {
            int id = state.entityIdAt(index);
            if (state.get(id, Entities.KIND) != EntityKind.HAZARD.ordinal() || state.tag(id) != tag) {
                index++;
                continue;
            }
            int ny = state.get(id, Entities.Y) + state.get(id, Entities.VY);
            int cy = ny >> MatchState.FP_SHIFT;
            if (cy >= h || Cell.blocks(state.cell(state.get(id, Entities.X) >> MatchState.FP_SHIFT, cy))) {
                state.remove(id);
                continue;
            }
            state.set(id, Entities.Y, ny);
            index++;
        }
    }

    private static boolean occupied(MatchState state, int x, int y) {
        for (int i = 0; i < state.entityCount(); i++) {
            int id = state.entityIdAt(i);
            if (state.get(id, Entities.KIND) != EntityKind.MARKER.ordinal() && state.occupies(id, x, y)) {
                return true;
            }
        }
        return false;
    }

    /** A paddle catches the crate: it disappears and the catcher scores 1. */
    @Override
    public void onDeflected(ModuleContext ctx, int seat, int hazardEntityId, int pawnEntityId) {
        ctx.state().remove(hazardEntityId);
        ctx.state().addScore(seat, 1);
    }
}
