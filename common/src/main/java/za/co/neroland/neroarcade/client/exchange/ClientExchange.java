package za.co.neroland.neroarcade.client.exchange;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroarcade.network.ExchangePagePayload;

/**
 * The client's mirror of the Arcade Exchange page it is looking at: the last {@link ExchangePagePayload} and a
 * counter the screen watches to rebuild. Display hints only. CLIENT-ONLY.
 */
public final class ClientExchange {

    private static @Nullable ExchangePagePayload page;
    private static int version;

    private ClientExchange() {
    }

    public static void accept(ExchangePagePayload payload) {
        page = payload;
        version++;
    }

    public static @Nullable ExchangePagePayload page() {
        return page;
    }

    public static int version() {
        return version;
    }

    public static void clear() {
        page = null;
        version++;
    }
}
