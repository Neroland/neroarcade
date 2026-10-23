package za.co.neroland.neroarcade.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.List;
import java.util.Optional;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.TestFiles;
import za.co.neroland.neroarcade.game.ArcadeSchema;
import za.co.neroland.neroarcade.game.GameDef;

/** The cartridge tooltip shows no raw ids to players (the lineage root stays in the inspect command). */
class CartridgeTooltipsTest {

    private static GameDef def(List<String> lineage) {
        return new GameDef(ArcadeSchema.CURRENT, "qqqqqqqqqqqqqqqq", "Remix", Optional.empty(), Optional.empty(),
                lineage, Optional.empty(), new EnumMap<>(za.co.neroland.neroarcade.module.ModuleType.class), 0L, 1);
    }

    @Test
    void remixLineHasNoId() {
        assertTrue(CartridgeTooltips.remixLine(def(List.of())).isEmpty(), "an original game says nothing");
        Component line = CartridgeTooltips.remixLine(def(List.of("k7m2p9qa3f8d1z6b"))).orElseThrow();
        TranslatableContents contents = (TranslatableContents) line.getContents();
        assertEquals("item.neroarcade.cartridge.remix", contents.getKey());
        assertEquals(0, contents.getArgs().length, "no argument: never the lineage root id");
        String text = TestFiles.lang().get("item.neroarcade.cartridge.remix");
        assertEquals("Remixed from another game", text);
        assertFalse(text.contains("%"));
    }
}
