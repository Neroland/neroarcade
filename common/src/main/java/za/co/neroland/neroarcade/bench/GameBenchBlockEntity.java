package za.co.neroland.neroarcade.bench;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroarcade.block.ArcadeBlockProperties;
import za.co.neroland.neroarcade.cabinet.AwakeCabinets;
import za.co.neroland.neroarcade.cabinet.BoardWriter;
import za.co.neroland.neroarcade.cabinet.DropReason;
import za.co.neroland.neroarcade.cabinet.LevelHostEnv;
import za.co.neroland.neroarcade.cabinet.MatchHost;
import za.co.neroland.neroarcade.cabinet.Seat;
import za.co.neroland.neroarcade.cabinet.SeatHolder;
import za.co.neroland.neroarcade.cabinet.SeatRegistry;
import za.co.neroland.neroarcade.config.NeroArcadeConfig;
import za.co.neroland.neroarcade.game.CartridgeValidator;
import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.item.ItemGiving;
import za.co.neroland.neroarcade.item.ModuleChips;
import za.co.neroland.neroarcade.module.BenchWarning;
import za.co.neroland.neroarcade.module.BenchWarnings;
import za.co.neroland.neroarcade.network.BenchStatePayload;
import za.co.neroland.neroarcade.platform.Services;
import za.co.neroland.neroarcade.registry.ModBlockEntities;
import za.co.neroland.neroarcade.registry.ModItems;
import za.co.neroland.neroarcade.runtime.MatchResult;

/**
 * The Game Bench: a 15-slot container (14 rack slots + the cartridge tray, {@link BenchSlots}), the
 * {@link BenchDraft} being built and, during TEST PLAY, a temporary solo {@link MatchHost} with a bench-backed
 * seat — the same host the cabinet uses, so a test play is exactly what the cabinet will run.
 *
 * <p><b>Persisted:</b> the container, the draft (title, values, origin) and the ghost mask. <b>Runtime only:</b>
 * the test host and its seat. Chips are templates and never consumed; LOAD-materialised chips ("ghosts") are
 * never dropped on break and cannot be taken out.
 */
public class GameBenchBlockEntity extends BlockEntity implements MenuProvider, SeatHolder {

    private static final String KEY_DRAFT = "draft";
    private static final String KEY_GHOSTS = "ghosts";

