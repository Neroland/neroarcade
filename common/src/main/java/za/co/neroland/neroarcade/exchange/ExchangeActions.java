package za.co.neroland.neroarcade.exchange;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.network.ExchangeActionPayload;
import za.co.neroland.neroarcade.network.ExchangePagePayload;

/**
 * Server handler for {@code ExchangeActionPayload} (003 §8.2, §11): accepted only while the sender's open menu is an
 * {@link ExchangeMenu}, at most {@link #ACTIONS_PER_SECOND} per player (the excess ignored, never answered). Every
 * accepted action answers with one {@code ExchangePagePayload} carrying the result sentence. Nothing here throws or
 * logs a player.
 */
public final class ExchangeActions {

    /** Actions per player per second. */
    public static final int ACTIONS_PER_SECOND = 4;

    private static final Map<UUID, long[]> WINDOWS = new HashMap<>();

    private ExchangeActions() {
    }

    public static void handle(ExchangeActionPayload payload, ServerPlayer player) {
        if (!(player.containerMenu instanceof ExchangeMenu menu) || menu.kiosk() == null) {
            return;
        }
        if (!withinRate(player.getUUID(), System.currentTimeMillis())) {
            return;
        }
        try {
            apply(menu, payload, player);
        } catch (RuntimeException e) {
            NeroArcadeCommon.LOGGER.warn("[NeroArcade] exchange action {} failed ({})", payload.op(),
                    e.getClass().getName());
            Exchange.reply(player, menu, ExchangePages.Status.deny("neroarcade.exchange.deny.failed"));
        }
    }

    /** Opening the kiosk: the first page. */
    public static void opened(ServerPlayer player, ExchangeMenu menu) {
        menu.view(ExchangePagePayload.TAB_LISTINGS, 0);
        Exchange.reply(player, menu, Exchange.enabled() ? ExchangePages.Status.NONE
                : ExchangePages.Status.deny(ExchangeRules.DISABLED));
    }

    private static void apply(ExchangeMenu menu, ExchangeActionPayload payload, ServerPlayer player) {
        switch (payload.op()) {
            case PAGE -> {
                menu.view(ExchangePagePayload.TAB_LISTINGS, payload.page());
                Exchange.reply(player, menu, ExchangePages.Status.NONE);
            }
            case DRAFTS -> {
                menu.view(ExchangePagePayload.TAB_DRAFTS, payload.page());
                Exchange.reply(player, menu, ExchangePages.Status.NONE);
            }
            case SELECT -> Exchange.sendPage(player, menu);
            case PUBLISH -> Exchange.publish(player, menu);
            case UNPUBLISH -> Exchange.reply(player, menu, Exchange.unpublish(player, payload.listingId()));
            case DOWNLOAD -> Exchange.reply(player, menu, Exchange.download(player, menu, payload.listingId()));
            case HEART -> Exchange.reply(player, menu, Exchange.heart(player, payload.listingId()));
            case FETCH_DRAFT -> Exchange.reply(player, menu, Exchange.fetchDraft(player, menu, payload.listingId()));
        }
    }

    /** Fixed one-second window per player; true while under {@link #ACTIONS_PER_SECOND}. */
    public static boolean withinRate(UUID player, long nowMillis) {
        long[] window = WINDOWS.computeIfAbsent(player, p -> new long[] {nowMillis, 0});
        if (nowMillis - window[0] >= 1000L || nowMillis < window[0]) {
            window[0] = nowMillis;
            window[1] = 0;
        }
        if (window[1] >= ACTIONS_PER_SECOND) {
            return false;
        }
        window[1]++;
        return true;
    }

    public static void forget(UUID player) {
        WINDOWS.remove(player);
    }

    /** Erasure-conformance probe: is a rate window held for {@code player}? */
    public static boolean holdsWindow(UUID player) {
        return WINDOWS.containsKey(player);
    }

    public static void clear() {
        WINDOWS.clear();
    }
}
