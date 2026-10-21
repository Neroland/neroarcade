package za.co.neroland.neroarcade.cabinet;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.block.ArcadeBlockProperties;
import za.co.neroland.neroarcade.block.CabinetStatus;
import za.co.neroland.neroarcade.block.PadDirection;
import za.co.neroland.neroarcade.config.NeroArcadeConfig;
import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.item.CartridgeItem;
import za.co.neroland.neroarcade.item.CartridgeTint;
import za.co.neroland.neroarcade.item.StoredCartridge;
import za.co.neroland.neroarcade.registry.ModBlockEntities;
import za.co.neroland.neroarcade.registry.ModSounds;
import za.co.neroland.neroarcade.runtime.Budgets;
import za.co.neroland.neroarcade.runtime.MatchResult;
import za.co.neroland.neroarcade.runtime.SeatInput;

/**
 * The cabinet's controller (lower half), shaped per 003 §1. A cabinet with a cartridge and no bus is its own
 * host on the loopback bus ({@code busId 0}, seat 0).
 *
 * <p><b>Persisted:</b> the cartridge item and the bus id cache (always 0 until Stage 4). <b>Runtime only:</b> the
 * {@link MatchHost}, the {@link Seat}, the sleep clock and the awake slot — a seat is a live session, never
 * written to the region file (no player UUID at rest), and an unload ends a running match as ABANDONED with no
 * board write.
 *
 * <p>Server tick (lower half): seat validity every 10 ticks; a promoted queue slot wakes the host; the host
 * ticks; the sleep rule; the blockstate mirror at ≤ 10 Hz and only on change. Teardown with world access lives in
 * {@link #preRemoveSideEffects} (real removal only); {@link #setRemoved} (also chunk unload) stays world-inert.
 */
public class ArcadeCabinetBlockEntity extends BlockEntity implements SeatHolder {

    private static final String KEY_CARTRIDGE = "cartridge";
    private static final String KEY_BUS = "bus";

    // --- persisted --------------------------------------------------------------------------------
    private ItemStack cartridge = ItemStack.EMPTY;
    private long busId;

    // --- runtime only -----------------------------------------------------------------------------
    private @Nullable MatchHost host;
    private @Nullable LevelHostEnv env;
    private @Nullable Seat seat;
    private final SleepClock sleep = new SleepClock();
    private boolean awake;
    private boolean queued;
    private @Nullable UUID queuedFor;
    private int rejectedInputs;
    private BusPhase lastPhase = BusPhase.IDLE;
    /** Cached validation of the inserted cartridge; null = no cartridge. */
    private @Nullable Readability readability;
    /** Client side only: what the update tag said about the cartridge (never the cartridge itself). */
    private CabinetClientInfo clientInfo = CabinetClientInfo.EMPTY;

    /** The cartridge's state on this server. */
    private record Readability(@Nullable ResolvedGame game, @Nullable ValidationError error) {
        boolean readable() {
            return game != null;
        }
    }

