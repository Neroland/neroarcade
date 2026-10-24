package za.co.neroland.neroarcade.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroarcade.NeroArcadeCommon;

/**
 * The example cartridges' attract runs, shipped as {@link Replay} JSON resources in
 * {@code data/neroarcade/arcade_replays/<slug>.json} (008b Q8). Each file is the cartridge itself (no author; the
 * credit "Gallery"), its fixed seed, the seat count and the input log of the run the test-side pseudo-player made;
 * {@code ShippedReplaysTest} replays every file to the hash pinned for {@code <slug>@<attract variant>}. The gallery
 * builds its cabinets and seeds its Exchange from these files, and the client's attract loop plays them back — the
 * pseudo-player never ships, the replay <i>is</i> the attract run.
 *
 * <p>Read from the mod jar with {@link Class#getResourceAsStream} (the path is fixed, so a datapack cannot swap
 * them; the server and the client read the same bytes). Loaded once, lazily; a missing or unreadable file is
 * skipped with one warning. Pure apart from the classpath read.
 */
public final class ShippedReplays {

    /** Folder inside the jar. */
    public static final String ROOT = "/data/neroarcade/arcade_replays/";
    /** The credit every gallery cartridge carries. */
    public static final String CREDIT = "Gallery";

    /** The sixteen example cartridges in 008 §4 order (#1 … #16). */
    public static final List<String> SLUGS = List.of("paddle_duel", "serpent_garden", "stack_nine", "drone_maze",
            "lane_hopper", "hot_seat_orbs", "tandem_sweep", "vault_run", "gunner_ring", "shrink_island",
            "mirror_pulse", "float_lane", "four_way_rally", "crate_catch", "blind_tube", "stack_duel");

    private static volatile @Nullable Map<String, Replay> cache;

    private ShippedReplays() {
    }

    /** Every shipped replay, slug → replay, in {@link #SLUGS} order (missing files left out). */
    public static Map<String, Replay> all() {
        Map<String, Replay> current = cache;
        if (current == null) {
            Map<String, Replay> loaded = new LinkedHashMap<>();
            for (String slug : SLUGS) {
                Replay replay = read(slug);
                if (replay != null) {
                    loaded.put(slug, replay);
                }
            }
            current = Collections.unmodifiableMap(loaded);
            cache = current;
        }
        return current;
    }

    public static Optional<Replay> bySlug(String slug) {
        return Optional.ofNullable(all().get(slug));
    }

    /** The shipped replay whose cartridge has this id (gallery cabinets, the attract loop). */
    public static Optional<Replay> forCartridge(String cartridgeId) {
        if (cartridgeId == null || cartridgeId.isEmpty()) {
            return Optional.empty();
        }
        for (Replay replay : all().values()) {
            if (replay.gameDef().id().equals(cartridgeId)) {
                return Optional.of(replay);
            }
        }
        return Optional.empty();
    }

    /** Parses one replay file's text; empty on any error. */
    public static Optional<Replay> parse(String json) {
        try {
            JsonElement element = JsonParser.parseString(json);
            return Replay.CODEC.parse(JsonOps.INSTANCE, element).result();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static @Nullable Replay read(String slug) {
        String path = ROOT + slug + ".json";
        try (InputStream in = ShippedReplays.class.getResourceAsStream(path)) {
            if (in == null) {
                NeroArcadeCommon.LOGGER.warn("[NeroArcade] shipped replay {} is missing", path);
                return null;
            }
            Optional<Replay> replay = parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            if (replay.isEmpty()) {
                NeroArcadeCommon.LOGGER.warn("[NeroArcade] shipped replay {} could not be read", path);
            }
            return replay.orElse(null);
        } catch (IOException e) {
            NeroArcadeCommon.LOGGER.warn("[NeroArcade] shipped replay {} could not be read ({})", path,
                    e.getClass().getSimpleName());
            return null;
        }
    }
}
