package za.co.neroland.neroarcade.privacy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonObject;

import net.minecraft.server.MinecraftServer;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.bench.LoadedBenches;
import za.co.neroland.neroarcade.board.LeaderboardStore;
import za.co.neroland.neroarcade.cabinet.SeatRegistry;
import za.co.neroland.neroarcade.config.NeroArcadeConfig;
import za.co.neroland.neroarcade.data.NeroArcadeData;
import za.co.neroland.neroarcade.exchange.Exchange;
import za.co.neroland.neroarcade.exchange.ExchangeStore;
import za.co.neroland.neroarcade.server.ServerHooks;

/**
 * Stage 6 wiring (POPIA/GDPR): the {@code "benches"} and {@code "exports"} erasers, the export command's work and
 * cooldown, and the export retention sweep (the first tick after a world loads, then hourly). Registered in common
 * init step 4, beside the other stores.
 */
public final class ArcadePrivacy {

    /** One in-game hour between export sweeps. */
    public static final int SWEEP_INTERVAL_TICKS = 72_000;

    /** One export per player per {@code privacy.exportCooldownSeconds}; memory only. */
    public static final ExportCooldown COOLDOWN = new ExportCooldown();

    /** Erasure, exports: the player's export file and their cooldown slot. Nothing persisted to back up. */
    public static final NeroArcadeData.StoreEraser EXPORTS_ERASER = new NeroArcadeData.StoreEraser() {
        @Override
        public String name() {
            return "exports";
        }

        @Override
        public void erase(MinecraftServer server, UUID player) {
            if (player == null) {
                return;
            }
            COOLDOWN.forget(player);
            PrivacyExport.delete(PrivacyExport.directory(server), player);
        }

        @Override
        public void backupNow(MinecraftServer server) {
            // a file deleted outright; there is no SavedData backup of it
        }
    };

    /** Opt-out: loaded benches drop the player's credit (compliance review F5). */
    static final CreditOptIn.OptOutListener BENCH_CREDIT_OPT_OUT = (server, player) -> LoadedBenches.stripCredit(player);

    private static MinecraftServer sweptFor;
    private static long ticksSinceSweep;
    private static boolean initialised;

    private ArcadePrivacy() {
    }

    /** Common init step 4 (after the Exchange). Idempotent. */
    public static synchronized void init() {
        if (initialised) {
            return;
        }
        initialised = true;
        NeroArcadeData.addStore(LoadedBenches.ERASER);
        NeroArcadeData.addStore(EXPORTS_ERASER);
        // credit opt-out reaches every loaded Game Bench too (compliance review F5)
        CreditOptIn.onOptOut(BENCH_CREDIT_OPT_OUT);
        ServerHooks.onTick(ArcadePrivacy::onServerTick);
        ServerHooks.onStopping(server -> {
            COOLDOWN.clear();
            LoadedBenches.clear();
            sweptFor = null;
            ticksSinceSweep = 0;
        });
    }

    /** The stores this class registers for erasure (conformance tests). */
    public static List<NeroArcadeData.StoreEraser> erasers() {
        return List.of(LoadedBenches.ERASER, EXPORTS_ERASER);
    }

    static void onServerTick(MinecraftServer server) {
        if (sweptFor != server) {
            sweptFor = server;
            ticksSinceSweep = SWEEP_INTERVAL_TICKS;
        }
        if (++ticksSinceSweep < SWEEP_INTERVAL_TICKS) {
            return;
        }
        ticksSinceSweep = 0;
        int removed = PrivacyExport.purgeExpired(PrivacyExport.directory(server), System.currentTimeMillis());
        if (removed > 0) {
            NeroArcadeCommon.LOGGER.debug("[NeroArcade] export retention removed {} file(s)", removed);
        }
    }

    /** What an export request answered. */
    public sealed interface ExportResult {
        /** Written: the path relative to the world folder. */
        record Written(String relativePath) implements ExportResult {
        }

        /** Refused by the cooldown: seconds left. */
        record Cooldown(long secondsLeft) implements ExportResult {
        }

        /** The file could not be written. */
        record Failed() implements ExportResult {
        }
    }

    /** The settings the export document reports. */
    public static PrivacyExport.Settings settings() {
        return new PrivacyExport.Settings(NeroArcadeConfig.LEADERBOARD_RETENTION_DAYS.get(),
                NeroArcadeConfig.PERSONAL_BEST_RETENTION_DAYS.get(),
                (int) (ExchangeStore.DRAFT_RETENTION_SECONDS / 86_400L));
    }

    /** {@code /neroarcade privacy export}: builds and writes {@code subject}'s export. Server thread. */
    public static ExportResult export(MinecraftServer server, UUID subject) {
        long now = System.currentTimeMillis();
        long left = COOLDOWN.tryAcquire(subject, now, NeroArcadeConfig.EXPORT_COOLDOWN_SECONDS.get() * 1000L);
        if (left > 0) {
            return new ExportResult.Cooldown(left);
        }
        try {
            JsonObject document = PrivacyExport.build(subject, LeaderboardStore.get(server), ExchangeStore.get(server),
                    CreditOptIn.get(server), settings(), new PrivacyExport.Live(SeatRegistry.SERVER.isSeated(subject),
                            Exchange.HEARTS.heartsGiven(subject)), now);
            Path directory = PrivacyExport.directory(server);
            return new ExportResult.Written(PrivacyExport.write(directory, subject, document));
        } catch (IOException | RuntimeException failure) {
            COOLDOWN.refund(subject);
            NeroArcadeCommon.LOGGER.warn("[NeroArcade] a privacy export could not be written ({}).",
                    failure.getClass().getName());
            return new ExportResult.Failed();
        }
    }
}
