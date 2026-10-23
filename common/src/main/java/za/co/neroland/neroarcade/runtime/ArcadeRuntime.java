package za.co.neroland.neroarcade.runtime;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.module.ModuleFlag;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.PropertyValues;
import za.co.neroland.neroarcade.module.hook.HazardModule;
import za.co.neroland.neroarcade.module.hook.HitOutcome;
import za.co.neroland.neroarcade.module.hook.Intent;
import za.co.neroland.neroarcade.module.hook.ModuleContext;
import za.co.neroland.neroarcade.module.hook.PawnModule;
import za.co.neroland.neroarcade.module.hook.PickupModule;
import za.co.neroland.neroarcade.module.hook.RuleOutcome;

/**
 * The deterministic simulation of one match: {@code (MatchState, SeatInput[], Rng)} in, the next state out.
 *
 * <p><b>Invariants.</b> Integer-only. The only randomness is {@link Rng}, a view over
 * {@code MatchState.rngState}. Hooks are called in exactly the contract's order (one simulated tick):
 * PACE divisor → ARENA tick (1b) → runtime-opponent decision → CONTROL interpret per seat → MULTIPLAYER active
 * mask → MODIFIER transformIntent per seat → PAWN applyInput per seat, then PAWN tick per seat → HAZARD tick
 * ×stack → PICKUP tick ×stack → collision pass (8: per seat, then pawn–pawn; 8b: goal cells; 8c: projectiles
 * vs hazards) → MODIFIER afterTick → MULTIPLAYER afterTick → housekeeping → RULE evaluate (with the hard
 * stop) → MULTIPLAYER resolve → {@code tick++}. Stack slots run 0→2, seats 0→3, entities in id order. Inputs
 * apply at tick boundaries only. Desync is a defect: the same {@code (game, seed, inputs)} always yields the
 * same {@link StateHash}.
 *
 * <p>Hits: the pawn decides ({@code PawnModule.onHit} → {@link HitOutcome}) and the runtime applies it; the
 * hook never calls {@code damageSeat} for the hit itself.
 *
 * <p>Pace: the base clock runs at 20 Hz; with divisor {@code d} the match simulates only when
 * {@code tick % d == 0}. On a skipped tick the latest input is held and any button that went down is kept
 * (sticky) for the next simulated tick, so a tap is never lost.
 *
 * <p>PAWN hooks run only for seats that are alive and currently have a pawn on the board.
 */
public final class ArcadeRuntime {

    /**
     * Simulated ticks between the default greedy opponent's <i>target</i> decisions (001 A13); its direction is
     * re-steered every simulated tick. A pawn that overrides {@code PawnModule.opponentInput} is asked every
     * simulated tick (001 A15.a); its answer is still an input latch, re-derived on replay.
     */
    public static final int OPPONENT_CADENCE = 4;

    private final ResolvedGame game;
    private final MatchState state;
    private final Rng rng;
    private final SeatInput[] latest = new SeatInput[Budgets.MAX_SEATS];
    private final SeatInput[] previous = new SeatInput[Budgets.MAX_SEATS];
    private final SeatInput[] now = new SeatInput[Budgets.MAX_SEATS];
    private final int[] sticky = new int[Budgets.MAX_SEATS];
    private final Intent[] intents = new Intent[Budgets.MAX_SEATS];
    private final int[] footX;
    private final int[] footY;
    private final int[] otherX;
    private final int[] otherY;
    private int maxTicks;
    private int opponentSeat = -1;
    private SeatInput opponentInput = SeatInput.NONE;
    /** True when the PAWN chip keeps the default {@code opponentInput} (greedy): target latched, steered per tick. */
    private boolean opponentSteers;
    /** The greedy's latched target ({@link RuntimeOpponent#target}); an input latch like {@link #opponentInput}. */
    private int opponentTarget = RuntimeOpponent.NO_TARGET;
    private int opponentTicks;
    private int steps;
    private MatchResult result;

