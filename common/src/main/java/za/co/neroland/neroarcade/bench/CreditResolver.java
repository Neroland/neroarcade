package za.co.neroland.neroarcade.bench;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;

/**
 * Decides the credit string a bench save writes into a cartridge (003 §6.3). Credit is opt-in and off by default:
 * {@code board.Boards.init()} installs a resolver that returns the profile name (≤ 16 characters, never a UUID) only
 * when the author has turned credit on ({@code /neroarcade privacy credit on}) at save time. Until then — and in
 * tests — {@link #NONE}: every cartridge is "Anonymous builder".
 */
@FunctionalInterface
public interface CreditResolver {

    /** Profile names are at most 16 characters. */
    int MAX_CREDIT = 16;

    CreditResolver NONE = (server, author, profileName) -> Optional.empty();

    Optional<String> creditFor(MinecraftServer server, UUID author, String profileName);

    /** A profile name cut to {@link #MAX_CREDIT}; empty for a blank one. */
    static Optional<String> trim(String profileName) {
        if (profileName == null || profileName.isBlank()) {
            return Optional.empty();
        }
        String name = profileName.strip();
        return Optional.of(name.length() > MAX_CREDIT ? name.substring(0, MAX_CREDIT) : name);
    }

    static void install(CreditResolver resolver) {
        Holder.current = resolver == null ? NONE : resolver;
    }

    static CreditResolver current() {
        return Holder.current;
    }

    /** The installed resolver. */
    final class Holder {
        private static volatile CreditResolver current = NONE;

        private Holder() {
        }
    }
}
