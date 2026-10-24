package za.co.neroland.neroarcade.privacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.UUID;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Stage 6: {@code /neroarcade privacy export}. The document of a player with nothing stored has every section, empty;
 * the file path cannot escape the export folder; the cooldown allows one export per window and refunds a failure; the
 * retention sweep deletes files older than seven days.
 */
class PrivacyExportTest {

    private static final long NOW = 1_800_000_000_000L;

    @TempDir
    Path dir;

    @Test
    void anEmptyPlayersDocumentHasEverySection() {
        UUID who = UUID.randomUUID();
        JsonObject doc = PrivacyExport.build(who, null, null, null, new PrivacyExport.Settings(90, 365, 30),
                PrivacyExport.Live.NONE, NOW);
        assertEquals("neroarcade", doc.get("mod").getAsString());
        assertEquals(PrivacyExport.FORMAT_VERSION, doc.get("format").getAsInt());
        assertEquals(who.toString(), doc.get("subject").getAsString());
        for (String section : new String[] {"privacy", "personal_bests", "listings", "drafts", "live", "notes"}) {
            assertTrue(doc.has(section), section);
        }
        assertEquals(0, doc.getAsJsonArray("personal_bests").size());
        assertFalse(doc.getAsJsonObject("privacy").get("credit_opt_in").getAsBoolean());
        assertEquals(90, doc.getAsJsonObject("privacy").get("leaderboard_retention_days").getAsInt());
    }

    @Test
    void thePathStaysInsideTheExportFolder() {
        assertThrows(IllegalStateException.class, () -> PrivacyExport.file(dir, "../escape"));
        assertThrows(IllegalStateException.class, () -> PrivacyExport.file(dir, "a/b"));
        UUID who = UUID.randomUUID();
        assertEquals(dir.resolve(who + ".json"), PrivacyExport.file(dir, who));
        assertEquals("neroarcade/exports/" + who + ".json", PrivacyExport.relativePath(who));
    }

    @Test
    void writeDeleteAndRetention() throws IOException {
        UUID who = UUID.randomUUID();
        PrivacyExport.write(dir, who, new JsonObject());
        assertTrue(PrivacyExport.exists(dir, who));
        assertEquals(0, PrivacyExport.purgeExpired(dir, System.currentTimeMillis()), "a fresh file stays");
        Files.setLastModifiedTime(PrivacyExport.file(dir, who),
                FileTime.fromMillis(System.currentTimeMillis() - 8 * 86_400_000L));
        assertEquals(1, PrivacyExport.purgeExpired(dir, System.currentTimeMillis()), "older than 7 days goes");
        assertFalse(PrivacyExport.exists(dir, who));
        PrivacyExport.write(dir, who, new JsonObject());
        assertTrue(PrivacyExport.delete(dir, who));
        assertFalse(PrivacyExport.delete(dir, who), "deleting twice is fine");
        assertEquals(0, PrivacyExport.purgeExpired(null, NOW));
    }

    @Test
    void cooldownAllowsOnePerWindowAndRefundsFailures() {
        ExportCooldown cooldown = new ExportCooldown();
        UUID who = UUID.randomUUID();
        assertEquals(0, cooldown.tryAcquire(who, NOW, 60_000L));
        assertEquals(60, cooldown.tryAcquire(who, NOW, 60_000L));
        assertEquals(1, cooldown.tryAcquire(who, NOW + 59_500L, 60_000L));
        assertEquals(0, cooldown.tryAcquire(who, NOW + 60_000L, 60_000L), "the window passed");
        cooldown.refund(who);
        assertFalse(cooldown.holds(who));
        assertEquals(0, cooldown.tryAcquire(who, NOW + 61_000L, 60_000L), "a refunded slot is free again");
        cooldown.forget(who);
        assertEquals(0, cooldown.size());
    }

    @Test
    void theExportsEraserDeletesTheFileAndTheCooldown() throws IOException {
        UUID who = UUID.randomUUID();
        PrivacyExport.overrideDirectory(dir);
        try {
            PrivacyExport.write(dir, who, new JsonObject());
            ArcadePrivacy.COOLDOWN.tryAcquire(who, NOW, 60_000L);
            ArcadePrivacy.EXPORTS_ERASER.erase(null, who);
            ArcadePrivacy.EXPORTS_ERASER.backupNow(null);
            assertFalse(PrivacyExport.exists(dir, who));
            assertFalse(ArcadePrivacy.COOLDOWN.holds(who));
        } finally {
            PrivacyExport.overrideDirectory(null);
            ArcadePrivacy.COOLDOWN.clear();
        }
    }
}
