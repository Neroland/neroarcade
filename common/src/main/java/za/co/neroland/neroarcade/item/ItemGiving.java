package za.co.neroland.neroarcade.item;

import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Hands an item to a player, dropping what does not fit at their feet. One helper because {@code Player#drop}
 * changed signature in 26.3; {@link Containers#dropItemStack} is the same on every line.
 */
public final class ItemGiving {

    private ItemGiving() {
    }

    public static void give(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), stack);
        }
    }
}
