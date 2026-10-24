package za.co.neroland.neroarcade.link;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import za.co.neroland.nerolandcore.link.LinkActionResult;

import za.co.neroland.neroarcade.TestFiles;
import za.co.neroland.neroarcade.board.LeaderboardTable;
import za.co.neroland.neroarcade.exchange.ExchangeTable;
import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.module.TestModules;

/** Players, cartridges, boards and wiring shared by the link tests. */
final class LinkFixtures {

    static final UUID ALICE = UUID.fromString("11111111-2222-4333-8444-555555555555");
    static final UUID BOB = UUID.fromString("66666666-7777-4888-9999-aaaaaaaaaaaa");
    static final UUID KESSLER = UUID.fromString("0badc0de-1234-4567-89ab-cdef01234567");
    static final List<String> TAGS = List.of("grid", "move", "survive", "solo", "neon");

    private LinkFixtures() {
    }

    /** A 16-char base32 id from a number (a..z, 2..7). */
    static String id(int n) {
        String alphabet = "abcdefghijklmnopqrstuvwxyz234567";
        StringBuilder out = new StringBuilder();
        long v = n;
        for (int i = 0; i < 16; i++) {
            out.append(alphabet.charAt((int) (v & 31)));
            v >>>= 5;
        }
        return out.toString();
    }

    static GameDef game(String id, String title, UUID author, String credit) {
        return new GameDef(1, id, title, Optional.ofNullable(author), Optional.ofNullable(credit), List.of(),
                Optional.empty(), Map.of(), 100L, 2);
    }

    static void publish(FakeLinkContext ctx, GameDef def, long at) {
        ExchangeTable.PublishResult result = ctx.exchange.publish(def, def.authorUuid().orElseThrow(),
                def.authorCredit(), TAGS, at, 200);
        if (result.status() == ExchangeTable.PublishStatus.FULL) {
            throw new IllegalStateException("full");
        }
    }

    /** A board: rows in order (uuid, score, name), plus personal bests equal to each row's score. */
    static LeaderboardTable.Board board(String root, Object... rows) {
        List<LeaderboardTable.Entry> top = new java.util.ArrayList<>();
        Map<UUID, Integer> bests = new LinkedHashMap<>();
        long at = 1_791_900_000L;
        for (int i = 0; i < rows.length; i += 3) {
            UUID uuid = (UUID) rows[i];
            int score = (Integer) rows[i + 1];
            String name = (String) rows[i + 2];
            top.add(new LeaderboardTable.Entry(uuid, score, at + i, name));
            bests.put(uuid, score);
        }
        return new LeaderboardTable.Board(root, top, bests, 1);
    }

    static ArcadeEvents.Pipeline pipeline(FakeLinkContext ctx) {
        return new ArcadeEvents.Pipeline(ctx);
    }

    static ArcadeSnapshotProvider provider(FakeLinkContext ctx, ArcadeEvents.Pipeline events) {
        return new ArcadeSnapshotProvider(ctx, events, LinkRateLimiter.standard(() -> ctx.settings.importPerHour()));
    }

    static ArcadeActionHandler handler(FakeLinkContext ctx) {
        return new ArcadeActionHandler(ctx, new LinkRequestDedup(),
                LinkRateLimiter.standard(() -> ctx.settings.importPerHour()));
    }

    /** A valid cartridge's JSON (the registry with the test chips installed). */
    static JsonObject validGameDef() {
        TestModules.install();
        return JsonParser.parseString(TestFiles.cartridge("orb_dodge.json")).getAsJsonObject();
    }

    static JsonObject importParams(String requestId, JsonObject gameDef) {
        JsonObject params = new JsonObject();
        if (requestId != null) {
            params.addProperty("requestId", requestId);
        }
        params.add("gameDef", gameDef);
        return params;
    }

    static JsonObject exportParams(String requestId, String root, String id) {
        JsonObject params = new JsonObject();
        if (requestId != null) {
            params.addProperty("requestId", requestId);
        }
        params.addProperty("lineageRoot", root);
        if (id != null) {
            params.addProperty("id", id);
        }
        return params;
    }

    static String describe(LinkActionResult r) {
        return r.ok() ? "ok " + r.state() : r.error() + " " + r.message();
    }
}
