package za.co.neroland.neroarcade.module.pack.multiplayer;

import java.util.List;
import java.util.Set;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.module.ModuleDefaults;
import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.hook.MultiplayerModule;
import za.co.neroland.neroarcade.module.hook.RuleOutcome;
import za.co.neroland.neroarcade.module.hook.SeatLayout;

/**
 * {@code neroarcade:multiplayer/solo} — the default: one seat, the whole board, the rule's outcome as is.
 * No properties. Every alive seat is active; lives are per seat; no runtime opponent.
 *
 * <p>Odd combinations: under a {@code MULTI_SEAT_ONLY} rule (e.g. {@code last_standing}) there is no runtime
 * opponent, so the rule's {@code onSeatDropped(ctx, 1)} solo fallback applies from tick 0. Stateless; pure.
 */
public final class SoloMultiplayer implements MultiplayerModule {

    public static final SoloMultiplayer INSTANCE = new SoloMultiplayer();

    private SoloMultiplayer() {
    }

    @Override
    public Identifier id() {
        return ModuleDefaults.MULTIPLAYER;
    }

    @Override
    public List<PropertySpec> properties() {
        return List.of();
    }

    @Override
    public Set<ModuleFlag> flags() {
        return Set.of();
    }

    @Override
    public int seats(PropertyValues props) {
        return 1;
    }

    @Override
    public SeatLayout layout(ModuleContext ctx) {
        return SeatLayout.shared(ctx.state().seatCount(), ctx.state().width(), ctx.state().height());
    }

    @Override
    public boolean sharesLives(PropertyValues props) {
        return false;
    }

    @Override
    public RuleOutcome resolve(ModuleContext ctx, RuleOutcome fromRule) {
        return fromRule;
    }
}
