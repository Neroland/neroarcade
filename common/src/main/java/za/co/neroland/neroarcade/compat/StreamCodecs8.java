package za.co.neroland.neroarcade.compat;

import net.minecraft.network.codec.StreamCodec;

/**
 * An eight-field {@code StreamCodec.composite}: Minecraft's own stops at six fields on 1.21.1 (eight from 1.21.4),
 * and two NeroArcade payloads carry eight. Encodes and decodes the fields in order, exactly as the vanilla
 * composite does. Used only on the lines whose StreamCodec lacks the eight-field overload.
 */
public final class StreamCodecs8 {

    private StreamCodecs8() {
    }

    @FunctionalInterface
    public interface Function8<T1, T2, T3, T4, T5, T6, T7, T8, R> {
        R apply(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5, T6 t6, T7 t7, T8 t8);
    }

    public static <B, C, T1, T2, T3, T4, T5, T6, T7, T8> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> c1, java.util.function.Function<C, T1> g1,
            StreamCodec<? super B, T2> c2, java.util.function.Function<C, T2> g2,
            StreamCodec<? super B, T3> c3, java.util.function.Function<C, T3> g3,
            StreamCodec<? super B, T4> c4, java.util.function.Function<C, T4> g4,
            StreamCodec<? super B, T5> c5, java.util.function.Function<C, T5> g5,
            StreamCodec<? super B, T6> c6, java.util.function.Function<C, T6> g6,
            StreamCodec<? super B, T7> c7, java.util.function.Function<C, T7> g7,
            StreamCodec<? super B, T8> c8, java.util.function.Function<C, T8> g8,
            Function8<T1, T2, T3, T4, T5, T6, T7, T8, C> make) {
        return StreamCodec.of((buf, value) -> {
            c1.encode(buf, g1.apply(value));
            c2.encode(buf, g2.apply(value));
            c3.encode(buf, g3.apply(value));
            c4.encode(buf, g4.apply(value));
            c5.encode(buf, g5.apply(value));
            c6.encode(buf, g6.apply(value));
            c7.encode(buf, g7.apply(value));
            c8.encode(buf, g8.apply(value));
        }, buf -> make.apply(c1.decode(buf), c2.decode(buf), c3.decode(buf), c4.decode(buf), c5.decode(buf),
                c6.decode(buf), c7.decode(buf), c8.decode(buf)));
    }
}
