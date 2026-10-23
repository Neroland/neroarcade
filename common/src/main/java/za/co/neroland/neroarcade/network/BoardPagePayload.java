package za.co.neroland.neroarcade.network;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client: one cartridge's leaderboard for the marquee's idle loop and the cabinet attract screen (003
 * §5, §11). Rows carry a rank, a score and a name <b>only for players who opted in to credit</b> — otherwise the
 * name is empty and the client shows "Anonymous". Never a UUID. {@code myBest} is the recipient's own personal best
 * on this board, or -1.
 */
public record BoardPagePayload(String lineageRoot, List<Row> rows, int myBest) implements CustomPacketPayload {

    /** Rows per page: the configurable board cap's ceiling. */
    public static final int MAX_ROWS = 25;
    /** Profile names are at most 16 characters. */
    public static final int MAX_NAME = 16;

    /** One board row: {@code name} is "" for anyone who has not opted in. */
    public record Row(int rank, String name, int score) {
        public Row {
            name = name == null ? "" : name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name;
        }
    }

    public static final CustomPacketPayload.Type<BoardPagePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("neroarcade", "board_page"));

    public static final StreamCodec<ByteBuf, BoardPagePayload> STREAM_CODEC = StreamCodec.of(
            BoardPagePayload::write, BoardPagePayload::read);

    public BoardPagePayload {
        lineageRoot = lineageRoot == null ? "" : lineageRoot;
        rows = List.copyOf(rows.size() > MAX_ROWS ? rows.subList(0, MAX_ROWS) : rows);
    }

    private static void write(ByteBuf buf, BoardPagePayload p) {
        ByteBufCodecs.stringUtf8(64).encode(buf, p.lineageRoot);
        buf.writeByte(p.rows.size());
        for (Row row : p.rows) {
            buf.writeByte(row.rank());
            ByteBufCodecs.stringUtf8(MAX_NAME * 4).encode(buf, row.name());
            ByteBufCodecs.VAR_INT.encode(buf, row.score());
        }
        ByteBufCodecs.VAR_INT.encode(buf, p.myBest);
    }

    private static BoardPagePayload read(ByteBuf buf) {
        String root = ByteBufCodecs.stringUtf8(64).decode(buf);
        int n = Math.min(buf.readUnsignedByte(), MAX_ROWS);
        List<Row> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int rank = buf.readUnsignedByte();
            String name = ByteBufCodecs.stringUtf8(MAX_NAME * 4).decode(buf);
            int score = ByteBufCodecs.VAR_INT.decode(buf);
            rows.add(new Row(rank, name, score));
        }
        int myBest = ByteBufCodecs.VAR_INT.decode(buf);
        return new BoardPagePayload(root, rows, myBest);
    }

    @Override
    public CustomPacketPayload.Type<BoardPagePayload> type() {
        return TYPE;
    }
}
