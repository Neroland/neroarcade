package za.co.neroland.neroarcade.cabinet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.nbt.NbtOps;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.TestFiles;
import za.co.neroland.neroarcade.bench.BenchDraft;
import za.co.neroland.neroarcade.game.GameDef;

/**
 * Compliance review F3: the cabinet and Game Bench update tags reach every client in range, and no client draws a
 * builder credit there, so the encoded {@code def}/preview carries neither the author UUID nor the credit string.
 * The source pins check both {@code getUpdateTag}s still encode through the stripping methods.
 */
class UpdateTagCarriesNoAuthorTest {

    private static final UUID AUTHOR = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final String CREDIT = "Alice_Builds";

    private static GameDef game() {
        return new GameDef(1, "abcdefghijklmnop", "Orb Dodge", Optional.of(AUTHOR), Optional.of(CREDIT), List.of(),
                Optional.empty(), Map.of(), 100L, 3);
    }

    private static void assertAnonymous(String what, GameDef def) {
        assertTrue(def.authorUuid().isEmpty(), what + ": no author UUID");
        assertTrue(def.authorCredit().isEmpty(), what + ": no credit");
        String nbt = GameDef.CODEC.encodeStart(NbtOps.INSTANCE, def).getOrThrow().toString();
        assertFalse(nbt.contains(AUTHOR.toString()), what + " encodes the author UUID: " + nbt);
        assertFalse(nbt.contains(CREDIT), what + " encodes the credit: " + nbt);
        assertFalse(nbt.toLowerCase(java.util.Locale.ROOT).contains("credit"), what + " has a credit field: " + nbt);
        assertTrue(nbt.contains("Orb Dodge"), what + " still carries the title the screen draws");
    }

    @Test
    void cabinetUpdateTagDefIsAnonymous() {
        assertAnonymous("cabinet def", CabinetClientInfo.anonymous(game()));
        CabinetClientInfo info = new CabinetClientInfo(true, "Orb Dodge", 0, true, "", Optional.of(game()));
        assertAnonymous("cabinet client info", info.def().orElseThrow());
    }

    @Test
    void benchUpdateTagPreviewIsAnonymous() {
        BenchDraft draft = BenchDraft.fromGameDef(game());
        assertTrue(draft.holdsAuthor(AUTHOR), "the server-side draft still knows its origin");
        assertAnonymous("bench preview", draft.preview());
    }

    @Test
    void bothUpdateTagsEncodeThroughTheStrippingMethods() throws IOException {
        Path main = TestFiles.repoRoot().resolve("common/src/main/java/za/co/neroland/neroarcade");
        String cabinet = updateTagBody(main.resolve("cabinet/ArcadeCabinetBlockEntity.java"));
        assertTrue(cabinet.contains("CabinetClientInfo.anonymous(def)"), "cabinet update tag uses anonymous()");
        String bench = updateTagBody(main.resolve("bench/GameBenchBlockEntity.java"));
        assertTrue(bench.contains("draft.preview()"), "bench update tag uses preview()");
        assertFalse(bench.contains("draft.meta()"), "bench update tag never reads the raw meta");
    }

    private static String updateTagBody(Path file) throws IOException {
        String src = Files.readString(file);
        int start = src.indexOf("public CompoundTag getUpdateTag(");
        assertTrue(start >= 0, file + " declares getUpdateTag");
        int end = src.indexOf("\n    }\n", start);
        return src.substring(start, end < 0 ? src.length() : end);
    }
}
