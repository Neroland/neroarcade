package za.co.neroland.neroarcade.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NbtOps;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import za.co.neroland.nerolandcore.data.ErasureConformance;
import za.co.neroland.nerolandcore.data.PlayerDataErasure;

import za.co.neroland.neroarcade.bench.BenchActions;
import za.co.neroland.neroarcade.bench.BenchDraft;
import za.co.neroland.neroarcade.bench.LoadedBenches;
import za.co.neroland.neroarcade.board.BoardErasureFixture;
import za.co.neroland.neroarcade.board.Boards;
import za.co.neroland.neroarcade.board.LeaderboardStore;
import za.co.neroland.neroarcade.cabinet.SeatRegistry;
import za.co.neroland.neroarcade.exchange.Exchange;
import za.co.neroland.neroarcade.exchange.ExchangeActions;
import za.co.neroland.neroarcade.exchange.ExchangeStore;
import za.co.neroland.neroarcade.exchange.ExchangeTable;
import za.co.neroland.neroarcade.exchange.Listing;
import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.link.LinkErasureFixture;
import za.co.neroland.neroarcade.privacy.ArcadePrivacy;
import za.co.neroland.neroarcade.privacy.BackupHook;
import za.co.neroland.neroarcade.privacy.CreditOptIn;
import za.co.neroland.neroarcade.privacy.PrivacyExport;
import za.co.neroland.neroarcade.privacy.Tombstones;
import za.co.neroland.neroarcade.server.ArcadeServer;

/**
 * Stage 6 gate: POPIA/GDPR <b>erasure conformance</b> for NeroArcade, run through Core's {@link ErasureConformance}
 * harness against the <b>real</b> erasers and real store objects (swapped in with each store's {@code ACCESS}
 * override, so no server is needed), and a manual erasure walked end to end.
 *
 * <p>The subject has: personal bests on two boards and an opted-in board name, a credit flag, a listing they
 * published, a listing an operator published of their game, their own draft and another player's draft of their
 * game, a heart, rate windows at the bench and the kiosk, a seat, a loaded Game Bench whose draft names them, a privacy
 * export file and its cooldown, and the link module's transient state. A bystander holds the same shapes and must keep
 * them. After erasure nothing about the subject remains in any store <b>or in any refreshed backup</b> (the
 * {@link BackupHook} recorder captures exactly what {@code SavedDataRecovery.backupNow} would write).
 *
 * <p>Plain JVM, {@code server} is {@code null}. Core's isolation pass deliberately logs one "Data eraser ... failed"
 * warning per conformance run; that is the assertion working.
 */
class ArcadeErasureConformanceTest {

    private static final long NOW = 1_800_000_500_000L;

    @TempDir
    Path exports;

    private final UUID subject = UUID.randomUUID();
    private final UUID bystander = UUID.randomUUID();
    private final List<NeroArcadeData.StoreEraser> added = new ArrayList<>();
    /** store name -> every backup content refreshed, as text. */
    private final Map<String, List<String>> backups = new LinkedHashMap<>();
    private boolean coreRegistered;

    private LeaderboardStore boards;
    private CreditOptIn credit;
    private ExchangeStore exchange;
    private LinkErasureFixture.Seeded link;
    private BenchDraft subjectBench;
    private BenchDraft bystanderBench;
    // strong references: LoadedBenches holds benches weakly
    private LoadedBenches.AuthorHolder subjectHolder;
    private LoadedBenches.AuthorHolder bystanderHolder;

