package za.co.neroland.neroarcade.client.render;

import java.util.Arrays;
import java.util.function.IntConsumer;

/**
 * Slot bookkeeping for the cabinet screen textures: at most {@code cap} screens (default
 * {@value #DEFAULT_CAP}) hold a dynamic texture at once. A screen asks for its slot every frame it is drawn; a
 * new screen takes a free slot, else the least recently used slot that was not used this tick, else gets none
 * (and draws nothing — the static model's dark ink face shows). Slots idle for {@code idleTicks} are released
 * (asleep, out of range, culled or unloaded cabinets). Pure; keys are {@code BlockPos.asLong()}.
 */
public final class TextureLru {

    /** Screens with a live texture at once: the 004 BER budget (20 cabinets + 2 marquees) plus headroom. */
    public static final int DEFAULT_CAP = 24;
    /** Ticks without a draw after which a screen's slot is released (2 s). */
    public static final int IDLE_TICKS = 40;

    private static final long NONE = Long.MIN_VALUE;

    private final long[] keys;
    private final long[] lastUsed;
    private int evictions;

    public TextureLru(int cap) {
        int size = Math.max(1, cap);
        keys = new long[size];
        lastUsed = new long[size];
        Arrays.fill(keys, NONE);
    }

    public int capacity() {
        return keys.length;
    }

    /** Slots in use. */
    public int size() {
        int n = 0;
        for (long key : keys) {
            n += key == NONE ? 0 : 1;
        }
        return n;
    }

    /** Times a slot was taken from another screen. */
    public int evictions() {
        return evictions;
    }

    /** The slot holding {@code key}, or -1. */
    public int slotOf(long key) {
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] == key) {
                return i;
            }
        }
        return -1;
    }

    /** The key in a slot, or {@link Long#MIN_VALUE} when free. */
    public long keyAt(int slot) {
        return keys[slot];
    }

    /**
     * The slot for {@code key} at tick {@code now}, taking a free or least-recently-used one if needed.
     *
     * @return the slot, or -1 when every slot was already used this tick by another screen
     */
    public int acquire(long key, long now) {
        int slot = slotOf(key);
        if (slot >= 0) {
            lastUsed[slot] = now;
            return slot;
        }
        int victim = -1;
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] == NONE) {
                victim = i;
                break;
            }
            if (lastUsed[i] < now && (victim < 0 || lastUsed[i] < lastUsed[victim])) {
                victim = i;
            }
        }
        if (victim < 0) {
            return -1;
        }
        if (keys[victim] != NONE) {
            evictions++;
        }
        keys[victim] = key;
        lastUsed[victim] = now;
        return victim;
    }

    /** Frees {@code key}'s slot if it has one; returns the slot or -1. */
    public int release(long key) {
        int slot = slotOf(key);
        if (slot >= 0) {
            keys[slot] = NONE;
        }
        return slot;
    }

    /** Frees every slot not used within {@code idleTicks}; {@code freed} receives each freed slot. */
    public int releaseIdle(long now, int idleTicks, IntConsumer freed) {
        int n = 0;
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] != NONE && now - lastUsed[i] > idleTicks) {
                keys[i] = NONE;
                n++;
                if (freed != null) {
                    freed.accept(i);
                }
            }
        }
        return n;
    }

    public void clear() {
        Arrays.fill(keys, NONE);
    }
}
