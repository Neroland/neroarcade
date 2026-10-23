package za.co.neroland.neroarcade.compat.economy;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

import za.co.neroland.neroarcade.compat.Seams;
import za.co.neroland.neroarcade.config.NeroArcadeConfig;
import za.co.neroland.neroarcade.privacy.Tombstones;

/**
 * The coin-op façade (003 §10): the {@code economy.coinOp} switch (<b>off by default</b>) and the prices are applied
 * here, so a seam is reached only when a charge is really due. With the switch off, a price of 0 or NeroEconomy absent
 * ({@link CoinOpSeam#NO_OP}), everything is free. A seam that throws refuses the action (fail closed for a charge)
 * and is logged by exception class only.
 */
public final class CoinOp {

    public static final String REASON_SEAT = "arcade_coin_op";
    public static final String REASON_DOWNLOAD = "arcade_download";

    private CoinOp() {
    }

    /** Seat-take: true = seat paid for or free. */
    public static boolean chargeSeat(MinecraftServer server, UUID player, BlockPos cabinetPos) {
        return chargeSeat(NeroArcadeConfig.ECONOMY_COIN_OP.get(), NeroArcadeConfig.ECONOMY_COIN_OP_PRICE.get(),
                Seams.coinOp(), server, player, cabinetPos);
    }

    /** Download: true = the download may go ahead. */
    public static boolean onDownload(MinecraftServer server, UUID downloader, UUID author, int listingId) {
        return onDownload(NeroArcadeConfig.ECONOMY_COIN_OP.get(), NeroArcadeConfig.ECONOMY_DOWNLOAD_PRICE.get(),
                Seams.coinOp(), server, downloader, author, listingId);
    }

    /** Pure core of {@link #chargeSeat(MinecraftServer, UUID, BlockPos)} (tests drive it directly). */
    public static boolean chargeSeat(boolean enabled, int price, CoinOpSeam seam, MinecraftServer server, UUID player,
            BlockPos cabinetPos) {
        if (!enabled || price <= 0 || seam == null) {
            return true;
        }
        try {
            return seam.chargeSeat(server, player, cabinetPos, null);
        } catch (RuntimeException | LinkageError e) {
            Seams.failed(CoinOpSeam.MOD_ID, e);
            return false;
        }
    }

    /** Pure core of {@link #onDownload(MinecraftServer, UUID, UUID, int)}. */
    public static boolean onDownload(boolean enabled, int price, CoinOpSeam seam, MinecraftServer server,
            UUID downloader, UUID author, int listingId) {
        if (!enabled || price <= 0 || seam == null || author == null || Tombstones.isTombstone(author)
                || author.equals(downloader)) {
            return true;
        }
        try {
            return seam.onDownload(server, downloader, author, listingId, null);
        } catch (RuntimeException | LinkageError e) {
            Seams.failed(CoinOpSeam.MOD_ID, e);
            return false;
        }
    }
}
