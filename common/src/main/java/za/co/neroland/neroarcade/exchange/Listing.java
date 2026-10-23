package za.co.neroland.neroarcade.exchange;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import za.co.neroland.neroarcade.game.GameDef;

/**
 * One Arcade Exchange listing (003 §8.1). {@code listingId} is dense and never reused; {@code authorUuid} is the
 * cartridge's author (or {@code Tombstones.ERASED_AUTHOR}) and is never sent to a client; {@code credit} is present
 * only if the author had credit turned on when the listing was published (and is stripped when they turn it off);
 * {@code plays} and {@code hearts} are aggregate counts — no record of who played or who hearted is kept.
 *
 * @param publishedAt epoch seconds of the first publish (a re-publish of the same id keeps it, so re-publishing
 *                    cannot bump a game up the list)
 */
public record Listing(int listingId, GameDef gameDef, UUID authorUuid, Optional<String> credit,
        List<String> genreTags, int plays, int hearts, long publishedAt) {

    public Listing {
        credit = credit == null ? Optional.empty() : credit;
        genreTags = genreTags == null ? List.of() : List.copyOf(genreTags);
    }

    /** The cartridge id (the key a re-publish replaces on). */
    public String cartridgeId() {
        return gameDef.id();
    }

    public String lineageRoot() {
        return gameDef.lineageRoot();
    }

    public String title() {
        return gameDef.title();
    }

    Listing withPlays(int value) {
        return new Listing(listingId, gameDef, authorUuid, credit, genreTags, value, hearts, publishedAt);
    }

    Listing withHearts(int value) {
        return new Listing(listingId, gameDef, authorUuid, credit, genreTags, plays, value, publishedAt);
    }
}
