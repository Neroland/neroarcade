package za.co.neroland.neroarcade.runtime;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import za.co.neroland.neroarcade.game.GameDef;

/**
 * Everything needed to reproduce a match bit for bit: the cartridge, the seed, the human seat count and the
 * input log, plus the expected final hash and step count.
 *
 * <p><b>Invariants:</b> {@link #play()} refuses a truncated log and throws if the replayed hash differs —
 * desync is a defect, never tolerated. A replay holds no personal data beyond what its cartridge carries
 * (gallery cartridges carry no author). Replays of real matches are not persisted in 0.1.0.
 *
 * @param seats     the seat count the match was started with (before any runtime opponent)
 * @param finalTick runtime steps since construction (countdown included) when the replay was taken
 */
public record Replay(GameDef gameDef, long seed, int seats, InputLog inputLog, long finalHash, int finalTick) {

    public static final Codec<Replay> CODEC = RecordCodecBuilder.create(i -> i.group(
            GameDef.CODEC.fieldOf("game_def").forGetter(Replay::gameDef),
            Codec.LONG.fieldOf("seed").forGetter(Replay::seed),
            Codec.INT.fieldOf("seats").forGetter(Replay::seats),
            InputLog.CODEC.fieldOf("input_log").forGetter(Replay::inputLog),
            Codec.LONG.fieldOf("final_hash").forGetter(Replay::finalHash),
            Codec.INT.fieldOf("final_tick").forGetter(Replay::finalTick)
    ).apply(i, Replay::new));

    /**
     * Replays the match on a fresh runtime to {@link #finalTick}.
     *
     * @return the match result, or empty when the replay ends before the match does
     * @throws IllegalStateException if the log is truncated or the final hash differs
     */
    public Optional<MatchResult> play() {
        return run().result();
    }

    /** As {@link #play()}, returning the harness (state, per-tick inspection). */
    public HeadlessMatch run() {
        if (inputLog.truncated()) {
            throw new IllegalStateException("refusing to replay a truncated input log");
        }
        HeadlessMatch match = HeadlessMatch.of(gameDef, seats, seed);
        SeatInput[] carry = new SeatInput[Budgets.MAX_SEATS];
        java.util.Arrays.fill(carry, SeatInput.NONE);
        for (int t = 0; t < finalTick; t++) {
            match.step(inputLog.inputsAt(t, carry).clone());
        }
        if (match.hash() != finalHash) {
            throw new IllegalStateException(String.format("replay desync: hash %016x, expected %016x",
                    match.hash(), finalHash));
        }
        return match;
    }
}
