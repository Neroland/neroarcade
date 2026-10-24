package za.co.neroland.neroarcade.link;

import za.co.neroland.neroarcade.config.NeroArcadeConfig;

/**
 * Every config value the link module reads, as one immutable value read per call (so one request sees one
 * configuration). Tests build their own.
 *
 * @param moduleEnabled         {@code link.enabled}
 * @param exportEnabled         {@code link.actions.export_cartridge}
 * @param importEnabled         {@code link.actions.import_cartridge}
 * @param exchangeEnabled       {@code exchange.enabled} (the feature switch both actions sit behind)
 * @param pushEnabled           {@code pushNotifications.enabled} (kill switch: nothing published on the bus)
 * @param importPerHour         {@code link.importPerHour}
 * @param recordCooldownMinutes {@code link.push.recordCooldownMinutes}
 * @param digestHour            {@code link.digestHour} (server-local)
 * @param libraryPageSize       {@code link.libraryPageSize}
 * @param draftsMax             {@code privacy.draftsPerPlayer}
 * @param retentionDays         {@code privacy.leaderboardRetentionDays}
 * @param leaderboardsEnabled   {@code leaderboards.enabled}
 */
record LinkSettings(boolean moduleEnabled, boolean exportEnabled, boolean importEnabled, boolean exchangeEnabled,
        boolean pushEnabled, int importPerHour, int recordCooldownMinutes, int digestHour, int libraryPageSize,
        int draftsMax, int retentionDays, boolean leaderboardsEnabled) {

    /** The shipped defaults (tests start here and change what they need). */
    static final LinkSettings DEFAULTS = new LinkSettings(true, true, true, true, true, 10, 60, 18, 50, 8, 90, true);

    /** The live configuration. */
    static LinkSettings live() {
        return new LinkSettings(NeroArcadeConfig.LINK_ENABLED.get(), NeroArcadeConfig.LINK_ACTION_EXPORT.get(),
                NeroArcadeConfig.LINK_ACTION_IMPORT.get(), NeroArcadeConfig.EXCHANGE_ENABLED.get(),
                NeroArcadeConfig.PUSH_NOTIFICATIONS_ENABLED.get(), NeroArcadeConfig.LINK_IMPORT_PER_HOUR.get(),
                NeroArcadeConfig.LINK_RECORD_COOLDOWN_MINUTES.get(), NeroArcadeConfig.LINK_DIGEST_HOUR.get(),
                NeroArcadeConfig.LINK_LIBRARY_PAGE_SIZE.get(), NeroArcadeConfig.DRAFTS_PER_PLAYER.get(),
                NeroArcadeConfig.LEADERBOARD_RETENTION_DAYS.get(), NeroArcadeConfig.LEADERBOARDS_ENABLED.get());
    }

    boolean exportAvailable() {
        return exportEnabled && exchangeEnabled;
    }

    boolean importAvailable() {
        return importEnabled && exchangeEnabled;
    }

    LinkSettings withPush(boolean value) {
        return new LinkSettings(moduleEnabled, exportEnabled, importEnabled, exchangeEnabled, value, importPerHour,
                recordCooldownMinutes, digestHour, libraryPageSize, draftsMax, retentionDays, leaderboardsEnabled);
    }

    LinkSettings withActions(boolean export, boolean imports, boolean exchange) {
        return new LinkSettings(moduleEnabled, export, imports, exchange, pushEnabled, importPerHour,
                recordCooldownMinutes, digestHour, libraryPageSize, draftsMax, retentionDays, leaderboardsEnabled);
    }

    LinkSettings withImportPerHour(int value) {
        return new LinkSettings(moduleEnabled, exportEnabled, importEnabled, exchangeEnabled, pushEnabled, value,
                recordCooldownMinutes, digestHour, libraryPageSize, draftsMax, retentionDays, leaderboardsEnabled);
    }
}
