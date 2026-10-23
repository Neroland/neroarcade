package za.co.neroland.neroarcade.cabinet;

import java.util.List;
import java.util.Objects;
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
import za.co.neroland.neroarcade.board.Boards;
import za.co.neroland.neroarcade.bus.BusRegistry;
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
 * host on the loopback bus ({@code busId 0}, seat 0). On a Link Bus the hub assigns it a {@link BusRole}: the
 * <b>host</b> (it holds the bus's cartridge and runs the one {@link MatchHost}), a <b>node</b> (it mirrors the host
 * and seats one player as bus seat {@code busSeatIndex}), or one of the waiting/locked roles.
 *
 * <p><b>Persisted:</b> the cartridge item and the bus id cache. <b>Runtime only:</b> the role, the
 * {@link MatchHost}, the {@link Seat}, the sleep clock and the awake slot — a seat is a live session, never
 * written to the region file (no player UUID at rest), and an unload ends a running match as ABANDONED with no
 * board write. A bus holds <b>one</b> awake slot: the host's.
 *
 * <p>Server tick (lower half): the bus check every 20 ticks; seat validity every 10 ticks (a node whose host is
 * gone drops its seat); a promoted queue slot wakes the host; the host ticks; the sleep rule (any seat on the bus,
 * or any player near any screen on the bus, keeps it awake); the blockstate mirror at ≤ 10 Hz and only on change.
 * Teardown with world access lives in {@link #preRemoveSideEffects} (real removal only); {@link #setRemoved} (also
 * chunk unload) stays world-inert.
 */
public class ArcadeCabinetBlockEntity extends BlockEntity implements SeatHolder {

    private static final String KEY_CARTRIDGE = "cartridge";
    private static final String KEY_BUS = "bus";
    /** Update tag only: the host a node mirrors, and the bus role. Never in saved data. */
    public static final String KEY_DISPLAY_OF = "display_of";
    public static final String KEY_ROLE = "role";

    // --- persisted --------------------------------------------------------------------------------
    private ItemStack cartridge = ItemStack.EMPTY;
    private long busId;

    // --- runtime only -----------------------------------------------------------------------------
    private BusRole role = BusRole.SOLO;
    private int busSeatIndex = -1;
    private @Nullable BlockPos displayOf;
    private int busCabinets = 1;
    private List<BlockPos> busScreens = List.of();
    private long insertedAt = Long.MAX_VALUE;
    private boolean firstTick = true;
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
    private @Nullable BlockPos clientDisplayOf;
    private BusRole clientRole = BusRole.SOLO;

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

    /** The match this cabinet plays in: its own host, or (a node) the bus host's. */
    @Override
    public @Nullable MatchHost matchHost() {
        if (role == BusRole.NODE) {
            ArcadeCabinetBlockEntity h = hostCabinet();
            return h == null ? null : h.host;
        }
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
        MatchHost match = matchHost();
        if (match != null) {
            match.unbind(leaving.seat, why);
        }
        if (level instanceof ServerLevel serverLevel) {
            SeatRegistry.SERVER.release(leaving.playerUuid, seatKey(serverLevel));
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(leaving.playerUuid);
            if (player != null && why != DropReason.ESC) {
                player.sendOverlayMessage(Component.translatable(why.langKey()));
            }
        }
    }

    // --- bus --------------------------------------------------------------------------------------

    public BusRole role() {
        return role;
    }

    public long busId() {
        return busId;
    }

    public int busSeatIndex() {
        return busSeatIndex;
    }

    /** A node: the host it mirrors (same dimension); null otherwise. */
    public @Nullable BlockPos displayOf() {
        return displayOf;
    }

    /** Runtime-only insertion time for host election (first inserted wins). */
    public long insertedAt() {
        return insertedAt;
    }

    /** The bus host's block entity for a node, or null when it is not loaded / gone. Resolved on every call. */
    public @Nullable ArcadeCabinetBlockEntity hostCabinet() {
        BlockPos at = displayOf;
        if (at == null || level == null || !level.isLoaded(at)) {
            return null;
        }
        return level.getBlockEntity(at) instanceof ArcadeCabinetBlockEntity h && h.role == BusRole.HOST ? h : null;
    }

