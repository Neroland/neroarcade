package za.co.neroland.neroarcade.exchange;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroarcade.registry.ModBlockEntities;

/**
 * The Arcade Exchange kiosk's lower half: a one-slot download tray (saved with the block, dropped when it breaks)
 * and the door to the {@link ExchangeMenu}. The listings themselves live in the world's {@link ExchangeStore}, so
 * every kiosk shows the same Exchange.
 */
public class ArcadeExchangeBlockEntity extends BlockEntity implements MenuProvider {

    private final SimpleContainer tray = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            ArcadeExchangeBlockEntity.this.setChanged();
        }
    };

    public ArcadeExchangeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ARCADE_EXCHANGE.get(), pos, state);
    }

    public SimpleContainer tray() {
        return tray;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.neroarcade.arcade_exchange");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ExchangeMenu(containerId, playerInventory, this);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            ItemStack stack = tray.getItem(0);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            }
            tray.clearContent();
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, tray.getItems());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tray.clearContent();
        ContainerHelper.loadAllItems(input, tray.getItems());
    }
}