    /**
     * @param state fresh (LOBBY, from {@link #newState}) or restored from a keyframe
     * @param rng   ignored unless it is already a view over {@code state}; the runtime always draws from
     *              {@code state.rngState} so the hash and keyframes cover the generator
     */
    public ArcadeRuntime(ResolvedGame game, MatchState state, Rng rng) {
        this.game = game;
        this.state = state;
        this.rng = rng != null && rng.isViewOf(state) ? rng : Rng.view(state);
        Arrays.fill(latest, SeatInput.NONE);
        Arrays.fill(previous, SeatInput.NONE);
        Arrays.fill(now, SeatInput.NONE);
        Arrays.fill(intents, Intent.NONE);
        int capacity = state.entityCapacity() + 1;
        this.footX = new int[capacity];
        this.footY = new int[capacity];
        this.otherX = new int[capacity];
        this.otherY = new int[capacity];
        this.maxTicks = maxTicks();
        if (state.phase == MatchPhase.ENDED) {
            result = buildResult();
        }
    }

    /** Allocates a LOBBY state at budget sizes: the arena clamped to 4..maxArenaSide, seats to 1..4. */
    public static MatchState newState(ResolvedGame game, int seats) {
        int side = Math.min(Budgets.maxArenaSide(), Budgets.HARD_MAX_ARENA_SIDE);
        int w = Math.max(Budgets.MIN_ARENA_SIDE, Math.min(side, game.arenaWidth()));
        int h = Math.max(Budgets.MIN_ARENA_SIDE, Math.min(side, game.arenaHeight()));
        int entities = Math.max(1, Math.min(Budgets.HARD_MAX_ENTITIES, Budgets.maxEntities()));
        return new MatchState(w, h, entities, Math.max(1, Math.min(Budgets.MAX_SEATS, seats)));
    }

    /**
     * LOBBY → COUNTDOWN. Seeds the RNG, then: ARENA build; RULE, HAZARD (stack order), PICKUP (stack order),
     * MODIFIER match start; MULTIPLAYER layout; PAWN spawn per seat. A multi-seat-only game started with one
     * seat gets a runtime opponent in seat 1 when the MULTIPLAYER chip offers one, otherwise the rule's
     * {@code onSeatDropped(ctx, 1)} solo fallback applies from tick 0.
     */
    public void start(long seed) {
        if (state.phase != MatchPhase.LOBBY) {
            throw new IllegalStateException("start() needs a LOBBY state, not " + state.phase);
        }
        rng.setState(seed);
        maxTicks = maxTicks();
        boolean wraps = game.arena().wraps(game.arenaProps());
        state.setWrap(wraps, wraps);
        game.arena().build(ctx(game.arenaProps(), 0));

        boolean multiSeatOnly = game.flags(ModuleType.RULE).contains(ModuleFlag.MULTI_SEAT_ONLY)
                || game.flags(ModuleType.MULTIPLAYER).contains(ModuleFlag.MULTI_SEAT_ONLY);
        boolean soloFallback = false;
        if (multiSeatOnly && state.seatCount == 1) {
            if (game.multiplayer().runtimeOpponent(game.multiplayerProps())) {
                opponentSeat = 1;
                opponentSteers = usesDefaultOpponent(game.pawn());
                state.seatCount = 2;
                state.aliveSeatMask |= 2;
                state.seatLives[1] = 1;
            } else {
                soloFallback = true;
            }
        }

        game.rule().onMatchStart(ctx(game.ruleProps(), 0));
        for (int i = 0; i < game.hazards().size(); i++) {
            game.hazards().get(i).onMatchStart(ctx(game.hazardProps().get(i), i));
        }
        for (int i = 0; i < game.pickups().size(); i++) {
            game.pickups().get(i).onMatchStart(ctx(game.pickupProps().get(i), i));
        }
        if (game.modifier().isPresent()) {
            game.modifier().get().onMatchStart(ctx(game.modifierProps().orElseThrow(), 0));
        }
        ModuleContext mp = ctx(game.multiplayerProps(), 0);
        state.layout = game.multiplayer().layout(mp);
        if (game.multiplayer().sharesLives(game.multiplayerProps())) {
            state.sharedLives = state.seatLives[0];
            for (int s = 0; s < state.seatCount; s++) {
                state.seatLives[s] = state.sharedLives;
            }
        } else {
            state.sharedLives = -1;
        }
        ModuleContext pawnCtx = ctx(game.pawnProps(), 0);
        ModuleContext arenaCtx = ctx(game.arenaProps(), 0);
        for (int s = 0; s < state.seatCount; s++) {
            int[] cell = game.arena().spawnCell(arenaCtx, s);
            game.pawn().spawn(pawnCtx, s, cell[0], cell[1]);
        }
        if (soloFallback) {
            game.rule().onSeatDropped(ctx(game.ruleProps(), 0), 1);
        }
        state.phase = MatchPhase.COUNTDOWN;
        state.countdownTicks = Budgets.COUNTDOWN_TICKS;
    }

