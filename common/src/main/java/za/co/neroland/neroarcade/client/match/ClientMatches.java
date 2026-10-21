package za.co.neroland.neroarcade.client.match;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.core.BlockPos;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroarcade.cabinet.BusPhase;
import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.network.HudLegendPayload;
import za.co.neroland.neroarcade.network.MatchPhasePayload;
import za.co.neroland.neroarcade.runtime.SeatInput;
import za.co.neroland.neroarcade.runtime.net.SnapshotPayload;

/**
 * Every host this client currently hears from, keyed by host position (cabinet, or bench during a test play).
 * Cleared on disconnect and on a dimension change; an entry nobody has sent anything for in {@link #FORGET_TICKS}
 * and that is not this player's seat is dropped, so the map stays as small as the set of nearby awake cabinets.
 * Client thread only. Pure (no client classes).
 */
public final class ClientMatches {

    /** Ticks without any packet after which a spectated host is forgotten (60 s). */
    public static final int FORGET_TICKS = 1200;

    private static final Map<BlockPos, ClientMatch> MATCHES = new HashMap<>();
    private static ClientMatch.Events events = ClientMatch.Events.NONE;
    private static Function<BlockPos, ResolvedGame> games = pos -> null;
    private static long now;

    private ClientMatches() {
    }

    /** Where phase/score sounds go (installed by the client). */
    public static void setEvents(ClientMatch.Events sink) {
        events = sink == null ? ClientMatch.Events.NONE : sink;
    }

    /** How a host position resolves to its game (the cabinet's synced cartridge or the bench draft). */
    public static void setGames(Function<BlockPos, ResolvedGame> source) {
        games = source == null ? pos -> null : source;
    }

    public static long now() {
        return now;
    }

    public static @Nullable ClientMatch get(BlockPos host) {
        return MATCHES.get(host);
    }

    public static Map<BlockPos, ClientMatch> all() {
        return java.util.Collections.unmodifiableMap(MATCHES);
    }

    private static ClientMatch entry(BlockPos host) {
        return MATCHES.computeIfAbsent(host.immutable(), ClientMatch::new);
    }

    public static void onSnapshot(SnapshotPayload payload) {
        ClientMatch match = entry(payload.cabinet());
        if (match.game() == null) {
            match.setGame(games.apply(match.host()));
        }
        match.onSnapshot(payload.matchId(), payload.body(), now, events);
    }

    public static void onPhase(MatchPhasePayload payload) {
        ClientMatch match = entry(payload.host());
        match.setGame(games.apply(match.host()));
        match.onPhase(payload, now, events);
    }

    public static void onLegend(HudLegendPayload payload) {
        entry(payload.host()).onLegend(payload, now);
    }

    /** The match this player is seated in (cabinet or bench test play), or null. */
    public static @Nullable ClientMatch seated() {
        for (ClientMatch match : MATCHES.values()) {
            if (match.seated() && match.busPhase() != BusPhase.IDLE) {
                return match;
            }
        }
        return null;
    }

    /** Re-reads a host's game (its cartridge or draft changed). */
    public static void refreshGame(BlockPos host) {
        ClientMatch match = MATCHES.get(host);
        if (match != null) {
            match.setGame(games.apply(host));
        }
    }

    /** One client tick: the seated match predicts with {@code myInput}; silent spectated hosts are forgotten. */
    public static void tick(SeatInput myInput) {
        now++;
        ClientMatch mine = seated();
        if (mine != null) {
            mine.tick(myInput);
        }
        if (now % 100 == 0) {
            Iterator<ClientMatch> it = MATCHES.values().iterator();
            while (it.hasNext()) {
                ClientMatch match = it.next();
                if (!match.seated() && now - match.lastSeen() > FORGET_TICKS) {
                    it.remove();
                }
            }
        }
    }

    /** Session end or dimension change: forget everything. */
    public static void clear() {
        MATCHES.clear();
    }
}
