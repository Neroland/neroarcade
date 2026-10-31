package za.co.neroland.neroarcade.privacy;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
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

/**
 * Credit opt-in (003 §6.3), a world {@link SavedData} on the overworld read through Core's
 * {@link SavedDataRecovery}: whether a player wants their name shown. <b>Off by default.</b> It governs, at the
 * moment of writing, the credit a Game Bench save puts in a cartridge and the name on a leaderboard row. Turning it
 * off strips every name the server holds for the player (registered {@link OptOutListener}s: the leaderboards now,
 * the exchange in a later stage); turning it on never names old rows or cartridges retroactively.
 */
public final class CreditOptIn extends SavedData {

    public static final String RECOVERY_NAME = "neroarcade:credit";

    /** Strips a player's stored names from one store when they turn credit off. */
    @FunctionalInterface
    public interface OptOutListener {
        void stripNames(MinecraftServer server, UUID player);
    }

    private static final List<OptOutListener> OPT_OUT = new CopyOnWriteArrayList<>();

    private static final Codec<CreditOptIn> CODEC =
            CompoundTag.CODEC.xmap(tag -> new CreditOptIn(CreditTable.load(tag)), store -> store.table.save());

    private static final Supplier<CreditOptIn> FACTORY = CreditOptIn::new;

    public static final SavedDataType<CreditOptIn> TYPE = SavedDataCompat.type(
            Identifier.fromNamespaceAndPath(NeroArcadeCommon.MOD_ID, "credit"), FACTORY, CODEC);

    /** Where the erasers find this store (tests swap in an instance). */
    public static final StoreAccess<CreditOptIn> ACCESS = new StoreAccess<>(CreditOptIn::get);

    private final CreditTable table;

    public CreditOptIn() {
        this(new CreditTable());
    }

    private CreditOptIn(CreditTable table) {
        this.table = table;
    }

    public static CreditOptIn get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, CreditOptIn::new, RECOVERY_NAME);
    }

    /** Whether {@code player} opted in on this server (false when there is no server). */
    public static boolean isOn(MinecraftServer server, UUID player) {
        return server != null && get(server).table.isOn(player);
    }

    /** Adds a store that must forget names on opt-out. Call from that store's init. */
    public static void onOptOut(OptOutListener listener) {
        if (listener != null && !OPT_OUT.contains(listener)) {
            OPT_OUT.add(listener);
        }
    }

    /** Sets the flag; turning it off strips every stored name. Backs the store up after a change. */
    public static void set(MinecraftServer server, UUID player, boolean on) {
        CreditOptIn store = get(server);
        if (store.table.set(player, on)) {
            store.setDirty();
        }
        if (!on) {
            for (OptOutListener listener : OPT_OUT) {
                listener.stripNames(server, player);
            }
        }
        store.backup(server);
    }

    public boolean erase(UUID player) {
        boolean changed = table.erase(player);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public boolean holds(UUID player) {
        return table.holds(player);
    }

    /** Whether {@code player} has credit on in this store instance (the export reads it). */
    public boolean isOnHere(UUID player) {
        return table.isOn(player);
    }

    /** Test seam: switch a flag on without a server (no opt-out listeners, no backup). */
    public void setForTest(UUID player, boolean on) {
        if (table.set(player, on)) {
            setDirty();
        }
    }

    /** What this store's saved form (and so its backup) holds. */
    public net.minecraft.nbt.CompoundTag saveForm() {
        return table.save();
    }

    public void backup(MinecraftServer server) {
        BackupHook.current().backup(server, "credit", table::save,
                () -> SavedDataRecovery.backupNow(server.overworld(), TYPE, this, RECOVERY_NAME));
    }
}
