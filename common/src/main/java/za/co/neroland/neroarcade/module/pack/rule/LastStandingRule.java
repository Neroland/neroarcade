package za.co.neroland.neroarcade.module.pack.rule;

import java.util.List;
import java.util.Set;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.hook.RuleModule;
import za.co.neroland.neroarcade.module.hook.RuleOutcome;
import za.co.neroland.neroarcade.runtime.Budgets;
import za.co.neroland.neroarcade.runtime.MatchState;

/**
 * {@code neroarcade:rule/last_standing} — the last pawn alive wins. Flags {@code MULTI_SEAT_ONLY}.
 *
 * <ul>
 *   <li>{@code lives} — lives per seat.</li>
 *   <li>{@code solo_seconds} — the clock a lone seat must survive (solo fallback, or the survivor of a drop).</li>
 * </ul>
 *
 * <p>With two or more undropped seats: exactly one seat alive wins; none alive is a DRAW (winners 0). With at most
 * one undropped seat the rule is a survival game: the timer is {@code solo_seconds × 20}; at zero the remaining
 * undropped alive seats win, none alive is a LOSE.
 *
 * <p>Odd combinations: started with one seat and a MULTIPLAYER chip offering a runtime opponent, seat 1 is that
 * opponent and the duel rules apply; without one the solo fallback applies from tick 0 (the runtime also calls
 * {@code onSeatDropped(ctx, 1)}, which re-arms the same clock). A seat dropped mid-match leaves the survivor on the
 * solo clock (dropped seats never count as winners). No clock in a duel. Stateless; pure.
 */
public final class LastStandingRule implements RuleModule {

    public static final LastStandingRule INSTANCE = new LastStandingRule();

    private static final Identifier ID = Identifier.fromNamespaceAndPath("neroarcade", "rule/last_standing");

    private static final List<PropertySpec> PROPERTIES = List.of(
            PropertySpec.integer("lives", 1, 1, 9),
            PropertySpec.integer("solo_seconds", 60, 10, 600));

    private static final Set<ModuleFlag> FLAGS = Set.of(ModuleFlag.MULTI_SEAT_ONLY);

    private LastStandingRule() {
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

    /** "Last alive · lives 1". */
    @Override
    public Object[] effectArgs(PropertyValues props) {
        return new Object[] {props.getInt("lives")};
    }

    /** "Last alive · lives 1". */
    @Override
    public int[] hudArgs(PropertyValues props) {
        return new int[] {props.getInt("lives")};
    }

    @Override
    public void onMatchStart(ModuleContext ctx) {
        MatchState state = ctx.state();
        int lives = ctx.props().getInt("lives");
        for (int seat = 0; seat < state.seatCount(); seat++) {
            state.setLives(seat, lives);
        }
        state.setTimerTicks(state.seatCount() == 1 ? soloTicks(ctx) : 0);
    }

    @Override
    public RuleOutcome evaluate(ModuleContext ctx) {
        MatchState state = ctx.state();
        int dropped = state.droppedSeatMask();
        int undropped = state.seatCount() - Integer.bitCount(dropped);
        if (undropped <= 1) {
            int alive = state.aliveSeatMask() & ~dropped;
            if (alive == 0) {
                state.setWinners(0);
                return RuleOutcome.LOSE;
            }
            if (state.timerTicks() == 0) {
                state.setWinners(alive);
                return RuleOutcome.WIN;
            }
            return RuleOutcome.CONTINUE;
        }
        int alive = state.aliveSeatMask();
        if (Integer.bitCount(alive) == 1) {
            state.setWinners(alive);
            return RuleOutcome.WIN;
        }
        if (alive == 0) {
            state.setWinners(0);
            return RuleOutcome.DRAW;
        }
        return RuleOutcome.CONTINUE;
    }

    @Override
    public void onSeatDropped(ModuleContext ctx, int seat) {
        MatchState state = ctx.state();
        int undropped = state.seatCount() - Integer.bitCount(state.droppedSeatMask());
        if (undropped == 1) {
            state.setTimerTicks(soloTicks(ctx));
        }
    }

    private static int soloTicks(ModuleContext ctx) {
        return ctx.props().getInt("solo_seconds") * Budgets.TICKS_PER_SECOND;
    }
}
