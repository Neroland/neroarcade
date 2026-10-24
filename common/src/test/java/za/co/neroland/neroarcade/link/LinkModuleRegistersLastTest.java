package za.co.neroland.neroarcade.link;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.TestFiles;
import za.co.neroland.neroarcade.data.NeroArcadeData;

/**
 * 007 §1: the link module is registered as the very last statement of {@code NeroArcadeCommon.init()}; a registry
 * that throws (an older or missing Core link API) is swallowed and leaves no eraser behind; {@code link.enabled=false}
 * registers nothing. The discovery entry is the five sections and the two actions.
 */
class LinkModuleRegistersLastTest {

    @Test
    void registeredLastInCommonInit() {
        String source = TestFiles.read(TestFiles.repoRoot()
                .resolve("common/src/main/java/za/co/neroland/neroarcade/NeroArcadeCommon.java"));
        Matcher init = Pattern.compile("public static void init\\(\\) \\{(.*)\\n    }", Pattern.DOTALL).matcher(source);
        assertTrue(init.find());
        List<String> statements = init.group(1).lines().map(String::trim)
                .filter(l -> !l.isEmpty() && !l.startsWith("//")).toList();
        assertEquals("NeroArcadeLinkModule.init();", statements.get(statements.size() - 1));
    }

    @Test
    void throwingRegistryIsSwallowed() {
        int before = NeroArcadeData.storeCount();
        boolean[] result = new boolean[1];
        assertDoesNotThrow(() -> result[0] = NeroArcadeLinkModule.init((s, a, i) -> {
            throw new NoSuchMethodError("older Core");
        }, true));
        assertFalse(result[0]);
        // the registry was reached, so the eraser stays with any provider that may be live; tidy up for other tests
        NeroArcadeData.StoreEraser left = NeroArcadeLinkModule.activeEraser;
        if (left != null) {
            NeroArcadeData.removeStore(left);
            NeroArcadeLinkModule.activeEraser = null;
        }
        assertEquals(before, NeroArcadeData.storeCount());
        assertFalse(NeroArcadeLinkModule.isRegistered());
    }

    @Test
    void disabledRegistersNothing() {
        int before = NeroArcadeData.storeCount();
        boolean[] called = new boolean[1];
        assertFalse(NeroArcadeLinkModule.init((s, a, i) -> called[0] = true, false));
        assertFalse(called[0]);
        assertEquals(before, NeroArcadeData.storeCount());
    }

    @Test
    void discoveryEntry() {
        var info = ArcadeLinkInfo.info(() -> "0.1.0-beta.1");
        assertEquals("neroarcade", info.moduleId());
        assertEquals(1, info.schemaVersion());
        assertEquals(List.of("library", "leaderboard", "cabinets", "events", "wiki"), info.dataSections());
        assertEquals(List.of("export_cartridge", "import_cartridge"), info.actionIds());
        assertEquals("unknown", ArcadeLinkInfo.info(() -> {
            throw new IllegalStateException();
        }).modVersion());
    }
}
