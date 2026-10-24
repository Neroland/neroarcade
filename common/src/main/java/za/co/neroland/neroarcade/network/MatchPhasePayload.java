package za.co.neroland.neroarcade.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.cabinet.BusPhase;
import za.co.neroland.neroarcade.runtime.Budgets;

/**
 * Server → client: the host's phase, sent on every change and to each new viewer (003 §11). Seat identities
 * are never sent — clients label seats P1–P4.
 *
 * @param host             the hosting cabinet (or bench during a test play)
 * @param seatCount        seats on this bus (1 for a solo cabinet)
 * @param boundMask        bit s set = seat s is taken
 * @param readyMask        bit s set = seat s pressed READY (LOBBY)
 * @param mySeat           the recipient's seat, or -1 for a spectator / a player who just left
 * @param ranks            per seat, 1-based (RESULTS only; zeros otherwise)
 * @param scores           per seat (RESULTS only; zeros otherwise)
 * @param newBestMask      bit s = seat s set a personal best (RESULTS only)
 * @param ticksLeftInPhase COUNTDOWN ticks left, RESULTS ticks left, LOBBY ticks until a short-handed start (0 when
 *                         the lobby is not waiting), else 0
 * @param paused           PLAYING but frozen for lack of power
 * @param outcome          {@code MatchResult.Outcome} ordinal in RESULTS, -1 otherwise
 * @param seatAt           the block the recipient sits at (a bus node or the host) — where its input and leave
 *                         packets go; the host for spectators. Only ever the recipient's own seat.
 */
public record MatchPhasePayload(BlockPos host, int matchId, BusPhase phase, int seatCount, int boundMask,
        int readyMask, int mySeat, int[] ranks, int[] scores, int newBestMask, int ticksLeftInPhase, boolean paused,
        int outcome, BlockPos seatAt) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MatchPhasePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("neroarcade", "match_phase"));

    public static final StreamCodec<ByteBuf, MatchPhasePayload> STREAM_CODEC = StreamCodec.of(
            MatchPhasePayload::write, MatchPhasePayload::read);

    public MatchPhasePayload {
        ranks = ranks == null ? new int[0] : ranks.clone();
        scores = scores == null ? new int[0] : scores.clone();
        seatAt = seatAt == null ? host : seatAt;
        if (ranks.length > Budgets.MAX_SEATS || scores.length > Budgets.MAX_SEATS) {
            throw new IllegalArgumentException("at most " + Budgets.MAX_SEATS + " seats");
        }
    }

    @Override
    public int[] ranks() {
        return ranks.clone();
    }

    @Override
    public int[] scores() {
        return scores.clone();
    }

    /**
     * "You are no longer seated at {@code host}'s match": IDLE, {@code mySeat = -1}. Sent by a cabinet that drops a
     * seat when its host is already gone (unloaded, dissolved) and so cannot send the phase itself (review 003b F8).
     */
    public static MatchPhasePayload released(BlockPos host) {
        return new MatchPhasePayload(host, 0, BusPhase.IDLE, 1, 0, 0, -1, new int[0], new int[0], 0, 0, false, -1,
                host);
    }

    /** The same packet addressed to another recipient (seated at the host, or a spectator). */
    public MatchPhasePayload forSeat(int seat) {
        return forSeat(seat, host);
    }

    /** The same packet addressed to a recipient seated at {@code at}. */
    public MatchPhasePayload forSeat(int seat, BlockPos at) {
        return new MatchPhasePayload(host, matchId, phase, seatCount, boundMask, readyMask, seat, ranks, scores,
                newBestMask, ticksLeftInPhase, paused, outcome, at);
    }

    private static void write(ByteBuf buf, MatchPhasePayload p) {
        BlockPos.STREAM_CODEC.encode(buf, p.host);
        ByteBufCodecs.VAR_INT.encode(buf, p.matchId);
        buf.writeByte(p.phase.ordinal());
        buf.writeByte(p.seatCount);
        buf.writeByte(p.boundMask);
        buf.writeByte(p.readyMask);
        buf.writeByte(p.mySeat);
        buf.writeByte(p.ranks.length);
        for (int rank : p.ranks) {
            buf.writeByte(rank);
        }
        buf.writeByte(p.scores.length);
        for (int score : p.scores) {
            ByteBufCodecs.VAR_INT.encode(buf, score);
        }
        buf.writeByte(p.newBestMask);
        ByteBufCodecs.VAR_INT.encode(buf, p.ticksLeftInPhase);
        buf.writeBoolean(p.paused);
        buf.writeByte(p.outcome);
        BlockPos.STREAM_CODEC.encode(buf, p.seatAt);
    }

    private static MatchPhasePayload read(ByteBuf buf) {
        BlockPos host = BlockPos.STREAM_CODEC.decode(buf);
        int matchId = ByteBufCodecs.VAR_INT.decode(buf);
        int phaseOrdinal = buf.readUnsignedByte();
        BusPhase phase = BusPhase.values()[Math.min(phaseOrdinal, BusPhase.values().length - 1)];
        int seatCount = buf.readUnsignedByte();
        int bound = buf.readUnsignedByte();
        int ready = buf.readUnsignedByte();
        int mySeat = buf.readByte();
        int[] ranks = new int[Math.min(buf.readUnsignedByte(), Budgets.MAX_SEATS)];
        for (int i = 0; i < ranks.length; i++) {
            ranks[i] = buf.readUnsignedByte();
        }
        int[] scores = new int[Math.min(buf.readUnsignedByte(), Budgets.MAX_SEATS)];
        for (int i = 0; i < scores.length; i++) {
            scores[i] = ByteBufCodecs.VAR_INT.decode(buf);
        }
        int newBest = buf.readUnsignedByte();
        int ticksLeft = ByteBufCodecs.VAR_INT.decode(buf);
        boolean paused = buf.readBoolean();
        int outcome = buf.readByte();
        BlockPos seatAt = BlockPos.STREAM_CODEC.decode(buf);
        return new MatchPhasePayload(host, matchId, phase, seatCount, bound, ready, mySeat, ranks, scores, newBest,
                ticksLeft, paused, outcome, seatAt);
    }

    @Override
    public CustomPacketPayload.Type<MatchPhasePayload> type() {
        return TYPE;
    }
}
