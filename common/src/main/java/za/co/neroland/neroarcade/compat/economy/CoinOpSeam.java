package za.co.neroland.neroarcade.compat.economy;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;

/**
 * Coin-op play and paid downloads through NeroEconomy (003 §10). An interface in {@code compat} that imports nothing
 * from NeroEconomy; the one implementation that does ({@code bridge.NeroEconomyCoinOp}) is found through
 * {@code META-INF/services} only when NeroEconomy is loaded. Callers go through {@link CoinOp}, which applies the
 * {@code economy.*} config (off by default) before ever reaching a seam.
 *
 * <p>A request key never carries a player id; {@code null} means "no replay protection" (one click, one charge).
 * Privacy: a seam receives the UUIDs the caller already holds and never logs them.
 */
public interface CoinOpSeam {

    /** The NeroEconomy mod id. */
    String MOD_ID = "neroeconomy";

    /**
     * Charges {@code player} one coin-op seat.
     *
     * @return true when the seat is paid for (or free); false = refused, and the player has been told why
     */
    boolean chargeSeat(MinecraftServer server, UUID player, BlockPos cabinetPos, @Nullable String requestKey);

    /**
     * Pays the author's cut of a download ({@code economy.downloadPrice}, downloader → author). A tombstoned author or
     * the author downloading their own game pays nothing.
     *
     * @return true when the download may go ahead; false = refused, and the downloader has been told why
     */
    boolean onDownload(MinecraftServer server, UUID downloader, UUID author, int listingId,
            @Nullable String requestKey);

    /** No economy: play and downloads are free. Every call is safe. */
    CoinOpSeam NO_OP = new CoinOpSeam() {
        @Override
        public boolean chargeSeat(MinecraftServer server, UUID player, BlockPos cabinetPos, @Nullable String requestKey) {
            return true;
        }

        @Override
        public boolean onDownload(MinecraftServer server, UUID downloader, UUID author, int listingId,
                @Nullable String requestKey) {
            return true;
        }
    };
}
