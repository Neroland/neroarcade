package za.co.neroland.neroarcade.exchange;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.server.network.FilteredText;

import za.co.neroland.neroarcade.bench.TitleRules;
import za.co.neroland.neroarcade.privacy.Tombstones;

/**
 * Who may do what at the Arcade Exchange (003 §8.2), pure. Each check returns the refusal's lang key
 * ({@code neroarcade.exchange.deny.*}, one plain sentence each) or empty for "allowed".
 */
public final class ExchangeRules {

    public static final String DISABLED = "neroarcade.exchange.deny.disabled";
    public static final String NOT_YOURS = "neroarcade.exchange.deny.not_yours";
    public static final String NO_AUTHOR = "neroarcade.exchange.deny.no_author";
    public static final String UNPUBLISH_NOT_YOURS = "neroarcade.exchange.deny.unpublish_not_yours";
    public static final String OWN_HEART = "neroarcade.exchange.deny.own_heart";
    /** A listing of the same id already belongs to another author (only an op may replace it). */
    public static final String REPLACE_NOT_YOURS = "neroarcade.exchange.deny.replace_not_yours";

    private ExchangeRules() {
    }

    /**
     * Publish: only the cartridge's author, or an op. A cartridge with no author (a gallery game, an erased author)
     * can be listed only by an op.
     */
    public static Optional<String> publish(boolean enabled, Optional<UUID> author, UUID player, boolean op) {
        if (!enabled) {
            return Optional.of(DISABLED);
        }
        if (op) {
            return Optional.empty();
        }
        if (author.isEmpty() || Tombstones.isTombstone(author.get())) {
            return Optional.of(NO_AUTHOR);
        }
        return author.get().equals(player) ? Optional.empty() : Optional.of(NOT_YOURS);
    }

    /** Unpublish: the listing's author or an op. */
    public static Optional<String> unpublish(boolean enabled, UUID listingAuthor, UUID player, boolean op) {
        if (!enabled) {
            return Optional.of(DISABLED);
        }
        return op || listingAuthor.equals(player) ? Optional.empty() : Optional.of(UNPUBLISH_NOT_YOURS);
    }

    /** Heart: anyone but the author. */
    public static Optional<String> heart(boolean enabled, UUID listingAuthor, UUID player) {
        if (!enabled) {
            return Optional.of(DISABLED);
        }
        return listingAuthor.equals(player) ? Optional.of(OWN_HEART) : Optional.empty();
    }

    public static final String TITLE_INVALID = "neroarcade.exchange.deny.title_invalid";
    public static final String TITLE_BLOCKED = "neroarcade.exchange.deny.title_blocked";

    /**
     * The title rule before the chat filter: the stored title must already be clean ({@link TitleRules}: no control or
     * format characters, trimmed, 1..32) — a title that cleaning would change is refused, never silently rewritten.
     */
    public static Optional<String> title(String raw) {
        Optional<String> cleaned = TitleRules.clean(raw);
        return cleaned.isPresent() && cleaned.get().equals(raw) ? Optional.empty() : Optional.of(TITLE_INVALID);
    }

    /** The vanilla chat filter's verdict: any masked character (or an emptied title) refuses the whole title. */
    public static Optional<String> filter(FilteredText filtered) {
        return filtered.isFiltered() || filtered.filteredOrEmpty().isBlank() ? Optional.of(TITLE_BLOCKED)
                : Optional.empty();
    }

    /** The author stored on a listing: the game's author, or the tombstone for an op-listed authorless game. */
    public static UUID listingAuthor(Optional<UUID> gameAuthor) {
        return gameAuthor.orElse(Tombstones.ERASED_AUTHOR);
    }
}