    @BeforeEach
    void seed() throws IOException {
        boards = BoardErasureFixture.seeded(subject, "Alice", bystander);
        LeaderboardStore.ACCESS.override(boards);

        credit = new CreditOptIn();
        credit.setForTest(subject, true);
        credit.setForTest(bystander, true);
        CreditOptIn.ACCESS.override(credit);

        ExchangeTable table = new ExchangeTable();
        table.publish(game(1, subject, "Alice"), subject, Optional.of("Alice"), List.of("grid"), 10, 200);
        table.publish(game(2, bystander, null), bystander, Optional.empty(), List.of("grid"), 11, 200);
        // an operator listed the subject's game under the bystander (op path): tombstoned, not deleted
        table.publish(game(5, subject, "Alice"), bystander, Optional.empty(), List.of("grid"), 12, 200, 0, true);
        table.addDraft(subject, game(4, subject, null), 20, 8);
        table.addDraft(bystander, game(3, subject, "Alice"), 21, 8);
        table.addDraft(bystander, game(6, bystander, null), 22, 8);
        exchange = ExchangeStore.of(table);
        ExchangeStore.ACCESS.override(exchange);

        int bystanderListing = table.listingByCartridgeId(id(2)).orElseThrow().listingId();
        int subjectListing = table.listingByCartridgeId(id(1)).orElseThrow().listingId();
        Exchange.HEARTS.tryHeart(subject, bystanderListing, NOW);
        Exchange.HEARTS.tryHeart(bystander, subjectListing, NOW);
        ExchangeActions.withinRate(subject, NOW);
        ExchangeActions.withinRate(bystander, NOW);
        BenchActions.withinRate(subject, 100L);
        BenchActions.withinRate(bystander, 100L);
        SeatRegistry.SERVER.claim(subject, new SeatRegistry.SeatKey("minecraft:overworld", 1L));
        SeatRegistry.SERVER.claim(bystander, new SeatRegistry.SeatKey("minecraft:overworld", 2L));

        subjectBench = BenchDraft.fromGameDef(game(7, subject, "Alice"));
        bystanderBench = BenchDraft.fromGameDef(game(8, bystander, null));
        subjectHolder = holder(subjectBench);
        bystanderHolder = holder(bystanderBench);
        LoadedBenches.add(subjectHolder);
        LoadedBenches.add(bystanderHolder);

        PrivacyExport.overrideDirectory(exports);
        PrivacyExport.write(exports, subject, PrivacyExport.build(subject, boards, exchange, credit,
                new PrivacyExport.Settings(90, 365, 30), PrivacyExport.Live.NONE, NOW));
        PrivacyExport.write(exports, bystander, PrivacyExport.build(bystander, boards, exchange, credit,
                new PrivacyExport.Settings(90, 365, 30), PrivacyExport.Live.NONE, NOW));
        ArcadePrivacy.COOLDOWN.tryAcquire(subject, NOW, 60_000L);
        ArcadePrivacy.COOLDOWN.tryAcquire(bystander, NOW, 60_000L);

        link = LinkErasureFixture.seed(subject, bystander);

        BackupHook.set((server, store, content, backup) ->
                backups.computeIfAbsent(store, k -> new ArrayList<>()).add(content.get().toString()));

        for (NeroArcadeData.StoreEraser e : allErasers()) {
            NeroArcadeData.addStore(e);
            added.add(e);
        }
    }

    @AfterEach
    void restore() {
        added.forEach(NeroArcadeData::removeStore);
        added.clear();
        if (coreRegistered) {
            PlayerDataErasure.unregister(NeroArcadeData.eraser());
        }
        LeaderboardStore.ACCESS.override(null);
        CreditOptIn.ACCESS.override(null);
        ExchangeStore.ACCESS.override(null);
        BackupHook.set(null);
        PrivacyExport.overrideDirectory(null);
        LoadedBenches.clear();
        SeatRegistry.SERVER.clear();
        Exchange.HEARTS.clear();
        ExchangeActions.clear();
        BenchActions.clear();
        ArcadePrivacy.COOLDOWN.clear();
    }

    /** Every eraser NeroArcade registers in production, in init order (the link module's over the seeded pipeline). */
    private List<NeroArcadeData.StoreEraser> allErasers() {
        List<NeroArcadeData.StoreEraser> all = new ArrayList<>();
        all.addAll(Boards.erasers());
        all.addAll(Exchange.erasers());
        all.addAll(ArcadePrivacy.erasers());
        all.addAll(ArcadeServer.erasers());
        all.add(link.eraser());
        return all;
    }

