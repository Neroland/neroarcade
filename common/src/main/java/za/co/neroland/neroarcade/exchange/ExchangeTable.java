package za.co.neroland.neroarcade.exchange;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.game.CartridgeValidator;
import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.privacy.Tombstones;

/**
 * The Arcade Exchange's listings and drafts (003 §8), pure so the rules run in a plain JVM; {@link ExchangeStore}
 * wraps it as world {@code SavedData}. Who may do what (author or op, the kill switch, the chat filter) is decided by
 * the caller ({@link Exchange}); this class holds the data rules:
 *
 * <ul>
 *   <li><b>Cap, no eviction.</b> A new listing at the cap is refused ({@link PublishStatus#FULL}); nothing is ever
 *       removed to make room.</li>
 *   <li><b>Same id replaces.</b> Publishing a cartridge whose id is already listed replaces that row's game, credit
 *       and tags and keeps its {@code listingId}, {@code plays}, {@code hearts} and {@code publishedAt}.</li>
 *   <li><b>Ids are never reused.</b> {@code nextListingId} only grows.</li>
 *   <li><b>Drafts</b> are per owner, capped (a full bucket refuses), one per cartridge id (a second import of the
 *       same id replaces it), and purged after the retention.</li>
 *   <li><b>Privacy.</b> {@link #stripCredit} removes a player's name from every listing and draft they authored;
 *       {@link #eraseListings} deletes their listings and tombstones any other row naming them as author;
 *       {@link #eraseDrafts} deletes their drafts and tombstones drafts others hold of their games.</li>
 * </ul>
 */
public final class ExchangeTable {

    /** What a publish did. */
    public enum PublishStatus {
        PUBLISHED,
        REPLACED,
        FULL,
        /** The author already holds {@code privacy.listingsPerAuthor} listings (review 003b F5). */
        AUTHOR_FULL,
        /**
         * The id is already listed under another author and the publisher is not an operator: a cartridge forged with
         * someone else's id cannot take over their listing (review 003b, Stage 8 note; carried to Stage 6).
         */
        NOT_OWNER
    }

    public record PublishResult(PublishStatus status, Optional<Listing> listing) {
    }

    /** What adding a draft did. */
    public enum DraftStatus {
        ADDED,
        REPLACED,
        FULL
    }

    /** Browse order: hearts desc, then the newest first, then the lowest id (stable). */
    public static final Comparator<Listing> BROWSE_ORDER = Comparator.comparingInt(Listing::hearts).reversed()
            .thenComparing(Comparator.comparingLong(Listing::publishedAt).reversed())
            .thenComparingInt(Listing::listingId);

    private static final String KEY_NEXT = "next_id";
    private static final String KEY_LISTINGS = "listings";
    private static final String KEY_DRAFTS = "drafts";
    private static final String KEY_ID = "id";
    private static final String KEY_GAME = "game";
    private static final String KEY_AUTHOR = "author";
    private static final String KEY_CREDIT = "credit";
    private static final String KEY_TAGS = "tags";
    private static final String KEY_PLAYS = "plays";
    private static final String KEY_HEARTS = "hearts";
    private static final String KEY_AT = "at";
    private static final String KEY_OWNER = "owner";
    private static final String KEY_DOWNLOADS_PENDING = "downloads_pending";

    private final List<Listing> listings = new ArrayList<>();
    private final Map<UUID, List<Draft>> drafts = new LinkedHashMap<>();
    /**
     * listingId → downloads since the last NeroLink daily digest (007 §4). An aggregate count per listing: nothing
     * records who downloaded. Persisted with the listing row; reset by {@link #drainDownloads}.
     */
    private final Map<Integer, Integer> downloadsPending = new LinkedHashMap<>();
    private int nextListingId = 1;
    private int version;

    // --- listings ----------------------------------------------------------------------------------

    /**
     * Publishes (or re-publishes) a game. The caller has already checked rights, the kill switch, the validator and
     * the title filter.
     *
     * @param cap the listing cap ({@code privacy.exchangeMaxListings}); only a <i>new</i> listing is refused at it
     */
    public PublishResult publish(GameDef def, UUID authorUuid, Optional<String> credit, List<String> tags, long now,
            int cap) {
        return publish(def, authorUuid, credit, tags, now, cap, 0);
    }

    /**
     * @param authorCap most listings one author may hold ({@code privacy.listingsPerAuthor}); 0 = no per-author cap
     *                  (an op publishing). Like the world cap, only a <i>new</i> listing is refused at it.
     */
    public PublishResult publish(GameDef def, UUID authorUuid, Optional<String> credit, List<String> tags, long now,
            int cap, int authorCap) {
        return publish(def, authorUuid, credit, tags, now, cap, authorCap, false);
    }

