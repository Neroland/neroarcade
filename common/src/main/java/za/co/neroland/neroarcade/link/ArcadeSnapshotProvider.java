package za.co.neroland.neroarcade.link;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.link.LinkSnapshotProvider;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.board.LeaderboardTable;
import za.co.neroland.neroarcade.exchange.Draft;
import za.co.neroland.neroarcade.exchange.GenreTags;
import za.co.neroland.neroarcade.exchange.Listing;
import za.co.neroland.neroarcade.game.GameDef;

/**
 * The five sections of 007 §2: {@code library}, {@code leaderboard?cartridge=}, {@code cabinets}, {@code events},
 * {@code wiki}. Every section is computed for the requesting player only: nothing iterates players, nothing carries
 * a UUID or a coordinate, and other players appear only as an opted-in leaderboard name (or {@code null}).
 *
 * <p>Boards are keyed by <b>cartridge id</b> (owner decision): {@code leaderboard?cartridge=<id>} takes a cartridge
 * id and answers {@code id} (the board) plus {@code lineageRoot} (informational); the store lookup goes through
 * {@link BoardKeys#cartridgeKey}.
 */
final class ArcadeSnapshotProvider implements LinkSnapshotProvider {

    static final Pattern CARTRIDGE = Pattern.compile("^[a-z2-7]{16}$");
    /** Byte budget of one {@code library} page. */
    static final int LIBRARY_BUDGET = 12 * 1024;
    static final int LEADERBOARD_ROWS = LeaderboardTable.RECORD_WINDOW;
    static final int CABINETS_MAX = 4;

    private final ArcadeLinkContext ctx;
    private final ArcadeEvents.Pipeline events;
    private final LinkRateLimiter limiter;

    ArcadeSnapshotProvider(ArcadeLinkContext ctx, ArcadeEvents.Pipeline events, LinkRateLimiter limiter) {
        this.ctx = ctx;
        this.events = events;
        this.limiter = limiter;
    }

    @Override
    public String moduleId() {
        return NeroArcadeLinkModule.MODULE_ID;
    }

    @Override
    public int schemaVersion() {
        return NeroArcadeLinkModule.SCHEMA_VERSION;
    }

    @Override
    public List<String> sections() {
        return NeroArcadeLinkModule.SECTIONS;
    }

    @Override
    public JsonObject snapshot(UUID playerId, String section, Map<String, String> params) {
        try {
            if (section == null || playerId == null) {
                return error("unknown section");
            }
            return switch (section) {
                case NeroArcadeLinkModule.SECTION_LIBRARY -> library(playerId, params);
                case NeroArcadeLinkModule.SECTION_LEADERBOARD -> leaderboard(playerId, params);
                case NeroArcadeLinkModule.SECTION_CABINETS -> cabinets(playerId);
                case NeroArcadeLinkModule.SECTION_EVENTS -> events(playerId, params);
                case NeroArcadeLinkModule.SECTION_WIKI -> wiki(params);
                default -> error("unknown section");
            };
        } catch (RuntimeException | LinkageError e) {
            NeroArcadeCommon.LOGGER.warn("[NeroArcade] link section {} failed ({}).",
                    NeroArcadeLinkModule.SECTIONS.contains(section) ? section : "?", e.getClass().getName());
            return error("unavailable");
        }
    }

    // --- library ----------------------------------------------------------------------------------

    JsonObject library(UUID player, @Nullable Map<String, String> params) {
        LinkSettings settings = ctx.settings();
        long now = ctx.nowMillis();
        List<JsonObject> rows = libraryRows(player);

        int pageSize = Math.max(1, Math.min(50, settings.libraryPageSize()));
        int limit = clamp(parseInt(param(params, "limit")), 1, pageSize, pageSize);
        String cursor = param(params, "cursor");
        int start = 0;
        if (cursor != null && !cursor.isBlank()) {
            start = -1;
            for (int i = rows.size() - 1; i >= 0; i--) {
                if (cursor.equals(rows.get(i).get("id").getAsString())) {
                    start = i + 1;
                    break;
                }
            }
            if (start < 0) {
                start = rows.size(); // an unknown cursor is an empty page, never a restart (no paging loops)
            }
        }

        JsonObject out = new JsonObject();
        out.addProperty("asOf", now);
        out.addProperty("creditOptIn", ctx.creditOptIn(player));
        JsonObject actions = new JsonObject();
        actions.addProperty(NeroArcadeLinkModule.ACTION_EXPORT, settings.exportAvailable());
        actions.addProperty(NeroArcadeLinkModule.ACTION_IMPORT, settings.importAvailable());
        out.add("actions", actions);
        JsonObject limits = new JsonObject();
        limits.addProperty("draftsMax", settings.draftsMax());
        limits.addProperty("draftsUsed", ctx.drafts(player).size());
        limits.addProperty("importPerHour", settings.importPerHour());
        out.add("limits", limits);

        JsonArray page = new JsonArray();
        int end = Math.min(rows.size(), start + limit);
        for (int i = start; i < end; i++) {
            page.add(rows.get(i));
        }
        out.add("cartridges", page);
        out.add("nextCursor", JsonNull.INSTANCE);
        // The byte budget: drop rows from the end of the page until it fits.
        while (page.size() > 1 && bytes(out) > LIBRARY_BUDGET) {
            page.remove(page.size() - 1);
            end--;
        }
        if (end < rows.size() && !page.isEmpty()) {
            out.addProperty("nextCursor", page.get(page.size() - 1).getAsJsonObject().get("id").getAsString());
        }
        return out;
    }