    private final SimpleContainer inventory = new SimpleContainer(BenchSlots.SIZE) {
        @Override
        public void setChanged() {
            super.setChanged();
            onInventoryChanged();
        }
    };

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case GameBenchMenu.DATA_TESTING -> testHost != null ? 1 : 0;
                case GameBenchMenu.DATA_GHOST_MASK -> ghostMask;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // server-authoritative: the client never writes bench data
        }

        @Override
        public int getCount() {
            return GameBenchMenu.DATA_COUNT;
        }
    };

    private final BenchDraft draft = new BenchDraft();
    private int ghostMask;
    private boolean muted;
    private boolean dirty = true;
    private String statusKey = "";
    private List<String> statusArgs = List.of();

    private @Nullable MatchHost testHost;
    private @Nullable Seat testSeat;
    private int rejectedInputs;

    public GameBenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GAME_BENCH.get(), pos, state);
    }

    public SimpleContainer inventory() {
        return inventory;
    }

    public ContainerData dataAccess() {
        return dataAccess;
    }

    public BenchDraft draft() {
        return draft;
    }

    public int ghostMask() {
        return ghostMask;
    }

    // --- inventory --------------------------------------------------------------------------------

    private void onInventoryChanged() {
        if (muted) {
            return;
        }
        int occupied = 0;
        for (int i = 0; i < BenchSlots.CHIP_SLOTS; i++) {
            if (!inventory.getItem(i).isEmpty()) {
                occupied |= 1 << i;
            }
        }
        ghostMask &= occupied;
        draft.syncChips(chipIds());
        markDirty();
    }

    /** Module id per rack slot (null = empty or not a known chip). */
    Identifier[] chipIds() {
        Identifier[] ids = new Identifier[BenchSlots.CHIP_SLOTS];
        for (int i = 0; i < BenchSlots.CHIP_SLOTS; i++) {
            ids[i] = ModuleChips.idOf(inventory.getItem(i)).orElse(null);
        }
        return ids;
    }

    /** Puts LOAD-materialised chips into the rack without the per-slot change clearing the draft's values. */
    void materialise(BenchDraft loaded, Player player) {
        muted = true;
        try {
            int ghosts = 0;
            for (int i = 0; i < BenchSlots.CHIP_SLOTS; i++) {
                ItemStack current = inventory.getItem(i);
                boolean ghost = (ghostMask & (1 << i)) != 0;
                if (!current.isEmpty() && !ghost) {
                    ItemGiving.give(player, current);
                }
                Identifier id = loaded.chipAt(i);
                ItemStack chip = id == null ? ItemStack.EMPTY
                        : za.co.neroland.neroarcade.module.ModuleRegistry.get(id).map(ModuleChips::stackFor)
                                .orElse(ItemStack.EMPTY);
                inventory.setItem(i, chip);
                if (!chip.isEmpty()) {
                    ghosts |= 1 << i;
                }
            }
            ghostMask = ghosts;
            draft.copyFrom(loaded);
        } finally {
            muted = false;
        }
        markDirty();
    }

    /** CLEAR_SLOT: removes a ghost chip (a real chip is taken out by hand). */
    boolean clearGhost(int index) {
        if (!BenchSlots.isChipSlot(index) || (ghostMask & (1 << index)) == 0) {
            return false;
        }
        ghostMask &= ~(1 << index);
        inventory.setItem(index, ItemStack.EMPTY);
        return true;
    }

    // --- state to clients -------------------------------------------------------------------------

    void setStatus(String key, String... args) {
        statusKey = key == null ? "" : key;
        statusArgs = List.of(args);
        markDirty();
    }

    void markDirty() {
        dirty = true;
        setChanged();
    }

    /** The preview draft, its warnings and the trough line. Validation refusals become the status line. */
    public BenchStatePayload statePayload() {
        GameDef preview = draft.preview();
        List<BenchWarning> warnings = List.of();
        String key = statusKey;
        List<String> args = statusArgs;
        try {
            warnings = BenchWarnings.evaluate(CartridgeValidator.validate(preview));
        } catch (ValidationError refused) {
            if (key.isEmpty()) {
                key = refused.langKey;
                args = refused.args;
            }
        }
        ItemStack tray = inventory.getItem(BenchSlots.TRAY);
        boolean canSave = tray.is(ModItems.BLANK_CARTRIDGE.get()) || tray.is(ModItems.CARTRIDGE.get());
        return new BenchStatePayload(worldPosition, preview, warnings, ghostMask, key, args, testHost != null,
                canSave);
    }

    /** Sends the draft to everyone who has this bench open. */
    void sendState(ServerLevel serverLevel) {
        BenchStatePayload payload = null;
        for (ServerPlayer player : serverLevel.players()) {
            if (player.containerMenu instanceof GameBenchMenu menu && menu.bench() == this) {
                if (payload == null) {
                    payload = statePayload();
                }
                Services.NETWORK.sendToPlayer(player, payload);
            }
        }
    }

    /** Server tick: pending state, the editing light, a running test play and its seat. */
    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (dirty) {
            dirty = false;
            sendState(serverLevel);
        }
        if (serverLevel.getGameTime() % 10 == 0) {
            mirrorBlockState(serverLevel);
            validateSeat(serverLevel);
        }
        MatchHost host = testHost;
        if (host != null) {
            host.tick();
        }
    }

    private void mirrorBlockState(ServerLevel serverLevel) {
        BlockState state = getBlockState();
        if (!state.hasProperty(ArcadeBlockProperties.EDITING)) {
            return;
        }
        boolean editing = false;
        for (ServerPlayer player : serverLevel.players()) {
            if (player.containerMenu instanceof GameBenchMenu menu && menu.bench() == this) {
                editing = true;
                break;
            }
        }
        BlockState wanted = state.setValue(ArcadeBlockProperties.EDITING, editing || testHost != null)
                .setValue(ArcadeBlockProperties.CARTRIDGE, !inventory.getItem(BenchSlots.TRAY).isEmpty());
        if (wanted != state) {
            serverLevel.setBlock(worldPosition, wanted, Block.UPDATE_CLIENTS);
        }
    }

    void onMenuClosed() {
        markDirty();
    }

    // --- test play (SeatHolder) -------------------------------------------------------------------

    @Override
    public @Nullable MatchHost matchHost() {
        return testHost;
    }

    @Override
    public @Nullable Seat heldSeat() {
        return testSeat;
    }

    @Override
    public void countRejectedInput() {
        rejectedInputs++;
    }

    public int rejectedInputs() {
        return rejectedInputs;
    }

    /**
     * TEST PLAY: the draft runs as a solo match on this bench, seat 0, for the player who pressed it. Refused when
     * the draft does not validate, the player sits elsewhere, a test already runs or the awake cap is reached.
     */
    void startTest(ServerPlayer player) {
        ServerLevel serverLevel = (ServerLevel) level;
        if (testHost != null) {
            setStatus("neroarcade.bench.status.testing");
            return;
        }
        ResolvedGame game;
        try {
            game = CartridgeValidator.validate(draft.preview());
        } catch (ValidationError refused) {
            setStatus(refused.langKey, refused.args.toArray(String[]::new));
            return;
        }
        SeatRegistry.SeatKey key = seatKey(serverLevel);
        if (!SeatRegistry.SERVER.claim(player.getUUID(), key)) {
            setStatus("neroarcade.bench.status.seated_elsewhere");
            return;
        }
        if (AwakeCabinets.SERVER.request(this) == AwakeCabinets.Grant.QUEUED) {
            AwakeCabinets.SERVER.release(this);
            SeatRegistry.SERVER.release(player.getUUID(), key);
            setStatus("neroarcade.bench.status.busy");
            return;
        }
        LevelHostEnv env = new LevelHostEnv(serverLevel, worldPosition, () -> true);
        MatchHost host = MatchHost.solo(env, worldPosition, game, BoardWriter.NONE);
        Seat seat = new Seat(0, player.getUUID(), serverLevel.getGameTime());
        testHost = host;
        testSeat = seat;
        if (!host.bind(0, seat)) {
            stopTest(DropReason.ESC);
            setStatus("neroarcade.bench.status.busy");
            return;
        }
        setStatus("neroarcade.bench.status.test_started");
    }

    /** STOP_TEST / leave / range / break: the test match ends (never written anywhere) and the seat frees. */
    void stopTest(DropReason why) {
        dropSeat(why);
    }

    @Override
    public void dropSeat(DropReason why) {
        MatchHost host = testHost;
        Seat seat = testSeat;
        testHost = null;
        testSeat = null;
        if (host != null) {
            if (seat != null) {
                host.unbind(seat.seat, why);
            }
            host.end(MatchResult.Outcome.ABANDONED);
        }
        AwakeCabinets.SERVER.release(this);
        if (seat != null && level instanceof ServerLevel serverLevel) {
            SeatRegistry.SERVER.release(seat.playerUuid, seatKey(serverLevel));
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(seat.playerUuid);
            if (player != null && why != DropReason.ESC) {
                player.sendOverlayMessage(Component.translatable(why.langKey()));
            }
        }
        if (host != null) {
            markDirty();
        }
    }

    private void validateSeat(ServerLevel serverLevel) {
        Seat seat = testSeat;
        if (seat == null) {
            return;
        }
        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(seat.playerUuid);
        if (player == null) {
            dropSeat(DropReason.LOGOUT);
            return;
        }
        double range = Math.max(NeroArcadeConfig.SEAT_RANGE.get(), 8.0);
        if (player.level() != serverLevel
                || player.getEyePosition().distanceToSqr(Vec3.atCenterOf(worldPosition)) > range * range) {
            dropSeat(DropReason.RANGE);
        }
    }

    public SeatRegistry.SeatKey seatKey(ServerLevel serverLevel) {
        return new SeatRegistry.SeatKey(serverLevel.dimension().identifier().toString(), worldPosition.asLong());
    }

    // --- menu ---------------------------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.neroarcade.game_bench");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        markDirty();
        return new GameBenchMenu(containerId, playerInventory, this);
    }

    // --- removal ------------------------------------------------------------------------------------

    /** Real removal: end a test play and drop every real item (never a LOAD-materialised chip). */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        dropSeat(DropReason.BROKEN);
        if (level != null) {
            for (int i = 0; i < BenchSlots.SIZE; i++) {
                boolean ghost = BenchSlots.isChipSlot(i) && (ghostMask & (1 << i)) != 0;
                ItemStack stack = inventory.getItem(i);
                if (!ghost && !stack.isEmpty()) {
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
                }
            }
            muted = true;
            inventory.clearContent();
            muted = false;
        }
    }

    /** Removal and unload: memory-only cleanup. */
    @Override
    public void setRemoved() {
        if (testSeat != null && level instanceof ServerLevel serverLevel) {
            SeatRegistry.SERVER.release(testSeat.playerUuid, seatKey(serverLevel));
        }
        testSeat = null;
        testHost = null;
        AwakeCabinets.SERVER.release(this);
        super.setRemoved();
    }

    // --- persistence --------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, inventory.getItems());
        output.store(KEY_DRAFT, BenchDraft.CODEC, draft);
        output.putInt(KEY_GHOSTS, ghostMask);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        muted = true;
        try {
            inventory.clearContent();
            ContainerHelper.loadAllItems(input, inventory.getItems());
            input.read(KEY_DRAFT, BenchDraft.CODEC).ifPresent(draft::copyFrom);
            ghostMask = input.getIntOr(KEY_GHOSTS, 0) & 0x3FFF;
        } finally {
            muted = false;
        }
        draft.syncChips(chipIds());
        dirty = true;
    }
}