    /**
     * @param op an operator may replace a listing of the same id whatever its author; anyone else only their own
     *           ({@code old.authorUuid == author || op})
     */
    public PublishResult publish(GameDef def, UUID authorUuid, Optional<String> credit, List<String> tags, long now,
            int cap, int authorCap, boolean op) {
        for (int i = 0; i < listings.size(); i++) {
            Listing old = listings.get(i);
            if (old.cartridgeId().equals(def.id())) {
                if (!op && !old.authorUuid().equals(authorUuid)) {
                    return new PublishResult(PublishStatus.NOT_OWNER, Optional.empty());
                }
                Listing replaced = new Listing(old.listingId(), def, authorUuid, credit, tags, old.plays(),
                        old.hearts(), old.publishedAt());
                listings.set(i, replaced);
                version++;
                return new PublishResult(PublishStatus.REPLACED, Optional.of(replaced));
            }
        }
        if (listings.size() >= Math.max(1, cap)) {
            return new PublishResult(PublishStatus.FULL, Optional.empty());
        }
        if (authorCap > 0 && listingCount(authorUuid) >= authorCap) {
            return new PublishResult(PublishStatus.AUTHOR_FULL, Optional.empty());
        }
        Listing fresh = new Listing(nextListingId++, def, authorUuid, credit, tags, 0, 0, now);
        listings.add(fresh);
        version++;
        return new PublishResult(PublishStatus.PUBLISHED, Optional.of(fresh));
    }

    /** Listings {@code author} holds (the per-author cap counts these). */
    public int listingCount(UUID author) {
        int n = 0;
        for (Listing l : listings) {
            if (l.authorUuid().equals(author)) {
                n++;
            }
        }
        return n;
    }

    /**
     * Admin purge ({@code /neroarcade admin unpublish <player>}, review 003b F5): every listing {@code author}
     * published goes. Downloads already made, drafts and other players' listings are untouched.
     *
     * @return listings removed
     */
    public int unpublishBy(UUID author) {
        int before = listings.size();
        listings.removeIf(l -> l.authorUuid().equals(author));
        int removed = before - listings.size();
        if (removed > 0) {
            version++;
        }
        return removed;
    }

    /** Removes a listing (the caller checked author-or-op). Downloads already made are untouched. */
    public Optional<Listing> unpublish(int listingId) {
        Iterator<Listing> it = listings.iterator();
        while (it.hasNext()) {
            Listing l = it.next();
            if (l.listingId() == listingId) {
                it.remove();
                version++;
                return Optional.of(l);
            }
        }
        return Optional.empty();
    }

    public Optional<Listing> listing(int listingId) {
        return listings.stream().filter(l -> l.listingId() == listingId).findFirst();
    }

    /** The listing of a cartridge id (copies share the id, so a copy counts towards it). */
    public Optional<Listing> listingByCartridgeId(String cartridgeId) {
        return listings.stream().filter(l -> l.cartridgeId().equals(cartridgeId)).findFirst();
    }

    /** The newest listing in a lineage (the "current published listing for the root", 007 §3.1). */
    public Optional<Listing> listingForRoot(String lineageRoot) {
        return listings.stream().filter(l -> l.lineageRoot().equals(lineageRoot))
                .max(Comparator.comparingLong(Listing::publishedAt).thenComparingInt(Listing::listingId));
    }

    /** Every listing {@code author} authored, newest first (007 §2.1 {@code library}). */
    public List<Listing> listingsBy(UUID author) {
        return listings.stream().filter(l -> l.authorUuid().equals(author))
                .sorted(Comparator.comparingLong(Listing::publishedAt).reversed()
                        .thenComparing(Comparator.comparingInt(Listing::listingId).reversed()))
                .toList();
    }

    /** Every listing in browse order. */
    public List<Listing> browse() {
        List<Listing> sorted = new ArrayList<>(listings);
        sorted.sort(BROWSE_ORDER);
        return sorted;
    }

    public int size() {
        return listings.size();
    }

    /** Listings not held by {@code author} (the published count without the gallery's showcase, final review L1). */
    public int sizeExcluding(UUID author) {
        return listings.size() - listingCount(author);
    }

    public int nextListingId() {
        return nextListingId;
    }

