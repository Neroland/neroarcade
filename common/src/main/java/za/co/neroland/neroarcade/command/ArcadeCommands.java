package za.co.neroland.neroarcade.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.cabinet.DropReason;
import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.item.CartridgeItem;
import za.co.neroland.neroarcade.item.StoredCartridge;
import za.co.neroland.neroarcade.module.ModuleType;
import za.co.neroland.neroarcade.server.ArcadeServer;

/**
 * The {@code /neroarcade} command tree, registered identically from all three loaders.
 *
 * <pre>
 * /neroarcade cartridge inspect          (any player) the cartridge in your main hand, in words
 * /neroarcade admin unseat &lt;player&gt;      (LEVEL_GAMEMASTERS) releases that player's seat wherever it is
 * </pre>
 *
 * <p>Output never contains a UUID: the author line is the opt-in credit or "Anonymous builder".
 */
public final class ArcadeCommands {

    private ArcadeCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(NeroArcadeCommon.MOD_ID)
                .then(Commands.literal("cartridge")
                        .then(Commands.literal("inspect")
                                .executes(ctx -> inspect(ctx.getSource()))))
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
