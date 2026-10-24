package za.co.neroland.neroarcade.network;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client: one page of the Arcade Exchange for the player who has the kiosk open (003 §11). <b>Metadata
 * only — never a {@code GameDef}, never a UUID.</b> A row carries the title, the credit (empty = "Anonymous
 * builder"), the genre words, the play and heart counts, the ten slot colours and flags relative to the recipient
 * ({@link #ROW_MINE}: they may unpublish it; {@link #ROW_HEARTED}: they already hearted it this session). On the
 * drafts tab the rows are the recipient's own drafts ({@code id} = the draft's index, {@code ageDays} its age).
 *
 * @param tab        {@link #TAB_LISTINGS} or {@link #TAB_DRAFTS}
 * @param flags      {@link #FLAG_ENABLED} (the kill switch), {@link #FLAG_MODERATOR} (the recipient is an op)
 * @param statusKey  the last action's result as a lang key ("" = none), with {@code statusArgs}
 * @param statusKind 0 neutral, 1 success, 2 refusal, 3 pending (the title filter is still checking)
 */
public record ExchangePagePayload(int tab, int page, int pageCount, int total, List<Row> rows, int flags,
        String statusKey, List<String> statusArgs, int statusKind, int draftsUsed, int draftsMax)
        implements CustomPacketPayload {

    public static final int TAB_LISTINGS = 0;
    public static final int TAB_DRAFTS = 1;

    public static final int FLAG_ENABLED = 1;
    public static final int FLAG_MODERATOR = 2;

    public static final int ROW_MINE = 1;
    public static final int ROW_HEARTED = 2;
    public static final int ROW_UNREADABLE = 4;

    public static final int STATUS_NEUTRAL = 0;
    public static final int STATUS_OK = 1;
    public static final int STATUS_DENY = 2;
    public static final int STATUS_PENDING = 3;

    /** The most rows a page may carry (the {@code exchange.pageSize} ceiling). */
    public static final int MAX_ROWS = 16;
    public static final int MAX_TITLE = 32;
    public static final int MAX_CREDIT = 16;
    public static final int MAX_TAGS = 6;
    public static final int SLOTS = 10;
    /** The longest {@code slotChips} entry (chip ids joined by {@code ,}). */
    public static final int MAX_SLOT_CHIPS = 160;
    private static final int MAX_ARGS = 4;

    /**
     * One listing (or draft) line. {@code slotColours} is ten {@code 0xRRGGBB} values in slot order;
     * {@code slotChips} is ten strings in slot order, each the chip ids in that slot joined by {@code ,}
     * ({@code ""} = empty slot, the game uses the default) — so the pane can name every slot, never colour alone.
     */
    public record Row(int id, String title, String credit, List<String> tags, int plays, int hearts,
            int[] slotColours, List<String> slotChips, int flags, int ageDays) {
        public Row {
            title = cut(title, MAX_TITLE);
            credit = cut(credit, MAX_CREDIT);
            tags = List.copyOf(tags.size() > MAX_TAGS ? tags.subList(0, MAX_TAGS) : tags);
            int[] colours = new int[SLOTS];
            if (slotColours != null) {
                System.arraycopy(slotColours, 0, colours, 0, Math.min(SLOTS, slotColours.length));
            }
            slotColours = colours;
            List<String> chips = new ArrayList<>(SLOTS);
            for (int i = 0; i < SLOTS; i++) {
                String s = slotChips != null && i < slotChips.size() ? slotChips.get(i) : "";
                chips.add(cut(s, MAX_SLOT_CHIPS));
            }
            slotChips = List.copyOf(chips);
        }

        /** The chip ids in slot {@code index} (empty = the slot is empty). */
        public List<String> chipsIn(int index) {
            String s = index >= 0 && index < SLOTS ? slotChips.get(index) : "";
            return s.isEmpty() ? List.of() : List.of(s.split(","));
        }

        @Override
        public int[] slotColours() {
            return slotColours.clone();
        }

        public boolean has(int flag) {
            return (flags & flag) != 0;
        }
    }

    public static final CustomPacketPayload.Type<ExchangePagePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("neroarcade", "exchange_page"));

    public static final StreamCodec<ByteBuf, ExchangePagePayload> STREAM_CODEC = StreamCodec.of(
            ExchangePagePayload::write, ExchangePagePayload::read);

    public ExchangePagePayload {
        rows = List.copyOf(rows.size() > MAX_ROWS ? rows.subList(0, MAX_ROWS) : rows);
        statusKey = statusKey == null ? "" : statusKey;
        statusArgs = List.copyOf(statusArgs.size() > MAX_ARGS ? statusArgs.subList(0, MAX_ARGS) : statusArgs);
    }

    public boolean enabled() {
        return (flags & FLAG_ENABLED) != 0;
    }

    public boolean moderator() {
        return (flags & FLAG_MODERATOR) != 0;
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.codePointCount(0, s.length()) > max ? s.substring(0, s.offsetByCodePoints(0, max)) : s;
    }

    private static final StreamCodec<ByteBuf, String> TEXT = ByteBufCodecs.stringUtf8(MAX_TITLE * 4);
    private static final StreamCodec<ByteBuf, String> WORD = ByteBufCodecs.stringUtf8(64);
    private static final StreamCodec<ByteBuf, String> CHIPS = ByteBufCodecs.stringUtf8(MAX_SLOT_CHIPS);

    private static void write(ByteBuf buf, ExchangePagePayload p) {
        buf.writeByte(p.tab);
        ByteBufCodecs.VAR_INT.encode(buf, p.page);
        ByteBufCodecs.VAR_INT.encode(buf, p.pageCount);
        ByteBufCodecs.VAR_INT.encode(buf, p.total);
        buf.writeByte(p.rows.size());
        for (Row r : p.rows) {
            ByteBufCodecs.VAR_INT.encode(buf, r.id());
            TEXT.encode(buf, r.title());
            TEXT.encode(buf, r.credit());
            buf.writeByte(r.tags().size());
            for (String t : r.tags()) {
                WORD.encode(buf, t);
            }
            ByteBufCodecs.VAR_INT.encode(buf, r.plays());
            ByteBufCodecs.VAR_INT.encode(buf, r.hearts());
            for (int c : r.slotColours()) {
                buf.writeMedium(c & 0xFFFFFF);
            }
            for (String chips : r.slotChips()) {
                CHIPS.encode(buf, chips);
            }
            buf.writeByte(r.flags());
            ByteBufCodecs.VAR_INT.encode(buf, r.ageDays());
        }
        buf.writeByte(p.flags);
        WORD.encode(buf, p.statusKey);
        buf.writeByte(p.statusArgs.size());
        for (String a : p.statusArgs) {
            TEXT.encode(buf, a);
        }
        buf.writeByte(p.statusKind);
        ByteBufCodecs.VAR_INT.encode(buf, p.draftsUsed);
        ByteBufCodecs.VAR_INT.encode(buf, p.draftsMax);
    }

    private static ExchangePagePayload read(ByteBuf buf) {
        int tab = buf.readUnsignedByte();
        int page = ByteBufCodecs.VAR_INT.decode(buf);
        int pageCount = ByteBufCodecs.VAR_INT.decode(buf);
        int total = ByteBufCodecs.VAR_INT.decode(buf);
        int n = Math.min(buf.readUnsignedByte(), MAX_ROWS);
        List<Row> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int id = ByteBufCodecs.VAR_INT.decode(buf);
            String title = TEXT.decode(buf);
            String credit = TEXT.decode(buf);
            int tagCount = Math.min(buf.readUnsignedByte(), MAX_TAGS);
            List<String> tags = new ArrayList<>(tagCount);
            for (int t = 0; t < tagCount; t++) {
                tags.add(WORD.decode(buf));
            }
            int plays = ByteBufCodecs.VAR_INT.decode(buf);
            int hearts = ByteBufCodecs.VAR_INT.decode(buf);
            int[] colours = new int[SLOTS];
            for (int s = 0; s < SLOTS; s++) {
                colours[s] = buf.readUnsignedMedium();
            }
            List<String> chips = new ArrayList<>(SLOTS);
            for (int s = 0; s < SLOTS; s++) {
                chips.add(CHIPS.decode(buf));
            }
            int flags = buf.readUnsignedByte();
            int age = ByteBufCodecs.VAR_INT.decode(buf);
            rows.add(new Row(id, title, credit, tags, plays, hearts, colours, chips, flags, age));
        }
        int flags = buf.readUnsignedByte();
        String statusKey = WORD.decode(buf);
        int argCount = Math.min(buf.readUnsignedByte(), MAX_ARGS);
        List<String> args = new ArrayList<>(argCount);
        for (int a = 0; a < argCount; a++) {
            args.add(TEXT.decode(buf));
        }
        int kind = buf.readUnsignedByte();
        int used = ByteBufCodecs.VAR_INT.decode(buf);
        int max = ByteBufCodecs.VAR_INT.decode(buf);
        return new ExchangePagePayload(tab, page, pageCount, total, rows, flags, statusKey, args, kind, used, max);
    }

    @Override
    public CustomPacketPayload.Type<ExchangePagePayload> type() {
        return TYPE;
    }
}