    /** Counts a play on the listing of {@code cartridgeId}, if it is listed. @return true if counted */
    public boolean countPlay(String cartridgeId) {
        for (int i = 0; i < listings.size(); i++) {
            Listing l = listings.get(i);
            if (l.cartridgeId().equals(cartridgeId)) {
                listings.set(i, l.withPlays(saturatingInc(l.plays())));
                version++;
                return true;
            }
        }
        return false;
    }

    /** Adds one heart (the caller deduplicated and rate-limited). @return the new count, or -1 if not listed */
    public int heart(int listingId) {
        for (int i = 0; i < listings.size(); i++) {
            Listing l = listings.get(i);
            if (l.listingId() == listingId) {
                Listing hearted = l.withHearts(saturatingInc(l.hearts()));
                listings.set(i, hearted);
                version++;
                return hearted.hearts();
            }
        }
        return -1;
    }

    /** One download of a listing (for the author's daily digest). @return true if the listing exists */
    public boolean countDownload(int listingId) {
        if (listing(listingId).isEmpty()) {
            return false;
        }
        downloadsPending.merge(listingId, 1, (a, b) -> saturatingInc(a));
        return true;
    }

    /** Downloads pending on a listing since the last digest. */
    public int downloadsPending(int listingId) {
        return downloadsPending.getOrDefault(listingId, 0);
    }

    /**
     * The digest: every still-listed listing with downloads pending, as listingId → count (in listing order), and
     * every counter reset (counters of listings that are gone are dropped too).
     */
    public Map<Integer, Integer> drainDownloads() {
        Map<Integer, Integer> out = new LinkedHashMap<>();
        for (Listing l : listings) {
            int n = downloadsPending.getOrDefault(l.listingId(), 0);
            if (n > 0) {
                out.put(l.listingId(), n);
            }
        }
        downloadsPending.clear();
        return out;
    }

    private static int saturatingInc(int value) {
        return value == Integer.MAX_VALUE ? value : value + 1;
    }

    // --- drafts ------------------------------------------------------------------------------------

    /**
     * Adds a draft for {@code owner} (Stage 7's {@code import_cartridge}). A draft with the same cartridge id
     * replaces the old one; a new one at the cap is refused.
     */
    public DraftStatus addDraft(UUID owner, GameDef def, long now, int cap) {
        List<Draft> bucket = drafts.computeIfAbsent(owner, k -> new ArrayList<>());
        for (int i = 0; i < bucket.size(); i++) {
            if (bucket.get(i).cartridgeId().equals(def.id())) {
                bucket.set(i, new Draft(owner, def, now));
                version++;
                return DraftStatus.REPLACED;
            }
        }
        if (bucket.size() >= Math.max(1, cap)) {
            if (bucket.isEmpty()) {
                drafts.remove(owner);
            }
            return DraftStatus.FULL;
        }
        bucket.add(new Draft(owner, def, now));
        version++;
        return DraftStatus.ADDED;
    }

    /** {@code owner}'s drafts, oldest first. */
    public List<Draft> drafts(UUID owner) {
        return List.copyOf(drafts.getOrDefault(owner, List.of()));
    }

    public Optional<Draft> draft(UUID owner, String cartridgeId) {
        return drafts.getOrDefault(owner, List.of()).stream().filter(d -> d.cartridgeId().equals(cartridgeId))
                .findFirst();
    }

    /** Removes and returns {@code owner}'s draft at {@code index} (a kiosk "fetch"). */
    public Optional<Draft> takeDraft(UUID owner, int index) {
        List<Draft> bucket = drafts.get(owner);
        if (bucket == null || index < 0 || index >= bucket.size()) {
            return Optional.empty();
        }
        Draft d = bucket.remove(index);
        if (bucket.isEmpty()) {
            drafts.remove(owner);
        }
        version++;
        return Optional.of(d);
    }

    /** Removes {@code owner}'s draft of {@code cartridgeId}. @return true if one went */
    public boolean removeDraft(UUID owner, String cartridgeId) {
        List<Draft> bucket = drafts.get(owner);
        if (bucket == null || !bucket.removeIf(d -> d.cartridgeId().equals(cartridgeId))) {
            return false;
        }
        if (bucket.isEmpty()) {
            drafts.remove(owner);
        }
        version++;
        return true;
    }

    /** Drops drafts created before {@code now - maxAgeSeconds}. @return drafts removed */
    public int purgeDrafts(long now, long maxAgeSeconds) {
        long cutoff = now - Math.max(0, maxAgeSeconds);
        int removed = 0;
        Iterator<Map.Entry<UUID, List<Draft>>> it = drafts.entrySet().iterator();
        while (it.hasNext()) {
            List<Draft> bucket = it.next().getValue();
            int before = bucket.size();
            bucket.removeIf(d -> d.createdAt() < cutoff);
            removed += before - bucket.size();
            if (bucket.isEmpty()) {
                it.remove();
            }
        }
        if (removed > 0) {
            version++;
        }
        return removed;
    }

