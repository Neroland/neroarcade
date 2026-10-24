package za.co.neroland.neroarcade.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.TestFiles;

/**
 * A textual guard on the gallery: none of its sources writes a leaderboard, counts a play, fires a milestone, a
 * quest hook, a threshold or a link event, reads a player's name or logs at INFO; and the safety checks are in place
 * (creative, nobody near, no inventory erased, cartridges discarded not dropped, listings taken down on clear).
 */
class GallerySafetyScanTest {

    private static final List<String> FORBIDDEN = List.of("Boards.", "LeaderboardStore", "BoardWriter",
            "countPlay", "ArcadeMilestones", "ArcadeMatchEnds", "QuestHooks", "ThresholdEvents", "ProgressionGates",
            "ArcadeEvents", "NeroArcadeLinkModule", "getName()", "getGameProfile", "getScoreboardName",
            "LOGGER.info", "CoinOp", "Tournaments");

    private static Path root() {
        return TestFiles.repoRoot().resolve("common/src/main/java/za/co/neroland/neroarcade");
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    @Test
    void theGalleryTouchesNothingReal() throws IOException {
        List<Path> files = new ArrayList<>();
        try (var list = Files.list(root().resolve("command"))) {
            list.filter(p -> p.getFileName().toString().startsWith("Gallery")).forEach(files::add);
        }
        files.add(root().resolve("client/GalleryCapture.java"));
        assertTrue(files.size() >= 4, "the scan found the gallery sources: " + files);
        List<String> offenders = new ArrayList<>();
        for (Path file : files) {
            String text = read(file);
            for (String word : FORBIDDEN) {
                if (text.contains(word)) {
                    offenders.add(file.getFileName() + ": " + word);
                }
            }
        }
        assertTrue(offenders.isEmpty(), "the gallery reaches real state: " + offenders);
    }

    @Test
    void theSafetyChecksArePresent() throws IOException {
        String commands = read(root().resolve("command/GalleryCommands.java"));
        assertTrue(commands.contains("instabuild"), "creative only");
        assertTrue(commands.contains("OTHERS_RANGE") && commands.contains("others_nearby"), "nobody else near");
        assertTrue(commands.contains("occupied(") && commands.contains("getBlockEntity"),
                "refuses to build over a block with contents");
        assertTrue(commands.contains("showcaseDiscard"), "the clear never drops a cartridge");
        assertTrue(commands.contains("unpublishBy(") && commands.contains("GALLERY_AUTHOR"),
                "the clear takes the gallery's listings down");
        assertTrue(commands.contains("is(change.placed().getBlock())"),
                "the clear only restores where the gallery's own block still stands");
        String tree = read(root().resolve("command/ArcadeCommands.java"));
        int gallery = tree.indexOf("literal(\"gallery\")");
        assertTrue(gallery > 0 && tree.indexOf("LEVEL_GAMEMASTERS", gallery) > gallery
                && tree.indexOf("LEVEL_GAMEMASTERS", gallery) < tree.indexOf("literal(\"admin\")"),
                "operators only");
        String capture = read(root().resolve("client/GalleryCapture.java"));
        assertTrue(capture.contains("\"nsarcgallery\"") && capture.contains("\"nsarcber\""),
                "client roots never equal the server root");
        assertTrue(capture.contains("kill @e[type=item,distance=..64]"));
        assertTrue(capture.contains("advance_time false") && capture.contains("advance_weather false"));
    }
}
