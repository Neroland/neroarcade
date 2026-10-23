package za.co.neroland.neroarcade.client.screen;

import java.util.List;

import za.co.neroland.neroarcade.runtime.Cell;
import za.co.neroland.neroarcade.runtime.Entities;
import za.co.neroland.neroarcade.runtime.EntityKind;
import za.co.neroland.neroarcade.runtime.MatchState;

import static za.co.neroland.neroarcade.client.screen.ThemePalettes.BACKGROUND;
import static za.co.neroland.neroarcade.client.screen.ThemePalettes.DANGER;
import static za.co.neroland.neroarcade.client.screen.ThemePalettes.EXIT;
import static za.co.neroland.neroarcade.client.screen.ThemePalettes.FLASH;
import static za.co.neroland.neroarcade.client.screen.ThemePalettes.FLOOR;
import static za.co.neroland.neroarcade.client.screen.ThemePalettes.HAZARD_HOT;
import static za.co.neroland.neroarcade.client.screen.ThemePalettes.PAWN;
import static za.co.neroland.neroarcade.client.screen.ThemePalettes.PICKUP_2;
import static za.co.neroland.neroarcade.client.screen.ThemePalettes.SIGNATURE;
import static za.co.neroland.neroarcade.client.screen.ThemePalettes.TEXT;
import static za.co.neroland.neroarcade.client.screen.ThemePalettes.TEXT_DIM;
import static za.co.neroland.neroarcade.client.screen.ThemePalettes.WALL;
import static za.co.neroland.neroarcade.client.screen.ThemePalettes.WALL_LIGHT;

/**
 * Draws one arcade screen into a reused 64x64 buffer of palette indices (004 §5): a match frame, one of the four
 * status screens, the attract loop, plus countdown and result banners. Shared by the cabinet BER, the bench
 * preview and (Stage 4) the Marquee. <b>Allocation-free per frame</b>: every call draws into {@link #pixels()};
 * colours are resolved later by {@link ScreenPixels}. Integer-only, no sub-pixel drawing.
 *
 * <p>The client draws whatever the snapshot says; it never decides a score or a phase. Pure Java (testable
 * without a game).
 */
public final class ScreenRenderer {

    public static final int SIZE = ArenaLayout.SCREEN;
    /** Attract loop length in client ticks (6 s): 2 s title, 3 s arena demo, 1 s high scores. */
    public static final int ATTRACT_TICKS = 120;

    private final byte[] pixels = new byte[SIZE * SIZE];
    private SpriteSheets sheets;
    private ArcadeFont font;

    public ScreenRenderer(SpriteSheets sheets, ArcadeFont font) {
        this.sheets = sheets;
        this.font = font;
    }

    /** Swaps the theme's sheets (a THEME chip is a pure palette/sheet swap). */
    public void use(SpriteSheets sheets, ArcadeFont font) {
        this.sheets = sheets;
        this.font = font;
    }

    public SpriteSheets sheets() {
        return sheets;
    }

    /** The buffer (row-major palette indices). Reused: copy it if you need to keep a frame. */
    public byte[] pixels() {
        return pixels;
    }

    public int pixel(int x, int y) {
        return pixels[y * SIZE + x] & 15;
    }

    /** FNV-1a 64 over the buffer: changes when the picture changes (used to skip identical uploads, and in tests). */
    public long checksum() {
        long h = 0xcbf29ce484222325L;
        for (byte b : pixels) {
            h ^= b & 0xFF;
            h *= 0x100000001b3L;
        }
        return h;
    }

    // --- primitives ---------------------------------------------------------------------------------

    public void clear(int index) {
        java.util.Arrays.fill(pixels, (byte) index);
    }

    public void put(int x, int y, int index) {
        if (x >= 0 && y >= 0 && x < SIZE && y < SIZE) {
            pixels[y * SIZE + x] = (byte) index;
        }
    }

    public void fill(int x, int y, int w, int h, int index) {
        int x0 = Math.max(0, x);
        int y0 = Math.max(0, y);
        int x1 = Math.min(SIZE, x + w);
        int y1 = Math.min(SIZE, y + h);
        for (int py = y0; py < y1; py++) {
            java.util.Arrays.fill(pixels, py * SIZE + x0, Math.max(py * SIZE + x0, py * SIZE + x1), (byte) index);
        }
    }

