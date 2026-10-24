package za.co.neroland.neroarcade.board;

import java.util.UUID;

import za.co.neroland.neroarcade.runtime.MatchResult;

/**
 * Seeds a real {@link LeaderboardStore} for the erasure conformance tests in other packages (the write path is
 * package-private on purpose, so the seeding lives here).
 */
public final class BoardErasureFixture {

    /** The board both players are on. */
    public static final String CARTRIDGE = BoardFixtures.CARTRIDGE;
    /** A second board where the subject holds only a personal best (they walked out). */
    public static final String SECOND = "ffff000011112222";

    private BoardErasureFixture() {
    }

    /**
     * {@code subject}: a top row carrying {@code subjectName} (they had credit on) and a best on {@link #CARTRIDGE},
     * and a best only on {@link #SECOND}; {@code bystander}: an anonymous top row and a best on {@link #CARTRIDGE}.
     */
    public static LeaderboardStore seeded(UUID subject, String subjectName, UUID bystander) {
        LeaderboardTable table = new LeaderboardTable();
        table.record(BoardFixtures.result(MatchResult.Outcome.WIN,
                BoardFixtures.seat(subject, 0, 50, false),
                BoardFixtures.seat(bystander, 1, 40, false)), 1_800_000_000L, 10,
                u -> u.equals(subject) ? subjectName : null);
        table.record(BoardFixtures.result(SECOND, MatchResult.Outcome.LOSE,
                BoardFixtures.seat(subject, 0, 9, true)), 1_800_000_100L, 10, u -> null);
        return LeaderboardStore.of(table);
    }
}
