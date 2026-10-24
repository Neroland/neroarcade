package za.co.neroland.neroarcade.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mojang.brigadier.Command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroarcade.block.ArcadeBlockProperties;
import za.co.neroland.neroarcade.block.Half;
import za.co.neroland.neroarcade.cabinet.ArcadeCabinetBlockEntity;
import za.co.neroland.neroarcade.command.GalleryLayout.Cable;
import za.co.neroland.neroarcade.command.GalleryLayout.ChipCabinet;
import za.co.neroland.neroarcade.command.GalleryLayout.Facing;
import za.co.neroland.neroarcade.command.GalleryLayout.Offset;
import za.co.neroland.neroarcade.exchange.ExchangeStore;
import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.item.CartridgeItem;
import za.co.neroland.neroarcade.module.ArcadeModule;
import za.co.neroland.neroarcade.module.ModuleRegistry;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.module.SlotEntry;
import za.co.neroland.neroarcade.registry.ModBlocks;
import za.co.neroland.neroarcade.runtime.Replay;
import za.co.neroland.neroarcade.runtime.ShippedReplays;

/**
 * The creative showcase (004 §9): {@code /neroarcade gallery} builds a lit hall north of the invoking operator —
 * one cabinet per shipped chip, each holding the lowest-numbered example cartridge that uses the chip, with a floor
 * sign naming the chip in its slot colour; a dais with a bench, a duplicator and an Exchange seeded with the sixteen
 * examples (credit "Gallery"); and a four-cabinet link row under a scale-3 Marquee Screen. No player ever sits down:
 * every screen runs its cartridge's shipped attract replay client-side, and the marquee shows the link row's replay
 * mid-match. {@code /neroarcade gallery clear} puts back exactly what the build changed.
 *
 * <p><b>Safety.</b> Operator level 2 (the command tree), a player in creative mode, and nobody else within
 * {@value #OTHERS_RANGE} blocks (the gallery replaces blocks: a test world is assumed, and someone else's build is
 * never in reach). The build refuses when its space holds any block with contents (a chest, a machine) that an
 * earlier gallery did not place, so it can never delete an inventory. Every change is recorded in
 * {@link GalleryStore} with the state it replaced; the clear restores those states only where the gallery's own
 * block still stands, discards the gallery's cartridges without dropping them, unpublishes the gallery's listings
 * and sweeps loose items within 64 blocks. Nothing here writes a leaderboard, counts a play, fires a milestone or
 * names a player.
 */
public final class GalleryCommands {

    /** The author every gallery listing is filed under: a fixed id that no real account uses. */
    public static final UUID GALLERY_AUTHOR = za.co.neroland.neroarcade.exchange.ExchangeStore.SHOWCASE_AUTHOR;
    /** Other players within this many blocks stop a build or a clear. */
    public static final int OTHERS_RANGE = 96;
    /** Loose items within this many blocks are swept after a build or a clear. */
    public static final int SWEEP_RANGE = 64;

    private static final int FLAGS = Block.UPDATE_CLIENTS;

    private GalleryCommands() {
    }

    // --- build ------------------------------------------------------------------------------------------

    static int build(CommandSourceStack source) {
        ServerPlayer player = requireCreativeAlone(source);
        if (player == null) {
            return 0;
        }
        ServerLevel level = player.level();
        MinecraftServer server = source.getServer();
        String dimension = level.dimension().identifier().toString();
        BlockPos origin = player.blockPosition();
        GalleryStore store = GalleryStore.get(server);
        // a build inside an existing gallery replaces it
        store.at(dimension, origin.getX(), origin.getY(), origin.getZ()).ifPresent(old -> clear(level, store, old));

        int occupied = occupied(level, store, dimension, origin);
        if (occupied > 0) {
            source.sendFailure(Component.translatable("neroarcade.command.gallery.occupied", occupied));
            return 0;
        }
        Map<String, Replay> replays = ShippedReplays.all();
        if (replays.size() != ShippedReplays.SLUGS.size()) {
            source.sendFailure(Component.translatable("neroarcade.command.gallery.no_replays"));
            return 0;
        }

        Recorder rec = new Recorder(level, origin);
        buildShell(rec);
        List<ArcadeModule> chips = chips();
        Map<String, String> cartridgeFor = GalleryLayout.cartridgeFor(
                chips.stream().map(m -> m.id().toString()).toList(), chipSets(replays));
        List<ChipCabinet> stands = GalleryLayout.chipCabinets();
        List<BlockPos> cabinets = new ArrayList<>();
        List<String> cabinetSlugs = new ArrayList<>();
        for (int i = 0; i < stands.size() && i < chips.size(); i++) {
            ArcadeModule chip = chips.get(i);
            String slug = cartridgeFor.get(chip.id().toString());
            if (slug == null) {
                continue;
            }
            ChipCabinet stand = stands.get(i);
            cabinets.add(placeCabinet(rec, stand.lower(), stand.facing()));
            cabinetSlugs.add(slug);
            GameDef def = replays.get(slug).gameDef();
            placeSign(rec, stand.sign(), stand.facing(), chip, def.title());
        }
        buildWorkshop(rec);
        List<BlockPos> linkRow = buildLinkRow(rec);

        // contents, after every block stands
        int running = 0;
        for (int i = 0; i < cabinets.size(); i++) {
            running += insert(level, cabinets.get(i), replays.get(cabinetSlugs.get(i)).gameDef()) ? 1 : 0;
        }
        insert(level, linkRow.get(GalleryLayout.LINK_HOST),
                replays.get(GalleryLayout.LINK_CARTRIDGE).gameDef());
        List<GameDef> defs = replays.values().stream().map(Replay::gameDef).toList();
        int listed = ExchangeStore.get(server).publishShowcase(server, defs, GALLERY_AUTHOR, ShippedReplays.CREDIT,
                System.currentTimeMillis() / 1000L);

        store.add(new GalleryStore.Gallery(dimension, origin.asLong(), rec.changes, listed > 0));
        sweep(level, origin);
        int count = running;
        source.sendSuccess(() -> Component.translatable("neroarcade.command.gallery.built", count, listed), true);
        return Command.SINGLE_SUCCESS;
    }

