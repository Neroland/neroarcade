package za.co.neroland.neroarcade.config;

import za.co.neroland.nerolandcore.config.ConfigManager;
import za.co.neroland.nerolandcore.config.ConfigSchema;
import za.co.neroland.nerolandcore.config.ConfigValue;

import za.co.neroland.neroarcade.NeroArcadeCommon;

/**
 * NeroArcade config schema, built on Neroland Core's config framework (file
 * {@code config/neroarcade.properties}, hot-reloadable via {@code /neroland config reload}).
 * Registered once from {@link NeroArcadeCommon#init()}, before anything else — every other
 * subsystem reads it, including telemetry's opt-out flag.
 *
 * <p>Core has no sections, so keys use dotted prefixes. Groups so far:
 * <ul>
 *   <li><b>general</b> — {@code telemetryEnabled}, {@code screenRenderRange}, {@code seatRange}. (Cabinet power
 *       has no key in 0.1.0: there is no energy input yet, so the NO POWER state cannot be switched on.)</li>
 *   <li><b>runtime</b> — {@code runtime.*}: the match budgets. Read through {@code runtime.Budgets}, once
 *       per match start, so a running match never sees a value change.</li>
 *   <li><b>client</b> — {@code client.scanlines}, {@code client.screenRenderRange}: this computer's look,
 *       never server-authoritative.</li>
 *   <li><b>kill switches</b> — {@code multiplayer.enabled}, {@code leaderboards.enabled},
 *       {@code exchange.enabled}, {@code economy.*} (003 §13).</li>
 *   <li><b>privacy</b> — {@code privacy.*}: leaderboard retention and caps.</li>
 * </ul>
 *
 * <p>Every gameplay key is server-authoritative: Core syncs it to clients on join, so the client's bench
 * preview and prediction shadow run against the same budgets as the server.
 *
 * <p><b>POPIA/GDPR:</b> {@code telemetryEnabled} is deliberately <b>not</b> server-authoritative —
 * anonymous crash reporting is a per-client opt-out that a server must never force on or off.
 */
public final class NeroArcadeConfig {

    public static final ConfigSchema SCHEMA =
            ConfigSchema.create(NeroArcadeCommon.MOD_ID, "NeroArcade configuration.");

    // --- General ---------------------------------------------------------------

    private static final ConfigValue<Boolean> TELEMETRY = SCHEMA.bool(
            "telemetryEnabled", true, false,
            "send anonymous, NeroArcade-only crash reports (Sentry, EU servers) - ON by default. Sends the "
                    + "stack trace, mod/MC/loader/OS/Java versions and your other installed mods' ids "
                    + "and versions; never your IP, username, UUID, world data or chat; file paths are "
                    + "scrubbed of your account name. Set false to opt out of all of it (takes effect "
                    + "on restart). See PRIVACY.md");

    /** Client screen cull distance in blocks; the server's "spectator present" test uses the same number. */
    public static final ConfigValue<Integer> SCREEN_RENDER_RANGE = SCHEMA.intRange(
            "screenRenderRange", 32, 8, 64, true,
            "distance in blocks within which arcade screens are drawn and a nearby player keeps a cabinet awake");

    /** How far (blocks, eye to the cabinet's lower-block centre) a seated player may stand before the seat drops. */
    public static final ConfigValue<Double> SEAT_RANGE = SCHEMA.doubleRange(
            "seatRange", 3.0, 1.5, 8.0, true,
            "distance in blocks a seated player may step away from a cabinet before their seat is released");

    // --- Runtime budgets (runtime.*) --------------------------------------------

    public static final ConfigValue<Integer> MAX_ARENA_SIDE = SCHEMA.intRange(
            "runtime.maxArenaSide", 32, 8, 32, true,
            "largest arena side in cells; a cartridge with a bigger arena is refused");

    public static final ConfigValue<Integer> MAX_ENTITIES = SCHEMA.intRange(
            "runtime.maxEntities", 256, 32, 256, true,
            "most live entities in one match; spawns beyond it do nothing");

    public static final ConfigValue<Integer> MAX_INPUT_PACKETS_PER_SECOND = SCHEMA.intRange(
            "runtime.maxInputPacketsPerSecond", 30, 10, 60, true,
            "input packets accepted per seat per second; excess packets are dropped");

    public static final ConfigValue<Integer> SNAPSHOT_MAX_BYTES = SCHEMA.intRange(
            "runtime.snapshotMaxBytes", 8192, 6400, 16384, true,
            "largest screen update in bytes; a bigger update is replaced by a full frame (the floor is above "
                    + "the largest possible full frame)");

    public static final ConfigValue<Integer> SNAPSHOT_HZ = SCHEMA.intRange(
            "runtime.snapshotHz", 10, 5, 20, true,
            "screen updates sent per second to viewers of a cabinet");

