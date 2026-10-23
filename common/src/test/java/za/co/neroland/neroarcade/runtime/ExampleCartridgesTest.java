package za.co.neroland.neroarcade.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.neroarcade.TestFiles;
import za.co.neroland.neroarcade.game.CartridgeValidator;
import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.TestModules;

/**
 * The sixteen example cartridges of 008 §4 (also the gallery seed): each runs with its fixed seed and scripted
 * input to a RULE outcome or the hard stop, replays from its input log to the same hash, lands on the hash
 * pinned in {@code cartridges/hashes.txt} as {@code <name>@<variant>}, and its gallery-attract run lasts as long
 * as 008 §5 expects.
 *
 * <p>Variants: {@code script} is 008's open-loop script for seat 0 (a versus cartridge then gets the runtime
 * opponent in seat 1); {@code script2}/{@code script4} script two/four human seats. Where an open-loop script
 * cannot stay alive long enough to show the cartridge off (chasers, beams, a paddle duel), the {@code attract}
 * variant drives seat 0 with {@link Bot}: a closed-loop pseudo-player that looks a few ticks ahead on a copy of
 * the state. It is still an input script: the run is the input log, and the replay re-derives nothing from it.
 */
class ExampleCartridgesTest {

    @BeforeEach
    void registry() {
        TestModules.install();
    }

    // ---- open-loop scripts -------------------------------------------------------------------------------

    static final int U = SeatInput.UP;
    static final int D = SeatInput.DOWN;
    static final int L = SeatInput.LEFT;
    static final int R = SeatInput.RIGHT;
    static final int A = SeatInput.A;
    static final int B = SeatInput.B;

    /** One seat's input for match tick {@code t} (0 = the first simulated tick); pure. */
    @FunctionalInterface
    interface Seat {
        SeatInput at(int t);
    }

    /** Repeats {buttons, ticks} pairs. */
    static Seat cycle(int... pairs) {
        int period = 0;
        for (int i = 1; i < pairs.length; i += 2) {
            period += pairs[i];
        }
        int total = period;
        return t -> {
            int k = Math.floorMod(t, total);
            for (int i = 0; i < pairs.length; i += 2) {
                if (k < pairs[i + 1]) {
                    return SeatInput.of(pairs[i], 0, 0);
                }
                k -= pairs[i + 1];
            }
            return SeatInput.NONE;
        };
    }

    /** {@code buttons} on the one tick where {@code t % period == offset}. */
    static Seat pulse(int period, int offset, int buttons) {
        return t -> Math.floorMod(t, period) == offset ? SeatInput.of(buttons, 0, 0) : SeatInput.NONE;
    }

    static Seat both(Seat a, Seat b) {
        return t -> {
            SeatInput x = a.at(t);
            SeatInput y = b.at(t);
            return SeatInput.of(x.buttons() | y.buttons(), x.aimX() + y.aimX(), x.aimY() + y.aimY());
        };
    }

    static Seat after(int start, Seat then) {
        return t -> t < start ? SeatInput.NONE : then.at(t - start);
    }

    /** Still for 90 ticks, then 20 ticks of alternating steps toward the centre, then still. */
    static Seat toCentre(int first, int second) {
        return t -> t < 90 || t >= 110 ? SeatInput.NONE : SeatInput.of(((t - 90) / 5) % 2 == 0 ? first : second, 0, 0);
    }

    static Seat idle() {
        return t -> SeatInput.NONE;
    }

    // ---- drivers -----------------------------------------------------------------------------------------

    /** Produces a seat's input each step; may read the match (a closed-loop pseudo-player). */
    interface Driver {
        SeatInput next(HeadlessMatch match, int t, int seat, SeatInput[] current);
    }

    static Supplier<Driver> open(Seat seat) {
        return () -> (match, t, s, current) -> seat.at(t);
    }

    /** What the bot steers toward besides staying alive and scoring. */
    enum Aim {
        /** Nearest pickup by maze distance, else the nearest open exit. */
        PICKUPS,
        /** Line the pawn up with the hazard closest to it (a paddle tracking the ball or crates). */
        TRACK,
        /** The middle of the board. */
        CENTRE,
        NOTHING
    }

