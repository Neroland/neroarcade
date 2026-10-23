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
 * {@code neroarcade:pickup/key} — keys that unlock the exit.
 * <ul>
 *   <li>{@code count} — keys placed once at match start, 4 cells clear of every seat's spawn.</li>
 * </ul>
 * Keys never respawn (001 A8), whatever the rule. The key/exit handshake is flags and cells only: a rule with
 * {@code NEEDS_KEY} writes {@code EXIT_LOCKED} and flips it to {@code EXIT} once no live PICKUP whose stack chip
 * has {@code IS_KEY} remains, so collecting a key does nothing here. Without a {@code NEEDS_KEY} rule a key is a
 * 5-point orb (bench: {@code KEY_WITHOUT_EXIT}). Declares {@code IS_KEY, CLEARABLE} (a clear-board game counts
 * keys too). Stateless; pure.
 */
public final class KeyPickup implements PickupModule {

    public static final KeyPickup INSTANCE = new KeyPickup();

    /** Score of a key when no rule needs one. */
    static final int ORB_VALUE = 5;
    /** Pawn clearance of the start placement. */
    static final int PAWN_CLEARANCE = 4;

    private static final Identifier ID = Identifier.fromNamespaceAndPath("neroarcade", "pickup/key");

    private static final List<PropertySpec> PROPERTIES = List.of(PropertySpec.integer("count", 1, 1, 4));

    private static final Set<ModuleFlag> FLAGS = Set.of(ModuleFlag.IS_KEY, ModuleFlag.CLEARABLE);

    private KeyPickup() {
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

    /** "Keys 1 · unlock exit". */
    @Override
    public Object[] effectArgs(PropertyValues props) {
        return new Object[] {props.getInt("count")};
    }

    @Override
    public void onMatchStart(ModuleContext ctx) {
        Placement.placeAtStart(ctx, ctx.props().getInt("count"), PAWN_CLEARANCE);
    }

    /** Keys never respawn. */
    @Override
    public void tick(ModuleContext ctx) {
    }

    @Override
    public void onCollect(ModuleContext ctx, int seat, int pickupEntityId) {
        if (!Placement.ruleHas(ctx, ModuleFlag.NEEDS_KEY)) {
            ctx.state().addScore(seat, ORB_VALUE);
        }
    }
}
