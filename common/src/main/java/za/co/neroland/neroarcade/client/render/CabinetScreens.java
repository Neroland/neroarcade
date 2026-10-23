package za.co.neroland.neroarcade.client.render;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.block.ArcadeBlockProperties;
import za.co.neroland.neroarcade.block.CabinetStatus;
import za.co.neroland.neroarcade.bus.MarqueeScreenBlockEntity;
import za.co.neroland.neroarcade.cabinet.ArcadeCabinetBlockEntity;
import za.co.neroland.neroarcade.cabinet.BusPhase;
import za.co.neroland.neroarcade.cabinet.BusRole;
import za.co.neroland.neroarcade.cabinet.CabinetClientInfo;
import za.co.neroland.neroarcade.cabinet.HotSeat;
import za.co.neroland.neroarcade.client.ArcadeClientConfig;
import za.co.neroland.neroarcade.client.board.ClientBoards;
import za.co.neroland.neroarcade.client.match.ClientMatch;
import za.co.neroland.neroarcade.client.match.ClientMatches;
import za.co.neroland.neroarcade.client.screen.ScreenRenderer;
import za.co.neroland.neroarcade.client.screen.ScreenStatus;
import za.co.neroland.neroarcade.client.screen.SpriteMap;
import za.co.neroland.neroarcade.client.screen.ThemePalettes;
import za.co.neroland.neroarcade.game.CartridgeValidator;
import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.network.MatchPhasePayload;
import za.co.neroland.neroarcade.runtime.ArcadeRuntime;
import za.co.neroland.neroarcade.runtime.MatchResult;
import za.co.neroland.neroarcade.runtime.MatchState;

/**
 * What each cabinet's screen shows and the pool of textures that show it. Decided per cabinet from its block state
 * (the server's status mirror), its synced cartridge view and the client's mirror of its match:
 *
 * <ul>
 *   <li>no cartridge: "NO CARTRIDGE"; {@code locked}: "UNREADABLE HERE" + the reason; {@code no_power}: "NO POWER"
 *       (blinking); {@code linked}: "LINKED — WAITING";</li>
 *   <li>{@code ready} = a readable cartridge and no match: the attract loop, drawn client-locally from the synced
 *       {@code GameDef} whether or not the server host is awake (004 A3 — sleep governs simulation, not the title
 *       card); {@code playing}: the latest snapshot, interpolated, with
 *       the seated player's own pawn predicted, plus the countdown digit, the result banner or "NO POWER" when
 *       paused.</li>
 * </ul>
 *
 * Each screen is redrawn at most once per client tick and re-uploaded only when its pixels change. At most
 * {@link TextureLru#DEFAULT_CAP} screens hold a texture; the rest draw nothing until a slot frees. Render thread
 * only. CLIENT-ONLY.
 */
public final class CabinetScreens {

    private static final TextureLru LRU = new TextureLru(TextureLru.DEFAULT_CAP);
    private static final ScreenTexture[] TEXTURES = new ScreenTexture[TextureLru.DEFAULT_CAP];
    private static final long[] DRAWN_AT = new long[TextureLru.DEFAULT_CAP];
    private static final long[] DRAWN_FOR = new long[TextureLru.DEFAULT_CAP];
    private static final Map<Long, GameCache> GAMES = new HashMap<>();
    /** Node → the host it last mirrored (for the 5 s "ABANDONED" frame after the host is gone). */
    private static final Map<Long, Long> LAST_HOST = new HashMap<>();
    /** How long a node keeps an abandoned match's results frame (003 §3.5: 5 s). */
    public static final int ABANDONED_TICKS = 100;
    private static @Nullable ScreenRenderer renderer;

    /** A cabinet's synced cartridge, resolved once: the game (null if unreadable here) and an attract arena. */
    private record GameCache(CabinetClientInfo info, @Nullable ResolvedGame game, @Nullable MatchState arena,
            SpriteMap map) {
    }

    private CabinetScreens() {
    }

    /** The resolved game of a cabinet's synced cartridge (for the seated player's prediction), or null. */
    public static @Nullable ResolvedGame gameOf(ArcadeCabinetBlockEntity be) {
        return cache(be).game();
    }

    /**
     * The texture showing this cabinet's screen this frame, drawing and uploading it if this tick has not yet;
     * null = draw nothing.
     */
    public static @Nullable Identifier prepare(ArcadeCabinetBlockEntity be, long now) {
        BlockState state = be.getBlockState();
        if (!state.hasProperty(ArcadeBlockProperties.STATUS)) {
            return null;
        }
        CabinetStatus status = state.getValue(ArcadeBlockProperties.STATUS);
        boolean cartridge = state.getValue(ArcadeBlockProperties.CARTRIDGE);
        long key = be.getBlockPos().asLong();
        ArcadeCabinetBlockEntity shows = shownCabinet(be);
        GameCache game = cache(shows);
        return draw(key, now, game.map().theme(), r -> draw(r, be, shows, status, cartridge, game, (int) now));
    }

