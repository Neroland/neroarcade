package za.co.neroland.neroarcade.client.hud;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import za.co.neroland.neroarcade.cabinet.BusPhase;
import za.co.neroland.neroarcade.cabinet.HotSeat;
import za.co.neroland.neroarcade.client.ArcadeClient;
import za.co.neroland.neroarcade.client.ArcadeColours;
import za.co.neroland.neroarcade.client.input.ArcadeKeys;
import za.co.neroland.neroarcade.client.match.ClientMatch;
import za.co.neroland.neroarcade.client.match.ClientMatches;
import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.network.HudLegendPayload;
import za.co.neroland.neroarcade.network.MatchPhasePayload;
import za.co.neroland.neroarcade.runtime.MatchResult;
import za.co.neroland.neroarcade.runtime.MatchState;
import za.co.neroland.neroarcade.runtime.Rng;

/**
 * The seated player's HUD (004 §5, §8.2) — drawn only for the player holding a seat, never for spectators:
 *
 * <ul>
 *   <li>top-left: the rule line from the RULE chip (amber, e.g. "Survive 60 s · lives 1"), the score at 2x, the
 *       timer {@code 0:42} (cyan), other seats' scores, and always a grey "ESC leave" so a seated player who missed
 *       the legend can still find the way out;</li>
 *   <li>top-centre: the control legend as keycaps with the player's actual bindings, from the seat bind until
 *       the first 5 s of play are over (fading out over the last 10 ticks);</li>
 *   <li>centre: the 3-2-1 countdown, the lobby card, the results card (rank, score, NEW BEST, "Press SPACE to play
 *       again" with the player's bound A key, "Press ESC to leave") or NO POWER while paused.</li>
 * </ul>
 *
 * The cabinet screen stays the only game view; the HUD never covers the middle of the screen while playing. Hidden
 * while any screen is open (the Game Bench shows its own test-play line). CLIENT-ONLY.
 */
public final class ArcadeHud {

    /** Legend time after play starts: 5 s, fading over the last 10 ticks. */
    public static final int LEGEND_TICKS = 100;
    public static final int FADE_TICKS = 10;
    private static final int PANEL_W = 160;
    private static final int PANEL_H = 80;

