package za.co.neroland.neroarcade.runtime.net;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.TestFiles;
import za.co.neroland.neroarcade.game.CartridgeValidator;
import za.co.neroland.neroarcade.module.TestModules;
import za.co.neroland.neroarcade.runtime.Budgets;
import za.co.neroland.neroarcade.runtime.HeadlessMatch;
import za.co.neroland.neroarcade.runtime.MatchState;
import za.co.neroland.neroarcade.runtime.SeatInput;
import za.co.neroland.neroarcade.runtime.StateHash;

class SnapshotCodecTest {

    @BeforeEach
    void registry() {
        TestModules.install();
    }

    private static HeadlessMatch busyMatch() throws Exception {
        return HeadlessMatch.of(CartridgeValidator.decodeJson(TestFiles.cartridge("coop_mirror.json"),
                Budgets.MAX_IMPORT_JSON_BYTES), 2, 11L);
    }

    @Test
    void keyframeRoundTrip() throws Exception {
        HeadlessMatch match = busyMatch().run(300, HeadlessMatch.InputScript.randomWalk());
        byte[] bytes = SnapshotCodec.writeKeyframe(match.state());
        MatchState decoded = MatchState.readKeyframe(ByteBuffer.wrap(bytes), Budgets.HARD_MAX_ENTITIES);
        assertArrayEquals(bytes, SnapshotCodec.writeKeyframe(decoded));
        assertEquals(match.hash(), decoded.stateHash());
    }

    @Test
    void deltaAppliedToPreviousEqualsCurrent() throws Exception {
        HeadlessMatch match = busyMatch().run(200, HeadlessMatch.InputScript.randomWalk());
        int deltas = 0;
        for (int i = 0; i < 40; i++) {
            MatchState before = match.state().copy();
            match.run(2, HeadlessMatch.InputScript.randomWalk());
            MatchState after = match.state();
            if (!after.deltaCompatibleWith(before)) {
                continue;
            }
            MatchState applied = before.applyDelta(ByteBuffer.wrap(SnapshotCodec.writeDelta(before, after)));
            assertEquals(StateHash.ofView(after, false), StateHash.ofView(applied, false));
            deltas++;
        }
        assertTrue(deltas > 10, "expected most sends to be deltas, got " + deltas);
    }

    @Test
    void chainOfFiftySendsReconstructsTheServerView() throws Exception {
        HeadlessMatch match = busyMatch();
        SnapshotCodec.Stream stream = new SnapshotCodec.Stream(Budgets.keyframeTicks(), Budgets.snapshotMaxBytes());
        HeadlessMatch.InputScript script = HeadlessMatch.InputScript.randomWalk();
        MatchState client = null;
        int sends = 0;
        int deltas = 0;
        for (int step = 0; step < 400 && sends < 120; step++) {
            match.run(1, script);
            if (step % 2 != 0) {
                continue;
            }
            var snapshot = stream.next(match.state());
            if (snapshot.isEmpty()) {
                continue;
            }
            sends++;
            deltas += snapshot.get().keyframe() ? 0 : 1;
            assertTrue(snapshot.get().size() <= Budgets.snapshotMaxBytes());
            client = SnapshotCodec.decode(snapshot.get().body(), client).orElseThrow();
            assertEquals(StateHash.ofView(match.state(), false), StateHash.ofView(client, false), "send " + sends);
        }
        assertTrue(sends >= 50, "sends: " + sends);
        assertTrue(deltas >= 25, "deltas: " + deltas);
    }

