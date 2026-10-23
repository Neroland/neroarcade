package za.co.neroland.neroarcade.bench;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.FilteredText;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.cabinet.DropReason;
import za.co.neroland.neroarcade.game.CartridgeIds;
import za.co.neroland.neroarcade.game.CartridgeValidator;
import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.item.CartridgeItem;
import za.co.neroland.neroarcade.item.StoredCartridge;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.network.BenchEditPayload;
import za.co.neroland.neroarcade.registry.ModItems;
import za.co.neroland.neroarcade.registry.ModSounds;

/**
 * Server handler for {@code BenchEditPayload} (003 §11). Accepted only while the sender's open menu is the
 * {@link GameBenchMenu} of the addressed bench; 20 edits per second per player, the excess ignored. Every refusal
 * becomes the bench's status line ({@code neroarcade.bench.status.*}); nothing here throws.
 *
 * <p><b>Title filter.</b> 26.x has {@code ServerPlayer.getTextFilter().processStreamMessage(String)} returning a
 * {@code CompletableFuture<FilteredText>}; a server with no filter configured gets {@code TextFilter.DUMMY}, which
 * passes everything. An already-complete future (the DUMMY filter) is applied at once, so a SAVE sent in the same
 * tick as the SET_TITLE sees the title (review 005a F8); a pending one is applied on the server thread when it
 * completes. Any masked character refuses the whole title ("Title not allowed"); {@link TitleRules} (length,
 * control characters) runs first.
 */
public final class BenchActions {

    /** Edits per player per second. */
    public static final int EDITS_PER_SECOND = 20;

    private static final Map<UUID, long[]> WINDOWS = new HashMap<>();

    private BenchActions() {
    }

    public static void handle(BenchEditPayload payload, ServerPlayer player) {
        if (!(player.containerMenu instanceof GameBenchMenu menu) || menu.bench() == null
                || !payload.bench().equals(menu.benchPos())) {
            return;
        }
        if (!withinRate(player.getUUID(), player.level().getGameTime())) {
            return;
        }
        GameBenchBlockEntity bench = menu.bench();
        try {
            apply(bench, payload, player);
        } catch (RuntimeException e) {
            NeroArcadeCommon.LOGGER.warn("[NeroArcade] bench edit {} failed ({})", payload.op(), e.getClass().getName());
            bench.setStatus("neroarcade.bench.status.failed");
        }
    }

    /** Fixed one-second window per player; true while under {@link #EDITS_PER_SECOND}. */
    static boolean withinRate(UUID player, long gameTime) {
        long[] window = WINDOWS.computeIfAbsent(player, p -> new long[] {gameTime, 0});
        if (gameTime - window[0] >= 20 || gameTime < window[0]) {
            window[0] = gameTime;
            window[1] = 0;
        }
        if (window[1] >= EDITS_PER_SECOND) {
            return false;
        }
        window[1]++;
        return true;
    }

    /** Forgets a player's rate window (logout). */
    public static void forget(UUID player) {
        WINDOWS.remove(player);
    }

    public static void clear() {
        WINDOWS.clear();
    }

    private static void apply(GameBenchBlockEntity bench, BenchEditPayload payload, ServerPlayer player) {
        switch (payload.op()) {
            case SET_PROP -> setProp(bench, payload);
            case SET_TITLE -> setTitle(bench, payload.title(), player);
            case SAVE -> save(bench, player);
            case LOAD -> load(bench, player);
            case TEST_PLAY -> bench.startTest(player);
            case STOP_TEST -> bench.stopTest(DropReason.ESC);
            case CLEAR_SLOT -> {
                int index = rackIndex(payload);
                if (index >= 0 && bench.clearGhost(index)) {
                    bench.setStatus("");
                }
            }
        }
    }

    private static int rackIndex(BenchEditPayload payload) {
        if (payload.slot() < 0 || payload.slot() >= ModuleType.values().length) {
            return -1;
        }
        return BenchSlots.index(ModuleType.values()[payload.slot()], payload.stackIndex());
    }

    private static void setProp(GameBenchBlockEntity bench, BenchEditPayload payload) {
        int index = rackIndex(payload);
        if (index < 0) {
            return;
        }
        if (bench.draft().setProp(index, payload.propKey(), payload.intValue(), payload.boolValue())) {
            bench.setStatus("");
        }
    }

    private static void setTitle(GameBenchBlockEntity bench, String raw, ServerPlayer player) {
        Optional<String> cleaned = TitleRules.clean(raw);
        if (cleaned.isEmpty()) {
            bench.setStatus("neroarcade.bench.status.title_invalid", Integer.toString(TitleRules.MAX_LENGTH));
            return;
        }
        String title = cleaned.get();
        MinecraftServer server = player.level().getServer();
        whenFiltered(player.getTextFilter().processStreamMessage(title), server, filtered -> {
            if (bench.isRemoved()) {
                return;
            }
            if (filtered.isFiltered()) {
                bench.setStatus("neroarcade.bench.status.title_blocked");
            } else {
                bench.draft().setTitle(title);
                bench.setStatus("");
            }
        });
    }

