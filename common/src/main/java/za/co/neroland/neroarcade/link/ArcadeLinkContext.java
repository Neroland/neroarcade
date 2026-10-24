package za.co.neroland.neroarcade.link;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.link.LinkAlert;
import za.co.neroland.nerolandcore.link.LinkEvent;

import za.co.neroland.neroarcade.board.LeaderboardTable;
import za.co.neroland.neroarcade.exchange.Draft;
import za.co.neroland.neroarcade.exchange.ExchangeTable;
import za.co.neroland.neroarcade.exchange.Listing;
import za.co.neroland.neroarcade.game.GameDef;

/**
 * Everything the link module reads or writes, behind one seam so every section, action and event runs in a plain
 * JVM test ({@link LiveArcadeLinkContext} is the server's). Every read is keyed by the requesting player or a
 * cartridge id; nothing here lists players.
 */
interface ArcadeLinkContext {

    LinkSettings settings();

    /** Epoch milliseconds. */
    long nowMillis();

    /** The server-local hour of {@code epochMillis} (0..23) and its day number, for the daily digest. */
    int localHour(long epochMillis);

    long localDay(long epochMillis);

    // --- the Exchange -----------------------------------------------------------------------------

    List<Listing> listingsBy(UUID author);

    List<Draft> drafts(UUID owner);

    Optional<Draft> draft(UUID owner, String cartridgeId);

    Optional<Listing> listing(int listingId);

    Optional<Listing> listingByCartridgeId(String cartridgeId);

    ExchangeTable.DraftStatus addDraft(UUID owner, GameDef def);

    boolean removeDraft(UUID owner, String cartridgeId);

    /** listingId → downloads since the last digest; resets the counters. */
    Map<Integer, Integer> drainDownloads();

    // --- boards and privacy -----------------------------------------------------------------------

    Optional<LeaderboardTable.Board> board(String cartridgeId);

    List<LeaderboardTable.Board> boardsWithBest(UUID player);

    boolean creditOptIn(UUID player);

    // --- cabinets ---------------------------------------------------------------------------------

    /** The cabinet {@code player} is seated at right now, as a coordinate-free view; empty when not seated. */
    Optional<CabinetInfo> cabinetOf(UUID player);

    // --- import helpers ---------------------------------------------------------------------------

    /** The vanilla chat filter's verdict on a title for {@code player}: true = allowed. */
    CompletableFuture<Boolean> titleAllowed(UUID player, String title);

    /** A fresh cartridge id ({@code CartridgeIds.mint}). */
    String mintId();

    // --- output -----------------------------------------------------------------------------------

    void publish(LinkEvent event);

    void raiseAlert(UUID player, LinkAlert alert);

    /**
     * One cabinet as the app may see it: no coordinate, no other player.
     *
     * @param dimensionId the dimension id ({@code minecraft:overworld}); only its display name leaves the server
     * @param cabinets    cabinets on the bus that can seat a player (1 = a solo cabinet)
     * @param host        true when the requester sits at the cabinet that runs the match
     * @param seat        the requester's bus seat index
     * @param phase       {@code BusPhase} name ({@code IDLE} when the cabinet sleeps)
     * @param multiplayer the multiplayer chip ({@code solo}, {@code versus}, ...), empty without a cartridge
     * @param seats       seats the match can hold
     */
    record CabinetInfo(String dimensionId, int cabinets, boolean host, int seat, String phase, String multiplayer,
            int seats, @Nullable String cartridgeId, @Nullable String lineageRoot, @Nullable String title) {
    }
}
