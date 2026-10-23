package za.co.neroland.neroarcade.compat;

/**
 * Whether a mod is loaded. {@link Seams} asks this before it looks for a bridge, so tests can inject a fake presence
 * instead of a real loader ({@code Services.PLATFORM::isModLoaded} in game).
 */
@FunctionalInterface
public interface ModPresence {

    ModPresence NONE = modId -> false;

    boolean isLoaded(String modId);
}
