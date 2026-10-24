package za.co.neroland.neroarcade.link;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroarcade.game.CartridgeIds;

/**
 * <b>The only builder of phone-notification text, stored-alert text and cabinet labels</b> in NeroArcade (007 §5).
 *
 * <p>Notification text is shown on a locked phone and passes through the NeroLink relay and the platform push
 * services (third-party processors). It therefore carries a fixed English phrase, the cartridge <b>title</b>
 * (already filtered and at most 32 characters at save/publish, cleaned again here) and integers, plus a deep link
 * that names only the cartridge id. Every input of every method here is a title, a rank, a count, a
 * label part or a cartridge id: no method can receive a player, a player id, a name, a position or a level, so none
 * of those can reach a notification. A {@code RECORD_BEATEN} notification names the cartridge and the new rank only
 * — never who beat the record and never where (no cabinet label, D5).
 *
 * <p>Phrases are the English strings of the bundled {@code en_us.json} (a dedicated server has no client language),
 * read once. Caps match the bridge's {@code NotifyCopy}: title 120, body 240, deep link 256 characters.
 */
public final class ArcadePushCopy {

    /** The reserved payload member the bridge reads. */
    public static final String FIELD = "_notify";
    public static final int TITLE_MAX = 120;
    public static final int BODY_MAX = 240;
    public static final int LINK_MAX = 256;
    /** Cartridge titles are at most 32 characters (the bench and the validator enforce it). */
    public static final int CARTRIDGE_TITLE_MAX = 32;
    /** The bridge substitutes its relay id here. */
    public static final String SERVER_PLACEHOLDER = "{server}";
    /** The app module segment of the deep link. */
    public static final String APP_MODULE = "arcade";
    /** The bridge's {@code NotifyCopy.DEEP_LINK} (checked here so a bad link never leaves the mod). */
    public static final Pattern DEEP_LINK = Pattern.compile(
            "^nerolink://server/[A-Za-z0-9_-]{1,64}/[a-z0-9_-]{1,32}(/[A-Za-z0-9_-]{1,64}){0,2}$");

    static final String KEY_RECORD_TITLE = "link.neroarcade.push.record.title";
    static final String KEY_RECORD = "link.neroarcade.push.record";
    static final String KEY_RECORD_OFF = "link.neroarcade.push.record.off";
    static final String KEY_DOWNLOADED_TITLE = "link.neroarcade.push.downloaded.title";
    static final String KEY_DOWNLOADED = "link.neroarcade.push.downloaded";
    static final String KEY_DOWNLOADED_ONE = "link.neroarcade.push.downloaded.one";
    static final String KEY_MORE = "link.neroarcade.push.more";
    static final String KEY_FALLBACK_TITLE = "link.neroarcade.title.fallback";
    static final String KEY_LABEL_SOLO = "link.neroarcade.label.solo";
    static final String KEY_LABEL_HALL = "link.neroarcade.label.hall";

    private static final String LANG = "/assets/neroarcade/lang/en_us.json";

    private static volatile Map<String, String> english;

    private ArcadePushCopy() {
    }

    // --- notifications ----------------------------------------------------------------------------

    /** {@code _notify} for {@code RECORD_BEATEN}: "Record beaten" / "Your record on <title> was beaten — now #7". */
    public static JsonObject recordNotify(String title, @Nullable Integer newRank, String cartridgeId) {
        return notify(english(KEY_RECORD_TITLE, "Record beaten"), recordBody(title, newRank), cartridgeId);
    }

    /** The persisted alert's text for {@code RECORD_BEATEN} (same sentence as the notification body). */
    public static String recordAlertText(String title, @Nullable Integer newRank) {
        return cap(recordBody(title, newRank), BODY_MAX);
    }

    /** {@code _notify} for {@code CARTRIDGE_DOWNLOADED}: "<title> was downloaded 4 times today" (+ "(+n more)"). */
    public static JsonObject downloadNotify(String title, int count, int more, String cartridgeId) {
        String body = count == 1
                ? format(english(KEY_DOWNLOADED_ONE, "%s was downloaded once today"), safeTitle(title))
                : format(english(KEY_DOWNLOADED, "%s was downloaded %s times today"), safeTitle(title),
                        Integer.toString(Math.max(0, count)));
        if (more > 0) {
            body += format(english(KEY_MORE, " (+%s more)"), Integer.toString(more));
        }
        return notify(english(KEY_DOWNLOADED_TITLE, "Cartridge downloaded"), body, cartridgeId);
    }

    /** {@code nerolink://server/{server}/arcade/<cartridgeId>}; null for anything that is not a cartridge id. */
    public static @Nullable String deepLink(String cartridgeId) {
        if (!CartridgeIds.isValid(cartridgeId)) {
            return null;
        }
        String link = "nerolink://server/" + SERVER_PLACEHOLDER + "/" + APP_MODULE + "/" + cartridgeId;
        return link.length() <= LINK_MAX ? link : null;
    }

