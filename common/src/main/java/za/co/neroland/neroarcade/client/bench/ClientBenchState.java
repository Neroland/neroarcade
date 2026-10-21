package za.co.neroland.neroarcade.client.bench;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;

import za.co.neroland.neroarcade.game.CartridgeValidator;
import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.network.BenchStatePayload;

/**
 * The client's copy of the open Game Bench's draft ({@code BenchStatePayload}), resolved once per payload through
 * the same validator the server uses, so the screen's rows, sliders and live preview read one consistent game.
 * {@link #version()} increments on every payload (the screen re-reads on change). Client thread only. Pure.
 */
public final class ClientBenchState {

    private static @Nullable BenchStatePayload state;
    private static @Nullable ResolvedGame resolved;
    private static @Nullable ValidationError refused;
    private static long version;

    private ClientBenchState() {
    }

    public static void accept(BenchStatePayload payload) {
        state = payload;
        try {
            resolved = CartridgeValidator.validate(payload.draft());
            refused = null;
        } catch (ValidationError e) {
            resolved = null;
            refused = e;
        } catch (RuntimeException e) {
            resolved = null;
            refused = new ValidationError(ValidationError.Code.MALFORMED);
        }
        version++;
    }

    public static @Nullable BenchStatePayload state() {
        return state;
    }

    /** The draft as a runnable game, or null when this client's validator refuses it ({@link #refused()}). */
    public static @Nullable ResolvedGame resolved() {
        return resolved;
    }

    public static @Nullable ValidationError refused() {
        return refused;
    }

    public static long version() {
        return version;
    }

    /** The resolved draft if {@code pos} is the bench it came from. */
    public static @Nullable ResolvedGame gameAt(BlockPos pos) {
        BenchStatePayload current = state;
        return current != null && current.bench().equals(pos) ? resolved : null;
    }

    public static void clear() {
        state = null;
        resolved = null;
        refused = null;
        version++;
    }
}