    /**
     * The cabinet whose game this one shows: itself, or — for a Link Bus node — the host it mirrors (003 §3.2:
     * nodes draw the host's frames verbatim).
     */
    static ArcadeCabinetBlockEntity shownCabinet(ArcadeCabinetBlockEntity be) {
        BlockPos host = be.clientRole() == BusRole.NODE ? be.clientDisplayOf() : null;
        if (host != null && be.getLevel() != null && be.getLevel().isLoaded(host)
                && be.getLevel().getBlockEntity(host) instanceof ArcadeCabinetBlockEntity h) {
            LAST_HOST.put(be.getBlockPos().asLong(), host.asLong());
            return h;
        }
        return be;
    }

    /** Takes (or keeps) {@code key}'s texture slot and redraws it once per client tick; null = no slot free. */
    private static @Nullable Identifier draw(long key, long now, ThemePalettes.Theme theme,
            java.util.function.Consumer<ScreenRenderer> painter) {
        int slot = LRU.acquire(key, now);
        if (slot < 0) {
            return null;
        }
        ScreenTexture texture = TEXTURES[slot];
        if (texture == null) {
            texture = new ScreenTexture(Identifier.fromNamespaceAndPath(NeroArcadeCommon.MOD_ID,
                    "dynamic/cabinet_screen_" + slot));
            TEXTURES[slot] = texture;
        }
        if (DRAWN_AT[slot] != now || DRAWN_FOR[slot] != key || !texture.ready()) {
            DRAWN_AT[slot] = now;
            DRAWN_FOR[slot] = key;
            ScreenRenderer r = renderer(theme);
            painter.accept(r);
            texture.upload(r, theme, ArcadeClientConfig.scanlines());
        }
        return texture.ready() ? texture.id() : null;
    }

    // --- marquee (003 §5, 004 §4.6) --------------------------------------------------------------------

    /** Salt for the marquee's second (score strip) slot key. */
    private static final long STRIP_KEY = 0x5A17_0000_0000_0000L;

    /** The live match a marquee mirrors (countdown, play or results), or null when its host is idle. */
    public static @Nullable ClientMatch marqueeMatch(MarqueeScreenBlockEntity marquee) {
        BlockPos host = marquee.clientDisplayOf();
        if (host == null) {
            return null;
        }
        ClientMatch match = ClientMatches.get(host);
        if (match == null || match.latest() == null) {
            return null;
        }
        BusPhase phase = match.busPhase();
        return phase == BusPhase.COUNTDOWN || phase == BusPhase.PLAYING || phase == BusPhase.RESULTS ? match : null;
    }

    /**
     * The marquee's main square: the host's own screen texture while a match is on (shared, not redrawn), else
     * the rolling top ten of the host's cartridge. Null = no bus / no host (the BER shows the static card).
     */
    public static @Nullable Identifier prepareMarquee(MarqueeScreenBlockEntity marquee, long now) {
        BlockPos host = marquee.clientDisplayOf();
        if (host == null || marquee.getLevel() == null || !marquee.getLevel().isLoaded(host)
                || !(marquee.getLevel().getBlockEntity(host) instanceof ArcadeCabinetBlockEntity hostBe)) {
            return null;
        }
        if (marqueeMatch(marquee) != null) {
            return prepare(hostBe, now);
        }
        GameCache game = cache(hostBe);
        String lineage = game.info().def().map(GameDef::lineageRoot).orElse("");
        List<ClientBoards.Line> rows = ClientBoards.lines(ClientBoards.get(lineage));
        return draw(marquee.getBlockPos().asLong(), now, game.map().theme(),
                r -> r.drawBoard("TOP 10", rows, (int) now));
    }

    /** The marquee's score strip (mode, timer, one line per seat) while a match is on; null otherwise. */
    public static @Nullable Identifier prepareStrip(MarqueeScreenBlockEntity marquee, long now) {
        ClientMatch match = marqueeMatch(marquee);
        if (match == null || match.latest() == null) {
            return null;
        }
        MatchState state = match.latest();
        ResolvedGame game = match.game();
        String mode = modeWord(game);
        String timer = state.timerTicks() <= 0 ? "" : clock(state.timerTicks());
        int[] scores = new int[Math.min(4, state.seatCount())];
        for (int s = 0; s < scores.length; s++) {
            scores[s] = state.score(s);
        }
        ThemePalettes.Theme theme = match.spriteMap().theme();
        return draw(marquee.getBlockPos().asLong() ^ STRIP_KEY, now, theme,
                r -> r.drawScoreStrip(mode, timer, scores, -1));
    }

