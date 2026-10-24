package za.co.neroland.neroarcade.client.exchange;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.Identifier;

import za.co.neroland.neroarcade.exchange.ExchangePages;
import za.co.neroland.neroarcade.module.ArcadeModule;
import za.co.neroland.neroarcade.module.ModuleRegistry;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.network.ExchangePagePayload;

/**
 * What the Exchange pane's ten slot cells say (004 §2, §8: never colour alone), pure so tests can read it: each
 * cell's tooltip is the slot word, then its chips' names joined by {@code " + "} — or "Empty — the game uses the
 * default". The screen turns these keys into components.
 */
public final class ExchangeSlotTips {

    /** The second line of an empty slot's tooltip. */
    public static final String SLOT_DEFAULT_KEY = "neroarcade.exchange.slot_default";
    /** The name shown for a chip id this client does not know. */
    public static final String UNKNOWN_CHIP_KEY = "item.neroarcade.module_chip.unknown";

    private ExchangeSlotTips() {
    }

    /** Whether slot {@code index} of {@code row} holds a chip. */
    public static boolean filled(ExchangePagePayload.Row row, int index) {
        return !row.chipsIn(index).isEmpty() || row.slotColours()[index] != ExchangePages.EMPTY_SLOT;
    }

    /** Line 1 of slot {@code index}'s tooltip: the slot word's key. */
    public static String slotKey(int index) {
        return ModuleType.values()[index].langKey();
    }

    /**
     * Line 2 of slot {@code index}'s tooltip as lang keys: one per chip (joined by {@code " + "} on screen), or the
     * single {@link #SLOT_DEFAULT_KEY} when the slot is empty.
     */
    public static List<String> chipKeys(ExchangePagePayload.Row row, int index) {
        List<String> chips = row.chipsIn(index);
        if (chips.isEmpty()) {
            return List.of(SLOT_DEFAULT_KEY);
        }
        List<String> keys = new ArrayList<>(chips.size());
        for (String id : chips) {
            keys.add(chipLangKey(id));
        }
        return keys;
    }

    /** A chip id's name key: the registered module's, else {@link #UNKNOWN_CHIP_KEY}. */
    public static String chipLangKey(String id) {
        Identifier parsed = Identifier.tryParse(id);
        if (parsed == null) {
            return UNKNOWN_CHIP_KEY;
        }
        return ModuleRegistry.get(parsed).map(ArcadeModule::langKey).orElse(UNKNOWN_CHIP_KEY);
    }
}
