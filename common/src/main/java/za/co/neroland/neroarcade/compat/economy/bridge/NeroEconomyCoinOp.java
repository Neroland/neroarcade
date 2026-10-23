package za.co.neroland.neroarcade.compat.economy.bridge;

import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroeconomy.api.EconomyCaller;
import za.co.neroland.neroeconomy.api.EconomyResult;
import za.co.neroland.neroeconomy.api.SiblingEconomy;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.compat.economy.CoinOp;
import za.co.neroland.neroarcade.compat.economy.CoinOpSeam;
import za.co.neroland.neroarcade.config.NeroArcadeConfig;

/**
 * The <b>only</b> class in NeroArcade that imports {@code za.co.neroland.neroeconomy}. NeroEconomy is compiled
 * {@code compileOnly} and optional; this class is instantiated through {@code META-INF/services} by
 * {@code compat.Seams} only after {@code isModLoaded("neroeconomy")}, during common init (NeroEconomy closes its
 * sibling claims when the first server starts). A build with {@code neroeconomy_compat=false} leaves this class and
 * its services line out of the jar.
 *
 * <p>Coin-op = {@code charge} (player → Reserve, reason {@code arcade_coin_op}); a download's author cut =
 * {@code transfer} (downloader → author, reason {@code arcade_download}). Privacy (POPIA/GDPR): passes the UUIDs the
 * caller holds straight to NeroEconomy and never logs them.
 */
public final class NeroEconomyCoinOp implements CoinOpSeam {

    private final EconomyCaller caller;

    public NeroEconomyCoinOp() {
        this.caller = SiblingEconomy.claim(NeroArcadeCommon.MOD_ID, Set.of(CoinOp.REASON_SEAT, CoinOp.REASON_DOWNLOAD));
    }

    @Override
    public boolean chargeSeat(MinecraftServer server, UUID player, BlockPos cabinetPos, @Nullable String requestKey) {
        long price = NeroArcadeConfig.ECONOMY_COIN_OP_PRICE.get();
        if (price <= 0) {
            return true;
        }
        return told(server, player, caller.charge(server, player, price, CoinOp.REASON_SEAT, requestKey));
    }

    @Override
    public boolean onDownload(MinecraftServer server, UUID downloader, UUID author, int listingId,
            @Nullable String requestKey) {
        long price = NeroArcadeConfig.ECONOMY_DOWNLOAD_PRICE.get();
        if (price <= 0) {
            return true;
        }
        return told(server, downloader,
                caller.transfer(server, downloader, author, price, CoinOp.REASON_DOWNLOAD, requestKey));
    }

    /** A refusal is shown to the paying player in NeroEconomy's own words. */
    private static boolean told(MinecraftServer server, UUID payer, EconomyResult result) {
        if (result != null && result.ok()) {
            return true;
        }
        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(payer);
        if (player != null && result != null) {
            player.sendSystemMessage(Component.translatable(result.translationKey()));
        }
        return false;
    }
}