    /**
     * Lookahead pseudo-player: every {@code replan} ticks it tries each option held for {@code horizon} ticks on a
     * copy of the state (other seats hold their current input; the runtime opponent idles in the copy) and keeps
     * the best: lives first, then the outcome, then score, then distance to its aim. Deterministic.
     */
    static final class Bot implements Driver {
        private final BiFunction<MatchState, Integer, SeatInput[]> options;
        private final Aim aim;
        private final int horizon;
        private final int replan;
        private final int on;
        private final int period;
        private SeatInput current = SeatInput.NONE;
        private int nextPlan;
        /** Press the chosen buttons on the plan tick only (edge-triggered controls such as one_button). */
        private boolean tap;

        /**
         * @param on     with {@code period}: the bot chases its aim and score only while {@code t % period < on};
         *               the rest of the time it only stays alive and prefers standing still (a human-paced player
         *               that looks around). {@code on == period} = always.
         */
        Bot(Aim aim, int horizon, int replan, int on, int period,
                BiFunction<MatchState, Integer, SeatInput[]> options) {
            this.aim = aim;
            this.horizon = horizon;
            this.replan = replan;
            this.on = on;
            this.period = period;
            this.options = options;
        }

        static Supplier<Driver> of(Aim aim, int horizon, int replan, int... buttonOptions) {
            return paced(aim, horizon, replan, 1, 1, buttonOptions);
        }

        static Supplier<Driver> tapping(Aim aim, int horizon, int replan, int... buttonOptions) {
            SeatInput[] opts = Arrays.stream(buttonOptions).mapToObj(b -> SeatInput.of(b, 0, 0))
                    .toArray(SeatInput[]::new);
            return () -> {
                Bot bot = new Bot(aim, horizon, replan, 1, 1, (s, seat) -> opts);
                bot.tap = true;
                return bot;
            };
        }

        static Supplier<Driver> paced(Aim aim, int horizon, int replan, int on, int period, int... buttonOptions) {
            SeatInput[] opts = Arrays.stream(buttonOptions).mapToObj(b -> SeatInput.of(b, 0, 0))
                    .toArray(SeatInput[]::new);
            return () -> new Bot(aim, horizon, replan, on, period, (s, seat) -> opts);
        }

        @Override
        public SeatInput next(HeadlessMatch match, int t, int seat, SeatInput[] now) {
            if (t >= nextPlan) {
                nextPlan = t + replan;
                current = plan(match, seat, now, Math.floorMod(t, period) < on);
                return current;
            }
            return tap ? SeatInput.NONE : current;
        }

        private SeatInput plan(HeadlessMatch match, int seat, SeatInput[] now, boolean aiming) {
            MatchState base = match.state();
            ResolvedGame game = match.runtime().game();
            SeatInput best = current;
            long bestValue = Long.MIN_VALUE;
            for (SeatInput option : options.apply(base, seat)) {
                MatchState copy = base.copy();
                ArcadeRuntime sim = new ArcadeRuntime(game, copy, null);
                SeatInput[] in = now.clone();
                in[seat] = option;
                int lives = base.lives(seat);
                int score = base.score(seat);
                int survived = horizon;
                SeatInput[] released = in.clone();
                released[seat] = SeatInput.NONE;
                for (int k = 0; k < horizon && copy.phase() != MatchPhase.ENDED; k++) {
                    sim.step(tap && k > 0 ? released : in);
                    // this seat's own body went down (a shared-lives partner's hit is not this seat's death)
                    if (survived == horizon && (!copy.seatAlive(seat) || copy.respawnTicks(seat) > 0)) {
                        survived = k;
                    }
                }
                long value = 0;
                if (survived < horizon) {
                    // a lost life outweighs everything; among losing options, the latest loss
                    value -= 1_000_000L - survived * 1000L;
                }
                value -= 10_000L * (lives - copy.lives(seat));
                if (copy.phase() == MatchPhase.ENDED) {
                    value += (copy.winnersMask() & (1 << seat)) != 0 ? 5000 : -3000;
                }
                if (aiming) {
                    value += 40L * (copy.score(seat) - score);
                    value -= 4L * distance(copy, seat);
                    if (option.equals(current)) {
                        value += 6;
                    }
                } else if (option.buttons() == 0) {
                    value += 6;
                } else if (option.equals(current)) {
                    value += 3;
                }
                if (value > bestValue) {
                    bestValue = value;
                    best = option;
                }
            }
            return best;
        }

