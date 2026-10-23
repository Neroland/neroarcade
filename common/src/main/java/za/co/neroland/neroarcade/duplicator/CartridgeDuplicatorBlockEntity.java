package za.co.neroland.neroarcade.duplicator;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
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

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.neroarcade.block.ArcadeBlockProperties;
import za.co.neroland.neroarcade.config.NeroArcadeConfig;
import za.co.neroland.neroarcade.game.CartridgeValidator;
import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.item.CartridgeItem;
import za.co.neroland.neroarcade.item.StoredCartridge;
import za.co.neroland.neroarcade.registry.ModBlockEntities;
import za.co.neroland.neroarcade.registry.ModItems;
import za.co.neroland.neroarcade.registry.ModSounds;

/**
 * The Cartridge Duplicator (003 §7). An original and a Blank Cartridge go in; {@code [COPY]} runs for
 * {@link DuplicatorRules#COPY_TICKS} ticks with {@code copying=true} (the BER spins the platter); then the original
 * and one identical copy come out in the two output slots — the same id, lineage, revision, author and credit.
 * With {@code cabinetsNeedPower} on, a copy needs {@link DuplicatorRules#COPY_ENERGY} FE at its start; the
 * duplicator has no energy buffer in 0.1.0 (like the cabinets), so it then refuses with "needs power".
 */
public class CartridgeDuplicatorBlockEntity extends BlockEntity implements MenuProvider {

    private static final String KEY_PROGRESS = "progress";
    private static final int SPIN_INTERVAL = 40;

    private final SimpleContainer inventory = new SimpleContainer(DuplicatorMenu.SIZE) {
        @Override
        public void setChanged() {
            super.setChanged();
            CartridgeDuplicatorBlockEntity.this.setChanged();
            syncState();
        }
    };

