package za.co.neroland.neroarcade.runtime;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * One seat's raw input for one tick: bits 0-7 buttons, bits 8-15 aimX (signed byte), bits 16-23 aimY
 * (signed byte). Integer-only; the server clamps aim to -127..127 and never trusts more than that.
 * {@code START} is the lobby "ready" / results "again" button and is ignored while PLAYING.
 */
public record SeatInput(int packed) {

    public static final int UP = 1;
    public static final int DOWN = 2;
    public static final int LEFT = 4;
    public static final int RIGHT = 8;
    public static final int A = 16;
    public static final int B = 32;
    public static final int START = 64;

    public static final SeatInput NONE = new SeatInput(0);

    /** VarInt of {@link #packed}. */
    public static final StreamCodec<ByteBuf, SeatInput> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(SeatInput::new, SeatInput::packed);

    public SeatInput {
        packed &= 0x00FF_FF7F; // bit 7 reserved; nothing above aimY
        int ax = (byte) (packed >> 8);
        int ay = (byte) (packed >> 16);
        if (ax == -128 || ay == -128) {
            packed = of(packed & 0xFF, ax, ay).packed;
        }
    }

    public static SeatInput of(int buttons, int aimX, int aimY) {
        int ax = Math.max(-127, Math.min(127, aimX));
        int ay = Math.max(-127, Math.min(127, aimY));
        return new SeatInput((buttons & 0x7F) | ((ax & 0xFF) << 8) | ((ay & 0xFF) << 16));
    }

    public boolean has(int button) {
        return (packed & button) != 0;
    }

    public int buttons() {
        return packed & 0xFF;
    }

    public int aimX() {
        return (byte) (packed >> 8);
    }

    public int aimY() {
        return (byte) (packed >> 16);
    }
}
