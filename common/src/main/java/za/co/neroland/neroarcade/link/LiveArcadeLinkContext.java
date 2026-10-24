package za.co.neroland.neroarcade.link;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.link.LinkAlert;
import za.co.neroland.nerolandcore.link.LinkAlerts;
import za.co.neroland.nerolandcore.link.LinkEvent;
import za.co.neroland.nerolandcore.link.NeroLinkRegistry;

import za.co.neroland.neroarcade.board.LeaderboardStore;
import za.co.neroland.neroarcade.board.LeaderboardTable;
import za.co.neroland.neroarcade.cabinet.ArcadeCabinetBlockEntity;
import za.co.neroland.neroarcade.cabinet.BusRole;
import za.co.neroland.neroarcade.cabinet.MatchHost;
import za.co.neroland.neroarcade.cabinet.MatchListener;
import za.co.neroland.neroarcade.cabinet.Seat;
import za.co.neroland.neroarcade.cabinet.SeatRegistry;
import za.co.neroland.neroarcade.exchange.Draft;
import za.co.neroland.neroarcade.exchange.ExchangeRules;
import za.co.neroland.neroarcade.exchange.ExchangeStore;
import za.co.neroland.neroarcade.exchange.ExchangeTable;
import za.co.neroland.neroarcade.exchange.Listing;
import za.co.neroland.neroarcade.game.CartridgeIds;
import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.privacy.CreditOptIn;

/**
 * The server's {@link ArcadeLinkContext}: the Exchange and leaderboard stores, the credit opt-in, the seat
 * registry, the vanilla chat filter, Core's link bus and alerts. The running server is captured from the tick hook
 * and dropped when it stops; every call made without one answers "nothing".
 *
 * <p>This is the only class in the link package that touches a block position: {@link #cabinetOf} reads the
 * requester's own seat to build a coordinate-free {@link CabinetInfo}, and {@link #matchEnd} reads the host cabinet's
 * bus size for the label. Neither position leaves this class.
 */
final class LiveArcadeLinkContext implements ArcadeLinkContext {

    static final LiveArcadeLinkContext INSTANCE = new LiveArcadeLinkContext();

    private volatile @Nullable MinecraftServer server;

    private LiveArcadeLinkContext() {
    }

    void attach(@Nullable MinecraftServer value) {
        server = value;
    }

    @Override
    public LinkSettings settings() {
        return LinkSettings.live();
    }

    @Override
    public long nowMillis() {
        return System.currentTimeMillis();
    }

    @Override
    public int localHour(long epochMillis) {
        return local(epochMillis).getHour();
    }

    @Override
    public long localDay(long epochMillis) {
        return local(epochMillis).toLocalDate().toEpochDay();
    }

