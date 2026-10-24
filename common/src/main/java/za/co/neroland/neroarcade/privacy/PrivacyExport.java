package za.co.neroland.neroarcade.privacy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.board.LeaderboardStore;
import za.co.neroland.neroarcade.board.LeaderboardTable;
import za.co.neroland.neroarcade.exchange.Draft;
import za.co.neroland.neroarcade.exchange.ExchangeStore;
import za.co.neroland.neroarcade.exchange.Listing;

/**
 * The data-subject access export (GDPR Art. 15/20, POPIA s23) behind {@code /neroarcade privacy export}: everything
 * NeroArcade holds about <b>one player</b>, as one JSON file at {@code <world>/neroarcade/exports/<uuid>.json}.
 *
 * <p>Never another player's data: the subject's own personal bests and top rows (rank, score, time and the name the
 * row carries), the Exchange listings they published or authored, their own drafts, their credit setting, and
 * <em>counts</em> of what the running server holds about them in memory. The path is built from the subject's own
 * UUID and checked to stay inside the export folder; on a POSIX file system the folder and file are restricted to the
 * server's own user. An export is deleted by an erasure request (store {@code "exports"}) and after
 * {@link #RETENTION_DAYS} days. Failures are logged by exception class only, because the real message holds the path,
 * and the file name is the UUID.
 */
public final class PrivacyExport {

    public static final int FORMAT_VERSION = 1;
    /** Export files are deleted this many days after they were written. */
    public static final int RETENTION_DAYS = 7;
    /** Relative to the world folder; shown to the player. */
    public static final String RELATIVE_DIR = "neroarcade/exports";

    private static final long DAY_MS = 86_400_000L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Set<PosixFilePermission> OWNER_FILE = PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> OWNER_DIRECTORY = PosixFilePermissions.fromString("rwx------");

    /** In-memory facts about the subject, as counts only. */
    public record Live(boolean seated, int heartsGivenThisSession) {

        public static final Live NONE = new Live(false, 0);
    }

    /** The privacy settings in force, for the document's {@code privacy} block. */
    public record Settings(int leaderboardRetentionDays, int personalBestRetentionDays, int draftRetentionDays) {
    }

    private PrivacyExport() {
    }

    // --- the document (pure) ---------------------------------------------------------------------------

