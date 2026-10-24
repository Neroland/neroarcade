package za.co.neroland.neroarcade.exchange;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.bench.BenchActions;
import za.co.neroland.neroarcade.bench.TitleRules;
import za.co.neroland.neroarcade.compat.economy.CoinOp;
import za.co.neroland.neroarcade.config.NeroArcadeConfig;
import za.co.neroland.neroarcade.data.NeroArcadeData;
import za.co.neroland.neroarcade.game.CartridgeValidator;
import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.item.CartridgeItem;
import za.co.neroland.neroarcade.item.ItemGiving;
import za.co.neroland.neroarcade.item.StoredCartridge;
import za.co.neroland.neroarcade.network.ExchangePagePayload;
import za.co.neroland.neroarcade.platform.Services;
import za.co.neroland.neroarcade.privacy.CreditOptIn;
import za.co.neroland.neroarcade.privacy.Tombstones;
import za.co.neroland.neroarcade.progression.ArcadeMilestones;
import za.co.neroland.neroarcade.registry.ModItems;
import za.co.neroland.neroarcade.registry.ModSounds;
import za.co.neroland.neroarcade.server.ServerHooks;

/**
 * The Arcade Exchange, server side (003 §8.2): every kiosk operation, the erasure and credit-opt-out registrations,
 * the hourly drafts sweep and the play counter. Every operation re-checks the kill switch ({@code exchange.enabled}:
 * off = every operation refused, the store untouched), the player's rights and the tray, and answers with one plain
 * sentence ({@code neroarcade.exchange.*}). Server thread only. Nothing here logs a player.
 */
public final class Exchange {

    /** One in-game hour between drafts sweeps. */
    public static final int SWEEP_INTERVAL_TICKS = 72_000;

    /** Heart dedup and rate limit: memory only, cleared on stop. */
    public static final ExchangeHearts HEARTS = new ExchangeHearts();

    /** Erasure, listings: published rows deleted, authored rows tombstoned. */
    public static final NeroArcadeData.StoreEraser EXCHANGE_ERASER = new NeroArcadeData.StoreEraser() {
        @Override
        public String name() {
            return "exchange";
        }

        @Override
        public void erase(MinecraftServer server, UUID player) {
            HEARTS.forget(player);
            ExchangeStore store = ExchangeStore.ACCESS.resolve(server);
            if (store != null) {
                store.eraseListings(player);
            }
        }

        @Override
        public void backupNow(MinecraftServer server) {
            ExchangeStore store = ExchangeStore.ACCESS.resolve(server);
            if (store != null) {
                store.backup(server);
            }
        }
    };

    /** Erasure, drafts: the player's bucket deleted, drafts of their games tombstoned. */
    public static final NeroArcadeData.StoreEraser DRAFTS_ERASER = new NeroArcadeData.StoreEraser() {
        @Override
        public String name() {
            return "drafts";
        }

        @Override
        public void erase(MinecraftServer server, UUID player) {
            ExchangeStore store = ExchangeStore.ACCESS.resolve(server);
            if (store != null) {
                store.eraseDrafts(player);
            }
        }

        @Override
        public void backupNow(MinecraftServer server) {
            ExchangeStore store = ExchangeStore.ACCESS.resolve(server);
            if (store != null) {
                store.backup(server);
            }
        }
    };

    private static MinecraftServer sweptFor;
    private static long ticksSinceSweep;
    private static boolean initialised;

    private Exchange() {
    }

    /** Common init step 4 (beside the leaderboards). Idempotent. */
    public static synchronized void init() {
        if (initialised) {
            return;
        }
        initialised = true;
        NeroArcadeData.addStore(EXCHANGE_ERASER);
        NeroArcadeData.addStore(DRAFTS_ERASER);
        CreditOptIn.onOptOut((server, player) -> ExchangeStore.get(server).stripCredit(server, player));
        ServerHooks.onTick(Exchange::onServerTick);
        ServerHooks.onPlayerLeave(player -> ExchangeActions.forget(player.getUUID()));
        ServerHooks.onStopping(server -> {
            HEARTS.clear();
            ExchangeActions.clear();
            sweptFor = null;
            ticksSinceSweep = 0;
        });
    }

    /** The stores this class registers for erasure (conformance tests). */
    public static List<NeroArcadeData.StoreEraser> erasers() {
        return List.of(EXCHANGE_ERASER, DRAFTS_ERASER);
    }

    public static boolean enabled() {
        return NeroArcadeConfig.EXCHANGE_ENABLED.get();
    }

    static boolean isOp(ServerPlayer player) {
        return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(player.createCommandSourceStack());
    }

