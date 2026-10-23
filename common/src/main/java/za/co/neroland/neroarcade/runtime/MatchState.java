package za.co.neroland.neroarcade.runtime;

import java.nio.ByteBuffer;
import java.util.function.IntConsumer;

import za.co.neroland.neroarcade.module.hook.SeatLayout;

/**
 * The whole mutable state of one match, owned by the runtime.
 *
 * <p><b>Invariants.</b>
 * <ul>
 *   <li><b>Integer-only.</b> Every field is an {@code int}, {@code long}, {@code boolean}, {@code byte[]},
 *       {@code int[]} or an enum. Positions and velocities are fixed point: {@link #FP} units per cell; the
 *       cell of a position is {@code x >> FP_SHIFT}.</li>
 *   <li><b>Sorted entities.</b> Live entity records sit in one {@code int[]} with stride
 *       {@link #ENTITY_STRIDE}, sorted by id, compacted on removal; {@link #forEach} visits ascending ids.
 *       The array is allocated once at the match's entity budget and never reallocated.</li>
 *   <li><b>No identities.</b> Nothing here names a player, a level, a module or a clock; seat indexes only.
 *       A replay therefore carries no personal data.</li>
 *   <li><b>One way in.</b> Hooks mutate only through the public API below. The canonical serialisation
 *       ({@link #writeKeyframe}) is both the keyframe wire format and the input of {@link #stateHash()}.</li>
 *   <li><b>Wire ranges.</b> The keyframe stores x/y/vx/vy/ttl as {@code s16}, owner as {@code s8}, hp and flags
 *       as {@code u8}. Chips keep entity values inside those ranges (32 cells = 8192 units fits easily); a
 *       value outside them would make a keyframe-restored state differ from the authoritative one.</li>
 * </ul>
 */
public final class MatchState {

    public static final int FP = 256;
    public static final int FP_SHIFT = 8;
    /** Ints per entity record. */
    public static final int ENTITY_STRIDE = 10;
    /** Gravity in units per tick squared when no modifier changes it (an eighth of a cell). */
    public static final int DEFAULT_GRAVITY = 32;

    static final int KIND_KEYFRAME = 0;
    static final int KIND_DELTA = 1;
    private static final int MAX_SEATS = Budgets.MAX_SEATS;
    private static final int ID_LIMIT = 0xFFFF;

    // --- arena ---
    int width;
    int height;
    byte[] cells;
    boolean wrapX;
    boolean wrapY;
    int gravity;
    int fogRadius;

    // --- entities ---
    int[] entities;
    int[] aux;
    int entityCount;
    int nextEntityId;

    // --- seats ---
    int seatCount;
    int[] seatScore = new int[MAX_SEATS];
    int[] seatLives = new int[MAX_SEATS];
    int[] seatStatusFlags = new int[MAX_SEATS];
    int[] seatStatusTicks = new int[MAX_SEATS];
    int[] seatPawnId = new int[MAX_SEATS];
    int[] seatRespawnTicks = new int[MAX_SEATS];
    int aliveSeatMask;
    int droppedSeatMask;
    SeatLayout layout;
    int sharedLives;

    // --- match ---
    MatchPhase phase;
    int tick;
    int countdownTicks;
    int timerTicks;
    int paceDivisor;
    int winnersMask;
    int result;
    long rngState;

    MatchState(int width, int height, int entityCapacity, int seats) {
        if (width < 1 || height < 1 || width > Budgets.HARD_MAX_ARENA_SIDE || height > Budgets.HARD_MAX_ARENA_SIDE) {
            throw new IllegalArgumentException("arena " + width + "x" + height + " out of range");
        }
        if (seats < 1 || seats > MAX_SEATS) {
            throw new IllegalArgumentException("seats out of range: " + seats);
        }
        if (entityCapacity < 0 || entityCapacity > Budgets.HARD_MAX_ENTITIES) {
            throw new IllegalArgumentException("entity capacity out of range: " + entityCapacity);
        }
        this.width = width;
        this.height = height;
        this.cells = new byte[width * height];
        this.gravity = DEFAULT_GRAVITY;
        this.entities = new int[entityCapacity * ENTITY_STRIDE];
        this.aux = new int[entityCapacity];
        this.seatCount = seats;
        java.util.Arrays.fill(seatPawnId, -1);
        for (int s = 0; s < seats; s++) {
            seatLives[s] = 1;
        }
        this.aliveSeatMask = (1 << seats) - 1;
        this.sharedLives = -1;
        this.phase = MatchPhase.LOBBY;
        this.paceDivisor = 1;
    }

