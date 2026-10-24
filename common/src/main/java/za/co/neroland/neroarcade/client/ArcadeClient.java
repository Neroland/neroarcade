package za.co.neroland.neroarcade.client;

import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroarcade.cabinet.ArcadeCabinetBlockEntity;
import za.co.neroland.neroarcade.cabinet.BusPhase;
import za.co.neroland.neroarcade.client.bench.ClientBenchState;
import za.co.neroland.neroarcade.client.board.ClientBoards;
import za.co.neroland.neroarcade.client.input.ArcadeKeys;
import za.co.neroland.neroarcade.client.input.InputSender;
import za.co.neroland.neroarcade.client.input.SeatedInput;
import za.co.neroland.neroarcade.client.match.ClientMatch;
import za.co.neroland.neroarcade.client.match.ClientMatches;
import za.co.neroland.neroarcade.client.render.CabinetScreens;
import za.co.neroland.neroarcade.client.screen.GameBenchScreen;
import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.network.ArcadeClientSink;
import za.co.neroland.neroarcade.network.BenchStatePayload;
import za.co.neroland.neroarcade.network.BoardPagePayload;
import za.co.neroland.neroarcade.network.HudLegendPayload;
import za.co.neroland.neroarcade.network.LeaveSeatPayload;
import za.co.neroland.neroarcade.network.MatchPhasePayload;
import za.co.neroland.neroarcade.platform.Services;
import za.co.neroland.neroarcade.registry.ModSounds;
import za.co.neroland.neroarcade.runtime.MatchResult;
import za.co.neroland.neroarcade.runtime.SeatInput;
import za.co.neroland.neroarcade.runtime.net.SeatInputPayload;
import za.co.neroland.neroarcade.runtime.net.SnapshotPayload;

/**
 * The client half every loader shares: installs the {@link ArcadeClientSink}, and once per client tick reads the
 * seated player's controls, predicts their pawn, sends their input (on change plus a 5 Hz keep-alive), holds their
 * movement, turns Esc into "leave the seat", and frees idle screen textures. Each loader registers the keys, the
 * HUD layer, the cabinet renderer and the bench screen through its own API and calls {@link #tick()}.
 *
 * <p><b>Esc semantics.</b> Seated at a cabinet, Esc sends {@code LeaveSeatPayload} and the pause menu that Esc
 * opened is closed again on the same client tick (it is never shown for more than one frame; a pause caused by the
 * window losing focus is left alone). During a bench test play the Game Bench screen is open, and Esc closes it,
 * which sends {@code STOP_TEST} ({@link GameBenchScreen#removed()}).
 *
 * <p><b>Sounds.</b> The server already plays {@code cabinet.start/win/lose} at a cabinet for everyone nearby, so the
 * client adds only the seated player's own feedback: {@code game.pickup} when their score rises, {@code game.hit}
 * when they lose a life, {@code game.tick} on each of the last five timer seconds, and the start/win/lose stings
 * for a bench test play (which the server does not play). The snapshot carries no event list, so pickup/hit are
 * inferred from the seat's score and lives. CLIENT-ONLY.
 */
public final class ArcadeClient {

    private static final InputSender SENDER = new InputSender();
    private static @Nullable ClientLevel lastLevel;
    private static long ticks;

    private ArcadeClient() {
    }

    /** Client init (every loader): payloads start reaching the client mirrors from here on. */
    public static void init() {
        ArcadeClientSink.install(new Sink());
        ClientMatches.setGames(ArcadeClient::gameAt);
        ClientMatches.setEvents(new Sounds());
    }

    // --- version helpers ----------------------------------------------------------------------------

    /** The open screen. 26.2 moved screen switching onto {@code Gui}. */
    public static @Nullable Screen screen() {
        Minecraft mc = Minecraft.getInstance();
        //? if >=26.2 {
        return mc.gui.screen();
        //?} else {
        /*return mc.screen;
        *///?}
    }

    /** Shows {@code screen} ({@code null}: back to the game). */
    public static void setScreen(@Nullable Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        //? if >=26.2 {
        mc.gui.setScreen(screen);
        //?} else {
        /*mc.setScreen(screen);
        *///?}
    }

    // --- tick ---------------------------------------------------------------------------------------

