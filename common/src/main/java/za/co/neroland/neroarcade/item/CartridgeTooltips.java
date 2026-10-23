package za.co.neroland.neroarcade.item;

import java.util.Optional;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import za.co.neroland.neroarcade.game.GameDef;

/** Cartridge tooltip lines that need no item or registry, so a test can hold them. Pure. */
public final class CartridgeTooltips {

    private CartridgeTooltips() {
    }

    /**
     * "Remixed from another game" for a cartridge with a lineage — never a raw id (review 005a F12); the lineage
     * root stays in {@code /neroarcade cartridge inspect}.
     */
    public static Optional<Component> remixLine(GameDef def) {
        if (def.lineage().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Component.translatable("item.neroarcade.cartridge.remix").withStyle(ChatFormatting.DARK_GRAY));
    }
}
