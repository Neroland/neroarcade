package za.co.neroland.neroarcade.compat;

//? if <1.20.5 {
/*import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;

public final class LegacyInventory {
    private LegacyInventory() {
    }

    public static void save(CompoundTag tag, Container inventory) {
        NonNullList<ItemStack> items = NonNullList.withSize(inventory.getContainerSize(), ItemStack.EMPTY);
        for (int slot = 0; slot < items.size(); slot++) {
            items.set(slot, inventory.getItem(slot));
        }
        ContainerHelper.saveAllItems(tag, items);
    }

    public static void load(CompoundTag tag, Container inventory) {
        NonNullList<ItemStack> items = NonNullList.withSize(inventory.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items);
        for (int slot = 0; slot < items.size(); slot++) {
            if (!items.get(slot).isEmpty()) {
                inventory.setItem(slot, items.get(slot));
            }
        }
    }
}
*///?}
