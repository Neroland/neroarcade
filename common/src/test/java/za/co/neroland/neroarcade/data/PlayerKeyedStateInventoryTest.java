package za.co.neroland.neroarcade.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.TestFiles;

/**
 * Stage 6: the inventory of every place NeroArcade keeps player-keyed state, and the tripwire that keeps it honest.
 * The main sources (client code excluded: it never runs on a server and holds nothing of other players) are scanned
 * for {@code extends SavedData} and for fields of a collection keyed by a {@code UUID} (or by a record with a
 * {@code UUID} component). Every hit must be listed below, either with the eraser that clears it (one of the names
 * the composite registers) or with the reason it needs none. A new store or map that is not listed fails this test:
 * add an eraser (and a probe in {@code ArcadeErasureConformanceTest}) or justify it here and in PRIVACY.md.
 */
class PlayerKeyedStateInventoryTest {

    /** Store names registered with {@link NeroArcadeData} in production. */
    private static final Set<String> ERASERS =
            Set.of("leaderboards", "credit", "exchange", "drafts", "seats", "benches", "exports", "link");

    /**
     * "File#field" (or "File" for a SavedData) -> eraser name, or "transient: ..." / "none: ..." saying why no
     * eraser is needed.
     */
    private static final Map<String, String> INVENTORY = new TreeMap<>(Map.ofEntries(
            // SavedData stores
            Map.entry("LeaderboardStore", "leaderboards"),
            Map.entry("CreditOptIn", "credit"),
            Map.entry("ExchangeStore", "exchange"),
            Map.entry("GalleryStore", "none: no player data (the block states the creative gallery replaced)"),
            // persisted, UUID-keyed
            Map.entry("LeaderboardTable#top", "leaderboards"),
            Map.entry("LeaderboardTable#bests", "leaderboards"),
            Map.entry("LeaderboardTable#bestAt", "leaderboards"),
            Map.entry("CreditTable#flags", "credit"),
            Map.entry("ExchangeTable#drafts", "drafts"),
            // memory only, per player
            Map.entry("SeatRegistry#seats", "seats"),
            Map.entry("ExchangeActions#WINDOWS", "seats"),
            Map.entry("BenchActions#WINDOWS", "seats"),
            Map.entry("ExchangeHearts#given", "exchange"),
            Map.entry("ExchangeHearts#recent", "exchange"),
            Map.entry("ExportCooldown#lastAt", "exports"),
            Map.entry("ArcadeEvents#rings", "link"),
            Map.entry("LinkRequestDedup#byPlayer", "link"),
            Map.entry("LinkRateLimiter#calls", "link"),
            Map.entry("RecordCoalescer#windows", "link"),
            // memory only, self-pruning to who is present right now
            Map.entry("LinkBusBlockEntity#sentBoards",
                    "transient: board version sent to each player near a marquee; pruned to current viewers every send"),
            Map.entry("MatchHost#knownViewers",
                    "transient: players in screen range at the last frame; replaced every frame"),
            Map.entry("QuestHooks#ONLINE",
                    "transient: players online right now (gate sync); pruned to the player list every scan")));

    private static final Pattern SAVED_DATA = Pattern.compile("class\\s+(\\w+)\\s+extends\\s+SavedData\\b");
    private static final Pattern KEY_RECORD = Pattern.compile("record\\s+(\\w+)\\s*\\(([^)]*)\\)");
    private static final String MODIFIERS = "(?:(?:private|public|protected|static|final|volatile|transient)\\s+)+";

    @Test
    void everyPlayerKeyedStoreAndMapIsInventoried() throws IOException {
        Path root = TestFiles.repoRoot().resolve("common/src/main/java/za/co/neroland/neroarcade");
        Set<String> found = new TreeSet<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (root.relativize(file).toString().replace('\\', '/').startsWith("client/")) {
                    continue;
                }
                String src = Files.readString(file, StandardCharsets.UTF_8);
                String name = file.getFileName().toString().replace(".java", "");
                Matcher saved = SAVED_DATA.matcher(src);
                while (saved.find()) {
                    found.add(saved.group(1));
                }
                Set<String> keys = new HashSet<>(Set.of("UUID"));
                Matcher rec = KEY_RECORD.matcher(src);
                while (rec.find()) {
                    if (rec.group(2).matches("(?s).*\\bUUID\\b.*")) {
                        keys.add(rec.group(1));
                    }
                }
                Pattern field = Pattern.compile("^\\s*" + MODIFIERS + "[\\w.]*(?:Map|Set|Deque|List|Queue)<\\s*(?:"
                        + String.join("|", keys) + ")\\b[^;(]*?\\s(\\w+)\\s*(?:=|;)", Pattern.MULTILINE);
                Matcher f = field.matcher(src);
                while (f.find()) {
                    found.add(name + "#" + f.group(1));
                }
            }
        }
        List<String> missing = new ArrayList<>();
        for (String hit : found) {
            if (!INVENTORY.containsKey(hit)) {
                missing.add(hit);
            }
        }
        assertTrue(missing.isEmpty(), "player-keyed state with no eraser or justification: " + missing
                + " (add an eraser + conformance probe, or a reason here and in PRIVACY.md)");
        Set<String> stale = new TreeSet<>(INVENTORY.keySet());
        stale.removeAll(found);
        assertTrue(stale.isEmpty(), "inventory entries no longer in the sources (remove them): " + stale);
    }

    @Test
    void everyInventoryEntryNamesARegisteredEraserOrAReason() {
        INVENTORY.forEach((where, what) -> assertTrue(ERASERS.contains(what) || what.startsWith("transient: ")
                || what.startsWith("none: "),
                where + " -> " + what));
    }

    /** Every {@code addStore} call in the main sources, so a store registered elsewhere is noticed. */
    @Test
    void storesAreAddedOnlyFromTheKnownPlaces() throws IOException {
        Path root = TestFiles.repoRoot().resolve("common/src/main/java/za/co/neroland/neroarcade");
        List<String> callers = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String src = Files.readString(file, StandardCharsets.UTF_8);
                Matcher m = Pattern.compile("NeroArcadeData\\.addStore\\((\\w+(?:\\.\\w+)?)\\)").matcher(src);
                while (m.find()) {
                    callers.add(file.getFileName() + ":" + m.group(1));
                }
            }
        }
        assertEquals(List.of("ArcadePrivacy.java:EXPORTS_ERASER", "ArcadePrivacy.java:LoadedBenches.ERASER",
                "ArcadeServer.java:SEATS_ERASER", "Boards.java:CREDIT_ERASER", "Boards.java:LEADERBOARD_ERASER",
                "Exchange.java:DRAFTS_ERASER", "Exchange.java:EXCHANGE_ERASER", "NeroArcadeLinkModule.java:linkEraser"),
                callers.stream().sorted().toList());
    }
}
