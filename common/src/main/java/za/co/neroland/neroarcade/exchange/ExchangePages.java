package za.co.neroland.neroarcade.exchange;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiPredicate;

import za.co.neroland.neroarcade.game.CartridgeValidator;
import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.network.ExchangePagePayload;

/**
 * Builds the {@link ExchangePagePayload} a kiosk viewer gets (003 §8.2, §11), pure: the page is clamped server side,
 * rows carry metadata only, and every per-row flag is relative to the viewer (never a UUID).
 */
public final class ExchangePages {

    /** The colour an empty slot shows in a listing's slot strip. */
    public static final int EMPTY_SLOT = 0x3A4458;

    private ExchangePages() {
    }

    /** The status line of a page. */
    public record Status(String key, List<String> args, int kind) {
        public static final Status NONE = new Status("", List.of(), ExchangePagePayload.STATUS_NEUTRAL);

        public static Status ok(String key, String... args) {
            return new Status(key, List.of(args), ExchangePagePayload.STATUS_OK);
        }

        public static Status deny(String key, String... args) {
            return new Status(key, List.of(args), ExchangePagePayload.STATUS_DENY);
        }

        public static Status pending(String key) {
            return new Status(key, List.of(), ExchangePagePayload.STATUS_PENDING);
        }
    }

    /** Pages for {@code total} rows of {@code size} (at least one page). */
    public static int pageCount(int total, int size) {
        return Math.max(1, (total + Math.max(1, size) - 1) / Math.max(1, size));
    }

    /** {@code page} clamped to 0..pageCount-1. */
    public static int clamp(int page, int total, int size) {
        return Math.max(0, Math.min(page, pageCount(total, size) - 1));
    }

    /** A browse page: listings in browse order. */
    public static ExchangePagePayload listings(List<Listing> browse, int page, int size, UUID viewer,
            BiPredicate<UUID, Integer> hearted, boolean enabled, boolean moderator, Status status, int draftsUsed,
            int draftsMax) {
        int p = clamp(page, browse.size(), size);
        List<ExchangePagePayload.Row> rows = new ArrayList<>();
        for (int i = p * size; i < browse.size() && i < (p + 1) * size; i++) {
            Listing l = browse.get(i);
            int flags = 0;
            if (moderator || l.authorUuid().equals(viewer)) {
                flags |= ExchangePagePayload.ROW_MINE;
            }
            if (viewer != null && hearted.test(viewer, l.listingId())) {
                flags |= ExchangePagePayload.ROW_HEARTED;
            }
            rows.add(new ExchangePagePayload.Row(l.listingId(), l.title(), l.credit().orElse(""), l.genreTags(),
                    l.plays(), l.hearts(), slotColours(l.gameDef()), flags, 0));
        }
        return new ExchangePagePayload(ExchangePagePayload.TAB_LISTINGS, p, pageCount(browse.size(), size),
                browse.size(), rows, flags(enabled, moderator), status.key(), status.args(), status.kind(), draftsUsed,
                draftsMax);
    }

    /** A drafts page: the viewer's own drafts, oldest first; {@code id} is the draft's index. */
    public static ExchangePagePayload drafts(List<Draft> drafts, int page, int size, long nowEpoch, boolean enabled,
            boolean moderator, Status status, int draftsMax) {
        int p = clamp(page, drafts.size(), size);
        List<ExchangePagePayload.Row> rows = new ArrayList<>();
        for (int i = p * size; i < drafts.size() && i < (p + 1) * size; i++) {
            Draft d = drafts.get(i);
            List<String> tags = List.of();
            int flags = ExchangePagePayload.ROW_MINE;
            try {
                tags = GenreTags.of(CartridgeValidator.validate(d.gameDef()));
            } catch (ValidationError unreadable) {
                flags |= ExchangePagePayload.ROW_UNREADABLE;
            }
            int age = (int) Math.max(0, (nowEpoch - d.createdAt()) / 86_400L);
            rows.add(new ExchangePagePayload.Row(i, d.gameDef().title(), "", tags, 0, 0, slotColours(d.gameDef()),
                    flags, age));
        }
        return new ExchangePagePayload(ExchangePagePayload.TAB_DRAFTS, p, pageCount(drafts.size(), size),
                drafts.size(), rows, flags(enabled, moderator), status.key(), status.args(), status.kind(),
                drafts.size(), draftsMax);
    }

    private static int flags(boolean enabled, boolean moderator) {
        return (enabled ? ExchangePagePayload.FLAG_ENABLED : 0) | (moderator ? ExchangePagePayload.FLAG_MODERATOR : 0);
    }

    /** The ten slot colours of a game: a filled slot in its colour, an empty one grey. */
    public static int[] slotColours(GameDef def) {
        ModuleType[] types = ModuleType.values();
        int[] out = new int[ExchangePagePayload.SLOTS];
        for (int i = 0; i < out.length && i < types.length; i++) {
            out[i] = def.slot(types[i]).isEmpty() ? EMPTY_SLOT : types[i].colour & 0xFFFFFF;
        }
        return out;
    }
}
