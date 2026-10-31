package za.co.neroland.neroarcade.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.TestFiles;
import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.module.TestModules;

/**
 * 008b Q8: every example cartridge's attract run ships as a {@link Replay} JSON resource in
 * {@code data/neroarcade/arcade_replays/<slug>.json}, and each shipped file replays to the hash pinned in
 * {@code cartridges/hashes.txt} for {@code <slug>@<attract variant>}.
 *
 * <p>The generator is this test: it re-runs each cartridge's attract variant from {@link ExampleCartridgesTest},
 * strips the fixture author (the gallery cartridge carries only the credit {@value ShippedReplays#CREDIT}) and
 * writes the canonical text to {@code build/arcade_replays/}. The committed files must equal that text; after a
 * deliberate re-pin, copy {@code build/arcade_replays/*.json} over the shipped ones.
 */
class ShippedReplaysTest {

    @BeforeEach
    void registry() {
        TestModules.install();
    }

    static Path shipped() {
        return TestFiles.repoRoot().resolve("common/src/main/resources" + ShippedReplays.ROOT);
    }

    static Path generated() {
        return TestFiles.repoRoot().resolve("build/arcade_replays");
    }

    /** The fixture cartridge as the gallery ships it: no author UUID, credit "Gallery". */
    static GameDef galleryDef(GameDef def) {
        return new GameDef(def.schemaVersion(), def.id(), def.title(), Optional.empty(),
                Optional.of(ShippedReplays.CREDIT), def.lineage(), def.seed(), def.slots(), def.createdAt(),
                def.revision());
    }

    /** Canonical text: two-space JSON, one input-log triple per line, LF, trailing newline. */
    static String text(Replay replay) {
        JsonElement encoded = Replay.CODEC.encodeStart(JsonOps.INSTANCE, replay).getOrThrow();
        // Field order as the codecs declare it (26.x writes that order; the 1.21.x DataFixerUpper does not).
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        for (String key : List.of("game_def", "seed", "seats", "input_log", "final_hash", "final_tick")) {
            JsonElement value = encoded.getAsJsonObject().get(key);
            json.add(key, key.equals("game_def")
                    ? za.co.neroland.neroarcade.game.GameDefCodecs.contractOrder(value) : value);
        }
        String pretty = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(json);
        String collapsed = pretty.replaceAll(
                "\\[\\s*\\n\\s*(-?\\d+),\\s*\\n\\s*(-?\\d+),\\s*\\n\\s*(-?\\d+)\\s*\\n\\s*]", "[$1, $2, $3]");
        return collapsed.replace("\r\n", "\n") + "\n";
    }

    static Replay attractReplay(ExampleCartridgesTest.Cart cart) throws Exception {
        ExampleCartridgesTest.Variant variant = cart.variants().stream()
                .filter(v -> v.key().equals(cart.attract())).findFirst().orElseThrow();
        Replay run = ExampleCartridgesTest.play(cart, variant).replay();
        return new Replay(galleryDef(run.gameDef()), run.seed(), run.seats(), run.inputLog(), run.finalHash(),
                run.finalTick());
    }

    @Test
    void theSlugsAreTheSixteenExamplesInOrder() {
        List<String> names = ExampleCartridgesTest.carts().stream().map(ExampleCartridgesTest.Cart::name).toList();
        assertEquals(names, ShippedReplays.SLUGS);
    }

    @Test
    void everyShippedReplayIsTheAttractRunAndReplaysToItsPin() throws Exception {
        Map<String, Long> pinned = ExampleCartridgesTest.pinned();
        Files.createDirectories(generated());
        List<String> stale = new ArrayList<>();
        for (ExampleCartridgesTest.Cart cart : ExampleCartridgesTest.carts()) {
            String key = cart.name() + "@" + cart.attract();
            Replay fresh = attractReplay(cart);
            String canonical = text(fresh);
            write(generated().resolve(cart.name() + ".json"), canonical);
            Long pin = pinned.get(key);
            assertEquals(pin, Long.valueOf(fresh.finalHash()), key + ": the gallery def moved the hash");
            // the author never reaches the simulation: the stripped def replays to the same pin
            assertEquals(fresh.finalHash(), fresh.run().hash(), key);

            Path file = shipped().resolve(cart.name() + ".json");
            if (!Files.isRegularFile(file)) {
                stale.add(cart.name() + " (missing)");
                continue;
            }
            String committed = TestFiles.read(file).replace("\r\n", "\n");
            if (!committed.equals(canonical)) {
                stale.add(cart.name() + " (differs)");
            }
            Replay loaded = ShippedReplays.parse(committed).orElseThrow(() -> new AssertionError(file + " unreadable"));
            assertEquals(pin, Long.valueOf(loaded.finalHash()), key);
            assertEquals(pin, Long.valueOf(loaded.run().hash()), key + ": the shipped file replays to its pin");
            assertTrue(loaded.run().result().isPresent(), key + ": the attract run ends with an outcome");
            assertTrue(loaded.gameDef().authorUuid().isEmpty(), key + ": no author UUID ships");
            assertEquals(Optional.of(ShippedReplays.CREDIT), loaded.gameDef().authorCredit(), key);
            assertTrue(loaded.seats() >= 1 && loaded.seats() <= Budgets.MAX_SEATS, key);
        }
        assertTrue(stale.isEmpty(), "copy build/arcade_replays/*.json to " + shipped() + ": " + stale);
    }

    @Test
    void theJarCopiesLoadThroughTheClasspath() {
        // the test classpath carries main resources, exactly as the mod jar does
        assertEquals(ShippedReplays.SLUGS, List.copyOf(ShippedReplays.all().keySet()));
        for (Replay replay : ShippedReplays.all().values()) {
            assertEquals(Optional.of(replay), ShippedReplays.forCartridge(replay.gameDef().id()));
        }
    }

    private static void write(Path file, String text) {
        try {
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
