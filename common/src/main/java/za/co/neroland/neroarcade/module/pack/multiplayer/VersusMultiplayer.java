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
import za.co.neroland.neroarcade.runtime.MatchState;

/**
 * {@code neroarcade:multiplayer/versus} — two to four players compete on one shared board (001 A9: no
 * {@code split}; viewport splitting is 0.2.0).
 *
 * <ul>
 *   <li>{@code seats} (2..4, default 2): how many seats the cabinet offers.</li>
 * </ul>
 *
 * <p>Lives are per seat. {@link #resolve} turns any rule into a contest:
 * <ul>
 *   <li>rule CONTINUE, two or more seats: exactly one seat alive → that seat WINs; none alive → DRAW
 *       (winners 0); otherwise CONTINUE. So {@code survive_time + versus} ends when one seat is left instead
 *       of leaving the survivor alone with the clock ({@code last_standing} reaches the same answer first).</li>
 *   <li>rule WIN naming more than one seat → narrowed to the highest score among those seats (ties keep
 *       every tied seat).</li>
 *   <li>rule LOSE → DRAW with winners 0 (a versus match has no collective loss).</li>
 *   <li>DRAW (including the runtime's hard stop) passes through.</li>
 * </ul>
 *
 * <p>Odd combinations: started with one human seat it declares {@code RUNTIME_OPPONENT}, so the runtime
 * fills seat 1 with the PAWN chip's opponent and the bench raises no {@code VERSUS_NEEDS_SEATS}. Under a
 * {@code SURVIVAL} rule with no threat the seats can only end it by the clock (a timed WIN for both, narrowed
 * by score). Stateless; pure.
 */
public final class VersusMultiplayer implements MultiplayerModule {

    public static final VersusMultiplayer INSTANCE = new VersusMultiplayer();

    private static final Identifier ID = Identifier.fromNamespaceAndPath("neroarcade", "multiplayer/versus");

    private static final List<PropertySpec> PROPERTIES = List.of(
            PropertySpec.integer("seats", 2, 2, 4));

    private VersusMultiplayer() {
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
        return Set.of(ModuleFlag.MULTI_SEAT_ONLY, ModuleFlag.RUNTIME_OPPONENT);
    }

    /** "2-player versus". */
    @Override
    public Object[] effectArgs(PropertyValues props) {
        return new Object[] {props.getInt("seats")};
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

    @Override
    public boolean runtimeOpponent(PropertyValues props) {
        return true;
    }

    @Override
    public RuleOutcome resolve(ModuleContext ctx, RuleOutcome fromRule) {
        MatchState state = ctx.state();
        switch (fromRule) {
            case CONTINUE -> {
                if (state.seatCount() < 2) {
                    return RuleOutcome.CONTINUE;
                }
                int alive = state.aliveSeatMask();
                if (alive == 0) {
                    state.setWinners(0);
                    return RuleOutcome.DRAW;
                }
                if (Integer.bitCount(alive) == 1) {
                    state.setWinners(alive);
                    return RuleOutcome.WIN;
                }
                return RuleOutcome.CONTINUE;
            }
            case WIN -> {
                int winners = state.winnersMask();
                if (Integer.bitCount(winners) > 1) {
                    state.setWinners(bestScoring(state, winners));
                }
                return RuleOutcome.WIN;
            }
            case LOSE -> {
                state.setWinners(0);
                return RuleOutcome.DRAW;
            }
            default -> {
                return fromRule;
            }
        }
    }

    /** The seats of {@code mask} holding its highest score (ties keep every tied seat). */
    static int bestScoring(MatchState state, int mask) {
        int best = Integer.MIN_VALUE;
        for (int seat = 0; seat < state.seatCount(); seat++) {
            if ((mask & (1 << seat)) != 0 && state.score(seat) > best) {
                best = state.score(seat);
            }
        }
        int out = 0;
        for (int seat = 0; seat < state.seatCount(); seat++) {
            if ((mask & (1 << seat)) != 0 && state.score(seat) == best) {
                out |= 1 << seat;
            }
        }
        return out;
    }

    /** The Arcade Exchange genre word ({@code neroarcade.tag.versus}). */
    @Override
    public String genreTag() {
        return "versus";
    }
}
