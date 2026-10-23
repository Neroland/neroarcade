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
 *   <li>{@code export_cartridge}: {@link #draft(UUID, String)} then {@link #listingForRoot(String)}.</li>
 *   <li>{@code import_cartridge}: {@link #isListedByOther(String, UUID)} (re-mint the id if true), then
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

    public static final SavedDataType<ExchangeStore> TYPE = new SavedDataType<ExchangeStore>(
            Identifier.fromNamespaceAndPath(NeroArcadeCommon.MOD_ID, "exchange"), FACTORY, CODEC, null);

    private final ExchangeTable table;

    public ExchangeStore() {
        this(new ExchangeTable());
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

    /** True when {@code cartridgeId} is listed and its author is not {@code requester} (import must re-mint). */
    public boolean isListedByOther(String cartridgeId, UUID requester) {
        return table.listingByCartridgeId(cartridgeId).filter(l -> !l.authorUuid().equals(requester)).isPresent();
    }

    // --- writes -------------------------------------------------------------------------------------

    ExchangeTable.PublishResult publish(MinecraftServer server, GameDef def, UUID author, Optional<String> credit,
            List<String> tags, long now) {
        ExchangeTable.PublishResult result = table.publish(def, author, credit, tags, now,
                NeroArcadeConfig.EXCHANGE_MAX_LISTINGS.get());
        if (result.status() != ExchangeTable.PublishStatus.FULL) {
            changed(server);
        }
        return result;
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

    public void backup(MinecraftServer server) {
        if (server == null) {
            return;
        }
        BackupHook.current().backup(server, "exchange",
                () -> SavedDataRecovery.backupNow(server.overworld(), TYPE, this, RECOVERY_NAME));
    }
}