    public ArcadeCabinetBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ARCADE_CABINET.get(), pos, state);
    }

    // --- SeatHolder -------------------------------------------------------------------------------

    @Override
    public @Nullable MatchHost matchHost() {
        return host;
    }

    @Override
    public @Nullable Seat heldSeat() {
        return seat;
    }

    @Override
    public void countRejectedInput() {
        rejectedInputs++;
    }

    /** Packets addressed here by someone without the seat (a debugging counter). */
    public int rejectedInputs() {
        return rejectedInputs;
    }

    @Override
    public void dropSeat(DropReason why) {
        Seat leaving = seat;
        if (leaving == null) {
            return;
        }
        seat = null;
        if (host != null) {
            host.unbind(leaving.seat, why);
        }
        if (level instanceof ServerLevel serverLevel) {
            SeatRegistry.SERVER.release(leaving.playerUuid, seatKey(serverLevel));
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(leaving.playerUuid);
            if (player != null && why != DropReason.ESC) {
                player.sendOverlayMessage(Component.translatable(why.langKey()));
            }
        }
    }

    // --- cartridge --------------------------------------------------------------------------------

    public ItemStack cartridge() {
        return cartridge;
    }

    public boolean hasCartridge() {
        return !cartridge.isEmpty();
    }

    /** The cartridge resolved against this server's modules, or null if absent or unreadable. */
    public @Nullable ResolvedGame game() {
        Readability r = readability();
        return r == null ? null : r.game();
    }

    /** Why the inserted cartridge cannot run here, if it cannot. */
    public Optional<ValidationError> cartridgeError() {
        Readability r = readability();
        return r == null ? Optional.empty() : Optional.ofNullable(r.error());
    }

    private @Nullable Readability readability() {
        if (cartridge.isEmpty()) {
            return null;
        }
        if (readability == null) {
            StoredCartridge stored = CartridgeItem.storedOf(cartridge);
            if (stored == null) {
                readability = new Readability(null, new ValidationError(ValidationError.Code.MALFORMED));
            } else {
                try {
                    readability = new Readability(stored.resolve(), null);
                } catch (ValidationError refused) {
                    readability = new Readability(null, refused);
                }
            }
        }
        return readability;
    }

    /** Puts a cartridge in (one item). The caller has checked the slot is empty. */
    void insert(ItemStack one) {
        cartridge = one.copyWithCount(1);
        readability = null;
        sync();
    }

    /** Takes the cartridge out, ending any match and dropping the seat. */
    ItemStack takeCartridge() {
        ItemStack out = cartridge;
        sleepNow(DropReason.BROKEN);
        cartridge = ItemStack.EMPTY;
        readability = null;
        sync();
        return out;
    }

    // --- seats ------------------------------------------------------------------------------------

    /** Result of a seat request, for {@link CabinetInteraction}'s message. */
    enum SeatResult {
        SEATED,
        QUEUED,
        TAKEN,
        IN_PROGRESS,
        NOT_READY
    }

    /**
     * Wakes the cabinet if needed (or queues it at the awake cap) and binds {@code player} to seat 0. The caller
     * has checked the cartridge, the registry and power.
     */
    SeatResult seat(ServerPlayer player) {
        ServerLevel serverLevel = (ServerLevel) level;
        if (seat != null) {
            return SeatResult.TAKEN;
        }
        if (host != null && host.phase().running()) {
            return SeatResult.IN_PROGRESS;
        }
        if (!awake) {
            AwakeCabinets.Grant grant = AwakeCabinets.SERVER.request(this);
            if (grant == AwakeCabinets.Grant.QUEUED) {
                queued = true;
                queuedFor = player.getUUID();
                return SeatResult.QUEUED;
            }
            awake = true;
            queued = false;
        }
        if (host == null && !startHost(serverLevel)) {
            return SeatResult.NOT_READY;
        }
        Seat candidate = new Seat(0, player.getUUID(), serverLevel.getGameTime());
        if (!SeatRegistry.SERVER.claim(player.getUUID(), seatKey(serverLevel))) {
            return SeatResult.TAKEN;
        }
        if (host == null || !host.bind(0, candidate)) {
            SeatRegistry.SERVER.release(player.getUUID(), seatKey(serverLevel));
            return SeatResult.IN_PROGRESS;
        }
        seat = candidate;
        sleep.reset();
        return SeatResult.SEATED;
    }

    /** The queue position while waiting for an awake slot, else 0. */
    int queuePosition() {
        return queued ? AwakeCabinets.SERVER.queuePosition(this) : 0;
    }

    private boolean startHost(ServerLevel serverLevel) {
        ResolvedGame game = game();
        if (game == null) {
            return false;
        }
        env = new LevelHostEnv(serverLevel, worldPosition, ArcadeCabinetBlockEntity::powerAvailable);
        host = MatchHost.solo(env, worldPosition, game, BoardWriter.NONE);
        lastPhase = host.phase();
        sleep.reset();
        return true;
    }

    /** Stage 2 has no energy buffer: with {@code cabinetsNeedPower} on, a cabinet has no power (002 §11). */
    static boolean powerAvailable() {
        return !NeroArcadeConfig.CABINETS_NEED_POWER.get();
    }

    public SeatRegistry.SeatKey seatKey(ServerLevel serverLevel) {
        return new SeatRegistry.SeatKey(serverLevel.dimension().identifier().toString(), worldPosition.asLong());
    }

    // --- tick -------------------------------------------------------------------------------------

    /** Server tick, lower half only (the block's ticker). */
    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        long time = serverLevel.getGameTime();
        if (time % 10 == 0) {
            validateSeat(serverLevel);
        }
        if (queued && AwakeCabinets.SERVER.isAwake(this)) {
            queued = false;
            awake = true;
            if (host == null) {
                startHost(serverLevel);
            }
            ServerPlayer waiting = queuedFor == null ? null : serverLevel.getServer().getPlayerList().getPlayer(queuedFor);
            if (waiting != null) {
                waiting.sendOverlayMessage(Component.translatable("neroarcade.cabinet.msg.your_turn"));
            }
            queuedFor = null;
        }
        MatchHost current = host;
        if (current != null) {
            current.tick();
            onPhase(serverLevel, current);
            boolean nearby = env != null && !env.viewers().isEmpty();
            if (sleep.tick(seat != null, nearby, Budgets.sleepAfterTicks())) {
                sleepNow(DropReason.SLEEP);
            }
        }
        if (time % 2 == 0) {
            mirrorBlockState(serverLevel);
        }
    }

    private void validateSeat(ServerLevel serverLevel) {
        Seat current = seat;
        if (current == null) {
            return;
        }
        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(current.playerUuid);
        if (player == null) {
            dropSeat(DropReason.LOGOUT);
            return;
        }
        double range = NeroArcadeConfig.SEAT_RANGE.get();
        if (player.level() != serverLevel
                || player.getEyePosition().distanceToSqr(Vec3.atCenterOf(worldPosition)) > range * range) {
            dropSeat(DropReason.RANGE);
        }
    }

    private void onPhase(ServerLevel serverLevel, MatchHost current) {
        BusPhase now = current.phase();
        if (now == lastPhase) {
            return;
        }
        if (now == BusPhase.COUNTDOWN) {
            play(serverLevel, ModSounds.CABINET_START.get());
        } else if (now == BusPhase.RESULTS) {
            boolean won = current.lastResult().map(r -> r.outcome() == MatchResult.Outcome.WIN).orElse(false);
            play(serverLevel, won ? ModSounds.CABINET_WIN.get() : ModSounds.CABINET_LOSE.get());
        }
        lastPhase = now;
    }

    void play(ServerLevel serverLevel, SoundEvent sound) {
        serverLevel.playSound(null, worldPosition, sound, SoundSource.BLOCKS, 0.8F, 1.0F);
    }

    /** Ends any match (ABANDONED, no board write), drops the seat, frees the awake slot. */
    private void sleepNow(DropReason why) {
        if (host != null) {
            host.end(MatchResult.Outcome.ABANDONED);
        }
        dropSeat(why);
        host = null;
        env = null;
        releaseSlot();
        sleep.reset();
        lastPhase = BusPhase.IDLE;
    }

    private void releaseSlot() {
        if (awake || queued) {
            AwakeCabinets.SERVER.release(this);
        }
        awake = false;
        queued = false;
        queuedFor = null;
    }

    /** The status strip per 003 §3.6 (solo subset). */
    public CabinetStatus computeStatus() {
        if (cartridge.isEmpty()) {
            return CabinetStatus.OFF;
        }
        Readability r = readability();
        if (r == null || !r.readable()) {
            return CabinetStatus.LOCKED;
        }
        if (!powerAvailable()) {
            return CabinetStatus.NO_POWER;
        }
        if (host == null) {
            return CabinetStatus.OFF;
        }
        return switch (host.phase()) {
            case COUNTDOWN, PLAYING, RESULTS -> CabinetStatus.PLAYING;
            default -> CabinetStatus.READY;
        };
    }

    private void mirrorBlockState(ServerLevel serverLevel) {
        BlockState state = getBlockState();
        if (!state.hasProperty(ArcadeBlockProperties.STATUS)) {
            return;
        }
        SeatInput input = seat == null ? SeatInput.NONE : seat.latched();
        BlockState wanted = state
                .setValue(ArcadeBlockProperties.STATUS, computeStatus())
                .setValue(ArcadeBlockProperties.PAD, PadDirection.of(input))
                .setValue(ArcadeBlockProperties.BTN_A, input.has(SeatInput.A))
                .setValue(ArcadeBlockProperties.BTN_B, input.has(SeatInput.B))
                .setValue(ArcadeBlockProperties.CARTRIDGE, !cartridge.isEmpty())
                .setValue(ArcadeBlockProperties.LINKED, busId != 0);
        if (wanted != state) {
            serverLevel.setBlock(worldPosition, wanted, Block.UPDATE_CLIENTS);
        }
    }

    // --- removal ----------------------------------------------------------------------------------

    /**
     * Real removal only (never chunk unload): end the match, drop the seat with a message, free the slot and
     * drop the cartridge item.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        sleepNow(DropReason.BROKEN);
        if (level != null && !cartridge.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, cartridge);
            cartridge = ItemStack.EMPTY;
        }
    }

    /**
     * Block removal and chunk unload: world-inert bookkeeping only (a world read here can reload the chunk being
     * unloaded). The seat and the slot are memory, so releasing them is safe; the match is simply discarded.
     */
    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel && seat != null) {
            SeatRegistry.SERVER.release(seat.playerUuid, seatKey(serverLevel));
        }
        seat = null;
        host = null;
        env = null;
        releaseSlot();
        super.setRemoved();
    }

    // --- persistence and sync ---------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!cartridge.isEmpty()) {
            output.store(KEY_CARTRIDGE, ItemStack.CODEC, cartridge);
        }
        output.putLong(KEY_BUS, busId);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        cartridge = input.read(KEY_CARTRIDGE, ItemStack.CODEC).orElse(ItemStack.EMPTY);
        busId = input.getLongOr(KEY_BUS, 0L);
        readability = null;
        // The update tag's client view (absent in saved data, so a server load always reads EMPTY).
        Optional<String> title = input.getString(CabinetClientInfo.KEY_TITLE);
        clientInfo = title.isEmpty() ? CabinetClientInfo.EMPTY
                : new CabinetClientInfo(true, title.get(),
                        input.getIntOr(CabinetClientInfo.KEY_TINT, CartridgeTint.GREY),
                        input.getBooleanOr(CabinetClientInfo.KEY_READABLE, false),
                        input.getStringOr(CabinetClientInfo.KEY_ERROR, ""),
                        input.read(CabinetClientInfo.KEY_DEF, GameDef.CODEC));
    }

    /** Client side: the synced view of the cartridge for the screen renderer and the seated player's prediction. */
    public CabinetClientInfo clientInfo() {
        return clientInfo;
    }

    /**
     * What the client renderer needs and nothing more: the title, the theme tint and whether the cartridge is
     * readable. Never the cartridge itself (it carries the author UUID).
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (!cartridge.isEmpty()) {
            StoredCartridge stored = CartridgeItem.storedOf(cartridge);
            tag.putString(CabinetClientInfo.KEY_TITLE, stored == null ? "" : stored.rawTitle());
            GameDef def = stored == null ? null : stored.tryDecode().orElse(null);
            tag.putInt(CabinetClientInfo.KEY_TINT, def == null ? CartridgeTint.GREY : CartridgeTint.of(def));
            Readability r = readability();
            tag.putBoolean(CabinetClientInfo.KEY_READABLE, r != null && r.readable());
            if (r != null && r.error() != null) {
                tag.putString(CabinetClientInfo.KEY_ERROR, r.error().code.name());
            }
            if (def != null) {
                // The screen's sprites and the seated player's prediction need the game; never the author UUID.
                GameDef.CODEC.encodeStart(NbtOps.INSTANCE, CabinetClientInfo.anonymous(def)).result()
                        .ifPresent(encoded -> tag.put(CabinetClientInfo.KEY_DEF, encoded));
            }
        }
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** For logs and admin messages: never a player. */
    @Override
    public String toString() {
        return NeroArcadeCommon.MOD_ID + ":arcade_cabinet@" + worldPosition.toShortString();
    }
}
