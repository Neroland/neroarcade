package za.co.neroland.neroarcade.module;

import java.util.List;
import java.util.Set;

import net.minecraft.resources.Identifier;

/**
 * One chip: a piece of game behaviour that fills one slot. Modules are Java and registered once at init;
 * their parameters are data ({@link PropertyValues}) carried by the cartridge.
 *
 * <p><b>Invariants.</b> A module is a stateless singleton: every behaviour hook is pure (it reads its
 * {@link za.co.neroland.neroarcade.module.hook.ModuleContext}, mutates only the match state through its API,
 * draws randomness only from the match RNG, never touches a static, a clock, a thread, a level, a player or
 * a float). A module implements exactly one of the ten hook interfaces in {@code module.hook}, matching its
 * {@link #type()}. {@link #id()}'s path starts with {@code type().key() + "/"}. {@link #properties()} is
 * immutable and ordered (the bench's slider order). {@link #flags()} describe capabilities so the runtime
 * and bench never compare ids; no chip may inspect another chip's id.
 */
public interface ArcadeModule {

    /** Namespace {@code neroarcade}, path {@code <slot key>/<name>}, e.g. {@code neroarcade:pawn/cursor}. */
    Identifier id();

    /** The slot this module fills; must match the hook interface it implements. */
    ModuleType type();

    /** Declared once, immutable, in bench order. */
    List<PropertySpec> properties();

    /** Capabilities, for bench warnings and runtime defaults; never ids. */
    Set<ModuleFlag> flags();

    /** {@code "module.neroarcade.pawn.cursor"} = "Cursor". */
    default String langKey() {
        return "module.neroarcade." + id().getPath().replace('/', '.');
    }

    /** One sentence of what the module does (the chip tooltip and the rack row's hover). */
    default String descLangKey() {
        return langKey() + ".desc";
    }

    /**
     * The rack row's short effect with live values, e.g. {@code "Survive %s s · lives %s"} → "Survive 60 s · lives 1".
     * <b>Contract (every chip):</b> the key exists in the shipped lang and renders in at most 22 characters with the
     * module's default values ({@code ShippedModulesConventionTest}); the long sentence stays in
     * {@link #descLangKey()}.
     */
    default String effectLangKey() {
        return langKey() + ".effect";
    }

    /**
     * The arguments of {@link #effectLangKey()}, from the resolved values. Pure (no state, no clock, no level).
     * Each argument is an {@link Integer}, a plain {@link String}, or a translatable
     * {@link net.minecraft.network.chat.Component} for a word that is itself a lang key (an ENUM constant's label,
     * say). Default: none.
     */
    default Object[] effectArgs(PropertyValues props) {
        return new Object[0];
    }
}
