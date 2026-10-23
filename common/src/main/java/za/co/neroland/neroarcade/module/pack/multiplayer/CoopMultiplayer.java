package za.co.neroland.neroarcade.module.pack.multiplayer;

import java.util.List;
import java.util.Set;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.hook.MultiplayerModule;
import za.co.neroland.neroarcade.module.hook.RuleOutcome;
import za.co.neroland.neroarcade.module.hook.SeatLayout;

/**
 * {@code neroarcade:multiplayer/coop} — two to four players on one shared board, playing together.
 *
 * <ul>
 *   <li>{@code seats} (2..4, default 2): how many seats the cabinet offers.</li>
 *   <li>{@code shared_lives} (default true): one pool of lives for the whole team. The pool is the rule's
 *       per-seat starting lives (seat 0's), taken by the runtime at match start; any seat's death spends from
 *       it, and when it reaches zero every seat is out. False: each seat keeps its own lives.</li>
 * </ul>
 *
 * <p>The board is shared ({@link SeatLayout#shared}); every alive seat is active; the rule's outcome passes
 * through unchanged — a WIN already names every alive seat, and {@code score_target} wins for whichever seat
 * reached the target (co-op scores stay per seat; the results strip sums them).
 *
 * <p>Odd combinations: with one seat it plays exactly like {@code solo} (no flag, so no bench warning);
 * under a {@code MULTI_SEAT_ONLY} rule with one seat there is no runtime opponent (no
 * {@code RUNTIME_OPPONENT}) and the rule's solo fallback applies. Stateless; pure.
 */
public final class CoopMultiplayer implements MultiplayerModule {

    public static final CoopMultiplayer INSTANCE = new CoopMultiplayer();

    private static final Identifier ID = Identifier.fromNamespaceAndPath("neroarcade", "multiplayer/coop");

    private static final List<PropertySpec> PROPERTIES = List.of(
            PropertySpec.integer("seats", 2, 2, 4),
            PropertySpec.bool("shared_lives", true));

    private CoopMultiplayer() {
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

    /** "2 co-op · shared lives" / "2 co-op · own lives". */
    @Override
    public Object[] effectArgs(PropertyValues props) {
        return new Object[] {props.getInt("seats"),
                Component.translatable(langKey() + (props.getBool("shared_lives") ? ".shared" : ".separate"))};
    }

    @Override
    public int seats(PropertyValues props) {
        return props.getInt("seats");
    }

    @Override
    public SeatLayout layout(ModuleContext ctx) {
        return SeatLayout.shared(ctx.state().seatCount(), ctx.state().width(), ctx.state().height());
    }

    @Override
    public boolean sharesLives(PropertyValues props) {
        return props.getBool("shared_lives");
    }

    @Override
    public RuleOutcome resolve(ModuleContext ctx, RuleOutcome fromRule) {
        return fromRule;
    }

    /** The Arcade Exchange genre word ({@code neroarcade.tag.coop}). */
    @Override
    public String genreTag() {
        return "coop";
    }
}
