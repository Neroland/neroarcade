package za.co.neroland.neroarcade.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.mojang.serialization.JsonOps;

import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.TestFiles;
import za.co.neroland.neroarcade.game.CartridgeValidator;
import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.module.TestModules;

/**
 * Determinism: the same {@code (GameDef, seed, inputLog)} always yields the same state hash, checked every 40
 * ticks; a different seed or inputs swapped between seats diverge; final hashes are pinned in
 * {@code cartridges/hashes.txt} so every loader x Minecraft cell must agree with every other.
 */
class ReplayTest {

    static final long SEED = 0x5EEDL;
    static final int TICKS = 1200;

    @BeforeEach
    void registry() {
        TestModules.install();
    }

    private static int seatsFor(String name) {
        return name.startsWith("coop") ? 2 : 1;
    }

    private record Run(HeadlessMatch match, long[] every40) {
    }

    private static Run run(GameDef def, int seats, long seed) {
        HeadlessMatch match = HeadlessMatch.of(def, seats, seed);
        HeadlessMatch.InputScript script = HeadlessMatch.InputScript.randomWalk();
        long[] every40 = new long[TICKS / 40];
        for (int k = 0; k < every40.length; k++) {
            match.run(40, script);
            every40[k] = match.hash();
        }
        return new Run(match, every40);
    }

    private static long[] replay(GameDef def, int seats, long seed, InputLog log, int ticks) {
        HeadlessMatch match = HeadlessMatch.of(def, seats, seed);
        SeatInput[] carry = new SeatInput[Budgets.MAX_SEATS];
        Arrays.fill(carry, SeatInput.NONE);
        long[] every40 = new long[ticks / 40];
        for (int t = 0; t < ticks; t++) {
            match.step(log.inputsAt(t, carry).clone());
            if ((t + 1) % 40 == 0) {
                every40[t / 40] = match.hash();
            }
        }
        return every40;
    }

    private static Map<String, Long> pinned() {
        Map<String, Long> out = new TreeMap<>();
        Path file = TestFiles.testResources().resolve("cartridges/hashes.txt");
        if (Files.exists(file)) {
            for (String line : TestFiles.read(file).lines().map(String::strip).toList()) {
                // "<name>@<variant>" lines are ExampleCartridgesTest's scripted runs
                if (!line.isEmpty() && !line.startsWith("#") && !line.contains("@")) {
                    String[] parts = line.split("=");
                    out.put(parts[0].strip(), Long.parseUnsignedLong(parts[1].strip().replace("0x", ""), 16));
                }
            }
        }
        return out;
    }

    @Test
    void replaysAreBitExactAndPinned() throws Exception {
        Map<String, Long> pinned = pinned();
        List<String> actual = new ArrayList<>();
        int scored = 0;
        int hits = 0;
        for (Path file : TestFiles.cartridges()) {
            String name = file.getFileName().toString().replace(".json", "");
            GameDef def = CartridgeValidator.decodeJson(TestFiles.read(file), Budgets.MAX_IMPORT_JSON_BYTES);
            int seats = seatsFor(name);

            // (a) scripted run
            Run first = run(def, seats, SEED);
            Replay replay = first.match().toReplay();
            assertEquals(TICKS, replay.finalTick());
            MatchState end = first.match().state();
            MatchState begin = HeadlessMatch.of(def, seats, SEED).state();
            for (int s = 0; s < end.seatCount(); s++) {
                scored += end.score(s);
                hits += begin.lives(s) - end.lives(s);
            }

            // (b) replay on a fresh runtime: equal every 40 ticks and at the end
            long[] again = replay(def, seats, SEED, replay.inputLog(), TICKS);
            assertTrue(Arrays.equals(first.every40(), again), name + ": replay diverged");
            replay.play();

            // (c) the same seed yields a byte-identical input log
            Run second = run(def, seats, SEED);
            assertEquals(first.match().log(), second.match().log(), name);
            Tag logA = InputLog.CODEC.encodeStart(NbtOps.INSTANCE, first.match().log()).getOrThrow();
            Tag logB = InputLog.CODEC.encodeStart(NbtOps.INSTANCE, second.match().log()).getOrThrow();
            assertEquals(logA, logB, name);

            // the replay itself survives its codec
            var json = Replay.CODEC.encodeStart(JsonOps.INSTANCE, replay).getOrThrow();
            Replay decoded = Replay.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
            assertEquals(replay.finalHash(), decoded.run().hash());

            // a different seed diverges
            long other = replay(def, seats, SEED + 1, replay.inputLog(), TICKS)[TICKS / 40 - 1];
            assertNotEquals(replay.finalHash(), other, name + ": seed has no effect");

            actual.add(name + "=" + String.format("%016x", replay.finalHash()));
            Long expected = pinned.get(name);
            if (expected != null) {
                assertEquals(expected.longValue(), replay.finalHash(), name + " final hash changed; actual: " + actual);
            }
        }
        assertEquals(TestFiles.cartridges().size(), pinned.size(), "pin these in cartridges/hashes.txt: " + actual);
        assertTrue(scored > 0, "the example runs must exercise pickups");
        assertTrue(hits > 0, "the example runs must exercise hazard hits");
    }

    @Test
    void inputsShuffledBetweenSeatsDiverge() throws Exception {
        GameDef def = CartridgeValidator.decodeJson(TestFiles.cartridge("coop_mirror.json"), Budgets.MAX_IMPORT_JSON_BYTES);
        Replay replay = run(def, 2, SEED).match().toReplay();
        InputLog swapped = new InputLog();
        SeatInput[] previous = {SeatInput.NONE, SeatInput.NONE};
        SeatInput[] carry = new SeatInput[Budgets.MAX_SEATS];
        Arrays.fill(carry, SeatInput.NONE);
        for (int t = 0; t < TICKS; t++) {
            SeatInput[] row = replay.inputLog().inputsAt(t, carry);
            SeatInput[] swap = {row[1], row[0]};
            for (int s = 0; s < 2; s++) {
                swapped.record(t, s, swap[s], previous[s]);
                previous[s] = swap[s];
            }
        }
        long shuffled = replay(def, 2, SEED, swapped, TICKS)[TICKS / 40 - 1];
        assertNotEquals(replay.finalHash(), shuffled);
    }
}
