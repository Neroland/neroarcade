package za.co.neroland.neroarcade.module.hook;

import net.minecraft.resources.Identifier;

/**
 * What a THEME chip chooses: ids only. The client resolves them to a palette, sprites and sounds; the server
 * never loads them and the simulation never reads them.
 */
public record ThemeSpec(Identifier paletteId, Identifier spriteSetId, Identifier soundStart, Identifier soundPickup,
        Identifier soundEnd) {
}
