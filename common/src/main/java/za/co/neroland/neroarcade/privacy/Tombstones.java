package za.co.neroland.neroarcade.privacy;

import java.util.UUID;

/**
 * The author written into a stored game whose author erased their data (003 §6.4): a fixed, version-4-shaped
 * UUID that can never be a real account. A tombstoned author is never named, credited or paid.
 */
public final class Tombstones {

    public static final UUID ERASED_AUTHOR = UUID.fromString("00000000-0000-4000-8000-00000000dead");

    private Tombstones() {
    }

    public static boolean isTombstone(UUID uuid) {
        return ERASED_AUTHOR.equals(uuid);
    }
}
