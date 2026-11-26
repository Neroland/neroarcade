package za.co.neroland.neroarcade.network;

//? if <1.20.5 {
/*import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class LegacyArcadePayloadTest {
    @Test
    void benchEditRoundTripsAllFields() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            BenchEditPayload edit = new BenchEditPayload(new BlockPos(-21, 63, 49), BenchEditPayload.Op.SET_PROP,
                    2, 1, "speed", -7, true, "Test Cabinet");
            BenchEditPayload.STREAM_CODEC.encode(buffer, edit);
            assertEquals(edit, BenchEditPayload.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void hudLegendRejectsOversizedListBeforeReadingEntries() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeBlockPos(BlockPos.ZERO);
            buffer.writeVarInt(Integer.MAX_VALUE);
            assertThrows(DecoderException.class, () -> HudLegendPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
*///?}