    /** One client tick (end of tick on every loader). */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        ClientLevel level = mc.level;
        if (level != lastLevel) {
            lastLevel = level;
            ClientMatches.clear(); // a new world or dimension never shows the last one's matches
            ClientBoards.clear();
            SENDER.reset();
        }
        if (level == null || mc.player == null) {
            SeatedInput.hold(null, false);
            return;
        }
        ClientMatch seated = ClientMatches.seated();
        boolean atCabinet = seated != null && level.getBlockEntity(seated.seatAt()) instanceof ArcadeCabinetBlockEntity;
        Screen screen = screen();
        if (atCabinet && screen instanceof PauseScreen && mc.isWindowActive()) {
            setScreen(null); // Esc while seated means "leave", not "pause"
            leave(seated);
            seated = null;
            atCabinet = false;
            screen = null;
        }
        SeatedInput.hold(mc.player, atCabinet);
        int mask = 0;
        if (seated != null) {
            if (atCabinet && screen == null) {
                mask = ArcadeKeys.worldMask();
            } else if (!atCabinet && screen instanceof GameBenchScreen) {
                mask = ArcadeKeys.screenMask();
            }
        }
        BusPhase phase = seated == null ? BusPhase.IDLE : seated.busPhase();
        SeatInput input = ArcadeKeys.toInput(mask, phase);
        ClientMatches.tick(input);
        if (seated != null && phase != BusPhase.IDLE) {
            SeatInput send = SENDER.poll(input, seated.matchId(), ticks);
            if (send != null) {
                Services.NETWORK.sendToServer(new SeatInputPayload(seated.seatAt(), seated.matchId(), send, (int) ticks));
            }
        } else {
            SENDER.reset();
        }
        CabinetScreens.tick(ClientMatches.now());
        GalleryCapture.tick();
    }

    /** Asks to leave the seat at {@code match}'s host. */
    public static void leave(ClientMatch match) {
        Services.NETWORK.sendToServer(new LeaveSeatPayload(match.seatAt()));
        match.forgetSeat();
        SENDER.reset();
    }

    /** A host's game: the cabinet's synced cartridge, or the open bench's draft. */
    private static @Nullable ResolvedGame gameAt(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        BlockEntity be = mc.level.getBlockEntity(pos);
        if (be instanceof ArcadeCabinetBlockEntity cabinet) {
            return CabinetScreens.gameOf(cabinet);
        }
        return ClientBenchState.gameAt(pos);
    }

    /** Drops every client mirror (disconnect). */
    public static void clear() {
        ClientMatches.clear();
        ClientBenchState.clear();
        ClientBoards.clear();
        za.co.neroland.neroarcade.client.exchange.ClientExchange.clear();
        CabinetScreens.clear();
        SENDER.reset();
        ArcadeKeys.releaseScreenKeys();
        lastLevel = null;
    }

    static void play(@Nullable Supplier<SoundEvent> sound, float pitch, float volume) {
        if (sound == null) {
            return;
        }
        try {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound.get(), pitch, volume));
        } catch (RuntimeException e) {
            // a missing sound never breaks a match
        }
    }

    /** Where client-bound payloads land (client thread). */
    private static final class Sink implements ArcadeClientSink {
        @Override
        public void onSnapshot(SnapshotPayload payload) {
            ClientMatches.onSnapshot(payload);
        }

        @Override
        public void onMatchPhase(MatchPhasePayload payload) {
            ClientMatches.onPhase(payload);
        }

        @Override
        public void onHudLegend(HudLegendPayload payload) {
            ClientMatches.onLegend(payload);
        }

        @Override
        public void onBenchState(BenchStatePayload payload) {
            ClientBenchState.accept(payload);
            ClientMatches.refreshGame(payload.bench());
        }

        @Override
        public void onBoardPage(BoardPagePayload payload) {
            ClientBoards.accept(payload);
        }

        @Override
        public void onExchangePage(za.co.neroland.neroarcade.network.ExchangePagePayload payload) {
            za.co.neroland.neroarcade.client.exchange.ClientExchange.accept(payload);
        }

        @Override
        public void clear() {
            ArcadeClient.clear();
        }
    }

    /** The seated player's own sounds (see the class comment for why phase stings play only for bench tests). */
    private static final class Sounds implements ClientMatch.Events {
        @Override
        public void phaseChanged(ClientMatch match, @Nullable BusPhase from, BusPhase to) {
            Minecraft mc = Minecraft.getInstance();
            if (!match.seated() || mc.level == null
                    || mc.level.getBlockEntity(match.host()) instanceof ArcadeCabinetBlockEntity) {
                return;
            }
            MatchPhasePayload phase = match.phase();
            if (to == BusPhase.COUNTDOWN) {
                play(ModSounds.CABINET_START, 1.0F, 0.8F);
            } else if (to == BusPhase.RESULTS && phase != null) {
                boolean won = phase.outcome() == MatchResult.Outcome.WIN.ordinal();
                play(won ? ModSounds.CABINET_WIN : ModSounds.CABINET_LOSE, 1.0F, 0.8F);
            }
        }

        @Override
        public void scored(ClientMatch match, int delta) {
            float pitch = 0.95F + Math.floorMod(ticks * 7919L, 11L) / 100F; // the 004 ±5 %
            play(ModSounds.get("game.pickup"), pitch, 0.6F);
        }

        @Override
        public void hit(ClientMatch match) {
            play(ModSounds.get("game.hit"), 1.0F, 0.7F);
        }

        @Override
        public void timerSecond(ClientMatch match, int secondsLeft) {
            play(ModSounds.get("game.tick"), 1.0F, 0.5F);
        }
    }
}