    private ArcadeHud() {
    }

    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || ArcadeClient.screen() != null) {
            return;
        }
        ClientMatch match = ClientMatches.seated();
        if (match == null) {
            return;
        }
        MatchPhasePayload phase = match.phase();
        if (phase == null) {
            return;
        }
        Font font = mc.font;
        long now = ClientMatches.now();
        int width = g.guiWidth();
        int height = g.guiHeight();
        drawStats(g, font, match);
        int legendAlpha = legendAlpha(phase.phase(), now - match.phaseSince());
        if (legendAlpha > 0) {
            drawLegend(g, font, match.legend(), width, legendAlpha);
        }
        switch (phase.phase()) {
            case COUNTDOWN -> drawCountdown(g, font, phase.ticksLeftInPhase(), width, height);
            case LOBBY -> drawLobby(g, font, phase, lobbyStartsInSeconds(phase.ticksLeftInPhase(),
                    now - match.phaseArrival()), width, height);
            case RESULTS -> drawResults(g, font, phase, match.mySeat(), width, height);
            case PLAYING -> {
                if (phase.paused()) {
                    Component text = Component.translatable("neroarcade.hud.no_power");
                    g.centeredText(font, text, width / 2, height / 2 - 20, ArcadeColours.DENY_TEXT);
                }
            }
            default -> {
            }
        }
    }

    /** Legend opacity (0..255): full until 5 s into play, then a 10-tick fade; full before play starts. Pure. */
    public static int legendAlpha(BusPhase phase, long ticksInPhase) {
        return switch (phase) {
            case LOBBY, COUNTDOWN -> 255;
            case PLAYING -> {
                long left = LEGEND_TICKS - ticksInPhase;
                yield left <= 0 ? 0 : (int) Math.min(255, left * 255 / FADE_TICKS);
            }
            default -> 0;
        };
    }

    /** {@code 0:42} from ticks (rounded up to whole seconds). Pure. */
    public static String clock(int ticks) {
        int seconds = Math.max(0, (ticks + 19) / 20);
        return (seconds / 60) + ":" + (seconds % 60 < 10 ? "0" : "") + (seconds % 60);
    }

    private static void drawStats(GuiGraphicsExtractor g, Font font, ClientMatch match) {
        MatchState state = match.latest();
        HudLegendPayload legend = match.legend();
        int seat = Math.max(0, match.mySeat());
        Component rule = legend == null || legend.ruleLangKey().isEmpty() ? Component.translatable("neroarcade.hud.rule")
                : Component.translatable(legend.ruleLangKey(), boxed(legend.ruleArgs()));
        Component score = Component.translatable("neroarcade.hud.score",
                state == null || seat >= state.seatCount() ? 0 : state.score(seat));
        String timer = state == null || state.timerTicks() <= 0 ? "" : clock(state.timerTicks());
        List<String> others = new ArrayList<>();
        if (state != null && state.seatCount() > 1) {
            for (int s = 0; s < state.seatCount(); s++) {
                if (s != seat) {
                    others.add("P" + (s + 1) + " " + state.score(s));
                }
            }
        }
        Component leave = leaveHint();
        int turnSeat = hotSeatTurn(match);
        Component turn = turnSeat < 0 ? null : Component.translatable("neroarcade.hud.hot_seat", turnSeat + 1);
        int w = Math.max(Math.max(Math.max(font.width(rule), font.width(score) * 2),
                font.width(String.join("   ", others))), font.width(leave)) + 8;
        if (turn != null) {
            w = Math.max(w, font.width(turn) + 8);
        }
        int h = 4 + 10 + 18 + (timer.isEmpty() ? 0 : 10) + (others.isEmpty() ? 0 : 10) + (turn == null ? 0 : 10) + 10;
        int x = 6;
        int y = 6;
        g.fill(x - 2, y - 2, x + w, y + h, ArcadeColours.HUD_BACK);
        g.text(font, rule, x + 2, y + 2, ArcadeColours.AMBER_TEXT, false);
        g.pose().pushMatrix();
        g.pose().translate(x + 2, y + 13);
        g.pose().scale(2f, 2f);
        g.text(font, score, 0, 0, ArcadeColours.TITLE, true);
        g.pose().popMatrix();
        int line = y + 32;
        if (!timer.isEmpty()) {
            g.text(font, timer, x + 2, line, ArcadeColours.CYAN_TEXT, false);
            line += 10;
        }
        if (!others.isEmpty()) {
            g.text(font, String.join("   ", others), x + 2, line, ArcadeColours.SUBTLE, false);
            line += 10;
        }
        if (turn != null) {
            g.text(font, turn, x + 2, line, ArcadeColours.CYAN_TEXT, false);
            line += 10;
        }
        g.text(font, leave, x + 2, line, ArcadeColours.SUBTLE, false);
    }

    /**
     * In a hot-seat game, the seat on turn (0-based) from the latest snapshot, asked of the MULTIPLAYER chip itself
     * (a pure read; no chip id is compared), else -1.
     */
    static int hotSeatTurn(ClientMatch match) {
        ResolvedGame game = match.game();
        MatchState state = match.latest();
        if (game == null || state == null || !HotSeat.is(game)) {
            return -1;
        }
        try {
            int mask = game.multiplayer().activeSeatMask(new ModuleContext(state, game.multiplayerProps(),
                    Rng.view(state), game, state.tick(), 0));
            return mask == 0 ? -1 : Integer.numberOfTrailingZeros(mask);
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /** "ESC leave" — the standing way out, always the last line of the stats block. */
    static Component leaveHint() {
        return Component.translatable("neroarcade.hud.leave_hint", Component.translatable("key.keyboard.escape"));
    }

    private static Object[] boxed(int[] args) {
        Object[] out = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            out[i] = args[i];
        }
        return out;
    }

    /** "[W][A][S][D] move · [SPACE] action · [SHIFT] 2nd action · [ESC] leave", then the CONTROL chip's own lines. */
    private static void drawLegend(GuiGraphicsExtractor g, Font font, HudLegendPayload legend, int width, int alpha) {
        int fg = (alpha << 24) | (ArcadeColours.TITLE & 0xFFFFFF);
        int subtle = (alpha << 24) | (ArcadeColours.SUBTLE & 0xFFFFFF);
        int cap = (alpha << 24) | (ArcadeColours.BUTTON_EDGE & 0xFFFFFF);
        int back = ((alpha * 0xC0 / 255) << 24) | (ArcadeColours.INK & 0xFFFFFF);
        List<Part> parts = List.of(
                cap(ArcadeKeys.UP), cap(ArcadeKeys.LEFT), cap(ArcadeKeys.DOWN), cap(ArcadeKeys.RIGHT),
                word("neroarcade.hud.legend.move"),
                cap(ArcadeKeys.BUTTON_A), word("neroarcade.hud.legend.a"),
                cap(ArcadeKeys.BUTTON_B), word("neroarcade.hud.legend.b"),
                new Part(Component.translatable("key.keyboard.escape"), true), word("neroarcade.hud.legend.leave"));
        int total = 0;
        for (Part part : parts) {
            total += part.width(font) + 3;
        }
        List<Component> lines = new ArrayList<>();
        if (legend != null) {
            // convention: %1$s = the A key's bound name, %2$s = the B key's
            Component a = ArcadeKeys.BUTTON_A.getTranslatedKeyMessage();
            Component b = ArcadeKeys.BUTTON_B.getTranslatedKeyMessage();
            for (String key : legend.legendLangKeys()) {
                lines.add(Component.translatable(key, a, b));
            }
        }
        int boxW = total + 8;
        for (Component line : lines) {
            boxW = Math.max(boxW, font.width(line) + 12);
        }
        int x0 = (width - boxW) / 2;
        int y0 = 6;
        g.fill(x0, y0, x0 + boxW, y0 + 17 + lines.size() * 10, back);
        int x = (width - total) / 2;
        for (Part part : parts) {
            if (part.keycap()) {
                keycap(g, font, part.text(), x, y0 + 3, fg, cap);
            } else {
                g.text(font, part.text(), x, y0 + 5, subtle, false);
            }
            x += part.width(font) + 3;
        }
        for (int i = 0; i < lines.size(); i++) {
            g.centeredText(font, lines.get(i), width / 2, y0 + 17 + i * 10, subtle);
        }
    }

    /** One piece of the legend line: a keycap (a key's bound name) or a word. */
    private record Part(Component text, boolean keycap) {
        int width(Font font) {
            return keycap ? Math.max(9, font.width(text) + 4) : font.width(text);
        }
    }

    private static Part cap(KeyMapping key) {
        return new Part(key.getTranslatedKeyMessage(), true);
    }

    private static Part word(String langKey) {
        return new Part(Component.translatable(langKey), false);
    }

    /** A keycap: the key's name in a 1-px box; returns its width. */
    private static int keycap(GuiGraphicsExtractor g, Font font, Component name, int x, int y, int fg, int edge) {
        int w = Math.max(9, font.width(name) + 4);
        g.outline(x, y, w, 11, edge);
        g.text(font, name, x + (w - font.width(name)) / 2, y + 2, fg, false);
        return w;
    }

    private static void drawCountdown(GuiGraphicsExtractor g, Font font, int ticksLeft, int width, int height) {
        String digit = Integer.toString(Math.max(1, (ticksLeft + 19) / 20));
        g.pose().pushMatrix();
        g.pose().translate(width / 2f, height / 2f - 40f);
        g.pose().scale(4f, 4f);
        g.text(font, digit, -font.width(digit) / 2, 0, ArcadeColours.TITLE, true);
        g.pose().popMatrix();
    }

    private static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, ArcadeColours.EDGE);
        g.fill(x, y, x + w, y + h, ArcadeColours.INNER);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, ArcadeColours.PANEL);
    }

    /**
     * Whole seconds until a short-handed lobby starts (rounded up, at least 1), or 0 when it is not waiting.
     * {@code ticksLeft} is the phase packet's value; {@code elapsed} the client ticks since it arrived. Pure.
     */
    public static int lobbyStartsInSeconds(int ticksLeft, long elapsed) {
        if (ticksLeft <= 0) {
            return 0;
        }
        long left = Math.max(0L, ticksLeft - Math.max(0L, elapsed));
        return (int) Math.max(1L, (left + 19) / 20);
    }

    private static void drawLobby(GuiGraphicsExtractor g, Font font, MatchPhasePayload phase, int startsIn, int width,
            int height) {
        int x = (width - PANEL_W) / 2;
        int y = height / 2 - PANEL_H - 10;
        panel(g, x, y, PANEL_W, PANEL_H);
        int seated = Integer.bitCount(phase.boundMask());
        // 005a P5: a lone seat back in the lobby after a results timeout is asked, not told to wait
        Component lobbyTitle = phase.seatCount() == 1 ? Component.translatable("neroarcade.hud.again")
                : Component.translatable("neroarcade.hud.waiting", seated, phase.seatCount());
        g.centeredText(font, lobbyTitle, width / 2, y + 6, ArcadeColours.TITLE);
        for (int s = 0; s < phase.seatCount() && s < 4; s++) {
            int ry = y + 20 + s * 11;
            boolean bound = (phase.boundMask() & (1 << s)) != 0;
            boolean ready = (phase.readyMask() & (1 << s)) != 0;
            g.text(font, Component.translatable("neroarcade.hud.player", s + 1), x + 8, ry,
                    bound ? ArcadeColours.TITLE : ArcadeColours.SUBTLE, false);
            if (ready) {
                Component word = Component.translatable("neroarcade.hud.ready");
                int pw = font.width(word) + 6;
                g.fill(x + PANEL_W - 8 - pw, ry - 1, x + PANEL_W - 8, ry + 9, ArcadeColours.OK_TEXT);
                g.text(font, word, x + PANEL_W - 5 - pw, ry, 0xFF05070D, false);
            }
        }
        g.centeredText(font, Component.translatable("neroarcade.hud.press_start",
                ArcadeKeys.BUTTON_A.getTranslatedKeyMessage()), width / 2, y + PANEL_H - 12,
                ArcadeColours.SUBTLE);
        if (startsIn > 0) {
            // final review M1: fewer players than seats, everyone present ready — the game starts without the rest
            g.centeredText(font, Component.translatable("neroarcade.hud.starting_in", startsIn), width / 2,
                    y + PANEL_H + 4, ArcadeColours.OK_TEXT);
        }
    }

    private static void drawResults(GuiGraphicsExtractor g, Font font, MatchPhasePayload phase, int mySeat, int width,
            int height) {
        int x = (width - PANEL_W) / 2;
        int y = height / 2 - PANEL_H - 10;
        panel(g, x, y, PANEL_W, PANEL_H);
        boolean won = phase.outcome() == MatchResult.Outcome.WIN.ordinal();
        g.centeredText(font, Component.translatable(outcomeKey(phase.outcome())), width / 2, y + 6,
                won ? ArcadeColours.OK_TEXT : ArcadeColours.TITLE);
        int[] ranks = phase.ranks();
        int[] scores = phase.scores();
        int rows = Math.min(4, Math.max(scores.length, 1));
        for (int s = 0; s < rows; s++) {
            int ry = y + 20 + s * 10;
            int colour = s == mySeat ? ArcadeColours.TITLE : ArcadeColours.SUBTLE;
            String rank = s < ranks.length && ranks[s] > 0 ? "#" + ranks[s] : "";
            g.text(font, Component.translatable("neroarcade.hud.player", s + 1), x + 8, ry, colour, false);
            g.text(font, rank, x + 40, ry, colour, false);
            g.text(font, Component.translatable("neroarcade.hud.score", s < scores.length ? scores[s] : 0), x + 64, ry,
                    colour, false);
            if ((phase.newBestMask() & (1 << s)) != 0) {
                g.text(font, Component.translatable("neroarcade.hud.new_best"), x + 112, ry, ArcadeColours.OK_TEXT,
                        false);
            }
        }
        g.centeredText(font, Component.translatable("neroarcade.hud.play_again",
                ArcadeKeys.BUTTON_A.getTranslatedKeyMessage()), width / 2, y + PANEL_H - 22,
                ArcadeColours.CYAN_TEXT);
        g.centeredText(font, Component.translatable("neroarcade.hud.esc_leave"), width / 2, y + PANEL_H - 12,
                ArcadeColours.SUBTLE);
    }

    static String outcomeKey(int outcome) {
        MatchResult.Outcome[] values = MatchResult.Outcome.values();
        MatchResult.Outcome o = outcome >= 0 && outcome < values.length ? values[outcome] : MatchResult.Outcome.LOSE;
        return "neroarcade.outcome." + o.name().toLowerCase(java.util.Locale.ROOT);
    }
}