    /** The marquee's mode word (pixel font, at most 4 characters). */
    static String modeWord(@Nullable ResolvedGame game) {
        if (game == null || game.seats() <= 1) {
            return "SOLO";
        }
        if (HotSeat.is(game)) {
            return "TURN";
        }
        return game.multiplayer().sharesLives(game.multiplayerProps()) ? "COOP" : "VS";
    }

    static String clock(int ticks) {
        int seconds = Math.max(0, (ticks + 19) / 20);
        return (seconds / 60) + ":" + (seconds % 60 < 10 ? "0" : "") + (seconds % 60);
    }

    /** A shared renderer switched to the theme's sheets. */
    static ScreenRenderer renderer(ThemePalettes.Theme theme) {
        ScreenRenderer r = renderer;
        if (r == null) {
            r = new ScreenRenderer(ScreenAssets.sheets(theme), ScreenAssets.font());
            renderer = r;
        } else if (r.sheets().theme() != theme) {
            r.use(ScreenAssets.sheets(theme), ScreenAssets.font());
        }
        return r;
    }

    private static void draw(ScreenRenderer r, ArcadeCabinetBlockEntity be, ArcadeCabinetBlockEntity shows,
            CabinetStatus status, boolean cartridge, GameCache game, int ticks) {
        BusRole role = be.clientRole();
        switch (role) {
            case CONFLICT -> {
                r.drawStatus(ScreenStatus.LINKED_CONFLICT, "", ticks);
                return;
            }
            case FULL -> {
                r.drawStatus(ScreenStatus.BUS_FULL, "", ticks);
                return;
            }
            case NODE_NO_HOST -> {
                if (!drawAbandoned(r, be, ticks)) {
                    r.drawStatus(ScreenStatus.LINKED_WAITING, "", ticks);
                }
                return;
            }
            default -> {
            }
        }
        boolean node = shows != be;
        if (!cartridge && !node) {
            if (role == BusRole.NODE || !drawAbandoned(r, be, ticks)) {
                r.drawStatus(role == BusRole.NODE ? ScreenStatus.LINKED_WAITING : ScreenStatus.NO_CARTRIDGE, "", ticks);
            }
            return;
        }
        switch (status) {
            case LOCKED -> r.drawStatus(ScreenStatus.UNREADABLE, reason(game.info().errorCode()), ticks);
            case NO_POWER -> r.drawStatus(ScreenStatus.NO_POWER, "", ticks);
            case LINKED -> {
                if (!drawAbandoned(r, be, ticks)) {
                    r.drawStatus(ScreenStatus.LINKED_WAITING, "", ticks);
                }
            }
            case PLAYING -> {
                ClientMatch match = ClientMatches.get(shows.getBlockPos());
                if (match != null && match.latest() != null) {
                    drawMatch(r, match, ticks);
                } else {
                    r.drawAttract(game.info().title(), game.arena(), game.map(), ticks, scores(game));
                }
            }
            default -> r.drawAttract(game.info().title(), game.arena(), game.map(), ticks, scores(game));
        }
    }

    /** The host cartridge's board rows, if this client has been sent them. */
    private static List<ClientBoards.Line> scores(GameCache game) {
        String lineage = game.info().def().map(GameDef::lineageRoot).orElse("");
        return ClientBoards.lines(ClientBoards.get(lineage));
    }

    /**
     * A node whose host just went away keeps the "MATCH ABANDONED" results frame for {@value #ABANDONED_TICKS}
     * ticks (003 §3.5) before it shows "LINKED — WAITING".
     */
    private static boolean drawAbandoned(ScreenRenderer r, ArcadeCabinetBlockEntity be, int ticks) {
        Long host = LAST_HOST.get(be.getBlockPos().asLong());
        if (host == null) {
            return false;
        }
        ClientMatch match = ClientMatches.get(BlockPos.of(host));
        MatchPhasePayload phase = match == null ? null : match.phase();
        if (match == null || phase == null || phase.phase() != BusPhase.RESULTS
                || phase.outcome() != MatchResult.Outcome.ABANDONED.ordinal()
                || ClientMatches.now() - match.phaseSince() > ABANDONED_TICKS || match.latest() == null) {
            return false;
        }
        drawMatch(r, match, ticks);
        return true;
    }

    /** A live match frame with its phase overlay; used by the cabinet and the bench test play. */
    public static void drawMatch(ScreenRenderer r, ClientMatch match, int ticks) {
        MatchState state = match.latest();
        if (state == null) {
            return;
        }
        r.drawMatch(state, match.spriteMap(), match.positions(ClientMatches.now() << 8), ticks);
        MatchPhasePayload phase = match.phase();
        if (phase == null) {
            return;
        }
        if (phase.phase() == BusPhase.COUNTDOWN) {
            r.drawCountdown(Math.max(1, phase.ticksLeftInPhase()));
        } else if (phase.phase() == BusPhase.RESULTS) {
            r.drawBanner(outcomeWord(phase.outcome()), "SCORE " + score(phase, state, match.mySeat()),
                    phase.outcome() == MatchResult.Outcome.WIN.ordinal() ? 11 : 15);
        } else if (phase.paused()) {
            if ((ticks / 20) % 2 == 0) {
                r.drawBanner("NO POWER", "", 15);
            }
        }
    }