    /** The persisted alert id: one record alert per board, replaced on each fall. */
    public static String recordAlertId(String cartridgeId) {
        return NeroArcadeLinkModule.MODULE_ID + "." + cartridgeId + ".record";
    }

    private static String recordBody(String title, @Nullable Integer newRank) {
        if (newRank == null) {
            return format(english(KEY_RECORD_OFF, "Your record on %s was beaten — you are off the top 10"),
                    safeTitle(title));
        }
        return format(english(KEY_RECORD, "Your record on %s was beaten — now #%s"), safeTitle(title),
                Integer.toString(newRank));
    }

    private static JsonObject notify(String title, String body, String cartridgeId) {
        JsonObject out = new JsonObject();
        out.addProperty("title", cap(title, TITLE_MAX));
        out.addProperty("body", cap(body, BODY_MAX));
        String link = deepLink(cartridgeId);
        if (link != null) {
            out.addProperty("deepLink", link);
        }
        return out;
    }

    // --- labels -----------------------------------------------------------------------------------

    /**
     * A cabinet's display label (D6): {@code "<dimension> · Solo cabinet"} or {@code "<dimension> · Linked hall (N
     * cabinets)"}. The dimension's display name and a count: never a coordinate.
     */
    public static String cabinetLabel(String dimensionId, int cabinets) {
        String dimension = dimensionName(dimensionId);
        if (cabinets <= 1) {
            return cap(format(english(KEY_LABEL_SOLO, "%s · Solo cabinet"), dimension), TITLE_MAX);
        }
        return cap(format(english(KEY_LABEL_HALL, "%s · Linked hall (%s cabinets)"), dimension,
                Integer.toString(cabinets)), TITLE_MAX);
    }

    /** {@code minecraft:overworld} → "Overworld"; a modded {@code ns:deep_space} → "Deep Space". */
    public static String dimensionName(String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) {
            return "Unknown";
        }
        switch (dimensionId) {
            case "minecraft:overworld" -> {
                return "Overworld";
            }
            case "minecraft:the_nether" -> {
                return "The Nether";
            }
            case "minecraft:the_end" -> {
                return "The End";
            }
            default -> {
            }
        }
        int colon = dimensionId.indexOf(':');
        String path = colon >= 0 ? dimensionId.substring(colon + 1) : dimensionId;
        StringBuilder out = new StringBuilder();
        for (String word : path.toLowerCase(Locale.ROOT).split("[_/.\\-]+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        String cleaned = clean(out.toString());
        return cleaned.isEmpty() ? "Unknown" : cap(cleaned, 48);
    }

    // --- cleaning ---------------------------------------------------------------------------------

    /**
     * A stored string as it may reach a phone: control and format characters removed, trimmed. Applied to every
     * string a section, event or notification carries even though the stores already filtered it.
     */
    public static String clean(@Nullable String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length());
        text.codePoints().filter(cp -> {
            int type = Character.getType(cp);
            return cp >= 0x20 && cp != 0x7F && type != Character.CONTROL && type != Character.FORMAT
                    && type != Character.LINE_SEPARATOR && type != Character.PARAGRAPH_SEPARATOR;
        }).forEach(out::appendCodePoint);
        return out.toString().trim();
    }

    /** A cartridge title, cleaned and cut to 32; the fallback "a cartridge" when nothing is left. */
    public static String safeTitle(@Nullable String title) {
        String cleaned = cap(clean(title), CARTRIDGE_TITLE_MAX);
        return cleaned.isEmpty() ? english(KEY_FALLBACK_TITLE, "a cartridge") : cleaned;
    }

    /** {@link #clean} then cut to {@code max} without splitting a surrogate pair. */
    static String cap(String text, int max) {
        String clean = clean(text);
        if (clean.length() <= max) {
            return clean;
        }
        int end = max;
        if (Character.isHighSurrogate(clean.charAt(end - 1))) {
            end--;
        }
        return clean.substring(0, end).trim();
    }

    private static String format(String pattern, String... args) {
        try {
            return String.format(Locale.ROOT, pattern, (Object[]) args);
        } catch (RuntimeException badPattern) {
            return String.join(" ", args);
        }
    }

    // --- phrases ----------------------------------------------------------------------------------

    /** The English string of {@code key} from the bundled language file, else {@code fallback}. */
    static String english(String key, String fallback) {
        String value = table().get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Map<String, String> table() {
        Map<String, String> local = english;
        if (local == null) {
            synchronized (ArcadePushCopy.class) {
                local = english;
                if (local == null) {
                    local = load();
                    english = local;
                }
            }
        }
        return local;
    }

    private static Map<String, String> load() {
        Map<String, String> out = new HashMap<>();
        try (InputStream in = ArcadePushCopy.class.getResourceAsStream(LANG)) {
            if (in == null) {
                return Map.of();
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonElement root = JsonParser.parseReader(reader);
                if (root.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().entrySet()) {
                        if (e.getKey().startsWith("link.neroarcade.") && e.getValue().isJsonPrimitive()) {
                            out.put(e.getKey(), e.getValue().getAsString());
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            return Map.of();
        }
        return Map.copyOf(out);
    }
}