    /** Chips in gallery order: slot order, then id (the bench rack's order). */
    static List<ArcadeModule> chips() {
        List<ArcadeModule> out = new ArrayList<>();
        for (ModuleType type : ModuleType.values()) {
            out.addAll(ModuleRegistry.byType(type));
        }
        return out;
    }

    /** Each example's chip ids, in 008 order. */
    static Map<String, Set<String>> chipSets(Map<String, Replay> replays) {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Replay> e : replays.entrySet()) {
            Set<String> ids = new LinkedHashSet<>();
            for (List<SlotEntry> entries : e.getValue().gameDef().slots().values()) {
                entries.forEach(entry -> ids.add(entry.id().toString()));
            }
            out.put(e.getKey(), ids);
        }
        return out;
    }

    private static void buildShell(Recorder rec) {
        BlockState floor = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        BlockState wall = Blocks.DEEPSLATE_TILES.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = GalleryLayout.MIN_DX; dx <= GalleryLayout.MAX_DX; dx++) {
            for (int dz = GalleryLayout.MIN_DZ; dz <= GalleryLayout.MAX_DZ; dz++) {
                rec.place(new Offset(dx, 0, dz), GalleryLayout.stripWindow(dx, dz) ? Blocks.GLASS.defaultBlockState() : floor);
                for (int dy = 1; dy < GalleryLayout.CEILING_DY; dy++) {
                    rec.place(new Offset(dx, dy, dz), GalleryLayout.wall(dx, dy, dz) ? wall
                            : GalleryLayout.dais(dx, dy, dz) ? Blocks.POLISHED_BLACKSTONE.defaultBlockState() : air);
                }
                rec.place(new Offset(dx, GalleryLayout.CEILING_DY, dz), wall);
            }
        }
        for (Cable cable : GalleryLayout.aisleStrip()) {
            rec.place(cable.at(), cable(cable));
        }
        for (Offset light : GalleryLayout.lights()) {
            rec.place(light, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, GalleryLayout.LIGHT_LEVEL));
        }
    }

    private static BlockPos placeCabinet(Recorder rec, Offset lower, Facing facing) {
        BlockState base = ModBlocks.ARCADE_CABINET.get().defaultBlockState()
                .setValue(ArcadeBlockProperties.FACING, direction(facing));
        rec.place(lower, base.setValue(ArcadeBlockProperties.HALF, Half.LOWER));
        rec.place(new Offset(lower.dx(), lower.dy() + 1, lower.dz()), base.setValue(ArcadeBlockProperties.HALF, Half.UPPER));
        return rec.at(lower);
    }

    private static void buildWorkshop(Recorder rec) {
        Direction facing = direction(GalleryLayout.WORKSHOP_FACING);
        rec.place(GalleryLayout.BENCH, ModBlocks.GAME_BENCH.get().defaultBlockState()
                .setValue(ArcadeBlockProperties.FACING, facing));
        rec.place(GalleryLayout.DUPLICATOR, ModBlocks.CARTRIDGE_DUPLICATOR.get().defaultBlockState()
                .setValue(ArcadeBlockProperties.FACING, facing));
        BlockState kiosk = ModBlocks.ARCADE_EXCHANGE.get().defaultBlockState()
                .setValue(ArcadeBlockProperties.FACING, facing);
        Offset ex = GalleryLayout.EXCHANGE;
        rec.place(ex, kiosk.setValue(ArcadeBlockProperties.HALF, Half.LOWER));
        rec.place(new Offset(ex.dx(), ex.dy() + 1, ex.dz()), kiosk.setValue(ArcadeBlockProperties.HALF, Half.UPPER));
    }

    /** The link row: cables in the back-wall line, the hub, the four cabinets and the marquee. */
    private static List<BlockPos> buildLinkRow(Recorder rec) {
        for (Cable cable : GalleryLayout.linkCables()) {
            rec.place(cable.at(), cable(cable).setValue(ArcadeBlockProperties.ACTIVE, false));
        }
        rec.place(GalleryLayout.HUB, ModBlocks.LINK_BUS.get().defaultBlockState()
                .setValue(ArcadeBlockProperties.FACING, Direction.SOUTH));
        List<BlockPos> out = new ArrayList<>();
        for (Offset lower : GalleryLayout.linkCabinets()) {
            out.add(placeCabinet(rec, lower, Facing.SOUTH));
        }
        rec.place(GalleryLayout.MARQUEE, ModBlocks.MARQUEE_SCREEN.get().defaultBlockState()
                .setValue(ArcadeBlockProperties.FACING, Direction.SOUTH)
                .setValue(ArcadeBlockProperties.SCALE, GalleryLayout.MARQUEE_SCALE));
        return out;
    }

    private static BlockState cable(Cable c) {
        return ModBlocks.LINK_CABLE.get().defaultBlockState()
                .setValue(ArcadeBlockProperties.NORTH, c.north())
                .setValue(ArcadeBlockProperties.SOUTH, c.south())
                .setValue(ArcadeBlockProperties.EAST, c.east())
                .setValue(ArcadeBlockProperties.WEST, c.west())
                .setValue(ArcadeBlockProperties.UP, c.up())
                .setValue(ArcadeBlockProperties.DOWN, c.down())
                .setValue(ArcadeBlockProperties.ACTIVE, true);
    }

    /** A waxed floor sign in front of a cabinet: slot word and chip name in the slot colour, then the game. */
    private static void placeSign(Recorder rec, Offset at, Facing facing, ArcadeModule chip, String title) {
        BlockState sign = Blocks.DARK_OAK_SIGN.defaultBlockState()
                .setValue(StandingSignBlock.ROTATION, RotationSegment.convertToSegment(direction(facing)));
        rec.place(at, sign);
        BlockPos pos = rec.at(at);
        if (!(rec.level.getBlockEntity(pos) instanceof SignBlockEntity be)) {
            return;
        }
        String colour = String.format("#%06x", chip.type().colour & 0xFFFFFF);
        ListTag lines = new ListTag();
        lines.add(line(null, "■ ", chip.type().langKey(), colour));
        lines.add(line(chip.langKey(), null, null, colour));
        lines.add(line(null, title, null, "#e8ecf4"));
        lines.add(line(null, "", null, colour));
        CompoundTag text = new CompoundTag();
        text.put("messages", lines);
        text.putString("color", "white");
        text.putBoolean("has_glowing_text", true);
        CompoundTag tag = new CompoundTag();
        tag.put("front_text", text);
        tag.put("back_text", text.copy());
        tag.putBoolean("is_waxed", true);
        be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, rec.level.registryAccess(), tag));
        be.setChanged();
        rec.level.sendBlockUpdated(pos, sign, sign, FLAGS);
    }

    /** One sign line as a text component: {@code literal} then {@code translate} (either may be null), coloured. */
    private static CompoundTag line(@Nullable String translate, @Nullable String literal, @Nullable String extraKey,
            String colour) {
        CompoundTag c = new CompoundTag();
        c.putString("color", colour);
        if (translate != null) {
            c.putString("translate", translate);
            return c;
        }
        c.putString("text", literal == null ? "" : literal);
        if (extraKey != null) {
            CompoundTag extra = new CompoundTag();
            extra.putString("translate", extraKey);
            ListTag list = new ListTag();
            list.add(extra);
            c.put("extra", list);
        }
        return c;
    }

    private static boolean insert(ServerLevel level, BlockPos lower, GameDef def) {
        if (!(level.getBlockEntity(lower) instanceof ArcadeCabinetBlockEntity cabinet)) {
            return false;
        }
        try {
            ItemStack stack = CartridgeItem.create(def);
            return cabinet.showcaseInsert(stack);
        } catch (ValidationError tooLarge) {
            return false;
        }
    }

    /** Blocks with contents in the footprint that no gallery placed (the build would erase them). */
    private static int occupied(ServerLevel level, GalleryStore store, String dimension, BlockPos origin) {
        int n = 0;
        for (int dx = GalleryLayout.MIN_DX; dx <= GalleryLayout.MAX_DX; dx++) {
            for (int dz = GalleryLayout.MIN_DZ; dz <= GalleryLayout.MAX_DZ; dz++) {
                for (int dy = GalleryLayout.STRIP_DY; dy <= GalleryLayout.CEILING_DY; dy++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be != null && !store.placedAt(dimension, pos.asLong())) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    // --- clear ------------------------------------------------------------------------------------------

    static int clear(CommandSourceStack source) {
        ServerPlayer player = requireCreativeAlone(source);
        if (player == null) {
            return 0;
        }
        ServerLevel level = player.level();
        GalleryStore store = GalleryStore.get(source.getServer());
        BlockPos at = player.blockPosition();
        Optional<GalleryStore.Gallery> gallery = store.at(level.dimension().identifier().toString(), at.getX(),
                at.getY(), at.getZ());
        if (gallery.isEmpty()) {
            source.sendFailure(Component.translatable("neroarcade.command.gallery.nothing_here"));
            return 0;
        }
        int[] result = clear(level, store, gallery.get());
        source.sendSuccess(() -> Component.translatable("neroarcade.command.gallery.cleared", result[0], result[1]),
                true);
        return Command.SINGLE_SUCCESS;
    }

    /** Puts back what {@code gallery} changed (newest first). @return {blocks restored, listings removed} */
    private static int[] clear(ServerLevel level, GalleryStore store, GalleryStore.Gallery gallery) {
        List<GalleryStore.Change> changes = gallery.changes();
        for (GalleryStore.Change change : changes) {
            if (level.getBlockEntity(BlockPos.of(change.pos())) instanceof ArcadeCabinetBlockEntity cabinet) {
                cabinet.showcaseDiscard();
            }
        }
        int restored = 0;
        for (int i = changes.size() - 1; i >= 0; i--) {
            GalleryStore.Change change = changes.get(i);
            BlockPos pos = BlockPos.of(change.pos());
            if (level.getBlockState(pos).is(change.placed().getBlock())) {
                level.setBlock(pos, change.before(), FLAGS);
                restored++;
            }
        }
        int unlisted = gallery.exchangeSeeded()
                ? ExchangeStore.get(level.getServer()).unpublishBy(level.getServer(), GALLERY_AUTHOR) : 0;
        store.remove(gallery);
        sweep(level, BlockPos.of(gallery.origin()));
        return new int[] {restored, unlisted};
    }

    // --- shared -------------------------------------------------------------------------------------------

    /** A creative player with nobody else near, or null after telling the source why not. */
    private static @Nullable ServerPlayer requireCreativeAlone(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("neroarcade.command.gallery.players_only"));
            return null;
        }
        if (!player.getAbilities().instabuild) {
            source.sendFailure(Component.translatable("neroarcade.command.gallery.creative_only"));
            return null;
        }
        int others = 0;
        for (ServerPlayer other : player.level().players()) {
            if (other != player && other.distanceToSqr(player) <= (double) OTHERS_RANGE * OTHERS_RANGE) {
                others++;
            }
        }
        if (others > 0) {
            source.sendFailure(Component.translatable("neroarcade.command.gallery.others_nearby", OTHERS_RANGE));
            return null;
        }
        return player;
    }

    private static void sweep(ServerLevel level, BlockPos origin) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(SWEEP_RANGE))) {
            item.discard();
        }
    }

    static Direction direction(Facing facing) {
        return switch (facing) {
            case NORTH -> Direction.NORTH;
            case SOUTH -> Direction.SOUTH;
            case WEST -> Direction.WEST;
            case EAST -> Direction.EAST;
        };
    }

    /** Places blocks relative to the origin and records each real change (position, before, placed). */
    private static final class Recorder {
        final ServerLevel level;
        final BlockPos origin;
        final List<GalleryStore.Change> changes = new ArrayList<>();

        Recorder(ServerLevel level, BlockPos origin) {
            this.level = level;
            this.origin = origin;
        }

        BlockPos at(Offset o) {
            return origin.offset(o.dx(), o.dy(), o.dz());
        }

        void place(Offset o, BlockState state) {
            BlockPos pos = at(o);
            BlockState before = level.getBlockState(pos);
            if (before == state) {
                return;
            }
            // the same position placed twice (the shell, then an exhibit): keep the first "before"
            BlockState original = before;
            for (int i = changes.size() - 1; i >= 0; i--) {
                if (changes.get(i).pos() == pos.asLong()) {
                    original = changes.remove(i).before();
                    break;
                }
            }
            level.setBlock(pos, state, FLAGS);
            changes.add(new GalleryStore.Change(pos.asLong(), original, state));
        }
    }
}