    /**
     * One base tick. {@code inputs[s]} is seat {@code s}'s latest input; missing or null entries are
     * {@link SeatInput#NONE}. LOBBY and ENDED only latch inputs; COUNTDOWN counts down and flips to PLAYING.
     */
    public void step(SeatInput[] inputs) {
        if (inputs.length > Budgets.MAX_SEATS) {
            throw new IllegalArgumentException("at most " + Budgets.MAX_SEATS + " seats");
        }
        steps++;
        for (int s = 0; s < Budgets.MAX_SEATS; s++) {
            SeatInput in = s < inputs.length ? inputs[s] : null;
            latest[s] = s < state.seatCount && in != null ? in : SeatInput.NONE;
        }
        if (opponentSeat >= 0) {
            latest[opponentSeat] = opponentInput;
        }
        switch (state.phase) {
            case LOBBY, ENDED -> latch();
            case COUNTDOWN -> {
                state.countdownTicks = Math.max(0, state.countdownTicks - 1);
                if (state.countdownTicks == 0) {
                    state.phase = MatchPhase.PLAYING;
                }
                latch();
            }
            case PLAYING -> simulate();
        }
    }

    private void simulate() {
        // 1. PACE
        int divisor = game.pace().divisor(ctx(game.paceProps(), 0));
        divisor = Math.max(1, Math.min(Budgets.MAX_PACE_DIVISOR, divisor));
        state.paceDivisor = divisor;
        if (state.tick % divisor != 0) {
            for (int s = 0; s < state.seatCount; s++) {
                sticky[s] |= latest[s].buttons() & ~previous[s].buttons();
            }
            state.tick++;
            return;
        }
        // 1b. ARENA tick
        game.arena().tick(ctx(game.arenaProps(), 0));
        // runtime opponent (an input, not state): greedy target every OPPONENT_CADENCE simulated ticks (A13),
        // an overriding pawn's opponentInput every simulated tick (A15.a)
        if (opponentSeat >= 0) {
            ModuleContext pawnCtx = ctx(game.pawnProps(), 0);
            boolean pawnUp = hasPawn(opponentSeat);
            if (opponentSteers) {
                // greedy: target every OPPONENT_CADENCE simulated ticks, direction every tick (PROGRESS 2026-10-21)
                if (opponentTicks % OPPONENT_CADENCE == 0) {
                    opponentTarget = pawnUp ? RuntimeOpponent.target(pawnCtx, opponentSeat) : RuntimeOpponent.NO_TARGET;
                }
                opponentInput = pawnUp ? RuntimeOpponent.steer(pawnCtx, opponentSeat, opponentTarget) : SeatInput.NONE;
            } else {
                SeatInput decided = pawnUp ? game.pawn().opponentInput(pawnCtx, opponentSeat) : SeatInput.NONE;
                opponentInput = decided == null ? SeatInput.NONE : decided;
            }
            opponentTicks++;
            latest[opponentSeat] = opponentInput;
        }
        for (int s = 0; s < state.seatCount; s++) {
            SeatInput in = latest[s];
            now[s] = sticky[s] == 0 ? in : SeatInput.of(in.buttons() | sticky[s], in.aimX(), in.aimY());
        }

        // 2. CONTROL
        ModuleContext controlCtx = ctx(game.controlProps(), 0);
        for (int s = 0; s < state.seatCount; s++) {
            Intent intent = game.control().interpret(controlCtx, s, now[s], previous[s]);
            intents[s] = intent == null ? Intent.NONE : intent;
        }
        // 3. MULTIPLAYER active mask
        ModuleContext mpCtx = ctx(game.multiplayerProps(), 0);
        int active = game.multiplayer().activeSeatMask(mpCtx) & ~state.droppedSeatMask;
        for (int s = 0; s < state.seatCount; s++) {
            if ((active & (1 << s)) == 0) {
                intents[s] = Intent.NONE;
            }
        }
        // 4. MODIFIER transformIntent
        ModuleContext modCtx = game.modifier().isPresent() ? ctx(game.modifierProps().orElseThrow(), 0) : null;
        if (modCtx != null) {
            for (int s = 0; s < state.seatCount; s++) {
                Intent out = game.modifier().get().transformIntent(modCtx, s, intents[s]);
                intents[s] = out == null ? Intent.NONE : out;
            }
        }
        // 5. PAWN applyInput, then tick
        ModuleContext pawnCtx = ctx(game.pawnProps(), 0);
        for (int s = 0; s < state.seatCount; s++) {
            if (hasPawn(s)) {
                game.pawn().applyInput(pawnCtx, s, intents[s]);
            }
        }
        for (int s = 0; s < state.seatCount; s++) {
            if (hasPawn(s)) {
                game.pawn().tick(pawnCtx, s);
            }
        }
        // 6. HAZARD, 7. PICKUP
        List<HazardModule> hazards = game.hazards();
        for (int i = 0; i < hazards.size(); i++) {
            hazards.get(i).tick(ctx(game.hazardProps().get(i), i));
        }
        List<PickupModule> pickups = game.pickups();
        for (int i = 0; i < pickups.size(); i++) {
            pickups.get(i).tick(ctx(game.pickupProps().get(i), i));
        }
        // 8. collision pass
        collisions(pawnCtx);
        // 9. MODIFIER afterTick, 10. MULTIPLAYER afterTick
        if (modCtx != null) {
            game.modifier().get().afterTick(modCtx);
        }
        game.multiplayer().afterTick(mpCtx);
        // 11. housekeeping
        housekeeping(pawnCtx, mpCtx);
        // 12. RULE (+ hard stop), 13. MULTIPLAYER resolve
        RuleOutcome outcome = game.rule().evaluate(ctx(game.ruleProps(), 0));
        if (outcome == null) {
            outcome = RuleOutcome.CONTINUE;
        }
        if (outcome == RuleOutcome.CONTINUE && state.tick + 1 >= maxTicks) {
            state.setWinners(0);
            outcome = RuleOutcome.DRAW;
        }
        RuleOutcome resolved = game.multiplayer().resolve(mpCtx, outcome);
        if (resolved == null) {
            resolved = outcome;
        }
        latch();
        // 14. tick++
        state.tick++;
        if (resolved != RuleOutcome.CONTINUE) {
            state.phase = MatchPhase.ENDED;
            state.result = switch (resolved) {
                case WIN -> MatchResult.Outcome.WIN.ordinal();
                case LOSE -> MatchResult.Outcome.LOSE.ordinal();
                default -> MatchResult.Outcome.DRAW.ordinal();
            };
            result = buildResult();
        }
    }