    // --- privacy -----------------------------------------------------------------------------------

    /** Credit opt-out: every listing and draft {@code player} authored loses its credit. @return true if changed */
    public boolean stripCredit(UUID player) {
        boolean changed = false;
        for (int i = 0; i < listings.size(); i++) {
            Listing l = listings.get(i);
            if (authoredBy(l, player) && (l.credit().isPresent() || l.gameDef().authorCredit().isPresent())) {
                listings.set(i, new Listing(l.listingId(), withoutCredit(l.gameDef()), l.authorUuid(),
                        Optional.empty(), l.genreTags(), l.plays(), l.hearts(), l.publishedAt()));
                changed = true;
            }
        }
        for (List<Draft> bucket : drafts.values()) {
            for (int i = 0; i < bucket.size(); i++) {
                Draft d = bucket.get(i);
                if (d.gameDef().authorUuid().filter(player::equals).isPresent()
                        && d.gameDef().authorCredit().isPresent()) {
                    bucket.set(i, new Draft(d.owner(), withoutCredit(d.gameDef()), d.createdAt()));
                    changed = true;
                }
            }
        }
        if (changed) {
            version++;
        }
        return changed;
    }

    /**
     * Erasure, listings (003 §6.4): every listing {@code player} published is deleted; every remaining listing whose
     * game names them as author is tombstoned (author = {@link Tombstones#ERASED_AUTHOR}, no credit).
     */
    public boolean eraseListings(UUID player) {
        boolean changed = listings.removeIf(l -> l.authorUuid().equals(player));
        for (int i = 0; i < listings.size(); i++) {
            Listing l = listings.get(i);
            if (l.gameDef().authorUuid().filter(player::equals).isPresent()) {
                listings.set(i, new Listing(l.listingId(), tombstoned(l.gameDef()), l.authorUuid(), Optional.empty(),
                        l.genreTags(), l.plays(), l.hearts(), l.publishedAt()));
                changed = true;
            }
        }
        if (changed) {
            version++;
        }
        return changed;
    }

    /** Erasure, drafts: {@code player}'s bucket is deleted; drafts others hold of their games are tombstoned. */
    public boolean eraseDrafts(UUID player) {
        boolean changed = drafts.remove(player) != null;
        for (List<Draft> bucket : drafts.values()) {
            for (int i = 0; i < bucket.size(); i++) {
                Draft d = bucket.get(i);
                if (d.gameDef().authorUuid().filter(player::equals).isPresent()) {
                    bucket.set(i, new Draft(d.owner(), tombstoned(d.gameDef()), d.createdAt()));
                    changed = true;
                }
            }
        }
        if (changed) {
            version++;
        }
        return changed;
    }

    /** Conformance probe: does any listing still name {@code player} as publisher or author? */
    public boolean holdsAuthor(UUID player) {
        return listings.stream().anyMatch(l -> authoredBy(l, player));
    }

    /** Conformance probe: does {@code player} own a draft, or does any draft name them as author? */
    public boolean holdsDrafts(UUID player) {
        if (drafts.containsKey(player)) {
            return true;
        }
        return drafts.values().stream().flatMap(List::stream)
                .anyMatch(d -> d.gameDef().authorUuid().filter(player::equals).isPresent());
    }

    private static boolean authoredBy(Listing l, UUID player) {
        return l.authorUuid().equals(player) || l.gameDef().authorUuid().filter(player::equals).isPresent();
    }

    private static GameDef withoutCredit(GameDef d) {
        return new GameDef(d.schemaVersion(), d.id(), d.title(), d.authorUuid(), Optional.empty(), d.lineage(),
                d.seed(), d.slots(), d.createdAt(), d.revision());
    }

    private static GameDef tombstoned(GameDef d) {
        return new GameDef(d.schemaVersion(), d.id(), d.title(), Optional.of(Tombstones.ERASED_AUTHOR),
                Optional.empty(), d.lineage(), d.seed(), d.slots(), d.createdAt(), d.revision());
    }

    /** Changes on every write (a cheap "did anything change" for open kiosks). */
    public int version() {
        return version;
    }