    /**
     * Runs {@code apply} now when the filter's future is already complete (normally), otherwise on {@code server}
     * once it completes — never queued behind packets that arrived after it when there is nothing to wait for.
     */
    static void whenFiltered(CompletableFuture<FilteredText> future, Executor server, Consumer<FilteredText> apply) {
        if (future.isDone() && !future.isCompletedExceptionally()) {
            apply.accept(future.join());
        } else {
            future.thenAcceptAsync(apply, server);
        }
    }

    /**
     * SAVE (rules in {@link BenchDraft#planSave}): a Blank Cartridge in the tray becomes a new cartridge with a new
     * id; the saver's own cartridge, when it is the draft's game, is re-saved in place (same id, revision + 1);
     * any other written cartridge is refused with a sentence that says what to do. The author UUID is the saver; the
     * credit comes from {@link CreditResolver} (empty until the Stage 6 opt-in exists).
     */
    private static void save(GameBenchBlockEntity bench, ServerPlayer player) {
        if (bench.matchHost() != null) {
            bench.setStatus("neroarcade.bench.status.testing");
            return;
        }
        ServerLevel level = (ServerLevel) bench.getLevel();
        ItemStack tray = bench.inventory().getItem(BenchSlots.TRAY);
        boolean blank = tray.is(ModItems.BLANK_CARTRIDGE.get());
        boolean written = tray.is(ModItems.CARTRIDGE.get());
        if (!blank && !written) {
            bench.setStatus("neroarcade.bench.status.need_blank");
            return;
        }
        StoredCartridge current = written ? CartridgeItem.storedOf(tray) : null;
        if (written && current == null) {
            bench.setStatus("neroarcade.bench.status.need_blank");
            return;
        }
        Optional<String> trayId = current == null ? Optional.empty() : Optional.of(current.rawId());
        GameDef saved;
        try {
            Optional<String> credit = CreditResolver.current()
                    .creditFor(player.getUUID(), player.getGameProfile().name());
            SavePlan plan = bench.draft().planSave(player.getUUID(), trayId,
                    () -> CartridgeIds.mint(level.getRandom()), System.currentTimeMillis() / 1000L, credit);
            if (plan.refused()) {
                bench.setStatus(plan.kind().statusKey());
                return;
            }
            if (plan.kind() == SavePlan.Kind.IN_PLACE && current != null && current.tryDecode()
                    .flatMap(GameDef::authorUuid).filter(a -> !a.equals(player.getUUID())).isPresent()) {
                bench.setStatus(SavePlan.Kind.REFUSED_NOT_YOURS.statusKey()); // the tray copy says otherwise
                return;
            }
            saved = plan.def().orElseThrow();
            CartridgeValidator.validate(saved);
            StoredCartridge.of(saved); // size cap, refused rather than truncated
        } catch (ValidationError refused) {
            bench.setStatus(refused.code == ValidationError.Code.BAD_TITLE ? "neroarcade.bench.status.need_title"
                    : refused.langKey, refused.args.toArray(String[]::new));
            return;
        }
        try {
            if (blank) {
                bench.inventory().setItem(BenchSlots.TRAY, CartridgeItem.create(saved));
            } else {
                ItemStack rewritten = tray.copy();
                CartridgeItem.write(rewritten, saved);
                bench.inventory().setItem(BenchSlots.TRAY, rewritten);
            }
        } catch (ValidationError refused) {
            bench.setStatus(refused.langKey, refused.args.toArray(String[]::new));
            return;
        }
        bench.draft().adopt(saved);
        level.playSound(null, bench.getBlockPos(), ModSounds.BENCH_SAVE.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        bench.setStatus("neroarcade.bench.status.saved", Integer.toString(saved.revision()));
    }

    /** LOAD: the tray's cartridge into the rack as ghost chips; an unreadable one only sets the status line. */
    private static void load(GameBenchBlockEntity bench, ServerPlayer player) {
        if (bench.matchHost() != null) {
            bench.setStatus("neroarcade.bench.status.testing");
            return;
        }
        StoredCartridge stored = CartridgeItem.storedOf(bench.inventory().getItem(BenchSlots.TRAY));
        if (stored == null) {
            bench.setStatus("neroarcade.bench.status.need_cartridge");
            return;
        }
        BenchDraft.Load result = BenchDraft.load(stored);
        if (result.error().isPresent()) {
            ValidationError error = result.error().get();
            bench.setStatus(error.langKey, error.args.toArray(String[]::new));
            return;
        }
        bench.materialise(result.draft().orElseThrow(), player);
        bench.setStatus("neroarcade.bench.status.loaded");
    }
}