    private void latch() {
        for (int s = 0; s < Budgets.MAX_SEATS; s++) {
            previous[s] = latest[s];
            sticky[s] = 0;
        }
    }

    private boolean hasPawn(int seat) {
        int id = state.pawnId(seat);
        return state.seatAlive(seat) && id >= 0 && state.exists(id);
    }

    /**
     * Step 8: pickups, hazards and enemy projectiles against each seat's body (the pawn decides a hit's
     * {@link HitOutcome}), then directional pawn-vs-pawn; 8b: goal cells; 8c: projectiles against hazards.
     * Entities carrying {@link Entities#F_HIDDEN} are off the board and skipped by 8, 8b and 8c (001 A15.c).
     */
    private void collisions(ModuleContext pawnCtx) {
        for (int seat = 0; seat < state.seatCount; seat++) {
            if (!hasPawn(seat)) {
                continue;
            }
            int feet = footprint(seat, footX, footY);
            int id = -1;
            while (hasPawn(seat)) {
                int index = state.lowerBound(id + 1);
                if (index >= state.entityCount) {
                    break;
                }
                int base = index * MatchState.ENTITY_STRIDE;
                id = state.entities[base + Entities.ID];
                int kind = state.entities[base + Entities.KIND];
                int owner = state.entities[base + Entities.OWNER];
                if (id == state.pawnId(seat) || (kind == EntityKind.SEGMENT.ordinal() && owner == seat)
                        || (state.entities[base + Entities.FLAGS] & Entities.F_HIDDEN) != 0
                        || !overlaps(state.entities[base + Entities.X] >> MatchState.FP_SHIFT,
                                state.entities[base + Entities.Y] >> MatchState.FP_SHIFT, footX, footY, feet)) {
                    continue;
                }
                int tag = state.aux[index] & 3;
                if (kind == EntityKind.PICKUP.ordinal()) {
                    if (tag < game.pickups().size()) {
                        game.pickups().get(tag).onCollect(ctx(game.pickupProps().get(tag), tag), seat, id);
                    }
                    if (hasPawn(seat)) {
                        game.pawn().onCollect(pawnCtx, seat, id);
                    }
                    int still = state.find(id);
                    if (still >= 0 && (state.entities[still * MatchState.ENTITY_STRIDE + Entities.FLAGS] & Entities.F_PERSISTENT) == 0) {
                        state.remove(id);
                    }
                } else if (kind == EntityKind.HAZARD.ordinal()) {
                    boolean lethal = true;
                    HazardModule hazard = null;
                    ModuleContext hazardCtx = null;
                    if (tag < game.hazards().size()) {
                        hazard = game.hazards().get(tag);
                        PropertyValues props = game.hazardProps().get(tag);
                        hazardCtx = ctx(props, tag);
                        hazard.onHitPawn(hazardCtx, seat, id);
                        lethal = hazard.lethal(props);
                    }
                    if (lethal && hasPawn(seat) && state.exists(id)) {
                        switch (outcome(game.pawn().onHit(pawnCtx, seat, id))) {
                            case DAMAGE -> state.damageSeat(seat, 1);
                            case DEFLECT -> {
                                if (hazard != null) {
                                    hazard.onDeflected(hazardCtx, seat, id, state.pawnId(seat));
                                } else {
                                    Deflect.reflect(state, id);
                                }
                            }
                            case IGNORE -> {
                            }
                        }
                    }
                } else if (kind == EntityKind.PROJECTILE.ordinal() && owner != seat) {
                    switch (outcome(game.pawn().onHit(pawnCtx, seat, id))) {
                        case DAMAGE -> {
                            state.damageSeat(seat, 1);
                            removeUnlessPersistent(id);
                        }
                        case DEFLECT -> {
                            if (state.exists(id)) {
                                Deflect.reflect(state, id);
                                state.set(id, Entities.OWNER, seat);
                            }
                        }
                        case IGNORE -> {
                        }
                    }
                }
            }
        }
        pawnVsPawn(pawnCtx);
        goalPass();
        projectilePass();
    }