    /**
     * The export document for {@code subject} (plain-JVM testable). Stores may be null (an absent store holds
     * nothing). Other players' rows, listings and drafts are never read into it.
     */
    public static JsonObject build(UUID subject, @Nullable LeaderboardStore boards, @Nullable ExchangeStore exchange,
            @Nullable CreditOptIn credit, Settings settings, Live live, long nowMillis) {
        JsonObject root = new JsonObject();
        root.addProperty("mod", NeroArcadeCommon.MOD_ID);
        root.addProperty("format", FORMAT_VERSION);
        root.addProperty("exported_at", Instant.ofEpochMilli(nowMillis).toString());
        root.addProperty("subject", subject.toString());

        JsonObject privacy = new JsonObject();
        privacy.addProperty("credit_opt_in", credit != null && credit.isOnHere(subject));
        privacy.addProperty("leaderboard_retention_days", settings.leaderboardRetentionDays());
        privacy.addProperty("personal_best_retention_days", settings.personalBestRetentionDays());
        privacy.addProperty("draft_retention_days", settings.draftRetentionDays());
        privacy.addProperty("play_history", false);
        root.add("privacy", privacy);

        JsonArray bests = new JsonArray();
        if (boards != null) {
            for (LeaderboardTable.Board board : boards.boardsWithBest(subject)) {
                JsonObject out = new JsonObject();
                out.addProperty("cartridge", board.cartridgeId());
                out.addProperty("best", board.personalBest().get(subject));
                Optional<Long> setAt = boards.bestSetAt(board.cartridgeId(), subject).filter(at -> at >= 0);
                if (setAt.isPresent()) {
                    out.addProperty("best_set_at", Instant.ofEpochSecond(setAt.get()).toString());
                } else {
                    out.add("best_set_at", JsonNull.INSTANCE);
                }
                out.add("top_row", topRow(board, subject));
                bests.add(out);
            }
        }
        root.add("personal_bests", bests);

        JsonArray listings = new JsonArray();
        JsonArray drafts = new JsonArray();
        if (exchange != null) {
            for (Listing l : exchange.browse()) {
                boolean publisher = l.authorUuid().equals(subject);
                boolean author = l.gameDef().authorUuid().filter(subject::equals).isPresent();
                if (!publisher && !author) {
                    continue; // other players' listings are theirs
                }
                JsonObject out = new JsonObject();
                out.addProperty("listing", l.listingId());
                out.addProperty("cartridge", l.cartridgeId());
                out.addProperty("title", l.title());
                out.addProperty("revision", l.gameDef().revision());
                out.addProperty("published_at", Instant.ofEpochSecond(l.publishedAt()).toString());
                out.addProperty("plays", l.plays());
                out.addProperty("hearts", l.hearts());
                JsonArray tags = new JsonArray();
                l.genreTags().forEach(tags::add);
                out.add("genre_tags", tags);
                if (l.credit().isPresent()) {
                    out.addProperty("credit", l.credit().get());
                } else {
                    out.add("credit", JsonNull.INSTANCE);
                }
                // A listing's author is the game's author (ExchangeRules.listingAuthor), so this is always "author"
                // in this version; "publisher" remains only for a legacy row that names the subject as lister of
                // someone else's game (compliance review F4).
                out.addProperty("you_are", author ? "author" : "publisher");
                listings.add(out);
            }
            for (Draft d : exchange.drafts(subject)) {
                JsonObject out = new JsonObject();
                out.addProperty("cartridge", d.cartridgeId());
                out.addProperty("title", d.gameDef().title());
                out.addProperty("created_at", Instant.ofEpochSecond(d.createdAt()).toString());
                out.addProperty("deleted_after", Instant.ofEpochSecond(d.createdAt()
                        + settings.draftRetentionDays() * 86_400L).toString());
                drafts.add(out);
            }
        }
        root.add("listings", listings);
        root.add("drafts", drafts);

        JsonObject liveBlock = new JsonObject();
        liveBlock.addProperty("seated", live.seated());
        liveBlock.addProperty("hearts_given_this_session", live.heartsGivenThisSession());
        root.add("live", liveBlock);

        JsonObject notes = new JsonObject();
        notes.addProperty("others", "No other player appears in this file. Plays and hearts on a listing are "
                + "aggregate counts; the server keeps no record of who played or hearted.");
        notes.addProperty("items", "Cartridges are items. A cartridge you saved carries your player id (and your "
                + "name only if credit was on) wherever it is: inventories, chests, a cabinet, the duplicator, or a "
                + "Game Bench's draft. The server cannot list items, so they are not in this file.");
        notes.addProperty("milestones", "Your arcade milestones (first cartridge, first published, first versus "
                + "win) are yes/no flags in Neroland Core's progression store; Core exports and erases them.");
        notes.addProperty("erasure", "'/neroland data eraseme' removes everything above, unpublishes your "
                + "listings, replaces you as the author of any other stored game with an anonymous marker, and "
                + "deletes this file.");
        notes.addProperty("retention", "This file is deleted after " + RETENTION_DAYS + " days.");
        root.add("notes", notes);
        return root;
    }

    /** The subject's own top row on a board (rank, score, time, the name it carries), or JSON null. */
    private static com.google.gson.JsonElement topRow(LeaderboardTable.Board board, UUID subject) {
        List<LeaderboardTable.Entry> top = new ArrayList<>(board.top());
        int rank = 0;
        int lastScore = Integer.MIN_VALUE;
        for (int i = 0; i < top.size(); i++) {
            LeaderboardTable.Entry e = top.get(i);
            if (i == 0 || e.score() != lastScore) {
                rank = i + 1;
                lastScore = e.score();
            }
            if (e.uuid().equals(subject)) {
                JsonObject row = new JsonObject();
                row.addProperty("rank", rank);
                row.addProperty("score", e.score());
                row.addProperty("at", Instant.ofEpochSecond(e.at()).toString());
                if (e.name() != null) {
                    row.addProperty("name", e.name());
                } else {
                    row.add("name", JsonNull.INSTANCE);
                }
                return row;
            }
        }
        return JsonNull.INSTANCE;
    }

