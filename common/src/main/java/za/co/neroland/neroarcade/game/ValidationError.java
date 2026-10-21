package za.co.neroland.neroarcade.game;

import java.util.List;
import java.util.Locale;

/**
 * A cartridge refused by {@link CartridgeValidator}. The validator runs its checks in a fixed order and the
 * first failure wins, so the {@link Code} a player sees is deterministic for any input.
 *
 * <p><b>Invariant:</b> a validation error is always a refusal, never a repair. Nothing is truncated, clamped
 * or substituted with a default; the lang key {@code neroarcade.cartridge.error.<code>} with {@link #args}
 * is the whole message.
 */
public final class ValidationError extends Exception {

    private static final long serialVersionUID = 1L;

    /** The thirteen refusal codes, in the validator's check order. */
    public enum Code {
        TOO_LARGE, NEWER_SCHEMA, MALFORMED, BAD_TITLE, BAD_ID, BAD_LINEAGE, BAD_CREDIT, UNKNOWN_SLOT,
        STACK_OVERFLOW, UNKNOWN_MODULE, WRONG_SLOT, BAD_PROPERTY, OVER_BUDGET;

        /** {@code neroarcade.cartridge.error.too_large}. */
        public String langKey() {
            return "neroarcade.cartridge.error." + name().toLowerCase(Locale.ROOT);
        }
    }

    public final Code code;
    public final String langKey;
    public final List<String> args;

    public ValidationError(Code code, String... args) {
        super(code.name() + (args.length == 0 ? "" : " " + String.join(", ", args)));
        this.code = code;
        this.langKey = code.langKey();
        this.args = List.of(args);
    }
}