    /** Drafts first, then published (newest first), then boards with only a personal best (newest first). */
    List<JsonObject> libraryRows(UUID player) {
        Map<String, LeaderboardTable.Board> bests = new LinkedHashMap<>();
        for (LeaderboardTable.Board board : ctx.boardsWithBest(player)) {
            bests.put(board.cartridgeId(), board); // the store's board key: a cartridge id (see BoardKeys)
        }
        Set<String> bestShown = new HashSet<>();
        List<JsonObject> rows = new ArrayList<>();
        for (Draft draft : ctx.drafts(player)) {
            GameDef def = draft.gameDef();
            JsonObject row = row(def.id(), def.lineageRoot(), def.title(), List.of(), def.revision(), "draft",
                    def.authorUuid().filter(player::equals).isPresent());
            row.addProperty("plays", 0);
            row.addProperty("hearts", 0);
            attachBest(row, player, bests.get(BoardKeys.cartridgeKey(def.id(), def.lineageRoot())), bestShown);
            rows.add(row);
        }
        for (Listing listing : ctx.listingsBy(player)) {
            JsonObject row = row(listing.cartridgeId(), listing.lineageRoot(), listing.title(), listing.genreTags(),
                    listing.gameDef().revision(), "published", true);
            row.addProperty("plays", listing.plays());
            row.addProperty("hearts", listing.hearts());
            attachBest(row, player, bests.get(BoardKeys.cartridgeKey(listing.cartridgeId(), listing.lineageRoot())),
                    bestShown);
            rows.add(row);
        }
        List<JsonObject> bestOnly = new ArrayList<>();
        List<Long> bestAt = new ArrayList<>();
        for (LeaderboardTable.Board board : bests.values()) {
            if (bestShown.contains(board.cartridgeId())) {
                continue;
            }
            String key = board.cartridgeId(); // boards are keyed by cartridge id
            Optional<Listing> listing = ctx.listingByCartridgeId(key);
            String title = listing.map(Listing::title).or(() -> events.titleFor(key)).orElse(null);
            String root = listing.map(Listing::lineageRoot).or(() -> events.rootFor(key)).orElse(key);
            JsonObject row = row(key, root, title, listing.map(Listing::genreTags).orElse(List.of()),
                    listing.map(l -> l.gameDef().revision()).orElse(0), "best_only", false);
            JsonObject best = best(player, board);
            if (best != null) {
                row.add("best", best);
            }
            long at = best != null && best.has("at") ? best.get("at").getAsLong() : 0L;
            int index = 0;
            while (index < bestAt.size() && bestAt.get(index) >= at) {
                index++;
            }
            bestOnly.add(index, row);
            bestAt.add(index, at);
        }
        rows.addAll(bestOnly);
        return rows;
    }

    private JsonObject row(String id, String root, @Nullable String title, List<String> tags, int revision,
            String state, boolean authored) {
        JsonObject row = new JsonObject();
        row.addProperty("id", id);
        row.addProperty("lineageRoot", root);
        row.addProperty("title", ArcadePushCopy.safeTitle(title));
        JsonArray genre = new JsonArray();
        for (String tag : tags) {
            if (genre.size() < GenreTags.MAX_TAGS) {
                genre.add(ArcadePushCopy.cap(tag, 16));
            }
        }
        row.add("genreTags", genre);
        row.addProperty("revision", revision);
        row.addProperty("state", state);
        row.addProperty("authored", authored);
        return row;
    }

    private void attachBest(JsonObject row, UUID player, @Nullable LeaderboardTable.Board board, Set<String> shown) {
        if (board == null || shown.contains(board.cartridgeId())) {
            return;
        }
        JsonObject best = best(player, board);
        if (best != null) {
            row.add("best", best);
            shown.add(board.cartridgeId());
        }
    }

