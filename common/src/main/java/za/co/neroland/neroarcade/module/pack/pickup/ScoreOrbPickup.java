package za.co.neroland.neroarcade.module.pack.pickup;

import java.util.List;
import java.util.Set;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.hook.PickupModule;

/**
 * {@code neroarcade:pickup/score_orb} — orbs worth points.
 * <ul>
 *   <li>{@code value} — points per orb collected.</li>
 *   <li>{@code count} — orbs on the board (1..32).</li>
 * </ul>
 * Places {@code count} at match start (3 cells clear of every seat's spawn). When the RULE declares
 * {@code NEEDS_CLEARABLE} the orbs are the board to clear: placed once, never respawned (001 A8). Otherwise each
 * collected orb returns 20 ticks later through the respawn recipe (a hidden MARKER, TTL 20). Declares
 * {@code SCORES, CLEARABLE}. Odd combinations: an orb left on a {@code shrinking_floor} VOID cell stays there
 * unreachable (a clear-board game then ends by its clock). Stateless; pure.
 */
public final class ScoreOrbPickup implements PickupModule {

    public static final ScoreOrbPickup INSTANCE = new ScoreOrbPickup();

    /** Ticks before a collected orb returns. */
    static final int RESPAWN_DELAY = 20;

    private static final Identifier ID = Identifier.fromNamespaceAndPath("neroarcade", "pickup/score_orb");

    private static final List<PropertySpec> PROPERTIES = List.of(
            PropertySpec.integer("value", 10, 1, 100),
            PropertySpec.integer("count", 3, 1, 32));

    private static final Set<ModuleFlag> FLAGS = Set.of(ModuleFlag.SCORES, ModuleFlag.CLEARABLE);

    private ScoreOrbPickup() {
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

    /** "Orbs 3 · 10 pts". */
    @Override
    public Object[] effectArgs(PropertyValues props) {
        return new Object[] {props.getInt("count"), props.getInt("value")};
    }

    private static boolean clearing(ModuleContext ctx) {
        return Placement.ruleHas(ctx, ModuleFlag.NEEDS_CLEARABLE);
    }

    @Override
    public void onMatchStart(ModuleContext ctx) {
        Placement.placeAtStart(ctx, ctx.props().getInt("count"), Placement.PAWN_CLEARANCE);
    }

    @Override
    public void tick(ModuleContext ctx) {
        if (!clearing(ctx)) {
            Placement.topUp(ctx, ctx.props().getInt("count"));
        }
    }

    @Override
    public void onCollect(ModuleContext ctx, int seat, int pickupEntityId) {
        ctx.state().addScore(seat, ctx.props().getInt("value"));
        if (!clearing(ctx)) {
            Placement.spawnMarker(ctx.state(), ctx.stackIndex(), RESPAWN_DELAY);
        }
    }
}
