package za.co.neroland.neroarcade.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server: one edit on the Game Bench the sender has open (003 §11). Chips are placed through the
 * menu's vanilla slots, so this carries only properties, the title and the actions. Accepted only while the
 * sender's open menu is the {@code GameBenchMenu} of {@code bench}; 20 per second per player, excess ignored.
 *
 * <ul>
 *   <li>{@link Op#SET_PROP}: {@code slot} = {@code ModuleType} ordinal, {@code stackIndex} 0..2 (stack slots),
 *       {@code propKey} the property name; INT reads {@code intValue} (clamped to the spec), BOOL reads
 *       {@code boolValue}, ENUM reads {@code intValue} as the constant's index (clamped).</li>
 *   <li>{@link Op#SET_TITLE}: {@code title} (≤ 32 characters after trimming; chat-filtered).</li>
 *   <li>{@link Op#SAVE}, {@link Op#LOAD}, {@link Op#TEST_PLAY}, {@link Op#STOP_TEST}: no arguments.</li>
 *   <li>{@link Op#CLEAR_SLOT}: removes a LOAD-materialised (ghost) chip from rack slot {@code slot}/{@code stackIndex}.</li>
 * </ul>
 */
public record BenchEditPayload(BlockPos bench, Op op, int slot, int stackIndex, String propKey, int intValue,
        boolean boolValue, String title) implements CustomPacketPayload {

    /** Wire cap for the property key. */
    public static final int MAX_PROP_KEY = 32;
    /** Wire cap for the title in characters; the server then applies the 32-character rule. */
    public static final int MAX_TITLE_WIRE = 128;

    public enum Op {
        SET_PROP,
        SET_TITLE,
        SAVE,
        LOAD,
        TEST_PLAY,
        STOP_TEST,
        CLEAR_SLOT
    }

    public static final CustomPacketPayload.Type<BenchEditPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("neroarcade", "bench_edit"));

    private static final StreamCodec<ByteBuf, Op> OP_CODEC = ByteBufCodecs.VAR_INT.map(
            i -> Op.values()[Math.floorMod(i, Op.values().length)], Op::ordinal);

    // 1.21.1's StreamCodec.composite stops at six fields.
    //? if >=1.21.4 {
    public static final StreamCodec<ByteBuf, BenchEditPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, BenchEditPayload::bench,
            OP_CODEC, BenchEditPayload::op,
            ByteBufCodecs.VAR_INT, BenchEditPayload::slot,
            ByteBufCodecs.VAR_INT, BenchEditPayload::stackIndex,
            ByteBufCodecs.stringUtf8(MAX_PROP_KEY), BenchEditPayload::propKey,
            ByteBufCodecs.VAR_INT, BenchEditPayload::intValue,
            ByteBufCodecs.BOOL, BenchEditPayload::boolValue,
            ByteBufCodecs.stringUtf8(MAX_TITLE_WIRE), BenchEditPayload::title,
            BenchEditPayload::new);
    //?} else {
    /*public static final StreamCodec<ByteBuf, BenchEditPayload> STREAM_CODEC = za.co.neroland.neroarcade.compat.StreamCodecs8.composite(
            BlockPos.STREAM_CODEC, BenchEditPayload::bench,
            OP_CODEC, BenchEditPayload::op,
            ByteBufCodecs.VAR_INT, BenchEditPayload::slot,
            ByteBufCodecs.VAR_INT, BenchEditPayload::stackIndex,
            ByteBufCodecs.stringUtf8(MAX_PROP_KEY), BenchEditPayload::propKey,
            ByteBufCodecs.VAR_INT, BenchEditPayload::intValue,
            ByteBufCodecs.BOOL, BenchEditPayload::boolValue,
            ByteBufCodecs.stringUtf8(MAX_TITLE_WIRE), BenchEditPayload::title,
            BenchEditPayload::new);
    *///?}

    public BenchEditPayload {
        propKey = propKey == null ? "" : propKey;
        title = title == null ? "" : title;
    }

    /** An action with no arguments (SAVE, LOAD, TEST_PLAY, STOP_TEST). */
    public static BenchEditPayload action(BlockPos bench, Op op) {
        return new BenchEditPayload(bench, op, 0, 0, "", 0, false, "");
    }

    @Override
    public CustomPacketPayload.Type<BenchEditPayload> type() {
        return TYPE;
    }
}