    private static HitOutcome outcome(HitOutcome fromHook) {
        return fromHook == null ? HitOutcome.DAMAGE : fromHook;
    }

    private void removeUnlessPersistent(int id) {
        int index = state.find(id);
        if (index >= 0 && (state.entities[index * MatchState.ENTITY_STRIDE + Entities.FLAGS] & Entities.F_PERSISTENT) == 0) {
            state.remove(id);
        }
    }

    /**
     * Pawn vs pawn ({@code LETHAL_TO_PAWNS}), directional: for seats a &lt; b, a seat whose head (its PAWN entity's
     * cell) lies in the other's footprint is hit by the other's pawn; head-on hits both. Both outcomes are asked
     * before either is applied. DEFLECT and IGNORE do nothing here.
     */
    private void pawnVsPawn(ModuleContext pawnCtx) {
        if (!game.pawn().flags().contains(ModuleFlag.LETHAL_TO_PAWNS)) {
            return;
        }
        for (int a = 0; a < state.seatCount; a++) {
            for (int b = a + 1; b < state.seatCount; b++) {
                if (!hasPawn(a) || !hasPawn(b)) {
                    continue;
                }
                int na = footprint(a, footX, footY);
                int nb = footprint(b, otherX, otherY);
                int pawnA = state.pawnId(a);
                int pawnB = state.pawnId(b);
                boolean aHit = overlaps(state.get(pawnA, Entities.X) >> MatchState.FP_SHIFT,
                        state.get(pawnA, Entities.Y) >> MatchState.FP_SHIFT, otherX, otherY, nb);
                boolean bHit = overlaps(state.get(pawnB, Entities.X) >> MatchState.FP_SHIFT,
                        state.get(pawnB, Entities.Y) >> MatchState.FP_SHIFT, footX, footY, na);
                HitOutcome outA = aHit ? outcome(game.pawn().onHit(pawnCtx, a, pawnB)) : HitOutcome.IGNORE;
                HitOutcome outB = bHit ? outcome(game.pawn().onHit(pawnCtx, b, pawnA)) : HitOutcome.IGNORE;
                if (outA == HitOutcome.DAMAGE) {
                    state.damageSeat(a, 1);
                }
                if (outB == HitOutcome.DAMAGE) {
                    state.damageSeat(b, 1);
                }
            }
        }
    }

