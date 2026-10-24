package za.co.neroland.neroarcade.client.board;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroarcade.network.BoardPagePayload;

/**
 * The leaderboards this client was sent, by lineage root (the marquee's idle loop and the cabinet attract screen).
 * At most {@link #CAP} boards, oldest dropped first; cleared on disconnect and on a world change. Pure; client
 * thread only.
 */
public final class ClientBoards {

    public static final int CAP = 16;
    /** Characters of a name the 64-px pixel screen can show beside a rank and a score. */
    public static final int NAME_CHARS = 4;

    /** One pixel-screen row: {@code left} = rank and short name, {@code right} = the score. */
    public record Line(String left, String right) {
    }

    private static final Map<String, BoardPagePayload> BOARDS = new LinkedHashMap<>();

    private ClientBoards() {
    }

    public static void accept(BoardPagePayload page) {
        if (page == null || page.cartridgeId().isEmpty()) {
            return;
        }
        BOARDS.remove(page.cartridgeId());
        BOARDS.put(page.cartridgeId(), page);
        while (BOARDS.size() > CAP) {
            BOARDS.remove(BOARDS.keySet().iterator().next());
        }
    }

    public static @Nullable BoardPagePayload get(String cartridgeId) {
        return cartridgeId == null ? null : BOARDS.get(cartridgeId);
    }

    /**
     * The board as pixel-font rows ("1 ANON" ... "120"): rank, a short upper-case name (or "ANON" for anyone who
     * has not opted in to credit) and the score. Empty when there is no board yet.
     */
    public static List<Line> lines(@Nullable BoardPagePayload page) {
        List<Line> out = new ArrayList<>();
        if (page == null) {
            return out;
        }
        for (BoardPagePayload.Row row : page.rows()) {
            String name = row.name().isEmpty() ? "ANON" : row.name().toUpperCase(Locale.ROOT);
            if (name.length() > NAME_CHARS) {
                name = name.substring(0, NAME_CHARS);
            }
            int score = Math.min(row.score(), 99_999);
            out.add(new Line(row.rank() + " " + name, Integer.toString(score)));
        }
        return out;
    }

    public static void clear() {
        BOARDS.clear();
    }
}