    static long now() {
        return System.currentTimeMillis() / 1000L;
    }

    /** Drafts older than 30 days go, on the first tick after a world loads and then hourly. */
    static void onServerTick(MinecraftServer server) {
        if (sweptFor != server) {
            sweptFor = server;
            ticksSinceSweep = SWEEP_INTERVAL_TICKS;
        }
        if (++ticksSinceSweep < SWEEP_INTERVAL_TICKS) {
            return;
        }
        ticksSinceSweep = 0;
        int removed = ExchangeStore.get(server).purgeDrafts(server, now());
        if (removed > 0) {
            NeroArcadeCommon.LOGGER.debug("[NeroArcade] exchange sweep removed {} old draft(s)", removed);
        }
    }

    /** One play on a listed cartridge (from {@code MatchHost.finish}, first match of each insertion). */
    public static void countPlay(MinecraftServer server, String cartridgeId) {
        if (server == null || !enabled() || cartridgeId == null) {
            return;
        }
        ExchangeStore.get(server).countPlay(server, cartridgeId);
    }

    // --- operations -------------------------------------------------------------------------------------

    /**
     * PUBLISH: the written cartridge in the tray (or, with no cartridge there, the main hand). Validator, rights, title
     * rules, then the vanilla chat filter (asynchronous: the kiosk shows "checking…" until it answers), then the
     * store. The cartridge stays with the player.
     */
    static void publish(ServerPlayer player, ExchangeMenu menu) {
        if (!enabled()) {
            reply(player, menu, ExchangePages.Status.deny(ExchangeRules.DISABLED));
            return;
        }
        ItemStack source = menu.tray().getItem(0);
        if (!source.is(ModItems.CARTRIDGE.get())) {
            source = player.getMainHandItem();
        }
        StoredCartridge stored = CartridgeItem.storedOf(source);
        if (stored == null) {
            reply(player, menu, ExchangePages.Status.deny("neroarcade.exchange.deny.need_cartridge"));
            return;
        }
        GameDef def;
        ResolvedGame game;
        try {
            def = stored.decode();
            game = CartridgeValidator.validate(def);
        } catch (ValidationError unreadable) {
            reply(player, menu, ExchangePages.Status.deny("neroarcade.exchange.deny.unreadable"));
            return;
        }
        Optional<String> refused = ExchangeRules.publish(true, def.authorUuid(), player.getUUID(), isOp(player));
        if (refused.isPresent()) {
            reply(player, menu, ExchangePages.Status.deny(refused.get()));
            return;
        }
        if (ExchangeRules.title(def.title()).isPresent()) {
            reply(player, menu, ExchangePages.Status.deny(ExchangeRules.TITLE_INVALID,
                    Integer.toString(TitleRules.MAX_LENGTH)));
            return;
        }
        reply(player, menu, ExchangePages.Status.pending("neroarcade.exchange.status.checking"));
        MinecraftServer server = player.level().getServer();
        BenchActions.whenFiltered(player.getTextFilter().processStreamMessage(def.title()), server, filtered -> {
            if (!completes(!player.hasDisconnected(), player.containerMenu == menu)) {
                return; // the kiosk was closed (or the player left) before the filter answered: nothing is listed
            }
            Optional<String> blocked = ExchangeRules.filter(filtered);
            ExchangePages.Status status = blocked.isPresent() ? ExchangePages.Status.deny(blocked.get())
                    : completePublish(server, player, menu, def, game);
            reply(player, menu, status);
        });
    }

    private static ExchangePages.Status completePublish(MinecraftServer server, ServerPlayer player, ExchangeMenu menu,
            GameDef def, ResolvedGame game) {
        if (!enabled()) {
            return ExchangePages.Status.deny(ExchangeRules.DISABLED);
        }
        UUID author = ExchangeRules.listingAuthor(def.authorUuid());
        Optional<String> credit = !Tombstones.isTombstone(author) && CreditOptIn.isOn(server, author)
                ? def.authorCredit() : Optional.empty();
        ExchangeStore store = ExchangeStore.get(server);
        boolean op = isOp(player);
        int authorCap = op ? 0 : NeroArcadeConfig.LISTINGS_PER_AUTHOR.get();
        ExchangeTable.PublishResult result = store.publish(server, def, author, credit, GenreTags.of(game), now(),
                authorCap, op);
        switch (result.status()) {
            case NOT_OWNER -> {
                return ExchangePages.Status.deny(ExchangeRules.REPLACE_NOT_YOURS);
            }
            case FULL -> {
                return ExchangePages.Status.deny("neroarcade.exchange.deny.full",
                        Integer.toString(NeroArcadeConfig.EXCHANGE_MAX_LISTINGS.get()));
            }
            case AUTHOR_FULL -> {
                return ExchangePages.Status.deny("neroarcade.exchange.deny.author_full",
                        Integer.toString(authorCap));
            }
            case PUBLISHED -> ArcadeMilestones.cartridgePublished(store.publishedCount()); // gallery excluded (L1)
            default -> {
            }
        }
        // the gate opens for whoever pressed PUBLISH — an op listing someone else's game earns it, the absent author
        // is not handed it (review 003b F11)
        ArcadeMilestones.firstPublished(server, gateFor(player.getUUID()));
        playAt(menu, "exchange.publish");
        menu.view(ExchangePagePayload.TAB_LISTINGS, menu.page());
        return ExchangePages.Status.ok(result.status() == ExchangeTable.PublishStatus.REPLACED
                ? "neroarcade.exchange.ok.replaced" : "neroarcade.exchange.ok.published", def.title());
    }

