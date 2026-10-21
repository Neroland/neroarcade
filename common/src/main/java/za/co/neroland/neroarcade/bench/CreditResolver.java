package za.co.neroland.neroarcade.bench;

import java.util.Optional;
import java.util.UUID;

/**
 * Decides the credit string a bench save writes into a cartridge (003 §6.3). Credit is opt-in and off by default;
 * the opt-in store arrives in Stage 6, so Stage 2 installs {@link #NONE}: every cartridge is "Anonymous builder".
 * Stage 6 installs a resolver that returns the profile name (≤ 16 characters, never a UUID) only when the author
 * has opted in at save time.
 */
@FunctionalInterface
public interface CreditResolver {

    CreditResolver NONE = (author, profileName) -> Optional.empty();

    Optional<String> creditFor(UUID author, String profileName);

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