        private int distance(MatchState s, int seat) {
            int pawn = s.pawnId(seat);
            if (pawn < 0 || !s.exists(pawn) || aim == Aim.NOTHING) {
                return 0;
            }
            int px = s.get(pawn, Entities.X) >> MatchState.FP_SHIFT;
            int py = s.get(pawn, Entities.Y) >> MatchState.FP_SHIFT;
            return switch (aim) {
                case CENTRE -> Math.abs(px - s.width() / 2) + Math.abs(py - s.height() / 2);
                case TRACK -> track(s, px, py);
                case PICKUPS -> mazeDistance(s, px, py);
                case NOTHING -> 0;
            };
        }

        private static int track(MatchState s, int px, int py) {
            int[] bestHazard = {Integer.MAX_VALUE, 0};
            s.forEach(EntityKind.HAZARD, id -> {
                int hy = s.get(id, Entities.Y) >> MatchState.FP_SHIFT;
                int dy = Math.abs(hy - py);
                if (dy < bestHazard[0]) {
                    bestHazard[0] = dy;
                    bestHazard[1] = s.get(id, Entities.X) >> MatchState.FP_SHIFT;
                }
            });
            return bestHazard[0] == Integer.MAX_VALUE ? 0 : Math.abs(bestHazard[1] - px);
        }