    /** One line of bitmap text at {@code scale} (1 = 5x7 glyphs). */
    public void text(String line, int x, int y, int index, int scale) {
        int cx = x;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            for (int gy = 0; gy < ArcadeFont.GLYPH_H; gy++) {
                for (int gx = 0; gx < ArcadeFont.GLYPH_W; gx++) {
                    if (font.lit(c, gx, gy)) {
                        fill(cx + gx * scale, y + gy * scale, scale, scale, index);
                    }
                }
            }
            cx += ArcadeFont.ADVANCE * scale;
        }
    }

    /** One line centred horizontally. */
    public void centred(String line, int y, int index, int scale) {
        text(line, (SIZE - ArcadeFont.width(line) * scale) / 2, y, index, scale);
    }

    /** Lines centred as a block around the screen's middle (line pitch 9 px). */
    public void centredBlock(List<String> lines, int index) {
        int pitch = ArcadeFont.GLYPH_H + 2;
        int top = (SIZE - (lines.size() * pitch - 2)) / 2;
        for (int i = 0; i < lines.size(); i++) {
            centred(lines.get(i), top + i * pitch, index, 1);
        }
    }

    /**
     * A sprite drawn into a {@code w x h} cell by nearest sampling; {@code from -> to} remaps one palette index
     * (another seat's pawn colour). Index 0 is transparent.
     */
    public void sprite(SpriteSheets.Sheet sheet, int sprite, int x, int y, int w, int h, int from, int to) {
        if (w <= 0 || h <= 0) {
            return;
        }
        for (int py = 0; py < h; py++) {
            int sy = py * SpriteSheets.SPRITE / h;
            for (int px = 0; px < w; px++) {
                int index = sheets.index(sheet, sprite, px * SpriteSheets.SPRITE / w, sy);
                if (index != 0) {
                    put(x + px, y + py, index == from ? to : index);
                }
            }
        }
    }

    // --- screens ------------------------------------------------------------------------------------

    /** A status screen (004 §5); {@code reason} is the short UNREADABLE reason; {@code ticks} drives the blinks. */
    public void drawStatus(ScreenStatus status, String reason, int ticks) {
        clear(BACKGROUND);
        switch (status) {
            case NO_CARTRIDGE -> centredBlock(List.of("NO", "CARTRIDGE"), TEXT_DIM);
            case NO_POWER -> {
                if ((ticks / 20) % 2 == 0) { // 0.5 Hz: one second on, one second off
                    centredBlock(List.of("NO", "POWER"), DANGER);
                }
            }
            case UNREADABLE -> {
                centred("UNREADABLE", 14, HAZARD_HOT, 1);
                centred("HERE", 23, HAZARD_HOT, 1);
                List<String> why = ArcadeFont.wrap(reason == null ? "" : reason, ArcadeFont.LINE_CHARS, 2);
                for (int i = 0; i < why.size(); i++) {
                    centred(why.get(i), 36 + i * 9, TEXT_DIM, 1);
                }
            }
            case LINKED_WAITING -> {
                centredBlock(List.of("LINKED", "WAITING"), EXIT);
                int lit = (ticks / 10) % 4;
                for (int i = 0; i < 3; i++) {
                    fill(26 + i * 5, 44, 2, 2, i < lit ? SIGNATURE : TEXT_DIM);
                }
            }
        }
    }

    /**
     * The attract loop of an awake, idle cabinet (004 §5): title card with a blinking "INSERT YOURSELF", the arena
     * with a pawn walking a scripted square ({@code arena} may be null: the title card stays up), then the high
     * scores (none until Stage 4 leaderboards).
     */
    public void drawAttract(String title, MatchState arena, SpriteMap map, int ticks) {
        int t = Math.floorMod(ticks, ATTRACT_TICKS);
        if (t >= 40 && t < 100 && arena != null) {
            ArenaLayout layout = ArenaLayout.of(arena.width(), arena.height());
            drawCells(arena, layout);
            int side = Math.max(1, Math.min(arena.width(), arena.height()) - 5);
            int step = (t - 40) / 2; // one cell per 2 ticks
            int lap = Math.floorMod(step, side * 4);
            int cx = 2;
            int cy = 2;
            if (lap < side) {
                cx += lap;
            } else if (lap < side * 2) {
                cx += side;
                cy += lap - side;
            } else if (lap < side * 3) {
                cx += side - (lap - side * 2);
                cy += side;
            } else {
                cy += side - (lap - side * 3);
            }
            sprite(SpriteSheets.Sheet.PAWNS, map.pawnSprite() * 2 + ((t / 8) & 1), layout.ox() + cx * layout.sx(),
                    layout.oy() + cy * layout.sy(), layout.sx(), layout.sy(), -1, -1);
            return;
        }
        clear(BACKGROUND);
        if (t >= 100) {
            centred("HIGH", 14, TEXT, 1);
            centred("SCORES", 23, TEXT, 1);
            centred("NONE YET", 38, TEXT_DIM, 1);
            return;
        }
        List<String> lines = ArcadeFont.wrap(title == null || title.isBlank() ? "NEROARCADE" : title,
                ArcadeFont.LINE_CHARS, 3);
        int pitch = ArcadeFont.GLYPH_H + 2;
        for (int i = 0; i < lines.size(); i++) {
            centred(lines.get(i), 6 + i * pitch, TEXT, 1);
        }
        if ((ticks / 10) % 2 == 0) { // 1 Hz
            centred("INSERT", 40, SIGNATURE, 1);
            centred("YOURSELF", 49, SIGNATURE, 1);
        }
    }

    /** A match frame: cells, then entities in id order, drawn at {@code positions}; {@code ticks} animates sprites. */
    public void drawMatch(MatchState state, SpriteMap map, EntityPositions positions, int ticks) {
        ArenaLayout layout = ArenaLayout.of(state.width(), state.height());
        drawCells(state, layout);
        int frame = (ticks / 8) & 1;
        int count = state.entityCount();
        for (int i = 0; i < count; i++) {
            int id = state.entityIdAt(i);
            if ((state.get(id, Entities.FLAGS) & Entities.F_HIDDEN) != 0) {
                continue;
            }
            int x = layout.px(positions.x(state, id));
            int y = layout.py(positions.y(state, id));
            int w = layout.sx();
            int h = layout.sy();
            int owner = state.get(id, Entities.OWNER);
            int seatColour = seatColour(owner);
            EntityKind kind = EntityKind.byOrdinal(state.get(id, Entities.KIND));
            switch (kind) {
                case PAWN -> sprite(SpriteSheets.Sheet.PAWNS, map.pawnSprite() * 2 + frame, x, y, w, h, PAWN,
                        seatColour);
                case SEGMENT -> sprite(SpriteSheets.Sheet.PAWNS, map.pawnSprite() * 2 + 1, x, y, w, h, PAWN,
                        seatColour); // frame 1 of the owning pawn's pair (008 §8 item 3)
                case HAZARD -> sprite(SpriteSheets.Sheet.HAZARDS, map.hazard(state.tag(id)) * 2 + frame, x, y, w, h,
                        -1, -1);
                case PICKUP -> sprite(SpriteSheets.Sheet.PICKUPS, map.pickup(state.tag(id)), x, y, w, h, -1, -1);
                case EXIT -> sprite(SpriteSheets.Sheet.TILES, SpriteSheets.TILE_EXIT, x, y, w, h, -1, -1);
                case PROJECTILE -> fill(x + w / 4, y + h / 4, Math.max(1, w / 2), Math.max(1, h / 2),
                        owner >= 0 ? FLASH : HAZARD_HOT);
                case MARKER -> put(x + w / 2, y + h / 2, TEXT_DIM);
            }
        }
        if (state.fogRadius() > 0) {
            drawFog(state, layout, positions);
        }
    }

    /** Pawn colour per seat: seat 0 keeps the theme's pawn colour, others get distinct palette roles. */
    static int seatColour(int seat) {
        return switch (seat) {
            case 1 -> PICKUP_2;
            case 2 -> HAZARD_HOT;
            case 3 -> EXIT;
            default -> PAWN;
        };
    }

    private void drawCells(MatchState state, ArenaLayout layout) {
        clear(BACKGROUND);
        int w = state.width();
        int h = state.height();
        for (int cy = 0; cy < h; cy++) {
            for (int cx = 0; cx < w; cx++) {
                byte cell = state.cellAt(cy * w + cx);
                int x = layout.ox() + cx * layout.sx();
                int y = layout.oy() + cy * layout.sy();
                int tile;
                int from = -1;
                int to = -1;
                int goalSeat = Cell.goalSeat(cell);
                if (cell == Cell.WALL) {
                    boolean above = cy > 0 && state.cellAt((cy - 1) * w + cx) == Cell.WALL;
                    tile = above ? SpriteSheets.TILE_WALL_TOP : SpriteSheets.TILE_WALL;
                } else if (cell == Cell.VOID) {
                    continue;
                } else if (cell == Cell.BLOCK) {
                    // cells carry no owner: the stack draws in the theme's pawn colour (seat 0's); falls back to wall
                    tile = sheets.has(SpriteSheets.Sheet.TILES, SpriteSheets.TILE_BLOCK) ? SpriteSheets.TILE_BLOCK
                            : SpriteSheets.TILE_WALL;
                } else if (goalSeat >= 0) {
                    tile = sheets.has(SpriteSheets.Sheet.TILES, SpriteSheets.TILE_GOAL) ? SpriteSheets.TILE_GOAL
                            : SpriteSheets.TILE_EXIT;
                    from = EXIT; // the dashed goal line (palette 5) in the goal seat's colour
                    to = seatColour(goalSeat);
                } else if (cell == Cell.EXIT || cell == Cell.EXIT_LOCKED) {
                    tile = SpriteSheets.TILE_EXIT;
                    if (cell == Cell.EXIT_LOCKED) {
                        from = EXIT;
                        to = TEXT_DIM;
                    }
                } else if (cell == Cell.LANE) {
                    tile = SpriteSheets.TILE_FLOOR_ALT;
                } else {
                    tile = ((cx + cy) & 1) == 0 ? SpriteSheets.TILE_FLOOR : SpriteSheets.TILE_FLOOR_ALT;
                }
                if (sheets.has(SpriteSheets.Sheet.TILES, tile)) {
                    sprite(SpriteSheets.Sheet.TILES, tile, x, y, layout.sx(), layout.sy(), from, to);
                } else {
                    fill(x, y, layout.sx(), layout.sy(), fallbackColour(tile));
                }
            }
        }
    }

    private static int fallbackColour(int tile) {
        return switch (tile) {
            case SpriteSheets.TILE_WALL -> WALL;
            case SpriteSheets.TILE_WALL_TOP -> WALL_LIGHT;
            case SpriteSheets.TILE_EXIT -> EXIT;
            default -> FLOOR;
        };
    }

    /** Cells further than the fog radius from every pawn go dark (a render hint, never simulation). */
    private void drawFog(MatchState state, ArenaLayout layout, EntityPositions positions) {
        int radius = state.fogRadius();
        int w = state.width();
        int h = state.height();
        int count = state.entityCount();
        for (int cy = 0; cy < h; cy++) {
            for (int cx = 0; cx < w; cx++) {
                boolean seen = false;
                for (int i = 0; i < count && !seen; i++) {
                    int id = state.entityIdAt(i);
                    if (state.get(id, Entities.KIND) == EntityKind.PAWN.ordinal()) {
                        int dx = (positions.x(state, id) >> MatchState.FP_SHIFT) - cx;
                        int dy = (positions.y(state, id) >> MatchState.FP_SHIFT) - cy;
                        seen = dx * dx + dy * dy <= radius * radius;
                    }
                }
                if (!seen) {
                    fill(layout.ox() + cx * layout.sx(), layout.oy() + cy * layout.sy(), layout.sx(), layout.sy(),
                            BACKGROUND);
                }
            }
        }
    }

    // --- overlays -----------------------------------------------------------------------------------

    /** COUNTDOWN: a 3x digit centred over the frame ({@code ticksLeft} 60..1 → 3..1). */
    public void drawCountdown(int ticksLeft) {
        int digit = Math.max(1, Math.min(9, (ticksLeft + 19) / 20));
        String text = Integer.toString(digit);
        fill(22, 18, 20, 27, BACKGROUND);
        centred(text, 21, FLASH, 3);
    }

    /** A centred band with one or two lines (RESULTS, PAUSED). */
    public void drawBanner(String line1, String line2, int index) {
        int lines = line2 == null || line2.isEmpty() ? 1 : 2;
        int h = lines * 9 + 4;
        int top = (SIZE - h) / 2;
        fill(0, top, SIZE, h, BACKGROUND);
        fill(0, top, SIZE, 1, index);
        fill(0, top + h - 1, SIZE, 1, index);
        centred(line1, top + 3, index, 1);
        if (lines == 2) {
            centred(line2, top + 12, TEXT, 1);
        }
    }
}
