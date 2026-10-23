package za.co.neroland.neroarcade.exchange;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.ArcadeModule;
import za.co.neroland.neroarcade.module.ModuleType;

/**
 * The genre words an Arcade Exchange listing shows (003 §8.1), derived once at publish time from the chips the
 * game resolves to: each chip's own {@link ArcadeModule#genreTag()}, in slot order (an empty slot contributes its
 * server default's word), duplicates dropped, at most {@link #MAX_TAGS}. A word is a lang-key suffix
 * ({@code neroarcade.tag.<word>}); this class never looks at another mod's ids. Pure.
 */
public final class GenreTags {

    public static final int MAX_TAGS = 6;

    /** A tag word: lowercase letters, 1..16. */
    public static final Pattern WORD = Pattern.compile("[a-z]{1,16}");

    private GenreTags() {
    }

    public static List<String> of(ResolvedGame game) {
        List<String> out = new ArrayList<>();
        for (ModuleType type : ModuleType.values()) {
            for (ArcadeModule module : game.modules(type)) {
                add(out, module.genreTag());
            }
        }
        return List.copyOf(out);
    }

    /** Adds a word if it is well formed, new and there is room. */
    static void add(List<String> tags, String word) {
        if (word != null && tags.size() < MAX_TAGS && WORD.matcher(word).matches() && !tags.contains(word)) {
            tags.add(word);
        }
    }

    /** The lang key of a tag word. */
    public static String langKey(String word) {
        return "neroarcade.tag." + word;
    }
}
