package za.co.neroland.neroarcade.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

/**
 * 007 §2.2 / §8.1: an unknown root answers exactly the shape of a real empty board (so the section cannot enumerate
 * roots); only opted-in names appear (else {@code null}); the requester's own row has no name field; a malformed
 * cartridge id is {@code {"error":"bad cartridge"}}; 30 calls a minute.
 */
class LeaderboardSectionShapeTest {

    @Test
    void unknownRootLooksLikeAnEmptyBoard() {
        FakeLinkContext ctx = new FakeLinkContext();
        String real = LinkFixtures.id(1);
        ctx.boards.put(real, LinkFixtures.board(real));
        ArcadeSnapshotProvider provider = LinkFixtures.provider(ctx, LinkFixtures.pipeline(ctx));
        JsonObject empty = provider.snapshot(LinkFixtures.ALICE, "leaderboard", Map.of("cartridge", real));
        JsonObject unknown = provider.snapshot(LinkFixtures.ALICE, "leaderboard",
                Map.of("cartridge", LinkFixtures.id(2)));
        assertEquals(empty.keySet(), unknown.keySet());
        assertEquals(0, unknown.getAsJsonArray("top").size());
        assertTrue(unknown.get("you").isJsonNull());
        assertEquals(empty.get("title"), unknown.get("title"));
    }

    @Test
    void namesOnlyWhenOptedInAndNeverYourOwn() {
        FakeLinkContext ctx = new FakeLinkContext();
        String root = LinkFixtures.id(1);
        ctx.boards.put(root, LinkFixtures.board(root, LinkFixtures.KESSLER, 90, "Kessler_K", LinkFixtures.BOB, 80, null,
                LinkFixtures.ALICE, 70, "AliceName"));
        JsonObject out = LinkFixtures.provider(ctx, LinkFixtures.pipeline(ctx)).snapshot(LinkFixtures.ALICE,
                "leaderboard", Map.of("cartridge", root));
        JsonArray top = out.getAsJsonArray("top");
        assertEquals("Kessler_K", top.get(0).getAsJsonObject().get("name").getAsString());
        assertTrue(top.get(1).getAsJsonObject().get("name").isJsonNull(), "no opt-in: null (the app shows Anonymous)");
        assertFalse(top.get(2).getAsJsonObject().has("name"), "the requester's own row carries no name");
        JsonObject you = out.getAsJsonObject("you");
        assertEquals(3, you.get("rank").getAsInt());
        assertEquals(70, you.get("score").getAsInt());
        assertTrue(you.get("inTop").getAsBoolean());
        assertFalse(you.has("name"));
        assertEquals(90, out.get("retentionDays").getAsInt());
        assertTrue(out.get("enabled").getAsBoolean());
    }

    @Test
    void badCartridgeAndRateLimit() {
        FakeLinkContext ctx = new FakeLinkContext();
        ArcadeSnapshotProvider provider = LinkFixtures.provider(ctx, LinkFixtures.pipeline(ctx));
        assertEquals("bad cartridge", provider.snapshot(LinkFixtures.ALICE, "leaderboard",
                Map.of("cartridge", "ABC")).get("error").getAsString());
        assertEquals("bad cartridge", provider.snapshot(LinkFixtures.ALICE, "leaderboard", Map.of()).get("error")
                .getAsString());
        for (int i = 0; i < 28; i++) {
            provider.snapshot(LinkFixtures.ALICE, "leaderboard", Map.of("cartridge", LinkFixtures.id(i)));
        }
        assertEquals("rate limited", provider.snapshot(LinkFixtures.ALICE, "leaderboard",
                Map.of("cartridge", LinkFixtures.id(1))).get("error").getAsString());
        assertFalse(provider.snapshot(LinkFixtures.BOB, "leaderboard", Map.of("cartridge", LinkFixtures.id(1)))
                .has("error"), "per player");
    }

    @Test
    void anUnlistedIdsRootComesFromTheMatchCacheOnlyForSomeoneWhoPlayedIt() {
        // Stage 7 security review L1: the server-wide match cache must not tell a stranger an unlisted id's root
        FakeLinkContext ctx = new FakeLinkContext();
        String id = LinkFixtures.id(20);
        String root = LinkFixtures.id(21);
        ctx.boards.put(id, LinkFixtures.board(id, LinkFixtures.BOB, 40, null));
        ArcadeEvents.Pipeline events = LinkFixtures.pipeline(ctx);
        events.onMatchEnd(new ArcadeEvents.MatchEndInfo(id, root, "Private Remix", "minecraft:overworld", 1,
                List.of(new za.co.neroland.neroarcade.cabinet.BusMatchResult.SeatResult(LinkFixtures.BOB, 0, 40, 1,
                        false)), "WIN", new za.co.neroland.neroarcade.board.RecordOutcome(1, 1, 0, 0), false));
        ArcadeSnapshotProvider provider = LinkFixtures.provider(ctx, events);

        JsonObject alice = provider.snapshot(LinkFixtures.ALICE, "leaderboard", Map.of("cartridge", id));
        assertEquals(id, alice.get("lineageRoot").getAsString(), "Alice never played it: the id itself");
        assertFalse(alice.toString().contains(root), alice.toString());
        assertFalse(alice.toString().contains("Private Remix"), alice.toString());

        JsonObject bob = provider.snapshot(LinkFixtures.BOB, "leaderboard", Map.of("cartridge", id));
        assertEquals(root, bob.get("lineageRoot").getAsString(), "Bob holds a best on it");
    }
}