    public static final ConfigValue<Integer> KEYFRAME_TICKS = SCHEMA.intRange(
            "runtime.keyframeTicks", 40, 20, 200, true,
            "ticks between full-frame screen updates");

    public static final ConfigValue<Integer> MAX_AWAKE_CABINETS = SCHEMA.intRange(
            "runtime.maxAwakeCabinets", 32, 4, 128, true,
            "cabinets that may run a match at once; further cabinets queue");

    public static final ConfigValue<Integer> SLEEP_AFTER_TICKS = SCHEMA.intRange(
            "runtime.sleepAfterTicks", 100, 40, 1200, true,
            "ticks a cabinet with no seated player and no nearby player waits before it sleeps");

    public static final ConfigValue<Integer> MAX_MATCH_SECONDS = SCHEMA.intRange(
            "runtime.maxMatchSeconds", 600, 30, 1800, true,
            "longest match in seconds; a match still running at this limit ends in a draw");

    // --- Client (client.*): this computer only, never synced, never sent ----------

    /** Darken every other texel row of arcade screens (a CRT look). Client-local, like {@code telemetryEnabled}. */
    public static final ConfigValue<Boolean> CLIENT_SCANLINES = SCHEMA.bool(
            "client.scanlines", false, false,
            "this computer only: draw arcade screens with CRT scanlines (every other row at 78 percent)");

    /** A shorter screen draw distance for this computer; 0 = the server's {@link #SCREEN_RENDER_RANGE}. */
    public static final ConfigValue<Integer> CLIENT_SCREEN_RENDER_RANGE = SCHEMA.intRange(
            "client.screenRenderRange", 0, 0, 64, false,
            "this computer only: draw arcade screens only within this many blocks (0 = the server's "
                    + "screenRenderRange; a larger number than the server's is capped to it)");

    // --- Kill switches and economy (003 §13) ------------------------------------

    /** Off: every Link Bus discovers nothing and every cabinet plays solo. */
    public static final ConfigValue<Boolean> MULTIPLAYER_ENABLED = SCHEMA.bool(
            "multiplayer.enabled", true, true,
            "if false, Link Buses join nothing and every cabinet plays on its own");

    /** Off: no match writes a leaderboard row or personal best; existing boards are kept and still shown. */
    public static final ConfigValue<Boolean> LEADERBOARDS_ENABLED = SCHEMA.bool(
            "leaderboards.enabled", true, true,
            "if false, finished matches write no leaderboard rows or personal bests");

    public static final ConfigValue<Boolean> EXCHANGE_ENABLED = SCHEMA.bool(
            "exchange.enabled", true, true,
            "if false, the Arcade Exchange kiosk refuses every action");

    public static final ConfigValue<Integer> EXCHANGE_PAGE_SIZE = SCHEMA.intRange(
            "exchange.pageSize", 8, 4, 16, true,
            "listings per page on the Arcade Exchange");

    public static final ConfigValue<Boolean> ECONOMY_COIN_OP = SCHEMA.bool(
            "economy.coinOp", false, true,
            "if true and NeroEconomy is installed, taking a cabinet seat costs economy.coinOpPrice");

    public static final ConfigValue<Integer> ECONOMY_COIN_OP_PRICE = SCHEMA.intRange(
            "economy.coinOpPrice", 10, 0, 10_000, true,
            "price of one cabinet seat when economy.coinOp is true");

    public static final ConfigValue<Integer> ECONOMY_DOWNLOAD_PRICE = SCHEMA.intRange(
            "economy.downloadPrice", 0, 0, 10_000, true,
            "price of one Arcade Exchange download, paid to the author (0 = free)");

    // --- Privacy (privacy.*) -------------------------------------------------------

    // !!! HARD CAP: 365 DAYS. DO NOT RAISE THE MAXIMUMS BELOW. !!!
    // A leaderboard row and a personal best are personal data (a player UUID next to a score). POPIA s14 and GDPR
    // Art. 5(1)(e) require that they are kept no longer than their purpose needs, and a year is the longest a score
    // stays interesting. The range's maximum is the hard cap: Core's config framework clamps any larger value in
    // neroarcade.properties back to it, so a server owner can shorten retention but never lengthen it past a year.
    // PRIVACY.md promises this cap; KillSwitchesAndRetentionTest pins it. Raising it is a privacy-policy change.

    /** Days a leaderboard row is kept (default 90). <b>Hard cap 365</b> (see the comment above). */
    public static final ConfigValue<Integer> LEADERBOARD_RETENTION_DAYS = SCHEMA.intRange(
            "privacy.leaderboardRetentionDays", 90, 1, 365, true,
            "days a leaderboard row is kept (a row is personal data; HARD CAP 365 - a year is the longest a score is "
                    + "interesting, and a larger value is clamped to 365)");

