package za.co.neroland.neroarcade.bench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.nbt.NbtOps;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.privacy.Tombstones;

/**
 * Review 005a / 003b P2: a Game Bench's draft keeps the author (and credit) of the cartridge last LOADed into it, in
 * block NBT. Erasure tombstones that author on every loaded bench (best effort; an unloaded bench is the documented
 * limit) and the bench's saved form no longer names the player. A later SAVE by anyone mints a new id.
 */
class BenchDraftErasureTest {

    private static final UUID ALICE = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final UUID BOB = UUID.fromString("66666666-7777-4888-9999-aaaaaaaaaaaa");

    @AfterEach
    void clear() {
        LoadedBenches.clear();
    }

    private static GameDef game(UUID author, String credit) {
        return new GameDef(1, "abcdefghijklmnop", "Orb Dodge", Optional.of(author), Optional.ofNullable(credit),
                List.of(), Optional.empty(), Map.of(), 100L, 3);
    }

    @Test
    void erasureTombstonesTheLoadedAuthorAndClearsTheCredit() {
        BenchDraft draft = BenchDraft.fromGameDef(game(ALICE, "Alice"));
        assertTrue(draft.holdsAuthor(ALICE));
        assertFalse(draft.eraseAuthor(BOB), "another player's erasure changes nothing");
        assertTrue(draft.eraseAuthor(ALICE));
        assertEquals(Optional.of(Tombstones.ERASED_AUTHOR), draft.meta().author());
        assertEquals(Optional.empty(), draft.meta().credit());
        assertEquals(Optional.of("abcdefghijklmnop"), draft.meta().id(), "the game itself stays");
        assertFalse(draft.holdsAuthor(ALICE));
        String nbt = BenchDraft.CODEC.encodeStart(NbtOps.INSTANCE, draft).getOrThrow().toString();
        assertFalse(nbt.contains(ALICE.toString()) || nbt.contains("Alice"));
        assertFalse(draft.eraseAuthor(ALICE), "erasing twice is a no-op");
    }

    @Test
    void theLoadedBenchesWalkReachesEveryLoadedBenchAndOnlyThose() {
        BenchDraft alices = BenchDraft.fromGameDef(game(ALICE, null));
        BenchDraft bobs = BenchDraft.fromGameDef(game(BOB, null));
        LoadedBenches.AuthorHolder a = holder(alices);
        LoadedBenches.AuthorHolder b = holder(bobs);
        LoadedBenches.add(a);
        LoadedBenches.add(b);
        assertTrue(LoadedBenches.holdsAuthor(ALICE));

        LoadedBenches.ERASER.erase(null, ALICE);
        LoadedBenches.ERASER.backupNow(null);
        assertFalse(LoadedBenches.holdsAuthor(ALICE));
        assertTrue(LoadedBenches.holdsAuthor(BOB), "per subject");

        LoadedBenches.remove(b); // unloaded: out of reach (the documented limit)
        LoadedBenches.ERASER.erase(null, BOB);
        assertTrue(bobs.holdsAuthor(BOB), "an unloaded bench is not reached");
        assertEquals("benches", LoadedBenches.ERASER.name());
    }

    @Test
    void creditOptOutStripsTheCreditOnEveryLoadedBenchButKeepsTheAuthor() {
        BenchDraft alices = BenchDraft.fromGameDef(game(ALICE, "Alice"));
        BenchDraft bobs = BenchDraft.fromGameDef(game(BOB, "Bob"));
        LoadedBenches.add(holder(alices));
        LoadedBenches.add(holder(bobs));

        assertEquals(1, LoadedBenches.stripCredit(ALICE), "compliance review F5: opt-out reaches loaded benches");
        assertEquals(Optional.empty(), alices.meta().credit());
        assertTrue(alices.holdsAuthor(ALICE), "opt-out is not erasure: the author stays");
        assertEquals(Optional.of("Bob"), bobs.meta().credit(), "per subject");
        String nbt = BenchDraft.CODEC.encodeStart(NbtOps.INSTANCE, alices).getOrThrow().toString();
        assertFalse(nbt.contains("Alice"), "the saved bench no longer carries the credit");
        assertEquals(0, LoadedBenches.stripCredit(ALICE), "a second opt-out is a no-op");
        assertFalse(alices.stripCredit(BOB), "another player's opt-out changes nothing");
    }

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
}