    // --- persistence -------------------------------------------------------------------------------

    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        root.putInt(KEY_NEXT, nextListingId);
        ListTag rows = new ListTag();
        for (Listing l : listings) {
            Optional<CompoundTag> game = encode(l.gameDef());
            if (game.isEmpty()) {
                continue;
            }
            CompoundTag row = new CompoundTag();
            row.putInt(KEY_ID, l.listingId());
            row.put(KEY_GAME, game.get());
            row.putString(KEY_AUTHOR, l.authorUuid().toString());
            l.credit().ifPresent(c -> row.putString(KEY_CREDIT, c));
            ListTag tags = new ListTag();
            l.genreTags().forEach(t -> tags.add(StringTag.valueOf(t)));
            row.put(KEY_TAGS, tags);
            row.putInt(KEY_PLAYS, l.plays());
            row.putInt(KEY_HEARTS, l.hearts());
            row.putLong(KEY_AT, l.publishedAt());
            int pending = downloadsPending.getOrDefault(l.listingId(), 0);
            if (pending > 0) {
                row.putInt(KEY_DOWNLOADS_PENDING, pending);
            }
            rows.add(row);
        }
        root.put(KEY_LISTINGS, rows);
        ListTag draftRows = new ListTag();
        drafts.forEach((owner, bucket) -> {
            for (Draft d : bucket) {
                Optional<CompoundTag> game = encode(d.gameDef());
                if (game.isEmpty()) {
                    continue;
                }
                CompoundTag row = new CompoundTag();
                row.putString(KEY_OWNER, owner.toString());
                row.put(KEY_GAME, game.get());
                row.putLong(KEY_AT, d.createdAt());
                draftRows.add(row);
            }
        });
        root.put(KEY_DRAFTS, draftRows);
        return root;
    }

    private static Optional<CompoundTag> encode(GameDef def) {
        try {
            return Optional.of(CartridgeValidator.encode(def));
        } catch (ValidationError tooLarge) {
            return Optional.empty();
        }
    }

    /** Structural decode only: a game naming a chip this server no longer has still loads (fail closed at play). */
    private static GameDef decode(Tag tag) {
        return GameDef.CODEC.parse(NbtOps.INSTANCE, tag).result().orElseThrow();
    }

    public static ExchangeTable load(CompoundTag root) {
        ExchangeTable table = new ExchangeTable();
        int maxId = 0;
        ListTag rows = root.getList(KEY_LISTINGS).orElseGet(ListTag::new);
        for (int i = 0; i < rows.size(); i++) {
            try {
                CompoundTag row = rows.getCompound(i).orElseThrow();
                List<String> tags = new ArrayList<>();
                ListTag tagList = row.getList(KEY_TAGS).orElseGet(ListTag::new);
                for (int t = 0; t < tagList.size(); t++) {
                    tagList.getString(t).ifPresent(word -> GenreTags.add(tags, word));
                }
                Listing l = new Listing(row.getInt(KEY_ID).orElseThrow(), decode(row.get(KEY_GAME)),
                        UUID.fromString(row.getString(KEY_AUTHOR).orElseThrow()), row.getString(KEY_CREDIT),
                        tags, Math.max(0, row.getIntOr(KEY_PLAYS, 0)), Math.max(0, row.getIntOr(KEY_HEARTS, 0)),
                        row.getLongOr(KEY_AT, 0L));
                table.listings.add(l);
                int pending = Math.max(0, row.getIntOr(KEY_DOWNLOADS_PENDING, 0));
                if (pending > 0) {
                    table.downloadsPending.put(l.listingId(), pending);
                }
                maxId = Math.max(maxId, l.listingId());
            } catch (RuntimeException malformed) {
                NeroArcadeCommon.LOGGER.warn("[NeroArcade] skipping malformed exchange listing {} ({})", i,
                        malformed.getClass().getSimpleName());
            }
        }
        table.nextListingId = Math.max(maxId + 1, root.getIntOr(KEY_NEXT, 1));
        ListTag draftRows = root.getList(KEY_DRAFTS).orElseGet(ListTag::new);
        for (int i = 0; i < draftRows.size(); i++) {
            try {
                CompoundTag row = draftRows.getCompound(i).orElseThrow();
                UUID owner = UUID.fromString(row.getString(KEY_OWNER).orElseThrow());
                table.drafts.computeIfAbsent(owner, k -> new ArrayList<>())
                        .add(new Draft(owner, decode(row.get(KEY_GAME)), row.getLongOr(KEY_AT, 0L)));
            } catch (RuntimeException malformed) {
                NeroArcadeCommon.LOGGER.warn("[NeroArcade] skipping malformed exchange draft {} ({})", i,
                        malformed.getClass().getSimpleName());
            }
        }
        return table;
    }
}
