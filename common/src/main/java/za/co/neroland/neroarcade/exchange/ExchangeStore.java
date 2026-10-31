package za.co.neroland.neroarcade.exchange;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import za.co.neroland.nerolandcore.data.SavedDataCompat;
import za.co.neroland.nerolandcore.data.SavedDataRecovery;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.config.NeroArcadeConfig;
import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.privacy.BackupHook;

/**
 * The Arcade Exchange (003 §8.1): a world {@link SavedData} on the overworld, read through Core's
 * {@link SavedDataRecovery} under {@code "neroarcade:exchange"}, wrapping an {@link ExchangeTable}. Listings and
 * drafts live here; heart deduplication does not ({@link ExchangeHearts} is memory only).
 *
 * <h2>API for the NeroLink module (Stage 7, 007 §2.1, §3)</h2>
 * <ul>
 *   <li>{@code library}: {@link #listingsBy(UUID)} + {@link #drafts(UUID)} + {@link #draftsUsed(UUID)}.</li>
 *   <li>{@code export_cartridge}: {@link #draft(UUID, String)} then {@link #listingByCartridgeId(String)} (by id
 *       only; there is no by-root lookup).</li>
 *   <li>{@code import_cartridge}: the id is kept only if the requester holds a draft with it or it is their own
 *       listing ({@link #draft(UUID, String)}, {@link #listingByCartridgeId(String)}); otherwise it is re-minted, then
 *       {@link #addDraft(MinecraftServer, UUID, GameDef)} ({@code FULL} = "drafts full").</li>
 * </ul>
 * Every write backs the store up; every read is a copy.
 */
public final class ExchangeStore extends SavedData {

    public static final String RECOVERY_NAME = "neroarcade:exchange";

    /** Drafts are kept this long (003 §8.2). */
    public static final long DRAFT_RETENTION_SECONDS = 30L * 86_400L;

    private static final Codec<ExchangeStore> CODEC =
            CompoundTag.CODEC.xmap(tag -> new ExchangeStore(ExchangeTable.load(tag)), store -> store.table.save());

    private static final Supplier<ExchangeStore> FACTORY = ExchangeStore::new;

    public static final SavedDataType<ExchangeStore> TYPE = SavedDataCompat.type(
            Identifier.fromNamespaceAndPath(NeroArcadeCommon.MOD_ID, "exchange"), FACTORY, CODEC);

    /** Where the erasers find this store (tests swap in an instance). */
    public static final za.co.neroland.neroarcade.privacy.StoreAccess<ExchangeStore> ACCESS =
            new za.co.neroland.neroarcade.privacy.StoreAccess<>(ExchangeStore::get);

    private final ExchangeTable table;

    /** The fixed author of the creative gallery's showcase listings (never a player). */
    public static final UUID SHOWCASE_AUTHOR = UUID.fromString("00000000-0000-4000-8000-00006a11e7a0");

    public ExchangeStore() {
        this(new ExchangeTable());
    }

    /** A store around an existing table (tests and the erasure fixtures). */
    public static ExchangeStore of(ExchangeTable table) {
        return new ExchangeStore(table);
    }

    private ExchangeStore(ExchangeTable table) {
        this.table = table;
    }