    // =====================================================================================================
    // Entities
    // =====================================================================================================

    /**
     * Creates an entity at fixed-point {@code (x, y)} with hp 1, no velocity, no flags and no TTL. {@code tag}
     * is stored in the low two bits of its aux word (a stacked chip passes its stack index).
     *
     * @return the new id, or -1 when the entity budget is full (a chip must then do nothing)
     */
    public int spawn(EntityKind kind, int x, int y, int ownerSeat, int tag) {
        if (entityCount >= aux.length) {
            return -1;
        }
        int id = nextEntityId;
        while (find(id) >= 0) {
            id = (id + 1) % ID_LIMIT;
        }
        nextEntityId = (id + 1) % ID_LIMIT;
        int index = lowerBound(id);
        insertAt(index);
        int base = index * ENTITY_STRIDE;
        entities[base + Entities.ID] = id;
        entities[base + Entities.KIND] = kind.ordinal();
        entities[base + Entities.X] = x;
        entities[base + Entities.Y] = y;
        entities[base + Entities.VX] = 0;
        entities[base + Entities.VY] = 0;
        entities[base + Entities.OWNER] = ownerSeat;
        entities[base + Entities.HP] = 1;
        entities[base + Entities.FLAGS] = 0;
        entities[base + Entities.TTL] = -1;
        aux[index] = tag & 3;
        return id;
    }

    /** Removes an entity; a missing id is ignored. O(n). */
    public void remove(int id) {
        int index = find(id);
        if (index < 0) {
            return;
        }
        int tail = entityCount - index - 1;
        System.arraycopy(entities, (index + 1) * ENTITY_STRIDE, entities, index * ENTITY_STRIDE, tail * ENTITY_STRIDE);
        System.arraycopy(aux, index + 1, aux, index, tail);
        entityCount--;
        java.util.Arrays.fill(entities, entityCount * ENTITY_STRIDE, (entityCount + 1) * ENTITY_STRIDE, 0);
        aux[entityCount] = 0;
    }

