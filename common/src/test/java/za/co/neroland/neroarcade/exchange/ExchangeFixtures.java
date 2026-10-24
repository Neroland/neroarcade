package za.co.neroland.neroarcade.exchange;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import za.co.neroland.neroarcade.game.GameDef;

/** Games and players for the Exchange tests. */
final class ExchangeFixtures {

    static final UUID ALICE = UUID.fromString("11111111-2222-4333-8444-555555555555");
    static final UUID BOB = UUID.fromString("66666666-7777-4888-9999-aaaaaaaaaaaa");
    static final UUID CAROL = UUID.fromString("bbbbbbbb-cccc-4ddd-8eee-ffffffffffff");
    static final List<String> TAGS = List.of("grid", "move", "survive", "solo", "neon");

    private ExchangeFixtures() {
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

    static GameDef game(int n, UUID author, String credit) {
        return game(id(n), List.of(), author, credit, 1);
    }

    static GameDef game(String id, List<String> lineage, UUID author, String credit, int revision) {
        return new GameDef(1, id, "Game " + id.substring(0, 4), Optional.ofNullable(author), Optional.ofNullable(credit),
                lineage, Optional.empty(), Map.of(), 100L, revision);
    }

    static ExchangeTable.PublishResult publish(ExchangeTable table, GameDef def, long now, int cap) {
        return table.publish(def, ExchangeRules.listingAuthor(def.authorUuid()), def.authorCredit(), TAGS, now, cap);
    }
}