    public static String toJson(JsonObject document) {
        return GSON.toJson(document);
    }

    // --- files -------------------------------------------------------------------------------------------

    private static volatile @Nullable Path directoryOverride;

    /** Test seam: the export folder the erasers and the sweep use without a server ({@code null} restores). */
    public static void overrideDirectory(@Nullable Path directory) {
        directoryOverride = directory;
    }

    /** The export folder of {@code server} (or the test override); null without either. */
    public static @Nullable Path directory(@Nullable MinecraftServer server) {
        Path forced = directoryOverride;
        if (forced != null) {
            return forced;
        }
        return server == null ? null : directory(server.getWorldPath(LevelResource.ROOT));
    }

    public static Path directory(Path worldRoot) {
        return worldRoot.resolve("neroarcade").resolve("exports").normalize();
    }

    /** One subject's export path, refusing anything that escapes the export folder. */
    public static Path file(Path directory, UUID subject) {
        return file(directory, String.valueOf(subject));
    }

    /** As {@link #file(Path, UUID)} for a raw name (the path-safety check is tested with hostile names). */
    static Path file(Path directory, String name) {
        Path root = directory.normalize();
        Path output = root.resolve(name + ".json").normalize();
        if (!output.startsWith(root) || output.getParent() == null || !output.getParent().equals(root)) {
            throw new IllegalStateException("Unsafe export path");
        }
        return output;
    }

    /** The path the player is told, relative to the world folder. */
    public static String relativePath(UUID subject) {
        return RELATIVE_DIR + "/" + subject + ".json";
    }

    /** Writes the document, replacing an earlier export. Server thread. */
    public static String write(Path directory, UUID subject, JsonObject document) throws IOException {
        Path output = file(directory, subject);
        Files.createDirectories(directory);
        try {
            Files.setPosixFilePermissions(directory, OWNER_DIRECTORY);
        } catch (UnsupportedOperationException | IOException bestEffort) {
            // Not POSIX (Windows), or not ours to re-mode: the platform defaults apply.
        }
        try {
            if (Files.exists(output)) {
                Files.setPosixFilePermissions(output, OWNER_FILE);
            } else {
                Files.createFile(output, PosixFilePermissions.asFileAttribute(OWNER_FILE));
            }
        } catch (UnsupportedOperationException notPosix) {
            // No POSIX permission view on this file system.
        }
        Files.writeString(output, toJson(document), StandardCharsets.UTF_8);
        return relativePath(subject);
    }

    /** Erasure: an export must not outlive the data it was taken from. Never throws. */
    public static boolean delete(@Nullable Path directory, UUID subject) {
        if (directory == null || subject == null) {
            return false;
        }
        try {
            return Files.deleteIfExists(file(directory, subject));
        } catch (IOException | RuntimeException failure) {
            NeroArcadeCommon.LOGGER.warn("[NeroArcade] an export file could not be deleted ({}).",
                    failure.getClass().getName());
            return false;
        }
    }

    /** Conformance probe: does an export file exist for {@code subject}? */
    public static boolean exists(@Nullable Path directory, UUID subject) {
        try {
            return directory != null && subject != null && Files.exists(file(directory, subject));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Retention: deletes export files older than {@link #RETENTION_DAYS}. Returns how many. Never throws. */
    public static int purgeExpired(@Nullable Path directory, long nowMillis) {
        if (directory == null || !Files.isDirectory(directory)) {
            return 0;
        }
        long cutoff = nowMillis - RETENTION_DAYS * DAY_MS;
        int removed = 0;
        try (Stream<Path> listing = Files.list(directory)) {
            for (Path file : listing.toList()) {
                if (Files.isRegularFile(file) && file.getFileName().toString().endsWith(".json")
                        && Files.getLastModifiedTime(file).toMillis() <= cutoff && Files.deleteIfExists(file)) {
                    removed++;
                }
            }
        } catch (IOException | RuntimeException failure) {
            NeroArcadeCommon.LOGGER.warn("[NeroArcade] export retention sweep failed ({}).",
                    failure.getClass().getName());
        }
        return removed;
    }
}
