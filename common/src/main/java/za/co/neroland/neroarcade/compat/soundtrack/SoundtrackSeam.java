package za.co.neroland.neroarcade.compat.soundtrack;

import java.util.Optional;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.module.hook.ThemeSpec;

/**
 * A theme's soundtrack from a NeroNotes disk (003 §10). NeroNotes publishes no API in 0.1.0, so this seam is a no-op:
 * a THEME chip may carry an optional soundtrack reference, which is stored and ignored, and themes play their own
 * {@code theme.<id>.*} sounds.
 */
public interface SoundtrackSeam {

    /** The NeroNotes mod id. */
    String MOD_ID = "neronotes";

    /** The sound to play for a theme instead of its own loop, or empty for "use the theme's own". */
    Optional<Identifier> diskFor(ThemeSpec theme);

    SoundtrackSeam NO_OP = theme -> Optional.empty();
}
