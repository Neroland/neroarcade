package za.co.neroland.neroarcade.network;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
//? if >=1.20.5 {
import net.minecraft.network.codec.ByteBufCodecs;
//?} else {
/*import net.minecraft.network.FriendlyByteBuf;
*///?}
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.module.BenchWarning;

/**
 * Server → client: the bench's current draft for the "Build your game" preview, sent when the menu opens and
 * after every accepted change (003 §11). The client runs its preview from {@link #draft} through the same
 * common runtime.
 *
 * <p>Privacy: the draft never carries an author UUID or a credit string (both stripped server side by
 * {@code BenchDraft.preview()}, compliance review F3); the screen's credit line is the editor's own opt-in hint.
 *
 * @param draft      the GameDef the rack would save now (placeholder id until the first save; empty slots = defaults)
 * @param warnings   bench warnings, grey, never refusals
 * @param ghostMask  bit i = rack slot i holds a LOAD-materialised chip (shown, not takeable; clear it with CLEAR_SLOT)
 * @param statusKey  a lang key for the feedback trough ("" = none), e.g. {@code neroarcade.bench.status.unreadable}
 * @param statusArgs plain-text arguments for {@code statusKey}
 * @param testing    a test play is running on this bench
 * @param canSave    the tray holds something SAVE can write to
 */
public record BenchStatePayload(BlockPos bench, GameDef draft, List<BenchWarning> warnings, int ghostMask,
        String statusKey, List<String> statusArgs, boolean testing, boolean canSave) implements CustomPacketPayload {

    /** Upper bound of the encoded draft (twice the cartridge cap; the server never sends more than the cap). */
    public static final long MAX_DRAFT_NBT = 16384;

    //? if >=1.20.5 {
    public static final CustomPacketPayload.Type<BenchStatePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("neroarcade", "bench_state"));
    //?} else {
    /*public static final CustomPacketPayload.Type<BenchStatePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("neroarcade", "bench_state"), BenchStatePayload.class);
    *///?}

    //? if >=1.20.5 {
    private static final StreamCodec<ByteBuf, GameDef> DRAFT_CODEC = ByteBufCodecs.compoundTagCodec(
            () -> net.minecraft.nbt.NbtAccounter.create(MAX_DRAFT_NBT)).map(
            tag -> GameDef.CODEC.parse(NbtOps.INSTANCE, tag)
                    .getOrThrow(message -> new DecoderException("bad bench draft: " + message)),
            def -> {
                Tag tag = GameDef.CODEC.encodeStart(NbtOps.INSTANCE, def).getOrThrow();
                return tag instanceof CompoundTag compound ? compound : new CompoundTag();
            });

    private static final StreamCodec<ByteBuf, List<BenchWarning>> WARNINGS_CODEC = ByteBufCodecs.VAR_INT.map(
            BenchStatePayload::warningsFromMask, BenchStatePayload::maskOf);

    // 1.21.1's StreamCodec.composite stops at six fields.
    //? if >=1.21.4 {
    public static final StreamCodec<ByteBuf, BenchStatePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, BenchStatePayload::bench,
            DRAFT_CODEC, BenchStatePayload::draft,
            WARNINGS_CODEC, BenchStatePayload::warnings,
            ByteBufCodecs.VAR_INT, BenchStatePayload::ghostMask,
            ByteBufCodecs.stringUtf8(128), BenchStatePayload::statusKey,
            ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(4)), BenchStatePayload::statusArgs,
            ByteBufCodecs.BOOL, BenchStatePayload::testing,
            ByteBufCodecs.BOOL, BenchStatePayload::canSave,
            BenchStatePayload::new);
    //?} else {
    /*public static final StreamCodec<ByteBuf, BenchStatePayload> STREAM_CODEC = za.co.neroland.neroarcade.compat.StreamCodecs8.composite(
            BlockPos.STREAM_CODEC, BenchStatePayload::bench,
            DRAFT_CODEC, BenchStatePayload::draft,
            WARNINGS_CODEC, BenchStatePayload::warnings,
            ByteBufCodecs.VAR_INT, BenchStatePayload::ghostMask,
            ByteBufCodecs.stringUtf8(128), BenchStatePayload::statusKey,
            ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(4)), BenchStatePayload::statusArgs,
            ByteBufCodecs.BOOL, BenchStatePayload::testing,
            ByteBufCodecs.BOOL, BenchStatePayload::canSave,
            BenchStatePayload::new);
    *///?}
    //?} else {
    /*public static final StreamCodec<FriendlyByteBuf, BenchStatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.bench());
                Tag tag = GameDef.CODEC.encodeStart(NbtOps.INSTANCE, payload.draft()).result().orElseThrow();
                buf.writeNbt(tag instanceof CompoundTag compound ? compound : new CompoundTag());
                buf.writeVarInt(maskOf(payload.warnings()));
                buf.writeVarInt(payload.ghostMask());
                buf.writeUtf(payload.statusKey(), 128);
                buf.writeVarInt(payload.statusArgs().size());
                for (String argument : payload.statusArgs()) {
                    buf.writeUtf(argument, 64);
                }
                buf.writeBoolean(payload.testing());
                buf.writeBoolean(payload.canSave());
            }, buf -> {
                BlockPos bench = buf.readBlockPos();
                //? if >=1.20.2 {
                //Tag tag = buf.readNbt(net.minecraft.nbt.NbtAccounter.create(MAX_DRAFT_NBT));
                //?} else {
                Tag tag = buf.readNbt(new net.minecraft.nbt.NbtAccounter(MAX_DRAFT_NBT));
                //?}
                if (!(tag instanceof CompoundTag draftTag)) {
                    throw new DecoderException("Missing bench draft");
                }
                GameDef draft = GameDef.CODEC.parse(NbtOps.INSTANCE, draftTag).result()
                        .orElseThrow(() -> new DecoderException("Invalid bench draft"));
                List<BenchWarning> warnings = warningsFromMask(buf.readVarInt());
                int ghostMask = buf.readVarInt();
                String statusKey = buf.readUtf(128);
                int argumentCount = buf.readVarInt();
                if (argumentCount < 0 || argumentCount > 4) {
                    throw new DecoderException("Too many bench status arguments");
                }
                List<String> arguments = new ArrayList<>(argumentCount);
                for (int index = 0; index < argumentCount; index++) {
                    arguments.add(buf.readUtf(64));
                }
                return new BenchStatePayload(bench, draft, warnings, ghostMask, statusKey, arguments,
                        buf.readBoolean(), buf.readBoolean());
            });
    *///?}

    public BenchStatePayload {
        warnings = List.copyOf(warnings);
        statusKey = statusKey == null ? "" : statusKey;
        statusArgs = statusArgs == null ? List.of() : List.copyOf(statusArgs);
    }

    static List<BenchWarning> warningsFromMask(int mask) {
        List<BenchWarning> out = new ArrayList<>();
        for (BenchWarning warning : BenchWarning.values()) {
            if ((mask & (1 << warning.ordinal())) != 0) {
                out.add(warning);
            }
        }
        return out;
    }

    static int maskOf(List<BenchWarning> warnings) {
        int mask = 0;
        for (BenchWarning warning : warnings) {
            mask |= 1 << warning.ordinal();
        }
        return mask;
    }

    @Override
    public CustomPacketPayload.Type<BenchStatePayload> type() {
        return TYPE;
    }
}