        /** Multi-source BFS from every pickup (else every open exit) over non-blocking, non-void cells. */
        private static int mazeDistance(MatchState s, int px, int py) {
            int w = s.width();
            int h = s.height();
            int[] dist = new int[w * h];
            Arrays.fill(dist, -1);
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            s.forEach(EntityKind.PICKUP, id -> {
                int x = s.get(id, Entities.X) >> MatchState.FP_SHIFT;
                int y = s.get(id, Entities.Y) >> MatchState.FP_SHIFT;
                if (x >= 0 && y >= 0 && x < w && y < h && dist[y * w + x] < 0) {
                    dist[y * w + x] = 0;
                    queue.add(y * w + x);
                }
            });
            if (queue.isEmpty()) {
                for (int i = 0; i < w * h; i++) {
                    if (s.cellAt(i) == Cell.EXIT) {
                        dist[i] = 0;
                        queue.add(i);
                    }
                }
            }
            if (queue.isEmpty()) {
                return 0;
            }
            int[] dx = {1, -1, 0, 0};
            int[] dy = {0, 0, 1, -1};
            while (!queue.isEmpty()) {
                int c = queue.poll();
                int cx = c % w;
                int cy = c / w;
                for (int k = 0; k < 4; k++) {
                    int nx = cx + dx[k];
                    int ny = cy + dy[k];
                    if (s.wrapsX()) {
                        nx = Math.floorMod(nx, w);
                    }
                    if (s.wrapsY()) {
                        ny = Math.floorMod(ny, h);
                    }
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                        continue;
                    }
                    int n = ny * w + nx;
                    byte cell = s.cellAt(n);
                    if (dist[n] >= 0 || Cell.blocks(cell) || cell == Cell.VOID) {
                        continue;
                    }
                    dist[n] = dist[c] + 1;
                    queue.add(n);
                }
            }
            int at = Math.floorMod(py, h) * w + Math.floorMod(px, w);
            return dist[at] < 0 ? 2 * (w + h) : dist[at];
        }
    }

    /**
     * The inner driver, except that for the last {@code off} ticks of every {@code period} the player looks away and
     * presses nothing (a serpent then runs straight on).
     */
    static Supplier<Driver> lapse(Supplier<Driver> inner, int period, int off) {
        return () -> {
            Driver d = inner.get();
            return (match, t, seat, now) -> Math.floorMod(t, period) >= period - off ? SeatInput.NONE
                    : d.next(match, t, seat, now);
        };
    }

    /**
     * The inner driver, except that for the last {@code off} ticks of every {@code period} the player waggles the
     * stick (left on even periods, right on odd ones): a paddle then puts english on the ball, or misses it.
     */
    static Supplier<Driver> nudge(Supplier<Driver> inner, int period, int off) {
        return () -> {
            Driver d = inner.get();
            return (match, t, seat, now) -> Math.floorMod(t, period) < period - off ? d.next(match, t, seat, now)
                    : SeatInput.of((t / period) % 2 == 0 ? L : R, 0, 0);
        };
    }

    /** Twin-stick: the four moves (or none), always firing, aimed at the nearest hazard. */
    static Supplier<Driver> gunner(int fireOn, int firePeriod) {
        return () -> new Bot(Aim.NOTHING, 16, 4, 1, 1, (s, seat) -> {
            boolean fire = Math.floorMod(s.tick(), firePeriod) < fireOn;
            int pawn = s.pawnId(seat);
            int ax = 127;
            int ay = 0;
            if (pawn >= 0 && s.exists(pawn)) {
                int px = s.get(pawn, Entities.X);
                int py = s.get(pawn, Entities.Y);
                long[] best = {Long.MAX_VALUE, 0, 0};
                s.forEach(EntityKind.HAZARD, id -> {
                    long dx = s.get(id, Entities.X) - px;
                    long dy = s.get(id, Entities.Y) - py;
                    long d = dx * dx + dy * dy;
                    if (d < best[0]) {
                        best[0] = d;
                        best[1] = dx;
                        best[2] = dy;
                    }
                });
                if (best[0] != Long.MAX_VALUE) {
                    long m = Math.max(1, Math.max(Math.abs(best[1]), Math.abs(best[2])));
                    ax = (int) (best[1] * 127 / m);
                    ay = (int) (best[2] * 127 / m);
                }
            }
            int[] moves = {0, U, D, L, R};
            SeatInput[] out = new SeatInput[moves.length];
            for (int i = 0; i < moves.length; i++) {
                out[i] = SeatInput.of(moves[i] | (fire ? A : 0), ax, ay);
            }
            return out;
        });
    }

    // ---- the cartridges ----------------------------------------------------------------------------------

    record Variant(String key, Supplier<Driver>... seats) {
    }

    /**
     * One example cartridge: its 008 §4 number, the gallery-attract length window in seconds (008 §5 as corrected
     * 2026-10-23 for the reference games #1–#5, the 20–120 s attract window for the rest), which variant is the
     * gallery-attract run, and, when the window cannot be met without changing a chip, why (reported, not
     * asserted; no cartridge is exempt since the Stage 3 review fixes).
     */
    record Cart(int number, String name, int minSeconds, int maxSeconds, String attract, String exempt,
            List<Variant> variants) {
    }

    static List<Cart> carts() {
        List<Cart> out = new ArrayList<>();
        // 008b F7: lives 3 and a paced tracker (aims 3 ticks in 5). A bar that stands still returns a corner-entry
        // ball along its own diagonal (both axes reflect), so the paced tracker alone rallies to the hard stop; a
        // 20-tick waggle every 15 s puts english on the ball and now and then drops a serve
        out.add(new Cart(1, "paddle_duel", 45, 120, "attract", null,
                List.of(new Variant("script", open(cycle(L, 20, R, 20))),
                        new Variant("script2", open(cycle(L, 20, R, 20)), open(cycle(R, 15, L, 15))),
                        new Variant("attract", nudge(Bot.paced(Aim.TRACK, 20, 2, 3, 5, 0, L, R), 300, 20)))));
        out.add(new Cart(2, "serpent_garden", 20, 90, "attract", null, List.of(
                new Variant("script", open(cycle(U, 12, R, 12, D, 12, L, 12))),
                new Variant("attract", Bot.paced(Aim.PICKUPS, 12, 3, 60, 300, U, D, L, R)))));
        // pieces shifted left/right/held and dropped, a rotation now and then; the stack tops out near 60 s
        Seat stack = cycle(L, 2, 0, 8, A, 1, 0, 9, B, 1, 0, 19,
                R, 2, 0, 2, R, 2, 0, 14, B, 1, 0, 19,
                0, 20, B, 1, 0, 19,
                L, 2, 0, 2, L, 2, 0, 2, L, 2, 0, 10, B, 1, 0, 19,
                R, 2, 0, 2, R, 2, 0, 2, R, 2, 0, 10, B, 1, 0, 19,
                A, 1, 0, 19, B, 1, 0, 19);
        out.add(new Cart(3, "stack_nine", 60, 180, "script", null, List.of(new Variant("script", open(stack)))));
        out.add(new Cart(4, "drone_maze", 20, 110, "attract", null, List.of(
                new Variant("script", open(cycle(R, 30, D, 30, L, 30, U, 30))),
                new Variant("attract", Bot.paced(Aim.PICKUPS, 16, 3, 3, 9, 0, U, D, L, R)))));
        out.add(new Cart(5, "lane_hopper", 30, 75, "attract", null, List.of(
                new Variant("script", open(pulse(30, 0, A))),
                new Variant("attract", Bot.of(Aim.NOTHING, 16, 2, 0, A)))));
        Seat hot = both(cycle(R, 40, D, 20, L, 40, U, 20), pulse(30, 0, A));
        out.add(new Cart(6, "hot_seat_orbs", 20, 120, "script2", null, List.of(
                new Variant("script2", open(hot), open(hot)))));
        int[] eight = {0, U, D, L, R, U | L, U | R, D | L, D | R};
        out.add(new Cart(7, "tandem_sweep", 20, 120, "attract", null, List.of(
                new Variant("script2", open(cycle(U | R, 20, D | L, 20)), open(cycle(D, 25, U, 25))),
                new Variant("attract", Bot.of(Aim.PICKUPS, 12, 2, eight), Bot.of(Aim.PICKUPS, 12, 2, eight)))));
        out.add(new Cart(8, "vault_run", 20, 120, "attract", null, List.of(
                new Variant("script", open(cycle(R, 60, D, 60))),
                new Variant("attract", Bot.of(Aim.PICKUPS, 20, 3, 0, U, D, L, R)))));
        out.add(new Cart(9, "gunner_ring", 20, 120, "attract", null, List.of(
                new Variant("script", open(both(t -> SeatInput.of(A, 127, 0), cycle(L, 10, R, 10)))),
                new Variant("attract", gunner(20, 120)))));
        // 008b F7: two serpents (speed 2, length 2) on a shrinking island; the greedy opponent hunts in the solo
        // script, a paced centre-seeking bot drives both seats in the gallery run. Two lookahead bots never die on
        // the 4x4 island, so each looks away for one second now and then (seat 0 every 60 s, seat 1 every 50 s)
        Supplier<Driver> islander = Bot.paced(Aim.CENTRE, 16, 3, 4, 8, U, D, L, R);
        out.add(new Cart(10, "shrink_island", 20, 120, "attract", null, List.of(
                new Variant("script", open(toCentre(D, R))),
                new Variant("attract", lapse(islander, 1200, 20), lapse(islander, 1000, 20)))));
        // mirrored horizontally: this is the real right, down, left, up square
        out.add(new Cart(11, "mirror_pulse", 20, 120, "attract", null, List.of(
                new Variant("script", open(cycle(L, 10, D, 10, R, 10, U, 10))),
                new Variant("attract", Bot.of(Aim.PICKUPS, 12, 2, U, D, L, R)))));
        out.add(new Cart(12, "float_lane", 20, 120, "attract", null, List.of(
                new Variant("script", open(both(pulse(24, 0, A), both(pulse(400, 0, R), pulse(400, 200, L))))),
                new Variant("attract", Bot.of(Aim.NOTHING, 16, 2, 0, L, R, A)))));
        Seat r0 = cycle(R, 10, D, 10, L, 10, U, 10);
        Seat r1 = after(10, cycle(R, 10, D, 10, L, 10, U, 10));
        Seat r2 = after(20, cycle(R, 10, D, 10, L, 10, U, 10));
        // the open-loop script4 run leaves one chaser circling clear of the two survivors until the hard stop once
        // respawns carry the A15.b grace; the gallery run is four paced orb-hunting bots
        Supplier<Driver> rally = Bot.paced(Aim.PICKUPS, 12, 3, 60, 300, U, D, L, R);
        out.add(new Cart(13, "four_way_rally", 20, 120, "attract", null, List.of(
                new Variant("script", open(r0)),
                new Variant("script4", open(r0), open(r1), open(r2), open(idle())),
                new Variant("attract", rally, rally, rally, rally))));
        Supplier<Driver> catcher = Bot.of(Aim.TRACK, 20, 2, 0, L, R);
        out.add(new Cart(14, "crate_catch", 20, 120, "attract", null, List.of(
                new Variant("script2", open(cycle(L, 8, R, 8)), open(cycle(R, 8, L, 8))),
                new Variant("attract", catcher, catcher))));
        out.add(new Cart(15, "blind_tube", 20, 120, "attract", null, List.of(
                new Variant("script", open(both(pulse(100, 20, A), both(pulse(100, 50, B), pulse(100, 80, A))))),
                new Variant("attract", Bot.tapping(Aim.PICKUPS, 16, 2, 0, A, B)))));
        Seat stackLeft = cycle(L, 3, 0, 9, B, 1, 0, 2, R, 3, 0, 12, B, 1, 0, 10);
        Seat stackRight = cycle(R, 3, 0, 9, B, 1, 0, 2, L, 3, 0, 12, B, 1, 0, 10);
        out.add(new Cart(16, "stack_duel", 20, 120, "attract", null, List.of(
                new Variant("script", open(stackLeft)),
                new Variant("script2", open(stackLeft), open(stackRight)),
                // no hard drops: the pieces fall on their own, so the duel lasts
                new Variant("attract", open(cycle(L, 3, 0, 37, R, 3, 0, 37)),
                        open(cycle(R, 3, 0, 37, L, 3, 0, 37))))));
        return out;
    }

    // ---- running -----------------------------------------------------------------------------------------

    record Run(String key, MatchResult result, long hash, Replay replay) {
        int seconds() {
            return result.ticks() / Budgets.TICKS_PER_SECOND;
        }
    }

    static GameDef def(String name) throws Exception {
        return CartridgeValidator.decodeJson(TestFiles.cartridge(name + ".json"), Budgets.MAX_IMPORT_JSON_BYTES);
    }

    static Run play(Cart cart, Variant v) throws Exception {
        GameDef def = def(cart.name());
        int seats = v.seats().length;
        HeadlessMatch match = HeadlessMatch.of(def, seats, def.seed().orElseThrow());
        Driver[] drivers = new Driver[seats];
        for (int s = 0; s < seats; s++) {
            drivers[s] = v.seats()[s].get();
        }
        SeatInput[] row = new SeatInput[seats];
        Arrays.fill(row, SeatInput.NONE);
        int limit = Budgets.COUNTDOWN_TICKS + Budgets.maxMatchSeconds() * Budgets.TICKS_PER_SECOND + 10;
        while (match.result().isEmpty() && match.runtime().steps() < limit) {
            int t = match.runtime().steps() - Budgets.COUNTDOWN_TICKS;
            for (int s = 0; s < seats; s++) {
                row[s] = t < 0 ? SeatInput.NONE : drivers[s].next(match, t, s, row);
            }
            match.step(row.clone());
        }
        return new Run(cart.name() + "@" + v.key(), match.result().orElseThrow(), match.hash(), match.toReplay());
    }

    static Map<String, Long> pinned() {
        Map<String, Long> out = new LinkedHashMap<>();
        Path file = TestFiles.testResources().resolve("cartridges/hashes.txt");
        for (String line : TestFiles.read(file).lines().map(String::strip).toList()) {
            if (!line.isEmpty() && !line.startsWith("#") && line.contains("@")) {
                String[] parts = line.split("=");
                out.put(parts[0].strip(), Long.parseUnsignedLong(parts[1].strip(), 16));
            }
        }
        return out;
    }

    @Test
    void everyExampleRunsToAnOutcomeReplaysAndIsPinned() throws Exception {
        Map<String, Long> pinned = pinned();
        List<String> actual = new ArrayList<>();
        List<String> lengths = new ArrayList<>();
        List<String> outOfRange = new ArrayList<>();
        for (Cart cart : carts()) {
            assertTrue(cart.variants().stream().anyMatch(v -> v.key().equals(cart.attract())), cart.name());
            for (Variant v : cart.variants()) {
                Run first = play(cart, v);
                Run second = play(cart, v);
                assertEquals(first.hash(), second.hash(), first.key() + ": the same script ran to another hash");
                assertEquals(first.result().ticks(), second.result().ticks(), first.key());
                // the replay verifies its own final hash (throws on a desync); run it twice
                assertEquals(first.hash(), first.replay().run().hash(), first.key());
                assertEquals(first.hash(), first.replay().run().hash(), first.key());
                actual.add(first.key() + "=" + String.format("%016x", first.hash()));
                MatchResult r = first.result();
                lengths.add(String.format("#%d %s %s winners=%d %d ticks (%d s) scores=%s", cart.number(), first.key(),
                        r.outcome(), r.winnersMask(), r.ticks(), first.seconds(), Arrays.toString(r.scores())));
                boolean inRange = first.seconds() >= cart.minSeconds() && first.seconds() <= cart.maxSeconds();
                if (v.key().equals(cart.attract()) && !inRange) {
                    String line = first.key() + " " + first.seconds() + " s not in " + cart.minSeconds() + ".."
                            + cart.maxSeconds();
                    if (cart.exempt() == null) {
                        outOfRange.add(line);
                    } else {
                        lengths.add("  known short: " + line + " (" + cart.exempt() + ")");
                    }
                }
            }
        }
        lengths.forEach(System.out::println);
        actual.forEach(System.out::println);
        for (String line : actual) {
            String key = line.substring(0, line.indexOf('='));
            long hash = Long.parseUnsignedLong(line.substring(line.indexOf('=') + 1), 16);
            assertEquals(pinned.get(key), Long.valueOf(hash), key + " final hash changed; actual: " + actual);
        }
        assertEquals(actual.size(), pinned.size(), "pin these in cartridges/hashes.txt: " + actual);
        assertTrue(outOfRange.isEmpty(), "gallery attract lengths out of range: " + outOfRange);
    }

    // ---- coverage and gallery labels ---------------------------------------------------------------------

    static List<String> chipIds(GameDef def) {
        List<String> ids = new ArrayList<>();
        def.slots().values().forEach(entries -> entries.forEach(e -> ids.add(e.id().toString())));
        return ids;
    }

    /** Every registered chip appears in at least one example; all four themes; coop, versus 2/4, turns. */
    @Test
    void theExamplesCoverEveryChip() throws Exception {
        TestModules.installShipped();
        try {
            java.util.Set<String> used = new java.util.TreeSet<>();
            List<Integer> versusSeats = new ArrayList<>();
            for (Cart cart : carts()) {
                GameDef def = def(cart.name());
                ResolvedGame game = CartridgeValidator.validate(def);
                used.addAll(chipIds(def));
                if (game.multiplayer().id().getPath().equals("multiplayer/versus")) {
                    versusSeats.add(game.multiplayerProps().getInt("seats"));
                }
            }
            List<String> missing = za.co.neroland.neroarcade.module.ModuleRegistry.all().stream()
                    .map(m -> m.id().toString())
                    .filter(id -> !used.contains(id))
                    .toList();
            assertEquals(List.of(), missing, "chips no example cartridge shows");
            for (String theme : List.of("neon_void", "hangar", "cindara_dusk", "terminal_green")) {
                assertTrue(used.contains("neroarcade:theme/" + theme), theme);
            }
            for (String mp : List.of("solo", "coop", "versus", "turns")) {
                assertTrue(used.contains("neroarcade:multiplayer/" + mp), mp);
            }
            assertTrue(versusSeats.contains(2) && versusSeats.contains(4), "versus at 2 and 4 seats: " + versusSeats);
            assertEquals(16, carts().size());
        } finally {
            TestModules.install();
        }
    }

    /**
     * 008 §4 gallery labels: one cabinet per chip, showing the lowest-numbered example that contains it; line 1
     * the cartridge title, line 2 the chip's name, each at most 15 characters for the hanging sign.
     */
    @Test
    void galleryLabelsFitTheSign() throws Exception {
        TestModules.installShipped();
        try {
            Map<String, String> lang = TestFiles.lang();
            Map<String, String> labels = new java.util.TreeMap<>();
            for (Cart cart : carts()) {
                GameDef def = def(cart.name());
                assertTrue(def.title().length() <= 15, def.title());
                for (String id : chipIds(def)) {
                    labels.putIfAbsent(id, "#" + cart.number() + " " + def.title());
                }
            }
            for (za.co.neroland.neroarcade.module.ArcadeModule module
                    : za.co.neroland.neroarcade.module.ModuleRegistry.all()) {
                String id = module.id().toString();
                assertTrue(labels.containsKey(id), "no cabinet for " + id);
                String name = lang.get(module.langKey());
                assertTrue(name != null && name.length() <= 15, module.langKey() + " = " + name);
            }
            assertEquals(43, labels.size(), labels.toString());
            labels.forEach((id, label) -> System.out.println(id + " -> " + label));
        } finally {
            TestModules.install();
        }
    }
}