    /** {score, rank?, at?}: rank and time only when the player holds a top row. Null without a personal best. */
    private static @Nullable JsonObject best(UUID player, LeaderboardTable.Board board) {
        Integer score = board.personalBest().get(player);
        if (score == null) {
            return null;
        }
        JsonObject best = new JsonObject();
        best.addProperty("score", score);
        TopRow mine = topRowOf(board, player);
        if (mine != null) {
            best.addProperty("rank", mine.rank());
            best.addProperty("at", mine.entry().at());
        }
        return best;
    }

    // --- leaderboard ------------------------------------------------------------------------------

    JsonObject leaderboard(UUID player, @Nullable Map<String, String> params) {
        long wait = limiter.tryAcquire(player, LinkRateLimiter.SECTION_LEADERBOARD, ctx.nowMillis());
        if (wait > 0L) {
            return error("rate limited");
        }
        String id = param(params, "cartridge");
        if (id == null || !CARTRIDGE.matcher(id).matches()) {
            return error("bad cartridge");
        }
        LinkSettings settings = ctx.settings();
        Optional<LeaderboardTable.Board> board = ctx.board(BoardKeys.cartridgeKey(id, id));
        String root = rootOf(player, id, board);
        JsonObject out = new JsonObject();
        out.addProperty("asOf", ctx.nowMillis());
        out.addProperty("id", id);
        out.addProperty("lineageRoot", root);
        out.addProperty("title", ArcadePushCopy.safeTitle(boardTitle(player, id, board)));
        out.addProperty("retentionDays", settings.retentionDays());
        out.addProperty("enabled", settings.leaderboardsEnabled());
        JsonArray top = new JsonArray();
        JsonObject you = null;
        if (board.isPresent()) {
            List<LeaderboardTable.Entry> entries = board.get().top();
            int rank = 0;
            int lastScore = Integer.MIN_VALUE;
            for (int i = 0; i < entries.size() && i < LEADERBOARD_ROWS; i++) {
                LeaderboardTable.Entry e = entries.get(i);
                if (i == 0 || e.score() != lastScore) {
                    rank = i + 1;
                    lastScore = e.score();
                }
                JsonObject r = new JsonObject();
                r.addProperty("rank", rank);
                if (!e.uuid().equals(player)) {
                    // the requester's own row carries no name field; others' only the opted-in name or null
                    String name = e.name() == null ? null : ArcadePushCopy.cap(e.name(), 16);
                    if (name == null || name.isEmpty()) {
                        r.add("name", JsonNull.INSTANCE);
                    } else {
                        r.addProperty("name", name);
                    }
                }
                r.addProperty("score", e.score());
                r.addProperty("at", e.at());
                top.add(r);
            }
            Integer best = board.get().personalBest().get(player);
            if (best != null) {
                you = new JsonObject();
                TopRow mine = topRowOf(board.get(), player);
                if (mine != null) {
                    you.addProperty("rank", mine.rank());
                }
                you.addProperty("score", mine != null ? Math.max(best, mine.entry().score()) : best);
                if (mine != null) {
                    you.addProperty("at", mine.entry().at());
                }
                you.addProperty("inTop", mine != null);
            }
        }
        out.add("top", top);
        if (you == null) {
            out.add("you", JsonNull.INSTANCE);
        } else {
            out.add("you", you);
        }
        return out;
    }

    /**
     * A board's title from public or the requester's own data only: the listing, the requester's draft, or (when the
     * requester has played it) the title seen at a finished match. An unknown id gets the generic title, the same as
     * an untitled board, so the section cannot tell which cartridges exist.
     */
    private @Nullable String boardTitle(UUID player, String id, Optional<LeaderboardTable.Board> board) {
        Optional<Listing> listing = ctx.listingByCartridgeId(id);
        if (listing.isPresent()) {
            return listing.get().title();
        }
        Optional<Draft> draft = ctx.draft(player, id);
        if (draft.isPresent()) {
            return draft.get().gameDef().title();
        }
        boolean played = board.map(b -> b.personalBest().containsKey(player)).orElse(false);
        return played ? events.titleFor(id).orElse(null) : null;
    }

    /**
     * Cartridge {@code id}'s lineage root from public or the requester's own data (the listing, their draft, or —
     * only when the requester holds a best on this board, the same rule as {@link #boardTitle} — the root seen at a
     * finished match), else the id itself. Informational only: the store keys boards by cartridge id. Without the
     * "played" gate the server-wide match cache would tell anyone the root of an unlisted id (security review L1).
     */
    private String rootOf(UUID player, String id, Optional<LeaderboardTable.Board> board) {
        boolean played = board.map(b -> b.personalBest().containsKey(player)).orElse(false);
        return ctx.listingByCartridgeId(id).map(Listing::lineageRoot)
                .or(() -> ctx.draft(player, id).map(d -> d.gameDef().lineageRoot()))
                .or(() -> played ? events.rootFor(id) : Optional.empty())
                .orElse(id);
    }

