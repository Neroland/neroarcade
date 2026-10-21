package za.co.neroland.neroarcade.module;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import za.co.neroland.neroarcade.game.ResolvedGame;

/**
 * Derives the bench's grey warnings from a resolved game.
 *
 * <p><b>Invariants:</b> pure; reads only {@link ModuleFlag}s, property values and the arena's size — never a
 * module id; returns warnings in {@link BenchWarning} declaration order; never throws.
 */
public final class BenchWarnings {

    /** Arenas at or below this many cells count as small for {@link BenchWarning#CROWDED}. */
    private static final int SMALL_ARENA_CELLS = 16 * 16;

    private BenchWarnings() {
    }

    public static List<BenchWarning> evaluate(ResolvedGame game) {
        List<BenchWarning> out = new ArrayList<>();
        try {
            Set<ModuleFlag> rule = game.flags(ModuleType.RULE);
            Set<ModuleFlag> pawn = game.flags(ModuleType.PAWN);
            Set<ModuleFlag> control = game.flags(ModuleType.CONTROL);
            Set<ModuleFlag> pickups = game.flags(ModuleType.PICKUP);
            Set<ModuleFlag> hazards = game.flags(ModuleType.HAZARD);
            Set<ModuleFlag> multiplayer = game.flags(ModuleType.MULTIPLAYER);

            if (rule.contains(ModuleFlag.NEEDS_SCORE) && !pickups.contains(ModuleFlag.SCORES)) {
                out.add(BenchWarning.SCORE_RULE_WITHOUT_SCORE_SOURCE);
            }
            if (pickups.contains(ModuleFlag.IS_KEY) && !rule.contains(ModuleFlag.PLACES_EXIT)) {
                out.add(BenchWarning.KEY_WITHOUT_EXIT);
            }
            if (rule.contains(ModuleFlag.PLACES_EXIT) && pawn.contains(ModuleFlag.ONE_AXIS)) {
                out.add(BenchWarning.EXIT_ON_ONE_AXIS);
            }
            if (control.contains(ModuleFlag.NEEDS_AIM) && !pawn.contains(ModuleFlag.FIRES)) {
                out.add(BenchWarning.AIM_WITHOUT_FIRING_PAWN);
            }
            if (control.contains(ModuleFlag.BUTTONS_ONLY) && !pawn.contains(ModuleFlag.AUTO_MOVE)) {
                out.add(BenchWarning.ONE_BUTTON_FREE_PAWN);
            }
            if (control.contains(ModuleFlag.ONE_AXIS) && !control.contains(ModuleFlag.BUTTONS_ONLY)
                    && !pawn.contains(ModuleFlag.ONE_AXIS)) {
                out.add(BenchWarning.TWO_WAY_ON_FREE_PAWN);
            }
            if (rule.contains(ModuleFlag.NEEDS_CLEARABLE) && !pickups.contains(ModuleFlag.CLEARABLE)) {
                out.add(BenchWarning.CLEAR_BOARD_WITHOUT_PICKUPS);
            }
            boolean multiSeat = rule.contains(ModuleFlag.MULTI_SEAT_ONLY) || multiplayer.contains(ModuleFlag.MULTI_SEAT_ONLY);
            boolean opponent = multiplayer.contains(ModuleFlag.RUNTIME_OPPONENT)
                    && game.multiplayer().runtimeOpponent(game.multiplayerProps());
            if (multiSeat && !opponent) {
                out.add(BenchWarning.VERSUS_NEEDS_SEATS);
            }
            if (rule.contains(ModuleFlag.SURVIVAL) && !hazards.contains(ModuleFlag.LETHAL)) {
                out.add(BenchWarning.NO_THREAT);
            }
            if (game.hazards().size() >= ModuleType.HAZARD.maxStack && pawn.contains(ModuleFlag.AUTO_MOVE)
                    && game.arenaWidth() * game.arenaHeight() <= SMALL_ARENA_CELLS) {
                out.add(BenchWarning.CROWDED);
            }
            if (game.unknownProperties()) {
                out.add(BenchWarning.UNKNOWN_PROPERTY);
            }
        } catch (RuntimeException broken) {
            // A chip whose flags/props hooks throw must not take the bench down; warnings are advisory.
            return List.copyOf(out);
        }
        return List.copyOf(out);
    }
}
