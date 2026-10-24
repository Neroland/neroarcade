package za.co.neroland.neroarcade.link;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import za.co.neroland.nerolandcore.link.LinkAlert;
import za.co.neroland.nerolandcore.link.LinkEvent;

import za.co.neroland.neroarcade.board.LeaderboardTable;
import za.co.neroland.neroarcade.exchange.Draft;
import za.co.neroland.neroarcade.exchange.ExchangeTable;
import za.co.neroland.neroarcade.exchange.Listing;
import za.co.neroland.neroarcade.game.GameDef;

/** A plain-JVM {@link ArcadeLinkContext}: a real {@link ExchangeTable}, boards by hand, recorded output. */
final class FakeLinkContext implements ArcadeLinkContext {

    LinkSettings settings = LinkSettings.DEFAULTS;
    long now = 1_792_000_000_000L;
    int hour = 12;
    long day = 1;
    final ExchangeTable exchange = new ExchangeTable();
    final Map<String, LeaderboardTable.Board> boards = new LinkedHashMap<>();
    final Set<UUID> optedIn = new LinkedHashSet<>();
    final Map<UUID, CabinetInfo> cabinets = new LinkedHashMap<>();
    final Set<String> blockedTitles = new LinkedHashSet<>();
    /** When set, the title filter answers later (the test completes it). */
    CompletableFuture<Boolean> pendingFilter;
    final List<LinkEvent> published = new ArrayList<>();
    /** player → alert id → alert (raise replaces, like Core). */
    final Map<UUID, Map<String, LinkAlert>> alerts = new LinkedHashMap<>();
    int raises;
    private int minted;

    @Override
    public LinkSettings settings() {
        return settings;
    }

    @Override
    public long nowMillis() {
        return now;
    }

    @Override
    public int localHour(long epochMillis) {
        return hour;
    }

    @Override
    public long localDay(long epochMillis) {
        return day;
    }

    @Override
    public List<Listing> listingsBy(UUID author) {
        return exchange.listingsBy(author);
    }

    @Override
    public List<Draft> drafts(UUID owner) {
        return exchange.drafts(owner);
    }

    @Override
    public Optional<Draft> draft(UUID owner, String cartridgeId) {
        return exchange.draft(owner, cartridgeId);
    }

    @Override
    public Optional<Listing> listing(int listingId) {
        return exchange.listing(listingId);
    }

    @Override
    public Optional<Listing> listingByCartridgeId(String cartridgeId) {
        return exchange.listingByCartridgeId(cartridgeId);
    }

    @Override
    public ExchangeTable.DraftStatus addDraft(UUID owner, GameDef def) {
        return exchange.addDraft(owner, def, now / 1000L, settings.draftsMax());
    }

    @Override
    public boolean removeDraft(UUID owner, String cartridgeId) {
        return exchange.removeDraft(owner, cartridgeId);
    }

    @Override
    public Map<Integer, Integer> drainDownloads() {
        return exchange.drainDownloads();
    }

    @Override
    public Optional<LeaderboardTable.Board> board(String cartridgeId) {
        return Optional.ofNullable(boards.get(cartridgeId));
    }

    @Override
    public List<LeaderboardTable.Board> boardsWithBest(UUID player) {
        return boards.values().stream().filter(b -> b.personalBest().containsKey(player)).toList();
    }

    @Override
    public boolean creditOptIn(UUID player) {
        return optedIn.contains(player);
    }

    @Override
    public Optional<CabinetInfo> cabinetOf(UUID player) {
        return Optional.ofNullable(cabinets.get(player));
    }

    @Override
    public CompletableFuture<Boolean> titleAllowed(UUID player, String title) {
        if (pendingFilter != null) {
            return pendingFilter;
        }
        return CompletableFuture.completedFuture(!blockedTitles.contains(title));
    }

    @Override
    public String mintId() {
        minted++;
        return LinkFixtures.id(9000 + minted);
    }

    @Override
    public void publish(LinkEvent event) {
        published.add(event);
    }

    @Override
    public void raiseAlert(UUID player, LinkAlert alert) {
        raises++;
        alerts.computeIfAbsent(player, k -> new LinkedHashMap<>()).put(alert.id(), alert);
    }

    /** Events published for {@code player} with a {@code _notify} member (what the bridge turns into pushes). */
    long pushesFor(UUID player) {
        return published.stream().filter(e -> player.equals(e.playerId()))
                .filter(e -> e.payload().has(ArcadePushCopy.FIELD)).count();
    }
}