    /** An asynchronous publish completes only while the publisher is online with this kiosk open. Pure. */
    static boolean completes(boolean online, boolean menuStillOpen) {
        return online && menuStillOpen;
    }

    /** Whose {@code first_published} gate a publish opens: the publisher's (review 003b F11). Pure. */
    static UUID gateFor(UUID publisher) {
        return publisher;
    }

    /** UNPUBLISH: the listing's author or an op. Copies already downloaded stay where they are. */
    static ExchangePages.Status unpublish(ServerPlayer player, int listingId) {
        MinecraftServer server = player.level().getServer();
        ExchangeStore store = ExchangeStore.get(server);
        Optional<Listing> listing = store.listing(listingId);
        if (!enabled()) {
            return ExchangePages.Status.deny(ExchangeRules.DISABLED);
        }
        if (listing.isEmpty()) {
            return ExchangePages.Status.deny("neroarcade.exchange.deny.gone");
        }
        Optional<String> refused = ExchangeRules.unpublish(true, listing.get().authorUuid(), player.getUUID(),
                isOp(player));
        if (refused.isPresent()) {
            return ExchangePages.Status.deny(refused.get());
        }
        store.unpublish(server, listingId);
        return ExchangePages.Status.ok("neroarcade.exchange.ok.unpublished", listing.get().title());
    }

    /**
     * DOWNLOAD: needs a Blank Cartridge in the tray; mints a copy with the same id, lineage, revision, author and
     * credit (copies pool onto one leaderboard). A play is counted when a match ends on it, not here.
     */
    static ExchangePages.Status download(ServerPlayer player, ExchangeMenu menu, int listingId) {
        if (!enabled()) {
            return ExchangePages.Status.deny(ExchangeRules.DISABLED);
        }
        MinecraftServer server = player.level().getServer();
        Optional<Listing> listing = ExchangeStore.get(server).listing(listingId);
        if (listing.isEmpty()) {
            return ExchangePages.Status.deny("neroarcade.exchange.deny.gone");
        }
        ItemStack tray = menu.tray().getItem(0);
        if (!tray.is(ModItems.BLANK_CARTRIDGE.get())) {
            return ExchangePages.Status.deny("neroarcade.exchange.deny.need_blank");
        }
        ItemStack copy;
        try {
            CartridgeValidator.validate(listing.get().gameDef());
            copy = CartridgeItem.create(listing.get().gameDef());
        } catch (ValidationError unreadable) {
            return ExchangePages.Status.deny("neroarcade.exchange.deny.unreadable");
        }
        if (!CoinOp.onDownload(server, player.getUUID(), listing.get().authorUuid(), listingId)) {
            return ExchangePages.Status.deny("neroarcade.exchange.deny.payment");
        }
        putInTray(player, menu, tray, copy);
        // an aggregate per-listing count for the author's NeroLink daily digest (007 §4); never who
        ExchangeStore.get(server).countDownload(listingId);
        playAt(menu, "cabinet.insert");
        return ExchangePages.Status.ok("neroarcade.exchange.ok.downloaded", listing.get().title());
    }

