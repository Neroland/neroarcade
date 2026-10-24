package za.co.neroland.neroarcade.exchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.module.TestModules;

/** The kiosk listing screen's rows: the top eight in browse order, a short title and a heart count, nothing else. */
class KioskBoardTest {

    @Test
    void topRowsInBrowseOrderTitlesAndHeartsOnly() {
        List<Listing> browse = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            browse.add(new Listing(i + 1, TestModules.def(java.util.Map.of()), UUID.randomUUID(),
                    Optional.of("Builder" + i), List.of(), 3, 1000 - i, 0L));
        }
        List<KioskBoard.Line> rows = KioskBoard.of(browse);
        assertEquals(KioskBoard.ROWS, rows.size());
        assertEquals(KioskBoard.MAX_HEARTS, rows.get(0).hearts(), "capped to three digits");
        for (KioskBoard.Line row : rows) {
            assertTrue(row.title().length() <= KioskBoard.TITLE_CHARS, row.title());
            assertTrue(row.title().length() + 1 + Integer.toString(row.hearts()).length() <= 10,
                    "fits the 10-character screen line");
            assertTrue(!row.title().contains("Builder"), "no credit ever reaches the kiosk");
        }
        assertEquals(List.of(), KioskBoard.of(List.of()));
    }

    @Test
    void screenTitlesUseTheFontsCharacters() {
        assertEquals("SERPEN", KioskBoard.screenTitle("Serpent Garden"));
        assertEquals("CAF?", KioskBoard.screenTitle("Café"));
        assertEquals("", KioskBoard.screenTitle(null));
    }
}