    private record TopRow(int rank, LeaderboardTable.Entry entry) {
    }

    private static @Nullable TopRow topRowOf(LeaderboardTable.Board board, UUID player) {
        List<LeaderboardTable.Entry> entries = board.top();
        int rank = 0;
        int lastScore = Integer.MIN_VALUE;
        for (int i = 0; i < entries.size(); i++) {
            LeaderboardTable.Entry e = entries.get(i);
            if (i == 0 || e.score() != lastScore) {
                rank = i + 1;
                lastScore = e.score();
            }
            if (e.uuid().equals(player)) {
                return new TopRow(rank, e);
            }
        }
        return null;
    }

    // --- cabinets ---------------------------------------------------------------------------------

    JsonObject cabinets(UUID player) {
        JsonObject out = new JsonObject();
        out.addProperty("asOf", ctx.nowMillis());
        JsonArray list = new JsonArray();
        Optional<ArcadeLinkContext.CabinetInfo> seated = ctx.cabinetOf(player);
        if (seated.isPresent() && list.size() < CABINETS_MAX) {
            ArcadeLinkContext.CabinetInfo c = seated.get();
            JsonObject row = new JsonObject();
            row.addProperty("label", ArcadePushCopy.cabinetLabel(c.dimensionId(), c.cabinets()));
            row.addProperty("role", c.host() ? "host" : "seat");
            row.addProperty("seat", c.seat());
            row.addProperty("phase", c.phase());
            row.addProperty("multiplayer", ArcadePushCopy.cap(c.multiplayer(), 32));
            row.addProperty("seats", c.seats());
            if (c.cartridgeId() == null || c.lineageRoot() == null) {
                row.add("cartridge", JsonNull.INSTANCE);
            } else {
                JsonObject cart = new JsonObject();
                cart.addProperty("id", c.cartridgeId());
                cart.addProperty("lineageRoot", c.lineageRoot());
                cart.addProperty("title", ArcadePushCopy.safeTitle(c.title()));
                row.add("cartridge", cart);
            }
            list.add(row);
        }
        out.add("cabinets", list);
        return out;
    }

    // --- events -----------------------------------------------------------------------------------

    JsonObject events(UUID player, @Nullable Map<String, String> params) {
        int limit = clamp(parseInt(param(params, "limit")), 1, ArcadeEvents.RING_MAX, ArcadeEvents.RING_MAX);
        JsonObject out = new JsonObject();
        out.addProperty("asOf", ctx.nowMillis());
        JsonArray list = new JsonArray();
        for (JsonObject row : events.events(player, limit)) {
            list.add(row);
        }
        out.add("events", list);
        return out;
    }

    // --- wiki -------------------------------------------------------------------------------------

    /** WIKI CONTRACT v1: public pages, the same for every player. */
    static JsonObject wiki(@Nullable Map<String, String> params) {
        long now = System.currentTimeMillis();
        String slug = params == null ? null : params.get("page");
        if (slug == null || slug.isBlank()) {
            JsonObject out = new JsonObject();
            out.addProperty("mod", NeroArcadeLinkModule.MODULE_ID);
            out.addProperty("title", "NeroArcade");
            JsonArray pages = new JsonArray();
            for (WikiLibrary.Page page : WikiLibrary.pages()) {
                JsonObject p = new JsonObject();
                p.addProperty("slug", page.slug());
                p.addProperty("title", page.title());
                pages.add(p);
            }
            out.add("pages", pages);
            out.addProperty("asOf", now);
            return out;
        }
        Optional<String> content = WikiLibrary.content(slug);
        if (content.isEmpty()) {
            return error("unknown page");
        }
        JsonObject out = new JsonObject();
        out.addProperty("mod", NeroArcadeLinkModule.MODULE_ID);
        out.addProperty("slug", slug);
        out.addProperty("title", WikiLibrary.titleOf(slug));
        out.addProperty("format", "markdown");
        out.addProperty("content", content.get());
        out.addProperty("asOf", now);
        return out;
    }

    // --- shared -----------------------------------------------------------------------------------

    static JsonObject error(String message) {
        JsonObject out = new JsonObject();
        out.addProperty("error", message);
        return out;
    }

    static int bytes(JsonObject json) {
        return json.toString().getBytes(StandardCharsets.UTF_8).length;
    }

    private static @Nullable String param(@Nullable Map<String, String> params, String key) {
        return params == null ? null : params.get(key);
    }

    private static @Nullable Integer parseInt(@Nullable String value) {
        if (value == null || value.isBlank() || value.length() > 6) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int clamp(@Nullable Integer value, int min, int max, int fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }
}
