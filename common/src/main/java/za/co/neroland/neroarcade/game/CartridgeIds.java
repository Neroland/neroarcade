package za.co.neroland.neroarcade.game;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import net.minecraft.util.RandomSource;

/**
 * Cartridge ids: 16 lowercase RFC 4648 base32 characters encoding 80 random bits — collision-free for any
 * plausible server, short enough to read aloud, safe in a deep link and free of 0/O and 1/l confusion.
 *
 * <p><b>Invariants:</b> an id is minted once, server side, at the first save of a new game; saving again
 * keeps it, remixing mints a new one and appends the parent to the lineage. {@link #mint} is the only place
 * NeroArcade uses a {@link RandomSource}, and it runs outside the simulation. The lineage never exceeds
 * {@link #MAX_LINEAGE} and never loses its root.
 */
public final class CartridgeIds {

    public static final int LENGTH = 16;
    public static final Pattern VALID = Pattern.compile("[a-z2-7]{16}");
    public static final String GALLERY_PREFIX = "gallery";
    public static final int MAX_LINEAGE = 8;

    private static final char[] ALPHABET = "abcdefghijklmnopqrstuvwxyz234567".toCharArray();

    private CartridgeIds() {
    }

    /** 80 random bits as 16 base32 characters. */
    public static String mint(RandomSource serverRandom) {
        long high = serverRandom.nextLong();
        int low = serverRandom.nextInt() & 0xFFFF;
        char[] out = new char[LENGTH];
        for (int i = 0; i < 12; i++) {
            out[i] = ALPHABET[(int) ((high >>> (59 - 5 * i)) & 31)];
        }
        long rest = ((high & 0xF) << 16) | low; // 20 bits
        for (int i = 0; i < 4; i++) {
            out[12 + i] = ALPHABET[(int) ((rest >>> (15 - 5 * i)) & 31)];
        }
        return new String(out);
    }

    public static boolean isValid(String id) {
        return id != null && VALID.matcher(id).matches();
    }

    /**
     * The lineage of a remix of a cartridge with {@code parentLineage} and id {@code parentId}: the parent's
     * lineage plus the parent. Past {@link #MAX_LINEAGE} it keeps the root and the most recent six, then
     * appends the parent.
     */
    public static List<String> remixLineage(List<String> parentLineage, String parentId) {
        List<String> out = new ArrayList<>(parentLineage);
        if (out.size() + 1 > MAX_LINEAGE) {
            List<String> trimmed = new ArrayList<>();
            trimmed.add(out.get(0));
            trimmed.addAll(out.subList(out.size() - (MAX_LINEAGE - 2), out.size()));
            out = trimmed;
        }
        out.add(parentId);
        return List.copyOf(out);
    }
}
