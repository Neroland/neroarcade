package za.co.neroland.neroarcade.bench;

import java.util.Optional;

/**
 * The bench title rule before the chat filter: control and format characters stripped, trimmed, 1..32
 * characters. Pure. The vanilla chat filter ({@code ServerPlayer.getTextFilter().processStreamMessage}) runs
 * after this, in {@link BenchActions}.
 */
public final class TitleRules {

    public static final int MAX_LENGTH = 32;

    private TitleRules() {
    }

    /** The cleaned title, or empty if nothing is left or it is longer than {@link #MAX_LENGTH}. */
    public static Optional<String> clean(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        StringBuilder out = new StringBuilder();
        raw.codePoints().filter(c -> {
            int type = Character.getType(c);
            return type != Character.CONTROL && type != Character.FORMAT && type != Character.LINE_SEPARATOR
                    && type != Character.PARAGRAPH_SEPARATOR && type != Character.UNASSIGNED
                    && type != Character.PRIVATE_USE && type != Character.SURROGATE;
        }).forEach(out::appendCodePoint);
        String cleaned = out.toString().strip();
        int length = cleaned.codePointCount(0, cleaned.length());
        if (length < 1 || length > MAX_LENGTH) {
            return Optional.empty();
        }
        return Optional.of(cleaned);
    }
}