    /** Binary search: the record index of {@code id}, or -1. */
    public int find(int id) {
        int lo = 0;
        int hi = entityCount - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int value = entities[mid * ENTITY_STRIDE];
            if (value < id) {
                lo = mid + 1;
            } else if (value > id) {
                hi = mid - 1;
            } else {
                return mid;
            }
        }
        return -1;
    }

    public boolean exists(int id) {
        return find(id) >= 0;
    }

    /** One field of a live entity ({@link Entities} offsets). @throws IllegalArgumentException if absent */
    public int get(int id, int field) {
        checkField(field);
        return entities[indexOf(id) * ENTITY_STRIDE + field];
    }

    /** Sets one field. The id field is immutable. @throws IllegalArgumentException if absent */
    public void set(int id, int field, int value) {
        checkField(field);
        if (field == Entities.ID) {
            throw new IllegalArgumentException("an entity's id cannot change");
        }
        entities[indexOf(id) * ENTITY_STRIDE + field] = value;
    }

    /** Chip-private data; bits 0-1 hold the spawning chip's stack index. */
    public int aux(int id) {
        return aux[indexOf(id)];
    }

    public void setAux(int id, int value) {
        aux[indexOf(id)] = value;
    }

    /** The owning stack index of an entity (low two bits of aux). */
    public int tag(int id) {
        return aux(id) & 3;
    }

    /**
     * Visits every live entity of {@code kind} (or every entity when {@code kind} is null) in ascending id
     * order. The visitor may remove the current id and may spawn; it must not remove other ids.
     *
     * @return the number of entities visited
     */
    public int forEach(EntityKind kind, IntConsumer visitor) {
        int visited = 0;
        int index = 0;
        while (index < entityCount) {
            int base = index * ENTITY_STRIDE;
            int id = entities[base + Entities.ID];
            if (kind == null || entities[base + Entities.KIND] == kind.ordinal()) {
                visitor.accept(id);
                visited++;
                int now = find(id);
                index = now >= 0 ? now + 1 : lowerBound(id);
            } else {
                index++;
            }
        }
        return visited;
    }

    /** Number of live entities of a kind (null = all). */
    public int count(EntityKind kind) {
        if (kind == null) {
            return entityCount;
        }
        int n = 0;
        for (int i = 0; i < entityCount; i++) {
            n += entities[i * ENTITY_STRIDE + Entities.KIND] == kind.ordinal() ? 1 : 0;
        }
        return n;
    }

    public int entityCount() {
        return entityCount;
    }

    /** The entity budget of this match (the array's fixed capacity). */
    public int entityCapacity() {
        return aux.length;
    }

    /** Id of the record at {@code index} (0..entityCount-1), ascending. */
    public int entityIdAt(int index) {
        if (index < 0 || index >= entityCount) {
            throw new IndexOutOfBoundsException(index);
        }
        return entities[index * ENTITY_STRIDE];
    }

    /** True when the entity's cell equals {@code (cx, cy)}. */
    public boolean occupies(int id, int cx, int cy) {
        int base = indexOf(id) * ENTITY_STRIDE;
        return (entities[base + Entities.X] >> FP_SHIFT) == cx && (entities[base + Entities.Y] >> FP_SHIFT) == cy;
    }

    /**
     * True when any live entity carrying {@link Entities#F_SOLID} sits in cell {@code (cx, cy)} (wrapped where the
     * board wraps). Scans in id order. Pawn chips treat such a cell as blocking in their own movement.
     */
    public boolean solidAt(int cx, int cy) {
        if (wrapX) {
            cx = Math.floorMod(cx, width);
        }
        if (wrapY) {
            cy = Math.floorMod(cy, height);
        }
        for (int i = 0; i < entityCount; i++) {
            int base = i * ENTITY_STRIDE;
            if ((entities[base + Entities.FLAGS] & Entities.F_SOLID) != 0
                    && (entities[base + Entities.X] >> FP_SHIFT) == cx && (entities[base + Entities.Y] >> FP_SHIFT) == cy) {
                return true;
            }
        }
        return false;
    }

    /**
     * Takes {@code amount} hp from an entity and removes it at 0 or below ({@code HazardModule.onShot}'s default).
     * A missing id or a non-positive amount is ignored.
     *
     * @return true when the entity was removed
     */
    public boolean damageEntity(int id, int amount) {
        int index = find(id);
        if (index < 0 || amount <= 0) {
            return false;
        }
        int base = index * ENTITY_STRIDE;
        int hp = entities[base + Entities.HP] - amount;
        if (hp <= 0) {
            remove(id);
            return true;
        }
        entities[base + Entities.HP] = hp;
        return false;
    }

    // =====================================================================================================
    // Cells and topology
    // =====================================================================================================

    /** The cell value; outside the arena is WALL unless that axis wraps. */
    public byte cell(int cx, int cy) {
        if (wrapX) {
            cx = Math.floorMod(cx, width);
        }
        if (wrapY) {
            cy = Math.floorMod(cy, height);
        }
        if (cx < 0 || cy < 0 || cx >= width || cy >= height) {
            return Cell.WALL;
        }
        return cells[cy * width + cx];
    }

    /** Sets a cell; outside the arena is ignored unless that axis wraps. */
    public void setCell(int cx, int cy, byte value) {
        if (wrapX) {
            cx = Math.floorMod(cx, width);
        }
        if (wrapY) {
            cy = Math.floorMod(cy, height);
        }
        if (cx >= 0 && cy >= 0 && cx < width && cy < height) {
            cells[cy * width + cx] = value;
        }
    }

    /** Applies horizontal topology to a fixed-point x. */
    public int wrapX(int xUnits) {
        return wrapX ? Math.floorMod(xUnits, width << FP_SHIFT) : xUnits;
    }

    /** Applies vertical topology to a fixed-point y. */
    public int wrapY(int yUnits) {
        return wrapY ? Math.floorMod(yUnits, height << FP_SHIFT) : yUnits;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public boolean wrapsX() {
        return wrapX;
    }

    public boolean wrapsY() {
        return wrapY;
    }

    public int gravity() {
        return gravity;
    }

    public int fogRadius() {
        return fogRadius;
    }

    /** Raw cell by row-major index (codec use). */
    public byte cellAt(int index) {
        return cells[index];
    }

    // =====================================================================================================
    // Seats
    // =====================================================================================================

    /** Adds to a seat's score (saturating). */
    public void addScore(int seat, int delta) {
        if (validSeat(seat)) {
            seatScore[seat] = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, (long) seatScore[seat] + delta));
        }
    }

    /**
     * Damages a seat. Ignored for a seat that is out, between lives (respawning) or holding SHIELD. Otherwise
     * the seat's body is removed and a life is spent (from the shared pool when lives are shared); with lives
     * left the seat respawns after {@link Budgets#RESPAWN_TICKS}, at zero it is out of the match.
     */
    public void damageSeat(int seat, int amount) {
        if (!validSeat(seat) || amount <= 0 || (aliveSeatMask & (1 << seat)) == 0 || seatRespawnTicks[seat] > 0
                || (seatStatusFlags[seat] & SeatState.SHIELD) != 0) {
            return;
        }
        removeSeatBody(seat);
        if (sharedLives >= 0) {
            sharedLives = Math.max(0, sharedLives - amount);
            for (int s = 0; s < seatCount; s++) {
                seatLives[s] = sharedLives;
            }
            if (sharedLives == 0) {
                for (int s = 0; s < seatCount; s++) {
                    removeSeatBody(s);
                    seatRespawnTicks[s] = 0;
                }
                aliveSeatMask = 0;
                return;
            }
        } else {
            seatLives[seat] = Math.max(0, seatLives[seat] - amount);
            if (seatLives[seat] == 0) {
                aliveSeatMask &= ~(1 << seat);
                return;
            }
        }
        seatRespawnTicks[seat] = Budgets.RESPAWN_TICKS;
    }

    /**
     * Removes the seat's pawn, every SEGMENT it owns and every PROJECTILE it owns (001 A15.d: a body's shots go
     * with it; a returned shot belongs to the returner); clears its pawn id.
     */
    public void removeSeatBody(int seat) {
        if (!validSeat(seat)) {
            return;
        }
        if (seatPawnId[seat] >= 0) {
            remove(seatPawnId[seat]);
            seatPawnId[seat] = -1;
        }
        int index = 0;
        while (index < entityCount) {
            int base = index * ENTITY_STRIDE;
            int kind = entities[base + Entities.KIND];
            if ((kind == EntityKind.SEGMENT.ordinal() || kind == EntityKind.PROJECTILE.ordinal())
                    && entities[base + Entities.OWNER] == seat) {
                remove(entities[base + Entities.ID]);
            } else {
                index++;
            }
        }
    }

    /** Sets one status ({@link SeatState#SPEED}, SHIELD or STUN) for {@code ticks} (0 clears; max 1023). */
    public void setStatus(int seat, int flag, int ticks) {
        if (!validSeat(seat)) {
            return;
        }
        int clamped = Math.max(0, Math.min(SeatState.MAX_TICKS, ticks));
        seatStatusTicks[seat] = SeatState.withTicks(seatStatusTicks[seat], flag, clamped);
        if (clamped > 0) {
            seatStatusFlags[seat] |= flag;
        } else {
            seatStatusFlags[seat] &= ~flag;
        }
    }

    public void setLives(int seat, int lives) {
        if (validSeat(seat)) {
            seatLives[seat] = Math.max(0, Math.min(255, lives));
        }
    }

    /** Records the seat's pawn entity (-1 = none). Pawn chips call this from {@code spawn}. */
    public void setPawnId(int seat, int id) {
        if (validSeat(seat)) {
            seatPawnId[seat] = id;
        }
    }

    public int score(int seat) {
        return validSeat(seat) ? seatScore[seat] : 0;
    }

    public int lives(int seat) {
        return validSeat(seat) ? seatLives[seat] : 0;
    }

    public int statusFlags(int seat) {
        return validSeat(seat) ? seatStatusFlags[seat] : 0;
    }

    public int statusTicks(int seat, int flag) {
        return validSeat(seat) ? SeatState.ticks(seatStatusTicks[seat], flag) : 0;
    }

    /** The seat's pawn entity id, or -1. */
    public int pawnId(int seat) {
        return validSeat(seat) ? seatPawnId[seat] : -1;
    }

    public int respawnTicks(int seat) {
        return validSeat(seat) ? seatRespawnTicks[seat] : 0;
    }

    public boolean seatAlive(int seat) {
        return validSeat(seat) && (aliveSeatMask & (1 << seat)) != 0;
    }

    public int aliveSeatMask() {
        return aliveSeatMask;
    }

    public int droppedSeatMask() {
        return droppedSeatMask;
    }

    public int seatCount() {
        return seatCount;
    }

    /** Shared life pool, or -1 when lives are per seat. */
    public int sharedLives() {
        return sharedLives;
    }

    /** Null until the match starts (and after a keyframe restore: the layout derives from the cartridge). */
    public SeatLayout layout() {
        return layout;
    }

    // =====================================================================================================
    // Match
    // =====================================================================================================

    public void setTimerTicks(int ticks) {
        timerTicks = Math.max(0, ticks);
    }

    public void setWinners(int mask) {
        winnersMask = mask & 0xF;
    }

    /** {@code gravity = DEFAULT_GRAVITY * percent / 100}, percent clamped to 0..100. */
    public void setGravityPercent(int percent) {
        gravity = DEFAULT_GRAVITY * Math.max(0, Math.min(100, percent)) / 100;
    }

    public void setWrap(boolean x, boolean y) {
        wrapX = x;
        wrapY = y;
    }

    /** A render hint only (0 = none); the simulation never reads it. */
    public void setFogRadius(int radius) {
        fogRadius = Math.max(0, Math.min(255, radius));
    }

    public int tick() {
        return tick;
    }

    public MatchPhase phase() {
        return phase;
    }

    public int countdownTicks() {
        return countdownTicks;
    }

    public int timerTicks() {
        return timerTicks;
    }

    public int paceDivisor() {
        return paceDivisor;
    }

    public int winnersMask() {
        return winnersMask;
    }

    /** {@code MatchResult.Outcome} ordinal; meaningful only when ENDED. */
    public int result() {
        return result;
    }

    public long rngState() {
        return rngState;
    }

    /** Deep copy (client prediction shadow, tests, the snapshot stream's last-sent state). */
    public MatchState copy() {
        MatchState c = new MatchState(width, height, aux.length, seatCount);
        System.arraycopy(cells, 0, c.cells, 0, cells.length);
        c.wrapX = wrapX;
        c.wrapY = wrapY;
        c.gravity = gravity;
        c.fogRadius = fogRadius;
        System.arraycopy(entities, 0, c.entities, 0, entities.length);
        System.arraycopy(aux, 0, c.aux, 0, aux.length);
        c.entityCount = entityCount;
        c.nextEntityId = nextEntityId;
        c.seatScore = seatScore.clone();
        c.seatLives = seatLives.clone();
        c.seatStatusFlags = seatStatusFlags.clone();
        c.seatStatusTicks = seatStatusTicks.clone();
        c.seatPawnId = seatPawnId.clone();
        c.seatRespawnTicks = seatRespawnTicks.clone();
        c.aliveSeatMask = aliveSeatMask;
        c.droppedSeatMask = droppedSeatMask;
        c.layout = layout;
        c.sharedLives = sharedLives;
        c.phase = phase;
        c.tick = tick;
        c.countdownTicks = countdownTicks;
        c.timerTicks = timerTicks;
        c.paceDivisor = paceDivisor;
        c.winnersMask = winnersMask;
        c.result = result;
        c.rngState = rngState;
        return c;
    }

    /** FNV-1a 64 over the canonical keyframe bytes. */
    public long stateHash() {
        return StateHash.of(this);
    }

    // =====================================================================================================
    // Canonical serialisation (keyframe = hash input) and deltas
    // =====================================================================================================

    /** Writes the keyframe: the canonical, big-endian serialisation (also the {@link #stateHash()} input). */
    public void writeKeyframe(IntConsumer out) {
        writeKeyframe(out, false, false);
    }

    /**
     * @param view       write zero for the two fields deltas do not carry (rngState, status countdowns)
     * @param ignoreTick write zero for the tick
     */
    void writeKeyframe(IntConsumer out, boolean view, boolean ignoreTick) {
        u8(out, KIND_KEYFRAME);
        u32(out, ignoreTick ? 0 : tick);
        u8(out, phase.ordinal());
        u8(out, seatCount);
        u8(out, paceDivisor);
        u8(out, (wrapX ? 1 : 0) | (wrapY ? 2 : 0));
        u16(out, width);
        u16(out, height);
        u16(out, gravity);
        u8(out, fogRadius);
        u32(out, timerTicks);
        u32(out, countdownTicks);
        u8(out, winnersMask);
        u8(out, result);
        u8(out, aliveSeatMask);
        u8(out, droppedSeatMask);
        u32(out, sharedLives);
        for (int s = 0; s < MAX_SEATS; s++) {
            u32(out, seatScore[s]);
            u8(out, seatLives[s]);
            u8(out, seatStatusFlags[s]);
            u32(out, view ? 0 : seatStatusTicks[s]);
            u16(out, seatPawnId[s] < 0 ? Entities.NONE : seatPawnId[s]);
            u8(out, seatRespawnTicks[s]);
        }
        u64(out, view ? 0L : rngState);
        int runs = rleRuns();
        if (2 * runs < cells.length) {
            u8(out, 1);
            int i = 0;
            while (i < cells.length) {
                byte value = cells[i];
                int length = 1;
                while (i + length < cells.length && cells[i + length] == value && length < 255) {
                    length++;
                }
                u8(out, value);
                u8(out, length);
                i += length;
            }
        } else {
            u8(out, 0);
            for (byte value : cells) {
                u8(out, value);
            }
        }
        u16(out, entityCount);
        for (int i = 0; i < entityCount; i++) {
            writeRecord(out, i);
        }
    }

    private int rleRuns() {
        int runs = 0;
        int i = 0;
        while (i < cells.length) {
            int length = 1;
            while (i + length < cells.length && cells[i + length] == cells[i] && length < 255) {
                length++;
            }
            runs++;
            i += length;
        }
        return runs;
    }

    private void writeRecord(IntConsumer out, int index) {
        int base = index * ENTITY_STRIDE;
        u16(out, entities[base + Entities.ID]);
        u8(out, entities[base + Entities.KIND]);
        u16(out, entities[base + Entities.X]);
        u16(out, entities[base + Entities.Y]);
        u16(out, entities[base + Entities.VX]);
        u16(out, entities[base + Entities.VY]);
        u8(out, entities[base + Entities.OWNER]);
        u8(out, entities[base + Entities.HP]);
        u8(out, entities[base + Entities.FLAGS]);
        u16(out, entities[base + Entities.TTL]);
        u32(out, aux[index]);
    }

    /**
     * Reads a keyframe written by {@link #writeKeyframe}. The arena layout is not part of the keyframe (it
     * derives from the cartridge) and is null in the result; nextEntityId restarts after the highest live id.
     *
     * @throws IllegalArgumentException on malformed input
     */
    public static MatchState readKeyframe(ByteBuffer in, int entityCapacity) {
        try {
            if ((in.get() & 0xFF) != KIND_KEYFRAME) {
                throw new IllegalArgumentException("not a keyframe");
            }
            int tick = in.getInt();
            int phase = in.get() & 0xFF;
            int seats = in.get() & 0xFF;
            int pace = in.get() & 0xFF;
            int flags = in.get() & 0xFF;
            int width = in.getShort() & 0xFFFF;
            int height = in.getShort() & 0xFFFF;
            MatchState s = new MatchState(width, height, entityCapacity, seats);
            s.tick = tick;
            s.phase = MatchPhase.values()[Math.min(phase, MatchPhase.values().length - 1)];
            s.paceDivisor = pace;
            s.wrapX = (flags & 1) != 0;
            s.wrapY = (flags & 2) != 0;
            s.gravity = in.getShort() & 0xFFFF;
            s.fogRadius = in.get() & 0xFF;
            s.timerTicks = in.getInt();
            s.countdownTicks = in.getInt();
            s.winnersMask = in.get() & 0xFF;
            s.result = in.get() & 0xFF;
            s.aliveSeatMask = in.get() & 0xFF;
            s.droppedSeatMask = in.get() & 0xFF;
            s.sharedLives = in.getInt();
            for (int seat = 0; seat < MAX_SEATS; seat++) {
                s.seatScore[seat] = in.getInt();
                s.seatLives[seat] = in.get() & 0xFF;
                s.seatStatusFlags[seat] = in.get() & 0xFF;
                s.seatStatusTicks[seat] = in.getInt();
                int pawn = in.getShort() & 0xFFFF;
                s.seatPawnId[seat] = pawn == Entities.NONE ? -1 : pawn;
                s.seatRespawnTicks[seat] = in.get() & 0xFF;
            }
            s.rngState = in.getLong();
            int encoding = in.get() & 0xFF;
            if (encoding == 1) {
                int i = 0;
                while (i < s.cells.length) {
                    byte value = in.get();
                    int length = in.get() & 0xFF;
                    if (length == 0 || i + length > s.cells.length) {
                        throw new IllegalArgumentException("bad cell run");
                    }
                    java.util.Arrays.fill(s.cells, i, i + length, value);
                    i += length;
                }
            } else {
                in.get(s.cells);
            }
            int count = in.getShort() & 0xFFFF;
            if (count > entityCapacity) {
                throw new IllegalArgumentException("too many entities: " + count);
            }
            int highest = -1;
            for (int i = 0; i < count; i++) {
                int id = s.readRecordInto(in);
                highest = Math.max(highest, id);
            }
            s.nextEntityId = (highest + 1) % ID_LIMIT;
            return s;
        } catch (java.nio.BufferUnderflowException | IndexOutOfBoundsException truncated) {
            throw new IllegalArgumentException("truncated keyframe", truncated);
        }
    }

    /** Reads one 20-byte record and upserts it (sorted). Returns its id. */
    private int readRecordInto(ByteBuffer in) {
        int id = in.getShort() & 0xFFFF;
        int kind = in.get() & 0xFF;
        int x = in.getShort();
        int y = in.getShort();
        int vx = in.getShort();
        int vy = in.getShort();
        int owner = in.get();
        int hp = in.get() & 0xFF;
        int flags = in.get() & 0xFF;
        int ttl = in.getShort();
        int auxValue = in.getInt();
        int index = find(id);
        if (index < 0) {
            if (entityCount >= aux.length) {
                throw new IllegalArgumentException("entity budget exceeded");
            }
            index = lowerBound(id);
            insertAt(index);
        }
        int base = index * ENTITY_STRIDE;
        entities[base + Entities.ID] = id;
        entities[base + Entities.KIND] = kind;
        entities[base + Entities.X] = x;
        entities[base + Entities.Y] = y;
        entities[base + Entities.VX] = vx;
        entities[base + Entities.VY] = vy;
        entities[base + Entities.OWNER] = owner;
        entities[base + Entities.HP] = hp;
        entities[base + Entities.FLAGS] = flags;
        entities[base + Entities.TTL] = ttl;
        aux[index] = auxValue;
        return id;
    }

    /**
     * True when {@code this} can be sent as a delta on top of {@code base}: every field the delta format does
     * not carry is unchanged, except the RNG state and status countdowns, which clients never read (they are
     * not predicted).
     */
    public boolean deltaCompatibleWith(MatchState base) {
        if (base.width != width || base.height != height || base.seatCount != seatCount || base.wrapX != wrapX
                || base.wrapY != wrapY || base.gravity != gravity || base.fogRadius != fogRadius
                || base.countdownTicks != countdownTicks || base.winnersMask != winnersMask || base.result != result
                || base.droppedSeatMask != droppedSeatMask || base.sharedLives != sharedLives) {
            return false;
        }
        for (int s = 0; s < MAX_SEATS; s++) {
            if (base.seatPawnId[s] != seatPawnId[s]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Writes the delta from {@code base} to {@code this} (002 section 8.2). The caller has checked
     * {@link #deltaCompatibleWith}.
     */
    public void writeDelta(MatchState base, IntConsumer out) {
        u8(out, KIND_DELTA);
        u32(out, base.tick);
        u32(out, tick);
        u8(out, paceDivisor);
        u8(out, phase.ordinal());
        u32(out, timerTicks);
        u8(out, aliveSeatMask);
        int changedSeats = 0;
        for (int s = 0; s < MAX_SEATS; s++) {
            if (seatScore[s] != base.seatScore[s] || seatLives[s] != base.seatLives[s]
                    || seatStatusFlags[s] != base.seatStatusFlags[s] || seatRespawnTicks[s] != base.seatRespawnTicks[s]) {
                changedSeats |= 1 << s;
            }
        }
        u8(out, changedSeats);
        for (int s = 0; s < MAX_SEATS; s++) {
            if ((changedSeats & (1 << s)) != 0) {
                u32(out, seatScore[s]);
                u8(out, seatLives[s]);
                u8(out, seatStatusFlags[s]);
                u8(out, seatRespawnTicks[s]);
            }
        }
        // changed cell runs: consecutive changed cells with the same new value, at most 255 long
        for (int pass = 0; pass < 2; pass++) {
            int runs = 0;
            int i = 0;
            while (i < cells.length) {
                if (cells[i] == base.cells[i]) {
                    i++;
                    continue;
                }
                byte value = cells[i];
                int length = 1;
                while (i + length < cells.length && cells[i + length] != base.cells[i + length]
                        && cells[i + length] == value && length < 255) {
                    length++;
                }
                if (pass == 1) {
                    u16(out, i);
                    u8(out, length);
                    u8(out, value);
                }
                runs++;
                i += length;
            }
            if (pass == 0) {
                u16(out, runs);
            }
        }
        // removed ids (in base, not here) — both arrays are sorted by id
        for (int pass = 0; pass < 2; pass++) {
            int removed = 0;
            for (int i = 0; i < base.entityCount; i++) {
                int id = base.entities[i * ENTITY_STRIDE];
                if (find(id) < 0) {
                    if (pass == 1) {
                        u16(out, id);
                    }
                    removed++;
                }
            }
            if (pass == 0) {
                u16(out, removed);
            }
        }
        // new or changed records
        for (int pass = 0; pass < 2; pass++) {
            int changed = 0;
            for (int i = 0; i < entityCount; i++) {
                if (recordDiffers(i, base)) {
                    if (pass == 1) {
                        writeRecord(out, i);
                    }
                    changed++;
                }
            }
            if (pass == 0) {
                u16(out, changed);
            }
        }
    }

    private boolean recordDiffers(int index, MatchState base) {
        int base0 = index * ENTITY_STRIDE;
        int other = base.find(entities[base0]);
        if (other < 0) {
            return true;
        }
        int base1 = other * ENTITY_STRIDE;
        for (int f = 0; f < ENTITY_STRIDE; f++) {
            if (entities[base0 + f] != base.entities[base1 + f]) {
                return true;
            }
        }
        return aux[index] != base.aux[other];
    }

    /**
     * Applies a delta built on this state and returns the result as a new state ({@code this} is unchanged).
     *
     * @throws IllegalStateException    if the delta's base tick is not this state's tick
     * @throws IllegalArgumentException on malformed input
     */
    public MatchState applyDelta(ByteBuffer in) {
        try {
            if ((in.get() & 0xFF) != KIND_DELTA) {
                throw new IllegalArgumentException("not a delta");
            }
            int baseTick = in.getInt();
            if (baseTick != tick) {
                throw new IllegalStateException("delta builds on tick " + baseTick + ", have " + tick);
            }
            MatchState s = copy();
            s.tick = in.getInt();
            s.paceDivisor = in.get() & 0xFF;
            int phase = in.get() & 0xFF;
            s.phase = MatchPhase.values()[Math.min(phase, MatchPhase.values().length - 1)];
            s.timerTicks = in.getInt();
            s.aliveSeatMask = in.get() & 0xFF;
            int changedSeats = in.get() & 0xFF;
            for (int seat = 0; seat < MAX_SEATS; seat++) {
                if ((changedSeats & (1 << seat)) != 0) {
                    s.seatScore[seat] = in.getInt();
                    s.seatLives[seat] = in.get() & 0xFF;
                    s.seatStatusFlags[seat] = in.get() & 0xFF;
                    s.seatRespawnTicks[seat] = in.get() & 0xFF;
                }
            }
            int runs = in.getShort() & 0xFFFF;
            for (int r = 0; r < runs; r++) {
                int start = in.getShort() & 0xFFFF;
                int length = in.get() & 0xFF;
                byte value = in.get();
                if (start + length > s.cells.length) {
                    throw new IllegalArgumentException("cell run out of range");
                }
                java.util.Arrays.fill(s.cells, start, start + length, value);
            }
            int removed = in.getShort() & 0xFFFF;
            for (int r = 0; r < removed; r++) {
                s.remove(in.getShort() & 0xFFFF);
            }
            int changed = in.getShort() & 0xFFFF;
            int highest = s.nextEntityId - 1;
            for (int c = 0; c < changed; c++) {
                highest = Math.max(highest, s.readRecordInto(in));
            }
            s.nextEntityId = (highest + 1) % ID_LIMIT;
            return s;
        } catch (java.nio.BufferUnderflowException | IndexOutOfBoundsException truncated) {
            throw new IllegalArgumentException("truncated delta", truncated);
        }
    }

    // =====================================================================================================
    // Internals
    // =====================================================================================================

    private boolean validSeat(int seat) {
        return seat >= 0 && seat < seatCount;
    }

    private int indexOf(int id) {
        int index = find(id);
        if (index < 0) {
            throw new IllegalArgumentException("no live entity " + id);
        }
        return index;
    }

    private static void checkField(int field) {
        if (field < 0 || field >= ENTITY_STRIDE) {
            throw new IllegalArgumentException("bad entity field " + field);
        }
    }

    /** Index of the first record whose id is at least {@code id}. */
    int lowerBound(int id) {
        int lo = 0;
        int hi = entityCount;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (entities[mid * ENTITY_STRIDE] < id) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private void insertAt(int index) {
        int tail = entityCount - index;
        System.arraycopy(entities, index * ENTITY_STRIDE, entities, (index + 1) * ENTITY_STRIDE, tail * ENTITY_STRIDE);
        System.arraycopy(aux, index, aux, index + 1, tail);
        entityCount++;
    }

    static void u8(IntConsumer out, int v) {
        out.accept(v & 0xFF);
    }

    static void u16(IntConsumer out, int v) {
        out.accept((v >>> 8) & 0xFF);
        out.accept(v & 0xFF);
    }

    static void u32(IntConsumer out, int v) {
        out.accept((v >>> 24) & 0xFF);
        out.accept((v >>> 16) & 0xFF);
        out.accept((v >>> 8) & 0xFF);
        out.accept(v & 0xFF);
    }

    static void u64(IntConsumer out, long v) {
        u32(out, (int) (v >>> 32));
        u32(out, (int) v);
    }
}
