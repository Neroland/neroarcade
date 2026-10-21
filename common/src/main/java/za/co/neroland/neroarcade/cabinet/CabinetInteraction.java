package za.co.neroland.neroarcade.cabinet;

import java.util.Optional;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.item.ItemGiving;
import za.co.neroland.neroarcade.registry.ModItems;
import za.co.neroland.neroarcade.registry.ModSounds;

/**
 * What using a cabinet does (003 §2.2, §3.5), server side. Every refusal is an action-bar sentence and a deny
 * sound; nothing here throws.
 *
 * <ul>
 *   <li><b>Cartridge in hand</b> → insert it (one item) if the slot is empty.</li>
 *   <li><b>Sneak, empty hand</b> → eject: refused while a match counts down or plays; refused to anyone but the
 *       seated player (or an op) while someone is seated.</li>
 *   <li><b>Empty hand</b> → take the seat. Checks, in order: a cartridge, readable here, power, one seat per
 *       player server-wide, the seat free, no match running, an awake slot. Solo starts on use.</li>
 * </ul>
 */
public final class CabinetInteraction {

    private CabinetInteraction() {
    }

    /** Written or blank cartridges both go through {@link #insert} (a blank is refused with a sentence). */
    public static boolean isCartridgeLike(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(ModItems.CARTRIDGE.get()) || stack.is(ModItems.BLANK_CARTRIDGE.get()));
    }

    public static InteractionResult insert(ArcadeCabinetBlockEntity cabinet, ServerPlayer player, ItemStack stack) {
        ServerLevel level = (ServerLevel) cabinet.getLevel();
        if (stack.is(ModItems.BLANK_CARTRIDGE.get())) {
            return deny(cabinet, player, "neroarcade.cabinet.msg.blank");
        }
        if (cabinet.hasCartridge()) {
            return deny(cabinet, player, "neroarcade.cabinet.msg.occupied");
        }
        cabinet.insert(stack);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        cabinet.play(level, ModSounds.CABINET_INSERT.get());
        Optional<ValidationError> error = cabinet.cartridgeError();
        if (error.isPresent()) {
            player.sendOverlayMessage(Component.translatable("neroarcade.cabinet.msg.unreadable",
                    Component.translatable(error.get().langKey, error.get().args.toArray())));
        } else {
            player.sendOverlayMessage(Component.translatable("neroarcade.cabinet.msg.inserted"));
        }
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult eject(ArcadeCabinetBlockEntity cabinet, ServerPlayer player) {
        if (!cabinet.hasCartridge()) {
            return deny(cabinet, player, "neroarcade.cabinet.msg.no_cartridge");
        }
        MatchHost host = cabinet.matchHost();
        if (host != null && host.phase().running()) {
            return deny(cabinet, player, "neroarcade.cabinet.msg.eject_in_progress");
        }
        Seat seat = cabinet.heldSeat();
        boolean op = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(player.createCommandSourceStack());
        if (seat != null && !seat.playerUuid.equals(player.getUUID()) && !op) {
            return deny(cabinet, player, "neroarcade.cabinet.msg.eject_refused");
        }
        ItemStack out = cabinet.takeCartridge();
        ItemGiving.give(player, out);
        player.sendOverlayMessage(Component.translatable("neroarcade.cabinet.msg.ejected"));
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult takeSeat(ArcadeCabinetBlockEntity cabinet, ServerPlayer player) {
        ServerLevel level = (ServerLevel) cabinet.getLevel();
        Seat held = cabinet.heldSeat();
        if (held != null && held.playerUuid.equals(player.getUUID())) {
            return InteractionResult.SUCCESS; // already seated here: the HUD has them
        }
        if (!cabinet.hasCartridge()) {
            return deny(cabinet, player, "neroarcade.cabinet.msg.no_cartridge");
        }
        Optional<ValidationError> error = cabinet.cartridgeError();
        if (error.isPresent()) {
            return deny(cabinet, player, "neroarcade.cabinet.msg.unreadable",
                    Component.translatable(error.get().langKey, error.get().args.toArray()));
        }
        if (!ArcadeCabinetBlockEntity.powerAvailable()) {
            return deny(cabinet, player, "neroarcade.cabinet.msg.no_power");
        }
        Optional<SeatRegistry.SeatKey> elsewhere = SeatRegistry.SERVER.seatOf(player.getUUID());
        if (elsewhere.isPresent() && !elsewhere.get().equals(cabinet.seatKey(level))) {
            net.minecraft.core.BlockPos at = net.minecraft.core.BlockPos.of(elsewhere.get().pos());
            return deny(cabinet, player, "neroarcade.cabinet.msg.already_seated", at.getX(), at.getY(), at.getZ());
        }
        return switch (cabinet.seat(player)) {
            case SEATED -> InteractionResult.SUCCESS;
            case QUEUED -> deny(cabinet, player, "neroarcade.cabinet.status.busy", cabinet.queuePosition());
            case TAKEN -> deny(cabinet, player, "neroarcade.cabinet.msg.taken");
            case IN_PROGRESS -> deny(cabinet, player, "neroarcade.cabinet.msg.in_progress");
            case NOT_READY -> deny(cabinet, player, "neroarcade.cabinet.msg.no_cartridge");
        };
    }

    private static InteractionResult deny(ArcadeCabinetBlockEntity cabinet, ServerPlayer player, String key,
            Object... args) {
        player.sendOverlayMessage(Component.translatable(key, args));
        if (cabinet.getLevel() instanceof ServerLevel level) {
            cabinet.play(level, ModSounds.CABINET_DENY.get());
        }
        return InteractionResult.CONSUME;
    }
}
