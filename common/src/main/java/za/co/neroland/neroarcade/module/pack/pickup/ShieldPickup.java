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
 * {@code neroarcade:pickup/shield} — grab it to shrug off hits for a while.
 * <ul>
 *   <li>{@code duration} — ticks of the seat's SHIELD status; {@code MatchState.damageSeat} ignores every hit
 *       while it lasts (hazards, enemy shots, pawn bites, VOID, a scrolled-in wall).</li>
 *   <li>{@code count} — shields on the board.</li>
 * </ul>
 * Places {@code count} at match start; a collected shield returns 100 ticks later through the respawn recipe
 * (hidden MARKER, TTL 100). A second shield restarts the duration. Odd combinations: with {@code chaser} the
 * drone's touch is absorbed; with {@code shrinking_floor} a shielded pawn survives VOID only until it expires.
 * Stateless; pure.
 */
public final class ShieldPickup implements PickupModule {

    public static final ShieldPickup INSTANCE = new ShieldPickup();

    /** Ticks before a collected shield returns. */
    static final int RESPAWN_DELAY = 100;

    private static final Identifier ID = Identifier.fromNamespaceAndPath("neroarcade", "pickup/shield");

    private static final List<PropertySpec> PROPERTIES = List.of(
            PropertySpec.integer("duration", 100, 20, 400),
            PropertySpec.integer("count", 1, 1, 4));

    private ShieldPickup() {
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

    /** "Shield 1 · 100 ticks". */
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
        ctx.state().setStatus(seat, SeatState.SHIELD, ctx.props().getInt("duration"));
        Placement.spawnMarker(ctx.state(), ctx.stackIndex(), RESPAWN_DELAY);
    }
}
