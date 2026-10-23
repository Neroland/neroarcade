package za.co.neroland.neroarcade.cabinet;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.hook.MultiplayerModule;

/**
 * Whether a game is hot-seat ("pass the controls", 003 §4.5): its MULTIPLAYER chip decides who is on turn by
 * overriding {@link MultiplayerModule#activeSeatMask}. Capability, not identity — no chip id is compared (the
 * runtime's {@code usesDefaultOpponent} precedent: one reflective lookup per call, made once per host).
 */
public final class HotSeat {

    private HotSeat() {
    }

    public static boolean is(ResolvedGame game) {
        if (game == null || game.seats() < 2) {
            return false;
        }
        try {
            return game.multiplayer().getClass().getMethod("activeSeatMask", ModuleContext.class)
                    .getDeclaringClass() != MultiplayerModule.class;
        } catch (NoSuchMethodException | SecurityException e) {
            return false;
        }
    }
}