    public static ExchangeStore get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, ExchangeStore::new, RECOVERY_NAME);
    }

    /** The rules (read freely; writes go through this store so they are saved and backed up). */
    ExchangeTable table() {
        return table;
    }

    public int version() {
        return table.version();
    }

    // --- reads (Stage 7 and the kiosk) ----------------------------------------------------------------

    public List<Listing> browse() {
        return table.browse();
    }

    public Optional<Listing> listing(int listingId) {
        return table.listing(listingId);
    }

    public Optional<Listing> listingByCartridgeId(String cartridgeId) {
        return table.listingByCartridgeId(cartridgeId);
    }

    public Optional<Listing> listingForRoot(String lineageRoot) {
        return table.listingForRoot(lineageRoot);
    }

    public List<Listing> listingsBy(UUID author) {
        return table.listingsBy(author);
    }

    public List<Draft> drafts(UUID owner) {
        return table.drafts(owner);
    }

    public Optional<Draft> draft(UUID owner, String cartridgeId) {
        return table.draft(owner, cartridgeId);
    }

    public int draftsUsed(UUID owner) {
        return table.drafts(owner).size();
    }

    public int size() {
        return table.size();
    }

    /**
     * Listings real players published: every listing except the gallery's showcase ({@link #SHOWCASE_AUTHOR}). The
     * {@code cartridge_published} channel counts these, so {@code /neroarcade gallery} cannot complete a server quest
     * (final review L1).
     */
    public int publishedCount() {
        return table.sizeExcluding(SHOWCASE_AUTHOR);
    }

    /** True when {@code cartridgeId} is listed and its author is not {@code requester} (import must re-mint). */
    public boolean isListedByOther(String cartridgeId, UUID requester) {
        return table.listingByCartridgeId(cartridgeId).filter(l -> !l.authorUuid().equals(requester)).isPresent();
    }

    // --- writes -------------------------------------------------------------------------------------

    /**
     * @param authorCap most listings per author; 0 = none (ops are exempt)
     * @param op        an operator may replace another author's listing of the same id; nobody else may
     */
    ExchangeTable.PublishResult publish(MinecraftServer server, GameDef def, UUID author, Optional<String> credit,
            List<String> tags, long now, int authorCap, boolean op) {
        ExchangeTable.PublishResult result = table.publish(def, author, credit, tags, now,
                NeroArcadeConfig.EXCHANGE_MAX_LISTINGS.get(), authorCap, op);
        if (result.status() == ExchangeTable.PublishStatus.PUBLISHED
                || result.status() == ExchangeTable.PublishStatus.REPLACED) {
            changed(server);
        }
        return result;
    }

    /**
     * The creative gallery's showcase (an operator's command in a test world): lists each cartridge under
     * {@code author} with {@code credit}, no per-author cap, the world cap still applies; a cartridge that does not
     * validate is skipped. {@code /neroarcade gallery clear} takes them down again with {@link #unpublishBy}.
     *
     * @return listings published or replaced
     */
    public int publishShowcase(MinecraftServer server, List<GameDef> defs, UUID author, String credit, long now) {
        int n = 0;
        for (GameDef def : defs) {
            List<String> tags;
            try {
                tags = GenreTags.of(za.co.neroland.neroarcade.game.CartridgeValidator.validate(def));
            } catch (za.co.neroland.neroarcade.game.ValidationError invalid) {
                continue;
            }
            ExchangeTable.PublishResult result = table.publish(def, author, Optional.of(credit), tags, now,
                    NeroArcadeConfig.EXCHANGE_MAX_LISTINGS.get(), 0, true);
            if (result.status() == ExchangeTable.PublishStatus.PUBLISHED
                    || result.status() == ExchangeTable.PublishStatus.REPLACED) {
                n++;
            }
        }
        if (n > 0) {
            changed(server);
        }
        return n;
    }

    /** Admin purge: every listing {@code author} published (review 003b F5). @return listings removed */
    public int unpublishBy(MinecraftServer server, UUID author) {
        int removed = table.unpublishBy(author);
        if (removed > 0) {
            changed(server);
        }
        return removed;
    }

    Optional<Listing> unpublish(MinecraftServer server, int listingId) {
        Optional<Listing> gone = table.unpublish(listingId);
        if (gone.isPresent()) {
            changed(server);
        }
        return gone;
    }

    /** One play on a listed cartridge (from a finished match). */
    public boolean countPlay(MinecraftServer server, String cartridgeId) {
        if (table.countPlay(cartridgeId)) {
            setDirty();
            return true;
        }
        return false;
    }

    /** One download of a listing, counted for the NeroLink daily digest (aggregate; no downloader is kept). */
    public void countDownload(int listingId) {
        if (table.countDownload(listingId)) {
            setDirty();
        }
    }

    /** The NeroLink daily digest: listingId → downloads since the last digest, then every counter reset. */
    public java.util.Map<Integer, Integer> drainDownloads() {
        java.util.Map<Integer, Integer> out = table.drainDownloads();
        if (!out.isEmpty()) {
            setDirty();
        }
        return out;
    }

    /** Removes {@code owner}'s draft of {@code cartridgeId} (an import whose title the chat filter refused late). */
    public boolean removeDraft(MinecraftServer server, UUID owner, String cartridgeId) {
        boolean removed = table.removeDraft(owner, cartridgeId);
        if (removed) {
            changed(server);
        }
        return removed;
    }

    int heart(int listingId) {
        int count = table.heart(listingId);
        if (count >= 0) {
            setDirty();
        }
        return count;
    }

    /**
     * Adds a draft to {@code owner}'s bucket (cap {@code privacy.draftsPerPlayer}). The caller has validated the game,
     * filtered its title, set {@code authorUuid = owner}, cleared {@code authorCredit} and re-minted a foreign id.
     */
    public ExchangeTable.DraftStatus addDraft(MinecraftServer server, UUID owner, GameDef def) {
        ExchangeTable.DraftStatus status = table.addDraft(owner, def, System.currentTimeMillis() / 1000L,
                NeroArcadeConfig.DRAFTS_PER_PLAYER.get());
        if (status != ExchangeTable.DraftStatus.FULL) {
            changed(server);
        }
        return status;
    }

    Optional<Draft> takeDraft(MinecraftServer server, UUID owner, int index) {
        Optional<Draft> taken = table.takeDraft(owner, index);
        if (taken.isPresent()) {
            changed(server);
        }
        return taken;
    }

    /** The hourly sweep: drafts older than 30 days. */
    public int purgeDrafts(MinecraftServer server, long nowEpoch) {
        int removed = table.purgeDrafts(nowEpoch, DRAFT_RETENTION_SECONDS);
        if (removed > 0) {
            changed(server);
        }
        return removed;
    }

    /** Credit opt-out. */
    public void stripCredit(MinecraftServer server, UUID player) {
        if (table.stripCredit(player)) {
            changed(server);
        }
    }

    /** Erasure, listings (the caller backs up). */
    public boolean eraseListings(UUID player) {
        boolean changed = table.eraseListings(player);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    /** Erasure, drafts (the caller backs up). */
    public boolean eraseDrafts(UUID player) {
        boolean changed = table.eraseDrafts(player);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public boolean holdsAuthor(UUID player) {
        return table.holdsAuthor(player);
    }

    public boolean holdsDrafts(UUID player) {
        return table.holdsDrafts(player);
    }

    private void changed(MinecraftServer server) {
        setDirty();
        backup(server);
    }

    /** What this store's saved form (and so its backup) holds. */
    public net.minecraft.nbt.CompoundTag saveForm() {
        return table.save();
    }

    public void backup(MinecraftServer server) {
        BackupHook.current().backup(server, "exchange", table::save,
                () -> SavedDataRecovery.backupNow(server.overworld(), TYPE, this, RECOVERY_NAME));
    }
}
