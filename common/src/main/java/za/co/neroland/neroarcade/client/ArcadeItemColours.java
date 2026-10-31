package za.co.neroland.neroarcade.client;

import net.minecraft.world.item.ItemStack;

import za.co.neroland.neroarcade.item.CartridgeItem;
import za.co.neroland.neroarcade.item.CartridgeTint;
import za.co.neroland.neroarcade.item.ModuleChips;
import za.co.neroland.neroarcade.item.StoredCartridge;
import za.co.neroland.neroarcade.registry.ModItems;

/**
 * Item tints for the Minecraft lines before 1.21.4, which have no item model definitions ({@code assets/neroarcade/
 * items/*.json}) to carry them: the same colours those files give, computed from the stack's own data and handed to
 * each loader's item-colour registration. Layer 0 is never tinted; the cartridge band (layer 1) takes the
 * cartridge's theme tint, a module chip's band and glyph (layers 1 and 2) its slot colour, and the blank items'
 * band the neutral grey. CLIENT-ONLY.
 */
public final class ArcadeItemColours {

    private static final int WHITE = 0xFFFFFF;

    private ArcadeItemColours() {
    }

    /** Opaque ARGB colour of {@code stack}'s layer {@code tintIndex}. */
    public static int colour(ItemStack stack, int tintIndex) {
        return 0xFF000000 | rgb(stack, tintIndex);
    }

    private static int rgb(ItemStack stack, int tintIndex) {
        if (stack.is(ModItems.CARTRIDGE.get())) {
            if (tintIndex != 1) {
                return WHITE;
            }
            StoredCartridge stored = CartridgeItem.storedOf(stack);
            return stored == null ? CartridgeTint.GREY
                    : stored.tryDecode().map(CartridgeTint::of).orElse(CartridgeTint.GREY);
        }
        if (stack.is(ModItems.MODULE_CHIP.get())) {
            var module = ModuleChips.moduleOf(stack);
            if (module.isEmpty()) {
                return tintIndex == 1 ? CartridgeTint.GREY : WHITE;
            }
            return tintIndex == 1 || tintIndex == 2 ? module.get().type().colour : WHITE;
        }
        if (stack.is(ModItems.BLANK_CARTRIDGE.get()) || stack.is(ModItems.BLANK_CHIP.get())) {
            return tintIndex == 1 ? CartridgeTint.GREY : WHITE;
        }
        return WHITE;
    }
}