    /**
     * The hub's assignment after a discovery (003 §3.2). A node whose host, index or role changed loses its seat;
     * a host or solo cabinet that stops hosting ends its match (ABANDONED) and frees its awake slot.
     *
     * @param cabinets bus cabinets that can seat a player (host + nodes)
     * @param screens  the bus's other nodes and marquees (the host's viewer group)
     */
    public void assignBus(long newBusId, BusRole newRole, int index, @Nullable BlockPos hostPos, int cabinets,
            List<BlockPos> screens) {
        BlockPos newDisplay = newRole == BusRole.NODE && hostPos != null ? hostPos.immutable() : null;
        boolean changed = busId != newBusId || role != newRole || busSeatIndex != index
                || !Objects.equals(displayOf, newDisplay);
        if (changed) {
            BusRole old = role;
            if (old == BusRole.NODE && seat != null) {
                dropSeat(DropReason.HOST_GONE);
            }
            boolean wasHosting = old == BusRole.HOST || old == BusRole.SOLO;
            boolean hosts = newRole == BusRole.HOST;
            if (wasHosting && !hosts && host != null) {
                sleepNow(DropReason.HOST_GONE);
            }
            if (!hosts && seat != null && newRole != BusRole.NODE) {
                dropSeat(DropReason.HOST_GONE);
            }
            busId = newBusId;
            role = newRole;
            busSeatIndex = index;
            displayOf = newDisplay;
            sync();
        }
        if (role == BusRole.HOST) {
            busCabinets = Math.max(1, cabinets);
            busScreens = List.copyOf(screens);
            MatchHost current = host;
            ResolvedGame game = game();
            if (current != null && game != null) {
                current.setBusSeats(MatchHost.busSeatsFor(game, busCabinets));
            }
        } else {
            busCabinets = 1;
            busScreens = List.of();
        }
    }

    /**
     * Leaves bus {@code fromBus} (hub broken or gone, cable cut, multiplayer disabled): back to solo. A host whose
     * running match has players from other cabinets ends it ABANDONED — it cannot go on with ghost seats.
     */
    public void leaveBus(long fromBus) {
        if (busId != fromBus || fromBus == 0) {
            return;
        }
        if (role == BusRole.HOST && host != null && host.busSeatsInMatch()) {
            host.end(MatchResult.Outcome.ABANDONED);
        }
        if (role == BusRole.NODE && seat != null) {
            dropSeat(DropReason.HOST_GONE);
        }
        role = BusRole.SOLO;
        busId = 0;
        busSeatIndex = -1;
        displayOf = null;
        busCabinets = 1;
        busScreens = List.of();
        if (host != null) {
            host.setBusSeats(1);
        }
        sync();
    }

    private String dimension(ServerLevel serverLevel) {
        return serverLevel.dimension().identifier().toString();
    }

