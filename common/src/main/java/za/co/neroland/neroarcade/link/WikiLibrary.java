package za.co.neroland.neroarcade.link;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import za.co.neroland.neroarcade.NeroArcadeCommon;

/**
 * Reads NeroArcade's public wiki pages from the runtime classpath for the link {@code wiki} section (WIKI CONTRACT
 * v1, copied from NeroSecurity). The in-repo {@code wiki/*.md} pages are bundled under {@code assets/neroarcade/wiki/}
 * by a Gradle copy step in each loader build that also writes a generated {@code index.json} of {@code {slug, title}}
 * entries; the index is the authoritative page list (classpath directory listing is unreliable across loaders).
 *
 * <p>All content here is public and the same for every player. When the generated index is missing (a plain-JVM
 * test) the 007 §2.5 page list is used; only page content needs the bundled resources. Only slugs in the index are
 * ever read (no path traversal); a page is at most {@value #MAX_PAGE_CHARS} characters.
 */
public final class WikiLibrary {

    private static final String BASE = "/assets/neroarcade/wiki/";
    static final int MAX_PAGE_CHARS = 32 * 1024;

    /** One page: its slug (file name without {@code .md}) and display title. */
    public record Page(String slug, String title) {
    }

    /** Resilience fallback only: the 007 §2.5 page list. */
    private static final List<String> FALLBACK_SLUGS = List.of("Home", "getting-started", "modules", "building-a-game",
            "cartridges-and-sharing", "multiplayer-and-link-bus", "leaderboards", "gallery", "commands", "config",
            "privacy");

    private static volatile List<Page> cachedIndex;

    private WikiLibrary() {
    }

    public static List<Page> pages() {
        List<Page> local = cachedIndex;
        if (local == null) {
            synchronized (WikiLibrary.class) {
                local = cachedIndex;
                if (local == null) {
                    local = loadIndex();
                    cachedIndex = local;
                }
            }
        }
        return local;
    }

    public static String titleOf(String slug) {
        for (Page page : pages()) {
            if (page.slug().equals(slug)) {
                return page.title();
            }
        }
        return slug.replace('-', ' ');
    }

    public static boolean has(String slug) {
        if (!isSafeSlug(slug)) {
            return false;
        }
        for (Page page : pages()) {
            if (page.slug().equals(slug)) {
                return true;
            }
        }
        return false;
    }

    public static Optional<String> content(String slug) {
        if (!has(slug)) {
            return Optional.empty();
        }
        return readResource(BASE + slug + ".md").map(text -> text.length() <= MAX_PAGE_CHARS ? text
                : text.substring(0, MAX_PAGE_CHARS));
    }

    private static List<Page> loadIndex() {
        Optional<String> json = readResource(BASE + "index.json");
        if (json.isPresent()) {
            try {
                JsonElement root = JsonParser.parseString(json.get());
                if (root.isJsonObject()) {
                    JsonArray arr = root.getAsJsonObject().getAsJsonArray("pages");
                    if (arr != null) {
                        List<Page> out = new ArrayList<>(arr.size());
                        for (JsonElement el : arr) {
                            JsonObject obj = el.getAsJsonObject();
                            String slug = obj.get("slug").getAsString();
                            if (!isSafeSlug(slug)) {
                                continue;
                            }
                            String title = obj.has("title") && !obj.get("title").isJsonNull()
                                    ? obj.get("title").getAsString() : slug.replace('-', ' ');
                            out.add(new Page(slug, ArcadePushCopy.cap(title, 80)));
                        }
                        if (!out.isEmpty()) {
                            return List.copyOf(out);
                        }
                    }
                }
            } catch (RuntimeException e) {
                NeroArcadeCommon.LOGGER.warn("[NeroArcade] could not parse the bundled wiki index ({}); using the "
                        + "fallback list.", e.getClass().getName());
            }
        }
        List<Page> out = new ArrayList<>(FALLBACK_SLUGS.size());
        for (String slug : FALLBACK_SLUGS) {
            out.add(new Page(slug, slug.replace('-', ' ')));
        }
        return List.copyOf(out);
    }

    private static Optional<String> readResource(String path) {
        try (InputStream in = WikiLibrary.class.getResourceAsStream(path)) {
            if (in == null) {
                return Optional.empty();
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                char[] buf = new char[4096];
                int read;
                while ((read = reader.read(buf)) != -1) {
                    sb.append(buf, 0, read);
                }
            }
            return Optional.of(sb.toString());
        } catch (IOException e) {
            NeroArcadeCommon.LOGGER.warn("[NeroArcade] could not read a bundled wiki page ({}).",
                    e.getClass().getName());
            return Optional.empty();
        }
    }

    /** Slugs are single path segments of a safe charset, at most 128 characters. */
    static boolean isSafeSlug(String slug) {
        if (slug == null || slug.isEmpty() || slug.length() > 128) {
            return false;
        }
        for (int i = 0; i < slug.length(); i++) {
            char c = slug.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_';
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