    private static int score(MatchPhasePayload phase, MatchState state, int seat) {
        int s = Math.max(0, seat);
        int[] scores = phase.scores();
        if (s < scores.length) {
            return scores[s];
        }
        return s < state.seatCount() ? state.score(s) : 0;
    }

    /** The pixel screen's outcome word (bitmap font, upper case, at most 10 characters). */
    static String outcomeWord(int outcome) {
        if (outcome == MatchResult.Outcome.WIN.ordinal()) {
            return "YOU WIN";
        }
        if (outcome == MatchResult.Outcome.DRAW.ordinal()) {
            return "DRAW";
        }
        if (outcome == MatchResult.Outcome.ABANDONED.ordinal()) {
            return "ABANDONED";
        }
        return "GAME OVER";
    }

    /** A short reason for the UNREADABLE screen (the bitmap font has no localisation; the tooltip/chat does). */
    public static String reason(String errorCode) {
        ValidationError.Code code;
        try {
            code = ValidationError.Code.valueOf(errorCode);
        } catch (IllegalArgumentException e) {
            return "DAMAGED";
        }
        return switch (code) {
            case TOO_LARGE -> "TOO LARGE";
            case NEWER_SCHEMA -> "NEWER MOD";
            case MALFORMED -> "DAMAGED";
            case BAD_TITLE -> "BAD TITLE";
            case BAD_ID -> "BAD ID";
            case BAD_LINEAGE -> "BAD HISTORY";
            case BAD_CREDIT -> "BAD CREDIT";
            case UNKNOWN_SLOT -> "UNKNOWN SLOT";
            case STACK_OVERFLOW -> "TOO MANY CHIPS";
            case UNKNOWN_MODULE -> "MISSING CHIP";
            case WRONG_SLOT -> "WRONG SLOT";
            case BAD_PROPERTY -> "BAD SETTING";
            case OVER_BUDGET -> "TOO BIG HERE";
        };
    }

    private static GameCache cache(ArcadeCabinetBlockEntity be) {
        long key = be.getBlockPos().asLong();
        CabinetClientInfo info = be.clientInfo();
        GameCache cached = GAMES.get(key);
        if (cached != null && cached.info().equals(info)) {
            return cached;
        }
        ResolvedGame game = null;
        MatchState arena = null;
        if (info.readable() && info.def().isPresent()) {
            game = resolve(info.def().get());
            arena = game == null ? null : attractArena(game);
        }
        GameCache fresh = new GameCache(info, game, arena, SpriteMap.of(game));
        GAMES.put(key, fresh);
        ClientMatches.refreshGame(be.getBlockPos());
        return fresh;
    }

    static @Nullable ResolvedGame resolve(GameDef def) {
        try {
            return CartridgeValidator.validate(def);
        } catch (ValidationError | RuntimeException e) {
            return null;
        }
    }

    /** The arena the attract loop walks a pawn around: the game's own board, built once. */
    static @Nullable MatchState attractArena(ResolvedGame game) {
        try {
            ArcadeRuntime runtime = new ArcadeRuntime(game, ArcadeRuntime.newState(game, game.seats()), null);
            runtime.start(0x5EEDL);
            return runtime.state();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** One client tick: free the slots of screens nobody drew for {@link TextureLru#IDLE_TICKS}. */
    public static void tick(long now) {
        LRU.releaseIdle(now, TextureLru.IDLE_TICKS, slot -> {
            if (TEXTURES[slot] != null) {
                TEXTURES[slot].invalidate();
            }
        });
        if (now % 200 == 0 && GAMES.size() > TextureLru.DEFAULT_CAP * 4) {
            GAMES.clear();
        }
        if (now % 200 == 0 && LAST_HOST.size() > TextureLru.DEFAULT_CAP * 4) {
            LAST_HOST.clear();
        }
    }

    /** Screens holding a texture now (budget readout). */
    public static int live() {
        return LRU.size();
    }

    /** Disconnect / resource reload: free every texture. */
    public static void clear() {
        LRU.clear();
        for (int i = 0; i < TEXTURES.length; i++) {
            if (TEXTURES[i] != null) {
                TEXTURES[i].close();
                TEXTURES[i] = null;
            }
        }
        GAMES.clear();
        LAST_HOST.clear();
        renderer = null;
    }
}
