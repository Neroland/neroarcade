package za.co.neroland.neroarcade.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.config.NeroArcadeConfig;
import za.co.neroland.neroarcade.game.CartridgeValidator;
import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.PropertyValue.IntValue;
import za.co.neroland.neroarcade.module.SlotEntry;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.runtime.net.Snapshot;
import za.co.neroland.neroarcade.runtime.net.SnapshotCodec;

/** Every budget of 002 section 9 that the runtime itself enforces. */
class BudgetTest {

    @BeforeEach
    void registry() {
        TestModules.install();
    }

    @AfterEach
    void clear() {
        Budgets.clearTestOverrides();
    }

    private static GameDef sized(int side) {
        return TestModules.def(Map.of(ModuleType.ARENA,
                List.of(new SlotEntry(TestModules.id("arena/test_sized"), Map.of("side", new IntValue(side))))));
    }

    @Test
    void arenaSide() throws ValidationError {
        assertEquals(ValidationError.Code.OVER_BUDGET,
                assertThrows(ValidationError.class, () -> CartridgeValidator.validate(sized(33))).code);
        CartridgeValidator.validate(sized(32));
        Budgets.overrideForTests(Budgets.Key.MAX_ARENA_SIDE, 16);
        assertEquals(ValidationError.Code.OVER_BUDGET,
                assertThrows(ValidationError.class, () -> CartridgeValidator.validate(sized(17))).code);
        CartridgeValidator.validate(sized(16));
    }

    @Test
    void entities() {
        MatchState s = new MatchState(16, 16, Budgets.maxEntities(), 1);
        int[] array = s.entities;
        for (int i = 0; i < 256; i++) {
            assertTrue(s.spawn(EntityKind.MARKER, 0, 0, -1, 0) >= 0);
        }
        assertEquals(-1, s.spawn(EntityKind.MARKER, 0, 0, -1, 0));
        assertEquals(256, s.entityCount());
        assertSame(array, s.entities);
        for (int i = 1; i < s.entityCount(); i++) {
            assertTrue(s.entityIdAt(i - 1) < s.entityIdAt(i));
        }
    }

    /** 32x32 alternating cells (RLE would be longer than raw), 256 entities, four seats. */
    static MatchState worstCase() {
        MatchState s = new MatchState(32, 32, 256, 4);
        for (int i = 0; i < 32 * 32; i++) {
            s.cells[i] = (byte) (i % 2);
        }
        for (int i = 0; i < 256; i++) {
            int id = s.spawn(EntityKind.HAZARD, (i % 32) << 8, (i / 32) << 8, -1, i & 3);
            s.set(id, Entities.VX, 64);
        }
        return s;
    }

    @Test
    void keyframeFitsCap() {
        int size = SnapshotCodec.writeKeyframe(worstCase()).length;
        assertEquals(Budgets.WORST_CASE_KEYFRAME_BYTES, size, "worst-case keyframe bytes");
        assertEquals(6239, size);
        assertTrue(size <= 6400, "the runtime.snapshotMaxBytes floor (6400) must hold a keyframe");
        assertTrue(size <= NeroArcadeConfig.SNAPSHOT_MAX_BYTES.defaultValue());
    }

    @Test
    void deltaFallsBackToKeyframe() {
        MatchState base = worstCase();
        MatchState current = base.copy();
        current.tick = base.tick + 2;
        for (int i = 0; i < current.cells.length; i++) {
            current.cells[i] = (byte) (2 + (i % 2));
        }
        for (int i = 0; i < current.entityCount(); i++) {
            int id = current.entityIdAt(i);
            current.set(id, Entities.X, current.get(id, Entities.X) + 1);
        }
        assertTrue(current.deltaCompatibleWith(base));
        Snapshot snapshot = SnapshotCodec.encode(base, current, false, Budgets.snapshotMaxBytes());
        assertTrue(snapshot.keyframe(), "a delta larger than the keyframe must be replaced by it");
        MatchState decoded = SnapshotCodec.decode(snapshot.body(), base).orElseThrow();
        assertEquals(current.stateHash(), decoded.stateHash());
        Snapshot small = SnapshotCodec.encode(base, base.copy(), false, Budgets.snapshotMaxBytes());
        assertTrue(!small.keyframe());
    }

    @Test
    void oversizeDeltaFallsBackEvenWhenSmallerThanTheKeyframe() {
        MatchState base = worstCase();
        MatchState current = base.copy();
        current.tick = base.tick + 2;
        for (int i = 0; i < 100; i++) {
            int id = current.entityIdAt(i);
            current.set(id, Entities.Y, current.get(id, Entities.Y) + 1);
        }
        assertTrue(!SnapshotCodec.encode(base, current, false, 8192).keyframe());
        assertTrue(SnapshotCodec.encode(base, current, false, 100).keyframe());
    }

    @Test
    void snapshotSkippedWhenIdle() {
        MatchState s = MatchStateTest.sample();
        SnapshotCodec.Stream stream = new SnapshotCodec.Stream(40, 8192);
        Optional<Snapshot> first = stream.next(s);
        assertTrue(first.isPresent());
        assertTrue(stream.next(s).isEmpty());
        s.rngState++;
        assertTrue(stream.next(s).isEmpty(), "an RNG-only change is invisible to viewers");
        s.addScore(0, 1);
        assertTrue(stream.next(s).isPresent());
    }

    @Test
    void configKeysAreServerAuthoritativeWithTheDocumentedDefaults() {
        assertEquals(32, NeroArcadeConfig.MAX_ARENA_SIDE.defaultValue());
        assertEquals(256, NeroArcadeConfig.MAX_ENTITIES.defaultValue());
        assertEquals(30, NeroArcadeConfig.MAX_INPUT_PACKETS_PER_SECOND.defaultValue());
        assertEquals(8192, NeroArcadeConfig.SNAPSHOT_MAX_BYTES.defaultValue());
        assertEquals(10, NeroArcadeConfig.SNAPSHOT_HZ.defaultValue());
        assertEquals(40, NeroArcadeConfig.KEYFRAME_TICKS.defaultValue());
        assertEquals(32, NeroArcadeConfig.MAX_AWAKE_CABINETS.defaultValue());
        assertEquals(100, NeroArcadeConfig.SLEEP_AFTER_TICKS.defaultValue());
        assertEquals(32, NeroArcadeConfig.SCREEN_RENDER_RANGE.defaultValue());
        assertEquals(600, NeroArcadeConfig.MAX_MATCH_SECONDS.defaultValue());
        assertEquals(false, NeroArcadeConfig.CABINETS_NEED_POWER.defaultValue());
        assertEquals(2, NeroArcadeConfig.CABINET_POWER_PER_TICK.defaultValue());
        for (var value : NeroArcadeConfig.SCHEMA.values()) {
            if (value.key().equals("telemetryEnabled") || value.key().startsWith("client.")) {
                // this computer's choice only: never forced by a server (privacy opt-out, screen look)
                assertTrue(!value.serverAuthoritative(), value.key());
            } else {
                assertTrue(value.serverAuthoritative(), value.key());
            }
        }
    }
}