    private ErasureConformance probes() {
        return ErasureConformance.create()
                .probe("neroarcade:leaderboards", boards::holds)
                .probe("neroarcade:credit", credit::holds)
                .probe("neroarcade:exchange.listings", exchange::holdsAuthor)
                .probe("neroarcade:exchange.drafts", exchange::holdsDrafts)
                .probe("neroarcade:exchange.hearts", Exchange.HEARTS::holds)
                .probe("neroarcade:exchange.rate", ExchangeActions::holdsWindow)
                .probe("neroarcade:bench.rate", BenchActions::holdsWindow)
                .probe("neroarcade:seats", SeatRegistry.SERVER::isSeated)
                .probe("neroarcade:benches", LoadedBenches::holdsAuthor)
                .probe("neroarcade:exports.file", u -> PrivacyExport.exists(exports, u))
                .probe("neroarcade:exports.cooldown", ArcadePrivacy.COOLDOWN::holds)
                .probe("neroarcade:link", link.holds());
    }

    @Test
    void everyStoreIsErasedThroughCoreAndTheReportCarriesNoIdentity() {
        PlayerDataErasure.register(NeroArcadeData.eraser());
        coreRegistered = true;

        ErasureConformance.Report report = probes().run(null, subject);

        assertTrue(report.passed(), report.describe());
        assertTrue(report.retainedSubsystems().isEmpty());
        assertFalse(report.describe().contains(subject.toString()), "the report must never carry the UUID");
        assertBystanderKeepsEverything();
    }

    @Test
    void theRegisteredStoresAreExactlyTheInventory() {
        assertEquals(List.of("leaderboards", "credit", "exchange", "drafts", "benches", "exports", "seats", "link"),
                allErasers().stream().map(NeroArcadeData.StoreEraser::name).toList());
    }

    /**
     * The manual erasure, end to end: one request through the composite Core calls, then every store, every
     * refreshed backup, the bench NBT form and the export folder are checked for the subject's UUID and name.
     */
    @Test
    void manualErasureLeavesNothingInAnyStoreOrBackup() throws IOException {
        String uuid = subject.toString();
        assertTrue(boards.saveForm().toString().contains(uuid) && boards.saveForm().toString().contains("Alice"),
                "seeded: the board names the subject");
        assertTrue(SeatRegistry.SERVER.isSeated(subject) && subjectBench.holdsAuthor(subject), "seeded");

        NeroArcadeData.eraser().erase(null, subject);

        // every persisted store refreshed its backup after erasing, and no backup holds the subject
        assertEquals(Set.of("leaderboards", "credit", "exchange"), backups.keySet());
        assertEquals(2, backups.get("exchange").size(), "listings and drafts each refresh the exchange backup");
        // each refresh overwrites the store's one backup file, so the last refresh is what stays on disk (the
        // exchange's first refresh, after the listings step, still holds the drafts the next step erases)
        backups.forEach((store, contents) -> {
            String last = contents.get(contents.size() - 1);
            assertFalse(last.contains(uuid), store + " backup still names the subject");
            assertFalse(last.contains("Alice"), store + " backup still carries the subject's name");
        });
        assertTrue(backups.get("leaderboards").get(0).contains(bystander.toString()), "the bystander's rows stay");

        // the stores themselves (what the next save writes)
        for (String form : List.of(boards.saveForm().toString(), credit.saveForm().toString(),
                exchange.saveForm().toString())) {
            assertFalse(form.contains(uuid));
        }
        assertFalse(boards.holds(subject));
        assertTrue(boards.board(BoardErasureFixture.SECOND).isEmpty(), "a board left empty is dropped");
        assertFalse(credit.holds(subject));

        // the Exchange: own listing unpublished, the op-listed copy tombstoned, drafts gone or tombstoned
        assertTrue(exchange.listingByCartridgeId(id(1)).isEmpty(), "the subject's listing is unpublished");
        Listing opListed = exchange.listingByCartridgeId(id(5)).orElseThrow();
        assertEquals(Optional.of(Tombstones.ERASED_AUTHOR), opListed.gameDef().authorUuid());
        assertEquals(Optional.empty(), opListed.gameDef().authorCredit(), "credit set to anonymous");
        assertEquals(Optional.empty(), opListed.credit());
        assertTrue(exchange.drafts(subject).isEmpty());
        assertEquals(Optional.of(Tombstones.ERASED_AUTHOR),
                exchange.draft(bystander, id(3)).orElseThrow().gameDef().authorUuid());

        // the bench's block NBT form
        assertEquals(Optional.of(Tombstones.ERASED_AUTHOR), subjectBench.meta().author());
        assertEquals(Optional.empty(), subjectBench.meta().credit());
        String benchNbt = BenchDraft.CODEC.encodeStart(NbtOps.INSTANCE, subjectBench).getOrThrow().toString();
        assertFalse(benchNbt.contains(uuid) || benchNbt.contains("Alice"));

        // memory-only state and the export file
        assertFalse(SeatRegistry.SERVER.isSeated(subject));
        assertFalse(Exchange.HEARTS.holds(subject));
        assertFalse(ExchangeActions.holdsWindow(subject) || BenchActions.holdsWindow(subject));
        assertFalse(Files.exists(PrivacyExport.file(exports, subject)), "the export file is deleted");
        assertFalse(ArcadePrivacy.COOLDOWN.holds(subject));
        assertFalse(link.holds().test(subject));

        assertBystanderKeepsEverything();
        // a second erasure of the same player is a clean no-op
        NeroArcadeData.eraser().erase(null, subject);
    }