    /**
     * 002 R2: a pawn death between two sends (pawn id → none) cannot travel as a delta; and over a scripted
     * 1200-tick run every delta the stream does send reconstructs the authoritative view exactly.
     */
    @Test
    void structuralChangeForcesKeyframe() throws Exception {
        HeadlessMatch match = busyMatch().run(Budgets.COUNTDOWN_TICKS + 10, HeadlessMatch.InputScript.idle());
        SnapshotCodec.Stream stream = new SnapshotCodec.Stream(Budgets.keyframeTicks(), Budgets.snapshotMaxBytes());
        assertTrue(stream.next(match.state()).orElseThrow().keyframe(), "first send");
        match.run(2, HeadlessMatch.InputScript.randomWalk());
        MatchState before = match.state().copy();
        match.state().setStatus(0, za.co.neroland.neroarcade.runtime.SeatState.SHIELD, 0);
        match.state().damageSeat(0, 1);
        assertEquals(-1, match.state().pawnId(0), "seat 0's pawn is gone");
        assertTrue(!match.state().deltaCompatibleWith(before));
        assertTrue(stream.next(match.state()).orElseThrow().keyframe(), "a death between sends is a keyframe");

        HeadlessMatch run = busyMatch();
        SnapshotCodec.Stream chain = new SnapshotCodec.Stream(Budgets.keyframeTicks(), Budgets.snapshotMaxBytes());
        HeadlessMatch.InputScript script = HeadlessMatch.InputScript.randomWalk();
        MatchState client = null;
        int deltas = 0;
        for (int step = 0; step < 1200; step++) {
            run.run(1, script);
            if (step % 2 != 0) {
                continue;
            }
            var snapshot = chain.next(run.state());
            if (snapshot.isEmpty()) {
                continue;
            }
            if (!snapshot.get().keyframe()) {
                deltas++;
            }
            client = SnapshotCodec.decode(snapshot.get().body(), client).orElseThrow();
            assertEquals(StateHash.ofView(run.state(), false), StateHash.ofView(client, false), "step " + step);
        }
        assertTrue(deltas > 0, "the run must exercise deltas");
    }

    @Test
    void deltaOnTheWrongBaseAsksForAKeyframe() throws Exception {
        HeadlessMatch match = busyMatch().run(100, HeadlessMatch.InputScript.randomWalk());
        MatchState base = match.state().copy();
        match.run(2, HeadlessMatch.InputScript.randomWalk());
        byte[] delta = SnapshotCodec.writeDelta(base, match.state());
        MatchState stale = base.copy();
        match.run(2, HeadlessMatch.InputScript.randomWalk());
        MatchState wrong = match.state().copy();
        assertTrue(SnapshotCodec.decode(delta, wrong).isEmpty());
        assertTrue(SnapshotCodec.decode(delta, null).isEmpty());
        assertTrue(SnapshotCodec.decode(delta, stale).isPresent());
        assertThrows(IllegalArgumentException.class, () -> SnapshotCodec.decode(new byte[] {0, 1, 2}, null));
    }

    @Test
    void historicHeadlessSnapshotsAreRecorded() throws Exception {
        HeadlessMatch match = busyMatch().withSnapshots().run(200, HeadlessMatch.InputScript.randomWalk());
        assertTrue(match.snapshotsSoFar().size() > 50);
        assertTrue(match.snapshotsSoFar().get(0).keyframe());
    }

    @Test
    void payloadCodecsRoundTrip() {
        ByteBuf buf = Unpooled.buffer();
        SnapshotPayload snapshot = new SnapshotPayload(new BlockPos(1, -2, 3), 7, new byte[] {1, 2, 3});
        SnapshotPayload.STREAM_CODEC.encode(buf, snapshot);
        SnapshotPayload back = SnapshotPayload.STREAM_CODEC.decode(buf);
        assertEquals(snapshot.cabinet(), back.cabinet());
        assertEquals(7, back.matchId());
        assertArrayEquals(new byte[] {1, 2, 3}, back.body());
        SeatInputPayload input = new SeatInputPayload(BlockPos.ZERO, 3, SeatInput.of(SeatInput.A | SeatInput.LEFT, -127, 90), 44);
        SeatInputPayload.STREAM_CODEC.encode(buf, input);
        assertEquals(input, SeatInputPayload.STREAM_CODEC.decode(buf));
        assertEquals(-127, input.input().aimX());
        assertEquals(90, input.input().aimY());
    }
}
