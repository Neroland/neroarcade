package za.co.neroland.neroarcade.cabinet;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.hook.MultiplayerModule;

/**
 * Whether a game is hot-seat ("pass the controls", 003 §4.5): its MULTIPLAYER chip decides who is on turn by
 * overriding {@link MultiplayerModule#activeSeatMask}. Capability, not identity — no chip id is compared (the
 * runtime's {@code usesDefaultOpponent} precedent). The reflective lookup is made once per chip class and cached
 * ({@link ClassValue}), since the hub asks on every rediscovery (review 003b F12).
 */
public final class HotSeat {

    private static final ClassValue<Boolean> OVERRIDES_MASK = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("activeSeatMask", ModuleContext.class).getDeclaringClass()
                        != MultiplayerModule.class;
            } catch (NoSuchMethodException | SecurityException e) {
                return false;
            }
        }
    };

    private HotSeat() {
    }

    public static boolean is(ResolvedGame game) {
        if (game == null || game.seats() < 2) {
            return false;
        }
        return OVERRIDES_MASK.get(game.multiplayer().getClass());
    }
}