    /**
     * Step 8b: every HAZARD or PROJECTILE (id order) standing on seat {@code s}'s goal cell costs seat {@code s} one
     * damage; a hazard then goes to its chip's {@code onGoal} (default: removed), a projectile is removed. A seat's
     * own projectile on its own goal line is ignored; pawns and pickups ignore goal cells.
     */
    private void goalPass() {
        int id = -1;
        while (true) {
            int index = state.lowerBound(id + 1);
            if (index >= state.entityCount) {
                return;
            }
            int base = index * MatchState.ENTITY_STRIDE;
            id = state.entities[base + Entities.ID];
            int kind = state.entities[base + Entities.KIND];
            boolean hazard = kind == EntityKind.HAZARD.ordinal();
            if ((!hazard && kind != EntityKind.PROJECTILE.ordinal())
                    || (state.entities[base + Entities.FLAGS] & Entities.F_HIDDEN) != 0) {
                continue;
            }
            int goal = Cell.goalSeat(state.cell(state.entities[base + Entities.X] >> MatchState.FP_SHIFT,
                    state.entities[base + Entities.Y] >> MatchState.FP_SHIFT));
            if (goal < 0 || goal >= state.seatCount) {
                continue;
            }
            if (!hazard) {
                if (state.entities[base + Entities.OWNER] != goal) {
                    state.damageSeat(goal, 1);
                    state.remove(id);
                }
                continue;
            }
            int tag = state.aux[index] & 3;
            state.damageSeat(goal, 1);
            if (tag < game.hazards().size()) {
                game.hazards().get(tag).onGoal(ctx(game.hazardProps().get(tag), tag), id, goal);
            } else {
                state.remove(id);
            }
        }
    }

