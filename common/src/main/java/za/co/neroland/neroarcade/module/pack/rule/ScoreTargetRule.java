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
import za.co.neroland.neroarcade.runtime.MatchState;

/**
 * {@code neroarcade:rule/score_target} — first to the target score wins. Flags {@code NEEDS_SCORE}.
 *
 * <ul>
 *   <li>{@code target} — the score a seat must reach (inclusive); every seat at or above it wins together.</li>
 *   <li>{@code lives} — lives per seat; when no seat is left the match is lost.</li>
 * </ul>
 *
 * <p>There is no clock (timer 0). Odd combinations: with no score source (no {@code SCORES} chip) the match can only
 * end by lives or by the hard stop ({@link #maxSeconds}, a DRAW); the bench warns
 * {@code SCORE_RULE_WITHOUT_SCORE_SOURCE}. With {@code turns}, the first seat to the target wins mid-rotation (A10).
 * Stateless; pure.
 */
public final class ScoreTargetRule implements RuleModule {

    public static final ScoreTargetRule INSTANCE = new ScoreTargetRule();

    private static final Identifier ID = Identifier.fromNamespaceAndPath("neroarcade", "rule/score_target");

    private static final List<PropertySpec> PROPERTIES = List.of(
            PropertySpec.integer("target", 100, 1, 9999),
            PropertySpec.integer("lives", 3, 1, 9));

    private static final Set<ModuleFlag> FLAGS = Set.of(ModuleFlag.NEEDS_SCORE);

    private ScoreTargetRule() {
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

    /** "Score 100 · lives 3". */
    @Override
    public Object[] effectArgs(PropertyValues props) {
        return new Object[] {props.getInt("target"), props.getInt("lives")};
    }

    /** "Reach 100 pts · lives 3". */
    @Override
    public int[] hudArgs(PropertyValues props) {
        return new int[] {props.getInt("target"), props.getInt("lives")};
    }

    @Override
    public void onMatchStart(ModuleContext ctx) {
        MatchState state = ctx.state();
        int lives = ctx.props().getInt("lives");
        for (int seat = 0; seat < state.seatCount(); seat++) {
            state.setLives(seat, lives);
        }
        state.setTimerTicks(0);
    }

    @Override
    public RuleOutcome evaluate(ModuleContext ctx) {
        MatchState state = ctx.state();
        int target = ctx.props().getInt("target");
        int mask = 0;
        for (int seat = 0; seat < state.seatCount(); seat++) {
            if (state.score(seat) >= target) {
                mask |= 1 << seat;
            }
        }
        if (mask != 0) {
            state.setWinners(mask);
            return RuleOutcome.WIN;
        }
        if (state.aliveSeatMask() == 0) {
            state.setWinners(0);
            return RuleOutcome.LOSE;
        }
        return RuleOutcome.CONTINUE;
    }
}
