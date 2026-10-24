package za.co.neroland.neroarcade.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.TestFiles;
import za.co.neroland.neroarcade.runtime.MatchResult;

/**
 * 003 §6.1/§13: the leaderboard has one write path. Reflection-free: the sources are scanned. {@code record} is
 * package-private on the store and the table; inside {@code board} only {@code Boards.writer} and the store call it;
 * outside {@code board} a {@code BoardWriter} is invoked only from {@code MatchHost.finish}. An ABANDONED result or a
 * dropped seat never writes a top row.
 */
class LeaderboardWriteOnlyFromMatchHostTest {

    private static final Pattern RECORD_DECL = Pattern.compile("^\\s*(public\\s+|protected\\s+)?\\S*RecordOutcome\\s+record\\(",
            Pattern.MULTILINE);
    private static final Pattern RECORD_CALL = Pattern.compile("\\.record\\(");

    private static List<Path> mainSources() throws IOException {
        Path root = TestFiles.repoRoot().resolve("common/src/main").resolve("java");
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    @Test
    void recordIsPackagePrivateOnTheStoreAndTheTable() throws IOException {
        for (String name : List.of("LeaderboardStore.java", "LeaderboardTable.java")) {
            Path file = TestFiles.repoRoot().resolve("common/src/main").resolve("java/za/co/neroland/neroarcade/board/" + name);
            String src = Files.readString(file, StandardCharsets.UTF_8);
            Matcher m = RECORD_DECL.matcher(src);
            assertTrue(m.find(), name + " declares record(");
            assertEquals(null, m.group(1), name + ": record must be package-private");
        }
    }

    @Test
    void onlyMatchHostFinishInvokesAWriter() throws IOException {
        List<String> callers = new ArrayList<>();
        for (Path file : mainSources()) {
            String src = Files.readString(file, StandardCharsets.UTF_8);
            Matcher m = RECORD_CALL.matcher(src);
            while (m.find()) {
                int lineStart = src.lastIndexOf('\n', m.start()) + 1;
                int lineEnd = src.indexOf('\n', m.start());
                String line = src.substring(lineStart, lineEnd < 0 ? src.length() : lineEnd).trim();
                if (line.startsWith("*") || line.startsWith("//")) {
                    continue; // javadoc
                }
                callers.add(file.getFileName() + ": " + line);
            }
        }
        // InputLog.record (the replay log) is unrelated; everything else must be the board path
        List<String> board = callers.stream().filter(c -> !c.contains("log.record(")).toList();
        assertEquals(List.of(
                "Boards.java: return result -> LeaderboardStore.get(server).record(server, result, names(server));",
                "LeaderboardStore.java: RecordOutcome outcome = table.record(result, System.currentTimeMillis() / 1000L,",
                "MatchHost.java: RecordOutcome outcome = writer.record(toBusResult(r));"), board.stream().sorted().toList());
        String host = Files.readString(TestFiles.repoRoot().resolve("common/src/main")
                .resolve("java/za/co/neroland/neroarcade/cabinet/MatchHost.java"), StandardCharsets.UTF_8);
        int finish = host.indexOf("private void finish(MatchResult r)");
        int call = host.indexOf("writer.record(");
        int next = host.indexOf("private BusMatchResult toBusResult", finish);
        assertTrue(finish > 0 && call > finish && call < next, "the writer call sits inside MatchHost.finish");
    }

    @Test
    void abandonedResultWritesNothing() {
        LeaderboardTable table = new LeaderboardTable();
        RecordOutcome out = table.record(BoardFixtures.result(MatchResult.Outcome.ABANDONED,
                BoardFixtures.seat(BoardFixtures.ALICE, 0, 500, false)), 1000L, 10, u -> null);
        assertEquals(RecordOutcome.NONE, out);
        assertFalse(table.holds(BoardFixtures.ALICE));
        assertEquals(0, table.boardCount());
    }

    @Test
    void droppedSeatGetsAPersonalBestButNoTopRow() {
        LeaderboardTable table = new LeaderboardTable();
        RecordOutcome out = table.record(BoardFixtures.result(MatchResult.Outcome.WIN,
                BoardFixtures.seat(BoardFixtures.ALICE, 0, 300, false),
                BoardFixtures.seat(BoardFixtures.BOB, 1, 900, true)), 1000L, 10, u -> null);
        LeaderboardTable.Board board = table.board(BoardFixtures.CARTRIDGE).orElseThrow();
        assertEquals(1, board.top().size());
        assertEquals(BoardFixtures.ALICE, board.top().get(0).uuid());
        assertEquals(900, board.personalBest().get(BoardFixtures.BOB));
        assertEquals(0b11, out.newBestMask());
        assertEquals(0b01, out.enteredTopMask());
    }

    @Test
    void onePlayerOneRowAndTheCapHolds() {
        LeaderboardTable table = new LeaderboardTable();
        table.record(BoardFixtures.result(MatchResult.Outcome.WIN, BoardFixtures.seat(BoardFixtures.ALICE, 0, 100,
                false)), 1L, 2, u -> null);
        table.record(BoardFixtures.result(MatchResult.Outcome.WIN, BoardFixtures.seat(BoardFixtures.ALICE, 0, 50,
                false)), 2L, 2, u -> null);
        table.record(BoardFixtures.result(MatchResult.Outcome.WIN, BoardFixtures.seat(BoardFixtures.ALICE, 0, 200,
                false)), 3L, 2, u -> null);
        LeaderboardTable.Board board = table.board(BoardFixtures.CARTRIDGE).orElseThrow();
        assertEquals(1, board.top().size(), "a player holds one row: their best");
        assertEquals(200, board.top().get(0).score());
        table.record(BoardFixtures.result(MatchResult.Outcome.WIN,
                BoardFixtures.seat(BoardFixtures.BOB, 0, 300, false),
                BoardFixtures.seat(java.util.UUID.randomUUID(), 1, 10, false)), 4L, 2, u -> null);
        board = table.board(BoardFixtures.CARTRIDGE).orElseThrow();
        assertEquals(2, board.top().size(), "capped at maxEntries");
        assertEquals(BoardFixtures.BOB, board.top().get(0).uuid());
    }
}