    /**
     * Step 8c: every PROJECTILE (id order) against every HAZARD (id order) in its cell: the hazard's chip gets
     * {@code onShot} (default hp −1, removed at 0), then the projectile is removed unless {@code F_PERSISTENT}
     * (a persistent one goes on to the next hazard in the cell).
     */
    private void projectilePass() {
        int pid = -1;
        while (true) {
            int index = state.lowerBound(pid + 1);
            if (index >= state.entityCount) {
                return;
            }
            int base = index * MatchState.ENTITY_STRIDE;
            pid = state.entities[base + Entities.ID];
            if (state.entities[base + Entities.KIND] != EntityKind.PROJECTILE.ordinal()
                    || (state.entities[base + Entities.FLAGS] & Entities.F_HIDDEN) != 0) {
                continue;
            }
            int cx = state.entities[base + Entities.X] >> MatchState.FP_SHIFT;
            int cy = state.entities[base + Entities.Y] >> MatchState.FP_SHIFT;
            int owner = state.entities[base + Entities.OWNER];
            int hid = -1;
            while (state.exists(pid)) {
                int hIndex = state.lowerBound(hid + 1);
                if (hIndex >= state.entityCount) {
                    break;
                }
                int hBase = hIndex * MatchState.ENTITY_STRIDE;
                hid = state.entities[hBase + Entities.ID];
                if (state.entities[hBase + Entities.KIND] != EntityKind.HAZARD.ordinal()
                        || (state.entities[hBase + Entities.FLAGS] & Entities.F_HIDDEN) != 0
                        || (state.entities[hBase + Entities.X] >> MatchState.FP_SHIFT) != cx
                        || (state.entities[hBase + Entities.Y] >> MatchState.FP_SHIFT) != cy) {
                    continue;
                }
                int tag = state.aux[hIndex] & 3;
                if (tag < game.hazards().size()) {
                    game.hazards().get(tag).onShot(ctx(game.hazardProps().get(tag), tag), owner, hid, pid);
                } else {
                    state.damageEntity(hid, 1);
                }
                removeUnlessPersistent(pid);
            }
        }
    }

    /** The cells of a seat's pawn and its SEGMENTs; returns how many were written. */
    private int footprint(int seat, int[] xs, int[] ys) {
        int n = 0;
        int pawn = state.pawnId(seat);
        for (int i = 0; i < state.entityCount && n < xs.length; i++) {
            int base = i * MatchState.ENTITY_STRIDE;
            int id = state.entities[base + Entities.ID];
            boolean mine = id == pawn || (state.entities[base + Entities.KIND] == EntityKind.SEGMENT.ordinal()
                    && state.entities[base + Entities.OWNER] == seat);
            if (mine) {
                xs[n] = state.entities[base + Entities.X] >> MatchState.FP_SHIFT;
                ys[n] = state.entities[base + Entities.Y] >> MatchState.FP_SHIFT;
                n++;
            }
        }
        return n;
    }

    private static boolean overlaps(int cx, int cy, int[] xs, int[] ys, int n) {
        for (int i = 0; i < n; i++) {
            if (xs[i] == cx && ys[i] == cy) {
                return true;
            }
        }
        return false;
    }