    private void assertBystanderKeepsEverything() {
        assertTrue(boards.holds(bystander));
        assertTrue(credit.holds(bystander));
        assertTrue(exchange.holdsAuthor(bystander) && exchange.holdsDrafts(bystander));
        assertTrue(Exchange.HEARTS.holds(bystander));
        assertTrue(SeatRegistry.SERVER.isSeated(bystander));
        assertTrue(ExchangeActions.holdsWindow(bystander) && BenchActions.holdsWindow(bystander));
        assertTrue(bystanderBench.holdsAuthor(bystander));
        assertTrue(PrivacyExport.exists(exports, bystander) && ArcadePrivacy.COOLDOWN.holds(bystander));
        assertTrue(link.holds().test(bystander));
    }

    @Test
    void theExportHoldsOnlyTheSubjectsOwnData() throws IOException {
        String json = Files.readString(PrivacyExport.file(exports, subject));
        var doc = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        assertEquals(subject.toString(), doc.get("subject").getAsString());
        assertFalse(json.contains(bystander.toString()), "never another player's id");
        assertTrue(doc.getAsJsonObject("privacy").get("credit_opt_in").getAsBoolean());
        assertFalse(doc.getAsJsonObject("privacy").get("play_history").getAsBoolean());
        assertEquals(2, doc.getAsJsonArray("personal_bests").size());
        assertEquals(2, doc.getAsJsonArray("listings").size(), "published (1) and op-listed authored (5)");
        assertEquals(1, doc.getAsJsonArray("drafts").size(), "own drafts only, never another player's");
        var first = doc.getAsJsonArray("personal_bests").get(0).getAsJsonObject();
        assertEquals("Alice", first.getAsJsonObject("top_row").get("name").getAsString());
        // compliance review F4: a listing's author is the game's author, so you_are is always "author"
        assertTrue(json.contains("\"you_are\": \"author\"") && !json.contains("\"you_are\": \"publisher\""));
        assertEquals(PrivacyExport.relativePath(subject), "neroarcade/exports/" + subject + ".json");
    }

    // --- fixtures ---------------------------------------------------------------------------------

    private static LoadedBenches.AuthorHolder holder(BenchDraft draft) {
        return new LoadedBenches.AuthorHolder() {
            @Override
            public boolean eraseAuthor(UUID player) {
                return draft.eraseAuthor(player);
            }

            @Override
            public boolean holdsAuthor(UUID player) {
                return draft.holdsAuthor(player);
            }

            @Override
            public boolean stripCredit(UUID player) {
                return draft.stripCredit(player);
            }
        };
    }

    /** A 16-char base32 id from a number. */
    static String id(int n) {
        String alphabet = "abcdefghijklmnopqrstuvwxyz234567";
        StringBuilder out = new StringBuilder();
        long v = n;
        for (int i = 0; i < 16; i++) {
            out.append(alphabet.charAt((int) (v & 31)));
            v >>>= 5;
        }
        return out.toString();
    }

    static GameDef game(int n, UUID author, String credit) {
        return new GameDef(1, id(n), "Game " + n, Optional.ofNullable(author), Optional.ofNullable(credit), List.of(),
                Optional.empty(), Map.of(), 100L, 1);
    }
}
