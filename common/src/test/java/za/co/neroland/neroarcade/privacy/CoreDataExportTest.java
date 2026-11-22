package za.co.neroland.neroarcade.privacy;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CoreDataExportTest {
    @AfterEach
    void clear() {
        za.co.neroland.nerolandcore.data.PlayerDataExport.clearForTests();
    }

    @Test
    void registersUnderTheModId() {
        ArcadePrivacy.registerCoreExport();
        assertTrue(za.co.neroland.nerolandcore.data.PlayerDataExport.registeredIds().contains("neroarcade"));
    }
}
