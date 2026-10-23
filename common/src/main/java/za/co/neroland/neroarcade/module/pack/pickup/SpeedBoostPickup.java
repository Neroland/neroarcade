package za.co.neroland.neroarcade.module.pack.pickup;

import java.util.List;
import java.util.Set;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.hook.PickupModule;
import za.co.neroland.neroarcade.runtime.SeatState;

/**
 * {@code neroarcade:pickup/speed_boost} — grab it to move faster for a while.
 * <ul>
 *   <li>{@code duration} — ticks of the seat's SPEED status (pawns add 2 to their speed while it lasts).</li>
 *   <li>{@code count} — boosts on the board.</li>
 * </ul>
 * Places {@code count} at match start; a collected boost returns 100 ticks later through the respawn recipe
 * (hidden MARKER, TTL 100). A second boost while one runs restarts the duration (no stacking). Pawns that ignore
 * SPEED (none in 0.1.0 besides a STUNned one) simply waste it. Stateless; pure.
 */
public final class SpeedBoostPickup implements PickupModule {

    public static final SpeedBoostPickup INSTANCE = new SpeedBoostPickup();

    /** Ticks before a collected boost returns. */
    static final int RESPAWN_DELAY = 100;

    private static final Identifier ID = Identifier.fromNamespaceAndPath("neroarcade", "pickup/speed_boost");

    private static final List<PropertySpec> PROPERTIES = List.of(
            PropertySpec.integer("duration", 100, 20, 400),
            PropertySpec.integer("count", 1, 1, 4));

    private SpeedBoostPickup() {
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
        return Set.of();
    }

    /** "Boost 1 · 100 ticks". */
    @Override
    public Object[] effectArgs(PropertyValues props) {
        return new Object[] {props.getInt("count"), props.getInt("duration")};
    }

    @Override
    public void onMatchStart(ModuleContext ctx) {
        Placement.placeAtStart(ctx, ctx.props().getInt("count"), Placement.PAWN_CLEARANCE);
    }

    @Override
    public void tick(ModuleContext ctx) {
        Placement.topUp(ctx, ctx.props().getInt("count"));
    }

    @Override
    public void onCollect(ModuleContext ctx, int seat, int pickupEntityId) {
        ctx.state().setStatus(seat, SeatState.SPEED, ctx.props().getInt("duration"));
        Placement.spawnMarker(ctx.state(), ctx.stackIndex(), RESPAWN_DELAY);
    }
}