    /** Every 20 ticks: a bus whose hub is gone (unloaded, broken) makes this cabinet solo (003 §3.3). */
    private void checkBus(ServerLevel serverLevel) {
        if (role == BusRole.SOLO || busId == 0) {
            return;
        }
        if (BusRegistry.SERVER.get(dimension(serverLevel), busId) == null) {
            leaveBus(busId);
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
        insertedAt = level == null ? 0L : level.getGameTime();
        touchBus();
        sync();
    }

    /** Takes the cartridge out, ending any match and dropping the seat. */
    ItemStack takeCartridge() {
        ItemStack out = cartridge;
        sleepNow(DropReason.BROKEN);
        cartridge = ItemStack.EMPTY;
        readability = null;
        insertedAt = Long.MAX_VALUE;
        touchBus();
        sync();
        return out;
    }

    /** The bus re-elects its host on the hub's next tick (a cartridge came or went). */
    private void touchBus() {
        if (busId != 0 && level instanceof ServerLevel serverLevel) {
            BusRegistry.SERVER.markDirty(dimension(serverLevel), busId);
        }
    }

    // --- seats ------------------------------------------------------------------------------------

    /** Result of a seat request, for {@link CabinetInteraction}'s message. */
    enum SeatResult {
        SEATED,
        QUEUED,
        TAKEN,
        IN_PROGRESS,
        NOT_READY,
        /** A node on a bus with no host (or a full bus). */
        NO_HOST,
        /** Holds a cartridge on a bus that already has a host. */
        CONFLICT,
        /** This cabinet's bus seat is beyond what the game seats. */
        NO_SEAT_HERE,
        /** A hot-seat game is played at the host cabinet only. */
        HOT_SEAT_HOST
    }

    /**
     * Wakes the bus host if needed (or queues it at the awake cap) and binds {@code player} to this cabinet's bus
     * seat. The caller has checked the registry and power.
     */
    SeatResult seat(ServerPlayer player) {
        ServerLevel serverLevel = (ServerLevel) level;
        if (seat != null) {
            return SeatResult.TAKEN;
        }
        switch (role) {
            case FULL, NODE_NO_HOST -> {
                return SeatResult.NO_HOST;
            }
            case CONFLICT -> {
                return SeatResult.CONFLICT;
            }
            default -> {
            }
        }
        ArcadeCabinetBlockEntity hostBe = role == BusRole.NODE ? hostCabinet() : this;
        if (hostBe == null || hostBe.game() == null) {
            return role == BusRole.NODE ? SeatResult.NO_HOST : SeatResult.NOT_READY;
        }
        if (role == BusRole.NODE && HotSeat.is(hostBe.game())) {
            return SeatResult.HOT_SEAT_HOST;
        }
        int index = role == BusRole.NODE ? busSeatIndex : 0;
        MatchHost current = hostBe.host;
        if (current != null && current.phase().running() && !current.hotSeat()) {
            return SeatResult.IN_PROGRESS;
        }
        if (!hostBe.awake) {
            AwakeCabinets.Grant grant = AwakeCabinets.SERVER.request(hostBe);
            if (grant == AwakeCabinets.Grant.QUEUED) {
                hostBe.queued = true;
                hostBe.queuedFor = player.getUUID();
                return SeatResult.QUEUED;
            }
            hostBe.awake = true;
            hostBe.queued = false;
        }
        if (hostBe.host == null && !hostBe.startHost(serverLevel)) {
            return SeatResult.NOT_READY;
        }
        current = hostBe.host;
        if (index < 0 || index >= current.seatCount()) {
            return SeatResult.NO_SEAT_HERE;
        }
        Seat candidate = new Seat(index, player.getUUID(), serverLevel.getGameTime(), worldPosition);
        if (!SeatRegistry.SERVER.claim(player.getUUID(), seatKey(serverLevel))) {
            return SeatResult.TAKEN;
        }
        if (!current.bind(index, candidate)) {
            SeatRegistry.SERVER.release(player.getUUID(), seatKey(serverLevel));
            return current.phase().running() ? SeatResult.IN_PROGRESS : SeatResult.TAKEN;
        }
        seat = candidate;
        hostBe.sleep.reset();
        return SeatResult.SEATED;
    }

    /** The queue position while waiting for an awake slot (the host's), else 0. */
    int queuePosition() {
        ArcadeCabinetBlockEntity hostBe = role == BusRole.NODE ? hostCabinet() : this;
        return hostBe != null && hostBe.queued ? AwakeCabinets.SERVER.queuePosition(hostBe) : 0;
    }

    private boolean startHost(ServerLevel serverLevel) {
        ResolvedGame game = game();
        if (game == null || role == BusRole.NODE || role == BusRole.CONFLICT) {
            return false;
        }
        env = new LevelHostEnv(serverLevel, worldPosition, ArcadeCabinetBlockEntity::powerAvailable,
                () -> busScreens);
        int seats = role == BusRole.HOST ? MatchHost.busSeatsFor(game, busCabinets) : 1;
        host = new MatchHost(env, worldPosition, game, Boards.writer(serverLevel.getServer()), seats);
        lastPhase = host.phase();
        sleep.reset();
        return true;
    }

    /** Stage 2 has no energy buffer: with {@code cabinetsNeedPower} on, a cabinet has no power (002 §11). */
    static boolean powerAvailable() {
        return !NeroArcadeConfig.CABINETS_NEED_POWER.get();
    }

    public SeatRegistry.SeatKey seatKey(ServerLevel serverLevel) {
        return new SeatRegistry.SeatKey(dimension(serverLevel), worldPosition.asLong());
    }

    // --- tick -------------------------------------------------------------------------------------

    /** Server tick, lower half only (the block's ticker). */
    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        long time = serverLevel.getGameTime();
        if (firstTick) {
            firstTick = false;
            // a loaded cabinet with a cached bus asks its hub to pick it up again (003 §3.3)
            BusRegistry.SERVER.markDirty(dimension(serverLevel), busId);
            BusRegistry.SERVER.touch(dimension(serverLevel), worldPosition);
        }
        if (time % 20 == 0) {
            checkBus(serverLevel);
        }
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
            if (sleep.tick(current.hasSeat(), nearby, Budgets.sleepAfterTicks())) {
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
        if (role == BusRole.NODE && matchHost() == null) {
            dropSeat(DropReason.HOST_GONE);
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

    /** The phase of the match this cabinet shows (its own, or its host's for a node); null when asleep. */
    public @Nullable BusPhase shownPhase() {
        MatchHost match = matchHost();
        return match == null ? null : match.phase();
    }

    /** The status strip per 003 §3.6. */
    public CabinetStatus computeStatus() {
        switch (role) {
            case FULL, NODE_NO_HOST -> {
                return CabinetStatus.LINKED;
            }
            case CONFLICT -> {
                return CabinetStatus.LOCKED;
            }
            case NODE -> {
                ArcadeCabinetBlockEntity h = hostCabinet();
                if (h == null) {
                    return CabinetStatus.LINKED;
                }
                return CabinetStatus.of(true, true, powerAvailable(), h.host == null ? null : h.host.phase());
            }
            default -> {
            }
        }
        if (cartridge.isEmpty()) {
            return CabinetStatus.of(false, false, powerAvailable(), null);
        }
        Readability r = readability();
        MatchHost current = host;
        return CabinetStatus.of(true, r != null && r.readable(), powerAvailable(), current == null ? null : current.phase());
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
                .setValue(ArcadeBlockProperties.LINKED, role.linked());
        if (wanted != state) {
            serverLevel.setBlock(worldPosition, wanted, Block.UPDATE_CLIENTS);
        }
    }

    // --- removal ----------------------------------------------------------------------------------

    /**
     * Real removal only (never chunk unload): end the match, drop the seat with a message, free the slot, drop the
     * cartridge item and make the bus rediscover.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        sleepNow(DropReason.BROKEN);
        if (level instanceof ServerLevel serverLevel) {
            BusRegistry.SERVER.markDirty(dimension(serverLevel), busId);
            BusRegistry.SERVER.touch(dimension(serverLevel), pos);
        }
        if (level != null && !cartridge.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, cartridge);
            cartridge = ItemStack.EMPTY;
        }
    }

    /**
     * Block removal and chunk unload: world-inert bookkeeping only (a world read here can reload the chunk being
     * unloaded). The seat and the slot are memory, so releasing them is safe; the match is simply discarded (nodes
     * notice their host is gone on their next seat check).
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
        long display = input.getLongOr(KEY_DISPLAY_OF, 0L);
        clientDisplayOf = display == 0L ? null : BlockPos.of(display);
        clientRole = BusRole.byOrdinal(input.getIntOr(KEY_ROLE, 0));
    }

    /** Client side: the synced view of the cartridge for the screen renderer and the seated player's prediction. */
    public CabinetClientInfo clientInfo() {
        return clientInfo;
    }

    /** Client side: the host this node mirrors, or null. */
    public @Nullable BlockPos clientDisplayOf() {
        return clientDisplayOf;
    }

    /** Client side: this cabinet's bus role. */
    public BusRole clientRole() {
        return clientRole;
    }

    /**
     * What the client renderer needs and nothing more: the title, the theme tint, whether the cartridge is
     * readable, and for a bus node the host it mirrors. Never the cartridge itself (it carries the author UUID).
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
        if (displayOf != null) {
            tag.putLong(KEY_DISPLAY_OF, displayOf.asLong());
        }
        tag.putInt(KEY_ROLE, role.ordinal());
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