    private int progress;
    private int status;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DuplicatorMenu.DATA_PROGRESS -> progress;
                case DuplicatorMenu.DATA_STATUS -> status;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // server-authoritative
        }

        @Override
        public int getCount() {
            return DuplicatorMenu.DATA_COUNT;
        }
    };

    public CartridgeDuplicatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CARTRIDGE_DUPLICATOR.get(), pos, state);
    }

    public SimpleContainer inventory() {
        return inventory;
    }

    public ContainerData dataAccess() {
        return dataAccess;
    }

    /** Power for one copy: free unless {@code cabinetsNeedPower} (no buffer in 0.1.0, so then never). */
    static boolean powered() {
        return !NeroArcadeConfig.CABINETS_NEED_POWER.get();
    }

    private Optional<GameDef> originalGame() {
        StoredCartridge stored = CartridgeItem.storedOf(inventory.getItem(DuplicatorMenu.SLOT_ORIGINAL));
        if (stored == null) {
            return Optional.empty();
        }
        try {
            GameDef def = stored.decode();
            CartridgeValidator.validate(def);
            return Optional.of(def);
        } catch (ValidationError unreadable) {
            return Optional.empty();
        }
    }

    /** {@code [COPY]}: starts a copy or records why not. */
    void startCopy() {
        Optional<DuplicatorRules.Deny> deny = DuplicatorRules.check(originalGame(),
                inventory.getItem(DuplicatorMenu.SLOT_ORIGINAL).is(ModItems.CARTRIDGE.get()),
                inventory.getItem(DuplicatorMenu.SLOT_BLANK).is(ModItems.BLANK_CARTRIDGE.get()),
                inventory.getItem(DuplicatorMenu.SLOT_OUT_ORIGINAL).isEmpty()
                        && inventory.getItem(DuplicatorMenu.SLOT_OUT_COPY).isEmpty(),
                powered(), progress > 0);
        if (deny.isPresent()) {
            status = DuplicatorMenu.STATUS_DENY_BASE + deny.get().ordinal();
            play(ModSounds.CABINET_DENY);
            return;
        }
        status = DuplicatorMenu.STATUS_NONE;
        progress = 1;
        play(ModSounds.get("duplicator.spin"));
        setChanged();
        syncState();
    }

    public void serverTick() {
        if (progress <= 0) {
            return;
        }
        progress++;
        if (progress % SPIN_INTERVAL == 0 && progress < DuplicatorRules.COPY_TICKS) {
            play(ModSounds.get("duplicator.spin"));
        }
        if (progress >= DuplicatorRules.COPY_TICKS) {
            finishCopy();
        }
    }

    private void finishCopy() {
        progress = 0;
        Optional<GameDef> original = originalGame();
        ItemStack blank = inventory.getItem(DuplicatorMenu.SLOT_BLANK);
        if (original.isEmpty() || !blank.is(ModItems.BLANK_CARTRIDGE.get())
                || !inventory.getItem(DuplicatorMenu.SLOT_OUT_ORIGINAL).isEmpty()
                || !inventory.getItem(DuplicatorMenu.SLOT_OUT_COPY).isEmpty()) {
            status = DuplicatorMenu.STATUS_DENY_BASE + DuplicatorRules.Deny.NEED_ORIGINAL.ordinal();
            syncState();
            return;
        }
        ItemStack copy;
        try {
            copy = CartridgeItem.create(DuplicatorRules.copyOf(original.get()));
        } catch (ValidationError tooLarge) {
            status = DuplicatorMenu.STATUS_DENY_BASE + DuplicatorRules.Deny.UNREADABLE.ordinal();
            syncState();
            return;
        }
        ItemStack originalStack = inventory.getItem(DuplicatorMenu.SLOT_ORIGINAL);
        inventory.setItem(DuplicatorMenu.SLOT_ORIGINAL, ItemStack.EMPTY);
        inventory.setItem(DuplicatorMenu.SLOT_OUT_ORIGINAL, originalStack);
        blank.shrink(1);
        inventory.setItem(DuplicatorMenu.SLOT_BLANK, blank.isEmpty() ? ItemStack.EMPTY : blank);
        inventory.setItem(DuplicatorMenu.SLOT_OUT_COPY, copy);
        status = DuplicatorMenu.STATUS_DONE;
        play(ModSounds.CABINET_INSERT);
        setChanged();
        syncState();
    }

    private void play(@Nullable RegistryEntry<SoundEvent> sound) {
        if (sound != null && level instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, worldPosition, sound.get(), SoundSource.BLOCKS, 0.7F, 1.0F);
        }
    }

    /** {@code copying}, {@code original}, {@code blank} follow the slots. */
    private void syncState() {
        if (!(level instanceof ServerLevel serverLevel) || isRemoved()) {
            return;
        }
        BlockState state = getBlockState();
        if (!state.hasProperty(ArcadeBlockProperties.COPYING)) {
            return;
        }
        BlockState wanted = state.setValue(ArcadeBlockProperties.COPYING, progress > 0)
                .setValue(ArcadeBlockProperties.ORIGINAL,
                        !inventory.getItem(DuplicatorMenu.SLOT_ORIGINAL).isEmpty()
                                || !inventory.getItem(DuplicatorMenu.SLOT_OUT_ORIGINAL).isEmpty())
                .setValue(ArcadeBlockProperties.BLANK,
                        !inventory.getItem(DuplicatorMenu.SLOT_BLANK).isEmpty()
                                || !inventory.getItem(DuplicatorMenu.SLOT_OUT_COPY).isEmpty());
        if (wanted != state) {
            serverLevel.setBlock(worldPosition, wanted, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.neroarcade.cartridge_duplicator");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        if (progress == 0) {
            status = DuplicatorMenu.STATUS_NONE;
        }
        return new DuplicatorMenu(containerId, playerInventory, this);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            for (int i = 0; i < DuplicatorMenu.SIZE; i++) {
                ItemStack stack = inventory.getItem(i);
                if (!stack.isEmpty()) {
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
                }
            }
            inventory.clearContent();
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, inventory.getItems());
        output.putInt(KEY_PROGRESS, progress);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        for (int i = 0; i < DuplicatorMenu.SIZE; i++) {
            inventory.getItems().set(i, ItemStack.EMPTY);
        }
        ContainerHelper.loadAllItems(input, inventory.getItems());
        progress = Math.max(0, Math.min(DuplicatorRules.COPY_TICKS, input.getIntOr(KEY_PROGRESS, 0)));
    }
}