    private static ZonedDateTime local(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault());
    }

    // --- the Exchange -----------------------------------------------------------------------------

    private @Nullable ExchangeStore exchange() {
        MinecraftServer s = server;
        return s == null ? null : ExchangeStore.get(s);
    }

    @Override
    public List<Listing> listingsBy(UUID author) {
        ExchangeStore store = exchange();
        return store == null ? List.of() : store.listingsBy(author);
    }

    @Override
    public List<Draft> drafts(UUID owner) {
        ExchangeStore store = exchange();
        return store == null ? List.of() : store.drafts(owner);
    }

    @Override
    public Optional<Draft> draft(UUID owner, String cartridgeId) {
        ExchangeStore store = exchange();
        return store == null ? Optional.empty() : store.draft(owner, cartridgeId);
    }

    @Override
    public Optional<Listing> listing(int listingId) {
        ExchangeStore store = exchange();
        return store == null ? Optional.empty() : store.listing(listingId);
    }

    @Override
    public Optional<Listing> listingByCartridgeId(String cartridgeId) {
        ExchangeStore store = exchange();
        return store == null ? Optional.empty() : store.listingByCartridgeId(cartridgeId);
    }

    @Override
    public ExchangeTable.DraftStatus addDraft(UUID owner, GameDef def) {
        MinecraftServer s = server;
        if (s == null) {
            return ExchangeTable.DraftStatus.FULL;
        }
        return ExchangeStore.get(s).addDraft(s, owner, def);
    }

    @Override
    public boolean removeDraft(UUID owner, String cartridgeId) {
        MinecraftServer s = server;
        return s != null && ExchangeStore.get(s).removeDraft(s, owner, cartridgeId);
    }

    @Override
    public Map<Integer, Integer> drainDownloads() {
        ExchangeStore store = exchange();
        return store == null ? Map.of() : store.drainDownloads();
    }

    // --- boards and privacy -----------------------------------------------------------------------

    @Override
    public Optional<LeaderboardTable.Board> board(String cartridgeId) {
        MinecraftServer s = server;
        return s == null ? Optional.empty() : LeaderboardStore.get(s).board(cartridgeId);
    }

    @Override
    public List<LeaderboardTable.Board> boardsWithBest(UUID player) {
        MinecraftServer s = server;
        return s == null ? List.of() : LeaderboardStore.get(s).boardsWithBest(player);
    }

    @Override
    public boolean creditOptIn(UUID player) {
        MinecraftServer s = server;
        return s != null && CreditOptIn.isOn(s, player);
    }

    // --- cabinets ---------------------------------------------------------------------------------

    @Override
    public Optional<CabinetInfo> cabinetOf(UUID player) {
        MinecraftServer s = server;
        if (s == null) {
            return Optional.empty();
        }
        Optional<SeatRegistry.SeatKey> key = SeatRegistry.SERVER.seatOf(player);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        ServerLevel level = levelOf(s, key.get().dimension());
        BlockPos at = BlockPos.of(key.get().pos());
        if (level == null || !level.isLoaded(at)
                || !(level.getBlockEntity(at) instanceof ArcadeCabinetBlockEntity cabinet)) {
            return Optional.empty(); // a bench test seat is not a cabinet
        }
        ArcadeCabinetBlockEntity hostCabinet = cabinet.role() == BusRole.NODE ? cabinet.hostCabinet() : cabinet;
        boolean host = cabinet.role() == BusRole.SOLO || cabinet.role() == BusRole.HOST;
        int cabinets = hostCabinet != null && hostCabinet.role() == BusRole.HOST ? hostCabinet.busCabinets() : 1;
        Seat seat = cabinet.heldSeat();
        int seatIndex = seat != null && seat.playerUuid.equals(player) ? seat.seat : Math.max(0, cabinet.busSeatIndex());
        MatchHost match = cabinet.matchHost();
        ResolvedGame game = hostCabinet == null ? null : hostCabinet.game();
        String phase = match == null ? "IDLE" : match.phase().name();
        int seats = match != null ? match.seatCount() : game != null ? MatchHost.busSeatsFor(game, cabinets) : 1;
        String multiplayer = game == null ? "" : chipName(game.multiplayer().id().getPath());
        GameDef def = game == null ? null : game.def();
        return Optional.of(new CabinetInfo(level.dimension().identifier().toString(), cabinets, host, seatIndex, phase,
                multiplayer, seats, def == null ? null : def.id(), def == null ? null : def.lineageRoot(),
                def == null ? null : def.title()));
    }

    /** A finished match as the events need it: the dimension id and the host's bus size, never the position. */
    static ArcadeEvents.MatchEndInfo matchEnd(ServerLevel level, MatchListener.MatchEnd end) {
        int cabinets = 1;
        BlockPos hostPos = end.hostPos();
        if (hostPos != null && level.isLoaded(hostPos)
                && level.getBlockEntity(hostPos) instanceof ArcadeCabinetBlockEntity cabinet
                && cabinet.role() == BusRole.HOST) {
            cabinets = cabinet.busCabinets();
        }
        return new ArcadeEvents.MatchEndInfo(end.result().cartridgeId(), end.result().lineageRoot(),
                end.game().def().title(),
                level.dimension().identifier().toString(), cabinets, end.result().seats(),
                end.result().outcome().name(), end.outcome(), end.hotSeat());
    }

    private static @Nullable ServerLevel levelOf(MinecraftServer s, String dimension) {
        for (ServerLevel level : s.getAllLevels()) {
            if (level.dimension().identifier().toString().equals(dimension)) {
                return level;
            }
        }
        return null;
    }

    private static String chipName(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    // --- import helpers ---------------------------------------------------------------------------

    @Override
    public CompletableFuture<Boolean> titleAllowed(UUID player, String title) {
        MinecraftServer s = server;
        ServerPlayer online = s == null ? null : s.getPlayerList().getPlayer(player);
        if (online == null) {
            return CompletableFuture.completedFuture(false);
        }
        var filtered = online.getTextFilter().processStreamMessage(title);
        if (filtered.isDone() && !filtered.isCompletedExceptionally()) {
            return CompletableFuture.completedFuture(ExchangeRules.filter(filtered.join()).isEmpty());
        }
        // answered later, on the server thread
        return filtered.thenApplyAsync(f -> ExchangeRules.filter(f).isEmpty(), s);
    }

    @Override
    public String mintId() {
        return CartridgeIds.mint(RandomSource.create());
    }

    // --- output -----------------------------------------------------------------------------------

    @Override
    public void publish(LinkEvent event) {
        NeroLinkRegistry.eventBus().publish(event);
    }

    @Override
    public void raiseAlert(UUID player, LinkAlert alert) {
        MinecraftServer s = server;
        if (s != null) {
            LinkAlerts.get(s).raise(s, player, alert);
        }
    }
}
