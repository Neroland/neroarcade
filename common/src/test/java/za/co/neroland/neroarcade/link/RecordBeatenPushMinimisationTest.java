package za.co.neroland.neroarcade.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.link.LinkAlert;
import za.co.neroland.nerolandcore.link.LinkEvent;

import za.co.neroland.neroarcade.board.RecordOutcome;
import za.co.neroland.neroarcade.cabinet.BusMatchResult;

/**
 * 007 §5 gate: a two-seat match where "Kessler_K" (credit on, known UUID) beats the fixture player's record. The
 * {@code RECORD_BEATEN} payload, its {@code _notify} copy and the stored alert name neither Kessler nor his UUID (with
 * or without dashes), carry no position or dimension key and no cabinet label; the deep link matches the bridge's
 * pattern once the bridge substitutes its server id.
 */
class RecordBeatenPushMinimisationTest {

    private static final String ROOT = LinkFixtures.id(77);
    /** A remix of ROOT: its own id is its own board (owner decision: boards by cartridge id). */
    private static final String ID = LinkFixtures.id(78);

    @Test
    void recordBeatenNamesTheCartridgeAndRankOnly() {
        FakeLinkContext ctx = new FakeLinkContext();
        ctx.optedIn.add(LinkFixtures.KESSLER);
        ctx.boards.put(ROOT, LinkFixtures.board(ROOT, LinkFixtures.KESSLER, 90, "Kessler_K", LinkFixtures.ALICE, 50,
                null));
        ArcadeEvents.Pipeline events = LinkFixtures.pipeline(ctx);

        events.onMatchEnd(new ArcadeEvents.MatchEndInfo(ID, ROOT, "Neon Dodge", "minecraft:overworld", 2,
                List.of(new BusMatchResult.SeatResult(LinkFixtures.ALICE, 0, 50, 2, false),
                        new BusMatchResult.SeatResult(LinkFixtures.KESSLER, 1, 90, 1, false)),
                "WIN", new RecordOutcome(2, 3, 1, 1, List.of(new RecordOutcome.Displaced(LinkFixtures.ALICE, 2))),
                false));

        List<LinkEvent> beaten = ctx.published.stream()
                .filter(e -> "RECORD_BEATEN".equals(e.payload().get("type").getAsString())).toList();
        assertEquals(1, beaten.size());
        LinkEvent event = beaten.get(0);
        assertEquals(LinkFixtures.ALICE, event.playerId(), "only the player whose record fell hears about it");
        assertEquals("neroarcade", event.moduleId());
        assertEquals("events", event.topic());

        String payload = event.payload().toString();
        assertClean(payload);
        assertNoKey(event.payload(), "x", "y", "z", "pos", "dimension", "label", "uuid", "player", "name");

        JsonObject notify = event.payload().getAsJsonObject(ArcadePushCopy.FIELD);
        assertNotNull(notify);
        assertEquals("Record beaten", notify.get("title").getAsString());
        assertEquals("Your record on Neon Dodge was beaten — now #2", notify.get("body").getAsString());
        String link = notify.get("deepLink").getAsString();
        assertTrue(ArcadePushCopy.DEEP_LINK.matcher(link.replace("{server}", "s1")).matches(), link);
        assertTrue(link.endsWith("/arcade/" + ID), "the deep link targets the cartridge id (the board): " + link);
        assertEquals(ID, event.payload().get("id").getAsString());
        assertEquals(ROOT, event.payload().get("lineageRoot").getAsString());
        assertFalse(notify.toString().contains("Overworld"), "no cabinet label in a record push (D5)");

        Map<String, LinkAlert> alerts = ctx.alerts.get(LinkFixtures.ALICE);
        assertNotNull(alerts);
        LinkAlert alert = alerts.get("neroarcade." + ID + ".record");
        assertNotNull(alert);
        assertClean(alert.text());
        assertFalse(alert.text().contains("Overworld"));
        assertFalse(ctx.alerts.containsKey(LinkFixtures.KESSLER), "the record breaker gets no alert");
    }

    @Test
    void pushCopyTakesNoPlayerTypes() {
        for (var method : ArcadePushCopy.class.getDeclaredMethods()) {
            for (Class<?> type : method.getParameterTypes()) {
                String name = type.getName();
                assertFalse(name.equals("java.util.UUID") || name.contains("Player") || name.contains("BlockPos")
                        || name.contains("Level") || name.contains("GameProfile"), method + " takes " + name);
            }
        }
    }

    private static void assertClean(String text) {
        assertFalse(text.contains("Kessler"), text);
        assertFalse(text.contains(LinkFixtures.KESSLER.toString()), text);
        assertFalse(text.contains(LinkFixtures.KESSLER.toString().replace("-", "")), text);
        assertFalse(text.contains(LinkFixtures.ALICE.toString()), text);
    }

    private static void assertNoKey(JsonElement json, String... keys) {
        if (json.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject().entrySet()) {
                for (String key : keys) {
                    assertFalse(e.getKey().equalsIgnoreCase(key), "key " + e.getKey());
                }
                assertNoKey(e.getValue(), keys);
            }
        } else if (json.isJsonArray()) {
            json.getAsJsonArray().forEach(el -> assertNoKey(el, keys));
        }
    }

    static void assertNoKeys(JsonElement json, String... keys) {
        assertNoKey(json, keys);
    }
}