    /** HEART: anyone but the author, once per listing per session, 20 an hour. */
    static ExchangePages.Status heart(ServerPlayer player, int listingId) {
        if (!enabled()) {
            return ExchangePages.Status.deny(ExchangeRules.DISABLED);
        }
        ExchangeStore store = ExchangeStore.get(player.level().getServer());
        Optional<Listing> listing = store.listing(listingId);
        if (listing.isEmpty()) {
            return ExchangePages.Status.deny("neroarcade.exchange.deny.gone");
        }
        Optional<String> refused = ExchangeRules.heart(true, listing.get().authorUuid(), player.getUUID());
        if (refused.isPresent()) {
            return ExchangePages.Status.deny(refused.get());
        }
        return switch (HEARTS.tryHeart(player.getUUID(), listingId, System.currentTimeMillis())) {
            case ALREADY -> ExchangePages.Status.deny("neroarcade.exchange.deny.already_hearted");
            case RATE_LIMITED -> ExchangePages.Status.deny("neroarcade.exchange.deny.heart_rate",
                    Integer.toString(ExchangeHearts.PER_HOUR));
            case OK -> {
                store.heart(listingId);
                yield ExchangePages.Status.ok("neroarcade.exchange.ok.hearted", listing.get().title());
            }
        };
    }

    /** FETCH: the player's own draft onto the Blank Cartridge in the tray; the draft is then deleted. */
    static ExchangePages.Status fetchDraft(ServerPlayer player, ExchangeMenu menu, int index) {
        if (!enabled()) {
            return ExchangePages.Status.deny(ExchangeRules.DISABLED);
        }
        MinecraftServer server = player.level().getServer();
        ExchangeStore store = ExchangeStore.get(server);
        List<Draft> drafts = store.drafts(player.getUUID());
        if (index < 0 || index >= drafts.size()) {
            return ExchangePages.Status.deny("neroarcade.exchange.deny.gone");
        }
        ItemStack tray = menu.tray().getItem(0);
        if (!tray.is(ModItems.BLANK_CARTRIDGE.get())) {
            return ExchangePages.Status.deny("neroarcade.exchange.deny.need_blank");
        }
        Draft draft = drafts.get(index);
        ItemStack copy;
        try {
            CartridgeValidator.validate(draft.gameDef());
            copy = CartridgeItem.create(draft.gameDef());
        } catch (ValidationError unreadable) {
            return ExchangePages.Status.deny("neroarcade.exchange.deny.unreadable");
        }
        if (store.takeDraft(server, player.getUUID(), index).isEmpty()) {
            return ExchangePages.Status.deny("neroarcade.exchange.deny.gone");
        }
        putInTray(player, menu, tray, copy);
        playAt(menu, "cabinet.insert");
        return ExchangePages.Status.ok("neroarcade.exchange.ok.fetched", draft.gameDef().title());
    }

    private static void putInTray(ServerPlayer player, ExchangeMenu menu, ItemStack tray, ItemStack copy) {
        if (tray.getCount() <= 1) {
            menu.tray().setItem(0, copy);
        } else {
            tray.shrink(1);
            menu.tray().setChanged();
            ItemGiving.give(player, copy);
        }
    }

    private static void playAt(ExchangeMenu menu, String sound) {
        ArcadeExchangeBlockEntity kiosk = menu.kiosk();
        if (kiosk != null && kiosk.getLevel() instanceof ServerLevel level && ModSounds.get(sound) != null) {
            level.playSound(null, kiosk.getBlockPos(), ModSounds.get(sound).get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        }
    }

    // --- pages --------------------------------------------------------------------------------------

    /** Records the status and sends the viewer their current page (if the kiosk is still open). */
    static void reply(ServerPlayer player, ExchangeMenu menu, ExchangePages.Status status) {
        menu.status(status);
        if (status.kind() == ExchangePagePayload.STATUS_DENY && menu.kiosk() != null
                && menu.kiosk().getLevel() instanceof ServerLevel) {
            playAt(menu, "cabinet.deny");
        }
        sendPage(player, menu);
    }

    static void sendPage(ServerPlayer player, ExchangeMenu menu) {
        if (player.containerMenu != menu) {
            return;
        }
        Services.NETWORK.sendToPlayer(player, page(player, menu));
    }

    static ExchangePagePayload page(ServerPlayer player, ExchangeMenu menu) {
        MinecraftServer server = player.level().getServer();
        ExchangeStore store = ExchangeStore.get(server);
        int size = NeroArcadeConfig.EXCHANGE_PAGE_SIZE.get();
        int draftsMax = NeroArcadeConfig.DRAFTS_PER_PLAYER.get();
        boolean op = isOp(player);
        ExchangePagePayload payload;
        if (menu.tab() == ExchangePagePayload.TAB_DRAFTS) {
            payload = ExchangePages.drafts(store.drafts(player.getUUID()), menu.page(), size, now(), enabled(), op,
                    menu.status(), draftsMax);
        } else {
            payload = ExchangePages.listings(store.browse(), menu.page(), size, player.getUUID(), HEARTS::hearted,
                    enabled(), op, menu.status(), store.draftsUsed(player.getUUID()), draftsMax);
        }
        menu.view(payload.tab(), payload.page());
        return payload;
    }
}