    /**
     * Step 11: entity TTL, status countdowns, timer, respawns (each followed by the A15.b SHIELD grace of
     * {@link Budgets#RESPAWN_GRACE_TICKS}; not at the initial spawn, nor for a MULTIPLAYER chip's own respawn).
     * A seat outside the MULTIPLAYER active mask (a waiting hot-seat seat) is never respawned here (001 A14): its
     * body would sit on the board for a tick before {@code turns.afterTick} removed it; the chip respawns it when
     * its turn comes. The mask is only asked when a respawn is due, so the per-tick hook order is unchanged.
     */
    private void housekeeping(ModuleContext pawnCtx, ModuleContext mpCtx) {
        int index = 0;
        while (index < state.entityCount) {
            int base = index * MatchState.ENTITY_STRIDE;
            int ttl = state.entities[base + Entities.TTL];
            if (ttl > 0) {
                ttl--;
                state.entities[base + Entities.TTL] = ttl;
                if (ttl == 0) {
                    state.remove(state.entities[base + Entities.ID]);
                    continue;
                }
            }
            index++;
        }
        for (int s = 0; s < state.seatCount; s++) {
            for (int flag = SeatState.SPEED; flag <= SeatState.STUN; flag <<= 1) {
                int left = state.statusTicks(s, flag);
                if (left > 0) {
                    state.setStatus(s, flag, left - 1);
                }
            }
        }
        if (state.timerTicks > 0) {
            state.timerTicks--;
        }
        int active = -1;
        for (int s = 0; s < state.seatCount; s++) {
            if (state.seatRespawnTicks[s] > 0) {
                state.seatRespawnTicks[s]--;
                if (state.seatRespawnTicks[s] == 0 && state.seatAlive(s)) {
                    if (active == -1) {
                        active = game.multiplayer().activeSeatMask(mpCtx);
                    }
                    if ((active & (1 << s)) == 0) {
                        continue;
                    }
                    game.pawn().onRespawn(pawnCtx, s);
                    // A15.b respawn grace: never shortens a longer SHIELD already running
                    state.setStatus(s, SeatState.SHIELD,
                            Math.max(state.statusTicks(s, SeatState.SHIELD), Budgets.RESPAWN_GRACE_TICKS));
                }
            }
        }
    }

    /** Marks a seat dropped and tells the rule. Never throws for an already-dropped or unknown seat. */
    public void dropSeat(int seat) {
        if (seat < 0 || seat >= state.seatCount || (state.droppedSeatMask & (1 << seat)) != 0) {
            return;
        }
        state.droppedSeatMask |= 1 << seat;
        game.rule().onSeatDropped(ctx(game.ruleProps(), 0), seat);
    }

    public MatchState state() {
        return state;
    }

    public ResolvedGame game() {
        return game;
    }

    public Optional<MatchResult> result() {
        return Optional.ofNullable(result);
    }

    /** Base ticks stepped since construction (countdown included); the input log's clock. */
    public int steps() {
        return steps;
    }

    /** The runtime-driven seat, or -1. */
    public int opponentSeat() {
        return opponentSeat;
    }

    /** Whether {@code pawn} inherits {@link PawnModule#opponentInput}'s default (one reflective lookup per match). */
    static boolean usesDefaultOpponent(PawnModule pawn) {
        try {
            return pawn.getClass().getMethod("opponentInput", ModuleContext.class, int.class)
                    .getDeclaringClass() == PawnModule.class;
        } catch (NoSuchMethodException | SecurityException e) {
            return false;
        }
    }

    private int maxTicks() {
        long seconds = Math.max(1, game.rule().maxSeconds(game.ruleProps()));
        return (int) Math.min(Integer.MAX_VALUE, seconds * Budgets.TICKS_PER_SECOND);
    }

    private ModuleContext ctx(PropertyValues props, int stackIndex) {
        return new ModuleContext(state, props, rng, game, state.tick, stackIndex);
    }

    private MatchResult buildResult() {
        MatchResult.Outcome[] outcomes = MatchResult.Outcome.values();
        int[] scores = Arrays.copyOf(state.seatScore, state.seatCount);
        return new MatchResult(outcomes[Math.min(state.result, outcomes.length - 1)], state.winnersMask, scores,
                state.tick, state.stateHash());
    }
}
