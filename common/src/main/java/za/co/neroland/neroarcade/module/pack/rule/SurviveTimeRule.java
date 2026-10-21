package za.co.neroland.neroarcade.module.pack.rule;

import java.util.List;
import java.util.Set;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.module.ModuleDefaults;
import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.PropertySpec;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.hook.RuleModule;
import za.co.neroland.neroarcade.module.hook.RuleOutcome;
import za.co.neroland.neroarcade.runtime.Budgets;
import za.co.neroland.neroarcade.runtime.MatchState;

/**
 * {@code neroarcade:rule/survive_time} — the default rule: stay alive until the timer runs out. Every seat
 * starts with {@code lives}; when the timer reaches zero the seats still in the match win; when no seat is
 * left the match is lost. The timer counts simulated ticks (the runtime's housekeeping decrements it), so a
 * slower PACE also stretches the clock. Stateless; pure.
 */
public final class SurviveTimeRule implements RuleModule {

    public static final SurviveTimeRule INSTANCE = new SurviveTimeRule();

    private static final List<PropertySpec> PROPERTIES = List.of(
            PropertySpec.integer("seconds", 60, 10, 600),
            PropertySpec.integer("lives", 1, 1, 9));

    private SurviveTimeRule() {
    }

    @Override
    public Identifier id() {
        return ModuleDefaults.RULE;
    }

    @Override
    public List<PropertySpec> properties() {
        return PROPERTIES;
    }

    @Override
    public Set<ModuleFlag> flags() {
        return Set.of(ModuleFlag.SURVIVAL);
    }

    @Override
    public void onMatchStart(ModuleContext ctx) {
        MatchState state = ctx.state();
        state.setTimerTicks(ctx.props().getInt("seconds") * Budgets.TICKS_PER_SECOND);
        for (int seat = 0; seat < state.seatCount(); seat++) {
            state.setLives(seat, ctx.props().getInt("lives"));
        }
    }

    @Override
    public RuleOutcome evaluate(ModuleContext ctx) {
        MatchState state = ctx.state();
        if (state.aliveSeatMask() == 0) {
            state.setWinners(0);
            return RuleOutcome.LOSE;
        }
        if (state.timerTicks() == 0) {
            state.setWinners(state.aliveSeatMask());
            return RuleOutcome.WIN;
        }
        return RuleOutcome.CONTINUE;
    }
}
