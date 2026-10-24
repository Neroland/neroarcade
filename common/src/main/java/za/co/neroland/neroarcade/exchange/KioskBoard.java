package za.co.neroland.neroarcade.exchange;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * What an Arcade Exchange kiosk's listing screen scrolls (004 §4.5): the top {@value #ROWS} listings in browse
 * order, as a title and a heart count each. That is all a client is sent — never an author, a credit, a play count
 * or a cartridge. Pure.
 */
public final class KioskBoard {

    /** Rows a kiosk is sent (the screen rolls through them). */
    public static final int ROWS = 8;
    /** Characters of a title the 64-px screen can show beside its heart count. */
    public static final int TITLE_CHARS = 6;
    /** The heart count shown (three digits fit beside a title on the 10-character line). */
    public static final int MAX_HEARTS = 999;

    public record Line(String title, int hearts) {

        public static final Codec<Line> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("t").forGetter(Line::title),
                Codec.INT.fieldOf("h").forGetter(Line::hearts)
        ).apply(i, Line::new));
    }

    public static final Codec<List<Line>> LIST_CODEC = Line.CODEC.listOf();

    private KioskBoard() {
    }

    /** The kiosk rows for {@code browse} (already in browse order). */
    public static List<Line> of(List<Listing> browse) {
        List<Line> out = new ArrayList<>();
        for (Listing listing : browse) {
            if (out.size() >= ROWS) {
                break;
            }
            out.add(new Line(screenTitle(listing.title()), Math.max(0, Math.min(MAX_HEARTS, listing.hearts()))));
        }
        return List.copyOf(out);
    }

    /** Upper case, the bitmap font's characters only, at most {@value #TITLE_CHARS} long. */
    public static String screenTitle(String title) {
        String upper = title == null ? "" : title.strip().toUpperCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < upper.length() && out.length() < TITLE_CHARS; i++) {
            char c = upper.charAt(i);
            out.append(c >= 32 && c <= 95 ? c : '?');
        }
        return out.toString().strip();
    }
}
