package za.co.neroland.neroarcade.module.pack.multiplayer;

import java.util.List;
import java.util.Set;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.hook.MultiplayerModule;
import za.co.neroland.neroarcade.module.hook.RuleOutcome;
import za.co.neroland.neroarcade.module.hook.SeatLayout;
import za.co.neroland.neroarcade.runtime.Budgets;
import za.co.neroland.neroarcade.runtime.MatchState;

/**
 * {@code neroarcade:multiplayer/turns} — hot-seat: players take turns at one cabinet (001 A10). 0.1.0 runs it
 * on a single cabinet only and it is uncredited (003 §4.5); none of that is this chip's business.
 *
 * <ul>
 *   <li>{@code seats} (2..4, default 2): how many players share the controls.</li>
 *   <li>{@code turn_seconds} (10..120, default 30): how long each turn lasts, in base ticks / 20.</li>
 * </ul>
 *
 * <p><b>Stateless rotation.</b> The seat on turn is the {@code slot}-th set bit (ascending) of the alive mask,
 * {@code slot = (tick / (turn_seconds * 20)) % bitCount(alive)} — a pure function of the state's tick and alive
 * mask, so client HUDs call {@link #activeSeatMask} (or {@link #activeMask}) on a snapshot to show whose turn
 * it is. Dead seats are skipped; when the alive set changes, the rotation re-derives at once.
 *
 * <p><b>Bodies.</b> {@link #afterTick} keeps exactly the active seat's body on the board: every alive seat
 * outside the mask that still has a pawn loses it ({@code removeSeatBody}), and the active seat, if it has no
 * pawn and is not counting down a respawn, gets one through the PAWN chip's {@code onRespawn}. Enforcing this
 * every tick (rather than only on the tick the mask changes) is a superset of A10's "on a turn change" and also
 * covers the rotation shifting because a seat ran out of lives, and a waiting seat's respawn timer expiring.
 * At tick 0 this removes every body except seat 0's. Score is per seat; the rule's outcome passes through
 * ({@code survive_time} = everyone who survived their turns wins; {@code score_target} = first to reach it,
 * even if a waiting seat never got a turn).
 *
 * <p>Odd combinations: started with one seat ({@code MULTI_SEAT_ONLY}, no {@code RUNTIME_OPPONENT}) the
 * rotation has one seat, so seat 0 is always on turn and the rule's solo fallback applies; the bench warns
 * {@code VERSUS_NEEDS_SEATS}. Stateless; pure.
 */
public final class TurnsMultiplayer implements MultiplayerModule {

    public static final TurnsMultiplayer INSTANCE = new TurnsMultiplayer();

    private static final Identifier ID = Identifier.fromNamespaceAndPath("neroarcade", "multiplayer/turns");

    private static final List<PropertySpec> PROPERTIES = List.of(
            PropertySpec.integer("seats", 2, 2, 4),
            PropertySpec.integer("turn_seconds", 30, 10, 120));

    private TurnsMultiplayer() {
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
        return Set.of(ModuleFlag.MULTI_SEAT_ONLY);
    }

    /** "2 players · 30 s turns". */
    @Override
    public Object[] effectArgs(PropertyValues props) {
        return new Object[] {props.getInt("seats"), props.getInt("turn_seconds")};
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
        return false;
    }

    /** A10: one bit — the seat on turn — or 0 when no seat is alive. Pure. */
    @Override
    public int activeSeatMask(ModuleContext ctx) {
        MatchState state = ctx.state();
        return activeMask(state.tick(), state.aliveSeatMask(), ctx.props().getInt("turn_seconds"));
    }

    /**
     * The rotation as a plain function: the {@code ((tick / (turnSeconds * 20)) % bitCount(alive))}-th set
     * bit of {@code alive}, ascending; 0 when {@code alive == 0}.
     */
    public static int activeMask(int tick, int alive, int turnSeconds) {
        int n = Integer.bitCount(alive);
        if (n == 0) {
            return 0;
        }
        int turnTicks = Math.max(1, turnSeconds * Budgets.TICKS_PER_SECOND);
        int slot = (Math.max(0, tick) / turnTicks) % n;
        int rest = alive;
        for (int i = 0; i < slot; i++) {
            rest &= rest - 1;
        }
        return Integer.lowestOneBit(rest);
    }

    @Override
    public void afterTick(ModuleContext ctx) {
        MatchState state = ctx.state();
        int active = activeSeatMask(ctx);
        ModuleContext pawnCtx = null;
        for (int seat = 0; seat < state.seatCount(); seat++) {
            if (!state.seatAlive(seat)) {
                continue;
            }
            int pawn = state.pawnId(seat);
            boolean hasBody = pawn >= 0 && state.exists(pawn);
            if ((active & (1 << seat)) == 0) {
                if (pawn >= 0) {
                    state.removeSeatBody(seat);
                }
            } else if (!hasBody && state.respawnTicks(seat) == 0) {
                if (pawnCtx == null) {
                    pawnCtx = ctx.withProps(ctx.game().pawnProps(), 0);
                }
                ctx.game().pawn().onRespawn(pawnCtx, seat);
            }
        }
    }

    @Override
    public RuleOutcome resolve(ModuleContext ctx, RuleOutcome fromRule) {
        return fromRule;
    }
}
