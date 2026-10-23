package za.co.neroland.neroarcade.command;

import java.util.Optional;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.bench.CreditResolver;
import za.co.neroland.neroarcade.block.ArcadeCabinetBlock;
import za.co.neroland.neroarcade.block.TwoHighBlock;
import za.co.neroland.neroarcade.board.Boards;
import za.co.neroland.neroarcade.board.LeaderboardStore;
import za.co.neroland.neroarcade.cabinet.ArcadeCabinetBlockEntity;
import za.co.neroland.neroarcade.cabinet.BusRole;
import za.co.neroland.neroarcade.cabinet.DropReason;
import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.item.CartridgeItem;
import za.co.neroland.neroarcade.item.StoredCartridge;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.network.BoardPagePayload;
import za.co.neroland.neroarcade.privacy.CreditOptIn;
import za.co.neroland.neroarcade.server.ArcadeServer;

/**
 * The {@code /neroarcade} command tree, registered identically from all three loaders.
 *
 * <pre>
 * /neroarcade cartridge inspect          (any player) the cartridge in your main hand, in words
 * /neroarcade top                        (any player) the leaderboard of the cartridge in your hand, else of the
 *                                        cabinet you are looking at
 * /neroarcade privacy credit [on|off]    (any player) show your name on boards and saved games, or not (off by default)
 * /neroarcade admin unseat &lt;player&gt;      (LEVEL_GAMEMASTERS) releases that player's seat wherever it is (any bus)
 * </pre>
 *
 * <p>Output never contains a UUID: the author line is the opt-in credit or "Anonymous builder", and a leaderboard
 * row shows a name only for a player who opted in (else "Anonymous").
 */
public final class ArcadeCommands {

    private ArcadeCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(NeroArcadeCommon.MOD_ID)
                .then(Commands.literal("cartridge")
                        .then(Commands.literal("inspect")
                                .executes(ctx -> inspect(ctx.getSource()))))
                .then(Commands.literal("top")
                        .executes(ctx -> top(ctx.getSource())))
                .then(Commands.literal("privacy")
                        .then(Commands.literal("credit")
                                .executes(ctx -> creditStatus(ctx.getSource()))
                                .then(Commands.literal("on").executes(ctx -> credit(ctx.getSource(), true)))
                                .then(Commands.literal("off").executes(ctx -> credit(ctx.getSource(), false)))))
                .then(Commands.literal("admin")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("unseat")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> unseat(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player")))))));
    }

    private static int inspect(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        StoredCartridge stored = CartridgeItem.storedOf(stack);
        if (stored == null) {
            source.sendFailure(Component.translatable("neroarcade.command.inspect.not_cartridge"));
            return 0;
        }
        GameDef def;
        try {
            def = stored.decode();
        } catch (ValidationError refused) {
            source.sendSuccess(() -> Component.translatable("neroarcade.command.inspect.unreadable",
                    stored.rawTitle(), Component.translatable(refused.langKey, refused.args.toArray()))
                    .withStyle(ChatFormatting.RED), false);
            return 0;
        }
        GameDef shown = def;
        source.sendSuccess(() -> Component.translatable("neroarcade.command.inspect.header", shown.title(), shown.id(),
                shown.revision()), false);
        source.sendSuccess(() -> shown.authorCredit()
                .<Component>map(name -> Component.translatable("item.neroarcade.cartridge.by", name))
                .orElseGet(() -> Component.translatable("item.neroarcade.cartridge.anonymous"))
                .copy().withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.translatable("neroarcade.command.inspect.lineage", shown.lineageRoot(),
                shown.lineage().size()).withStyle(ChatFormatting.GRAY), false);
        for (ModuleType type : ModuleType.values()) {
            source.sendSuccess(() -> CartridgeItem.slotLine(shown, type), false);
        }
        return 1;
    }

    /** Reach for {@code /neroarcade top}'s look-at test, in blocks. */
    private static final double LOOK_RANGE = 6.0;

    private static int top(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Optional<GameDef> def = Optional.empty();
        StoredCartridge held = CartridgeItem.storedOf(player.getMainHandItem());
        if (held != null) {
            def = held.tryDecode();
        }
        if (def.isEmpty()) {
            def = lookedAtGame(player);
        }
        if (def.isEmpty()) {
            source.sendFailure(Component.translatable("neroarcade.command.top.nothing"));
            return 0;
        }
        String lineage = def.get().lineageRoot();
        String title = def.get().title();
        BoardPagePayload page = Boards.page(lineage, LeaderboardStore.get(source.getServer()).board(lineage),
                player.getUUID(), BoardPagePayload.MAX_ROWS);
        source.sendSuccess(() -> Component.translatable("neroarcade.command.top.header", title), false);
        if (page.rows().isEmpty()) {
            source.sendSuccess(() -> Component.translatable("neroarcade.command.top.empty")
                    .withStyle(ChatFormatting.GRAY), false);
        }
        for (BoardPagePayload.Row row : page.rows()) {
            Component name = row.name().isEmpty() ? Component.translatable("neroarcade.board.anonymous")
                    : Component.literal(row.name());
            source.sendSuccess(() -> Component.translatable("neroarcade.command.top.row", row.rank(), name,
                    row.score()), false);
        }
        if (page.myBest() >= 0) {
            source.sendSuccess(() -> Component.translatable("neroarcade.command.top.my_best", page.myBest())
                    .withStyle(ChatFormatting.AQUA), false);
        }
        return 1;
    }

    /** The game of the cabinet the player looks at (a node shows its host's), by a server-side ray. */
    private static Optional<GameDef> lookedAtGame(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(LOOK_RANGE));
        BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return Optional.empty();
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = player.level().getBlockState(pos);
        if (!(state.getBlock() instanceof ArcadeCabinetBlock)) {
            return Optional.empty();
        }
        if (!(TwoHighBlock.controller(player.level(), pos, state) instanceof ArcadeCabinetBlockEntity cabinet)) {
            return Optional.empty();
        }
        ArcadeCabinetBlockEntity source = cabinet.role() == BusRole.NODE ? cabinet.hostCabinet() : cabinet;
        ResolvedGame game = source == null ? null : source.game();
        return game == null ? Optional.empty() : Optional.of(game.def());
    }

    private static int creditStatus(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean on = CreditOptIn.isOn(source.getServer(), player.getUUID());
        source.sendSuccess(() -> Component.translatable(on ? "neroarcade.command.credit.status_on"
                : "neroarcade.command.credit.status_off"), false);
        return 1;
    }

    private static int credit(CommandSourceStack source, boolean on) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CreditOptIn.set(source.getServer(), player.getUUID(), on);
        if (on) {
            String name = CreditResolver.trim(player.getGameProfile().name()).orElse("");
            source.sendSuccess(() -> Component.translatable("neroarcade.command.credit.on", name), false);
        } else {
            source.sendSuccess(() -> Component.translatable("neroarcade.command.credit.off"), false);
        }
        return 1;
    }

    private static int unseat(CommandSourceStack source, ServerPlayer target) {
        boolean dropped = ArcadeServer.unseat(source.getServer(), target.getUUID(), DropReason.ADMIN);
        if (!dropped) {
            source.sendFailure(Component.translatable("neroarcade.command.unseat.not_seated", target.getDisplayName()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("neroarcade.command.unseat.done", target.getDisplayName()),
                true);
        return 1;
    }
}