    /**
     * Days a personal best is kept after it was set (default 365, <b>hard cap 365</b>). The clock restarts only when
     * the player beats their best, so no play history is kept (Stage 6 decision on review 003b P3).
     */
    public static final ConfigValue<Integer> PERSONAL_BEST_RETENTION_DAYS = SCHEMA.intRange(
            "privacy.personalBestRetentionDays", 365, 1, 365, true,
            "days a personal best is kept after it was set (beating it restarts the clock; HARD CAP 365)");

    public static final ConfigValue<Integer> LEADERBOARD_MAX_ENTRIES = SCHEMA.intRange(
            "privacy.leaderboardMaxEntries", 10, 1, 25, true,
            "rows kept on each cartridge's leaderboard");

    public static final ConfigValue<Integer> EXCHANGE_MAX_LISTINGS = SCHEMA.intRange(
            "privacy.exchangeMaxListings", 200, 1, 1000, true,
            "most games listed on the Arcade Exchange at once (no eviction: an admin unpublishes)");

    public static final ConfigValue<Integer> LISTINGS_PER_AUTHOR = SCHEMA.intRange(
            "privacy.listingsPerAuthor", 16, 1, 200, true,
            "most games one player may list on the Arcade Exchange at once (operators are exempt)");

    public static final ConfigValue<Integer> DRAFTS_PER_PLAYER = SCHEMA.intRange(
            "privacy.draftsPerPlayer", 8, 1, 32, true,
            "imported game drafts kept per player");

    /** One {@code /neroarcade privacy export} per player per this many seconds. */
    public static final ConfigValue<Integer> EXPORT_COOLDOWN_SECONDS = SCHEMA.intRange(
            "privacy.exportCooldownSeconds", 60, 10, 3600, true,
            "seconds a player waits between two /neroarcade privacy export requests");

    /**
     * Declared for the disclosure only: NeroArcade keeps no per-player play history, and no code path reads this
     * as true.
     */
    public static final ConfigValue<Boolean> PLAY_HISTORY = SCHEMA.bool(
            "privacy.playHistory", false, true,
            "always false: NeroArcade keeps no record of which games a player played (declared for the disclosure)");

    // --- NeroLink companion app (link.*, 007 §9) -----------------------------------------

    /** Off: the link module is not registered (the app sees no NeroArcade module). Read at startup. */
    public static final ConfigValue<Boolean> LINK_ENABLED = SCHEMA.bool(
            "link.enabled", true, true,
            "if false, NeroArcade does not register with the NeroLink companion app (takes effect on restart)");

    public static final ConfigValue<Boolean> LINK_ACTION_EXPORT = SCHEMA.bool(
            "link.actions.export_cartridge", true, true,
            "if false, the companion app cannot export a cartridge as a .nac file");

    public static final ConfigValue<Boolean> LINK_ACTION_IMPORT = SCHEMA.bool(
            "link.actions.import_cartridge", true, true,
            "if false, the companion app cannot import a .nac file into the player's own Exchange drafts");

    public static final ConfigValue<Integer> LINK_IMPORT_PER_HOUR = SCHEMA.intRange(
            "link.importPerHour", 10, 1, 100, true,
            "cartridge imports one player may make from the companion app per hour (also at most 3 a minute)");

    public static final ConfigValue<Integer> LINK_RECORD_COOLDOWN_MINUTES = SCHEMA.intRange(
            "link.push.recordCooldownMinutes", 60, 5, 1440, true,
            "minutes between two 'record beaten' phone notifications for the same player and board");

    public static final ConfigValue<Integer> LINK_DIGEST_HOUR = SCHEMA.intRange(
            "link.digestHour", 18, 0, 23, true,
            "server-local hour of the daily 'your cartridge was downloaded' digest");

    public static final ConfigValue<Integer> LINK_LIBRARY_PAGE_SIZE = SCHEMA.intRange(
            "link.libraryPageSize", 50, 10, 50, true,
            "most cartridges in one page of the companion app's library");

    /**
     * Kill switch: off = nothing is published to NeroLink (events stay in the app's events section; the record alert
     * is still stored, and a NeroLink bridge may announce it generically). Stage 7 security review M1.
     */
    public static final ConfigValue<Boolean> PUSH_NOTIFICATIONS_ENABLED = SCHEMA.bool(
            "pushNotifications.enabled", true, true,
            "if false, NeroArcade publishes nothing to NeroLink (in-app events still update; a stored alert may still be announced generically)");

    private NeroArcadeConfig() {
    }

    /**
     * Whether anonymous NeroArcade-only crash reporting is on (default true, opt-out). Read once at
     * bootstrap by {@code NeroArcadeTelemetry.init()}; changes take effect on restart.
     */
    public static boolean isTelemetryEnabled() {
        return TELEMETRY.get();
    }

    /** Registers the schema with Core's ConfigManager. Called once from common init. */
    public static void init() {
        ConfigManager.register(SCHEMA);
    }
}
