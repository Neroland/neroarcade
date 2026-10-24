package za.co.neroland.neroarcade.progression;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.event.CoreEvents;
import za.co.neroland.nerolandcore.progression.GateScope;
import za.co.neroland.nerolandcore.progression.ProgressionGates;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.item.ItemGiving;
import za.co.neroland.neroarcade.registry.ModItems;
import za.co.neroland.neroarcade.server.ServerHooks;

/**
 * Listener for the <b>hook gate</b> a quest mod opens when a player completes one of NeroArcade's own quests
 * ({@code data/neroarcade/neroland_gates/hook/*.json}, named by the pack's {@code mod_hook} rewards). NeroArcade never
 * imports the quest mod: it only hears Core's progression events (the family pattern).
 *
 * <p>{@code neroarcade:hook/blank_cartridges} owes the player {@link #BLANKS} Blank Cartridges. Delivered on the hook
 * if they are online, else on their next login (a once-a-second sweep of the online list); the gate is then closed to
 * re-arm it. Delivery is bounded and idempotent: one bundle per opening.
 *
 * <p>Privacy: the gate change's target (a UUID string) is used only to find the online player and close the gate,
 * never logged. The online set used for login detection is process-local and dropped when the server changes.
 */
public final class QuestHooks {

    public static final Identifier BLANK_CARTRIDGES =
            Identifier.fromNamespaceAndPath(NeroArcadeCommon.MOD_ID, "hook/blank_cartridges");

    /** Blank Cartridges one hook delivers. */
    public static final int BLANKS = 2;

    private static final int JOIN_SWEEP_INTERVAL = 20;
    private static final Set<UUID> ONLINE = new HashSet<>();
    private static volatile @Nullable MinecraftServer server;
    private static @Nullable MinecraftServer sweptServer;
    private static boolean registered;

    private QuestHooks() {
    }

    /** Common init step 7, once. */
    public static synchronized void init() {
        if (registered) {
            return;
        }
        registered = true;
        CoreEvents.onProgression(change -> {
            if (!change.open() || change.scope() != GateScope.PLAYER || !BLANK_CARTRIDGES.equals(change.gate())) {
                return;
            }
            MinecraftServer running = server;
            if (running == null) {
                return; // the gate stays open; the login sweep delivers it
            }
            UUID id;
            try {
                id = UUID.fromString(change.target());
            } catch (IllegalArgumentException e) {
                return;
            }
            ServerPlayer player = running.getPlayerList().getPlayer(id);
            if (player != null) {
                deliver(player);
                ProgressionGates.close(running, id, BLANK_CARTRIDGES);
            }
        });
        ServerHooks.onTick(QuestHooks::tick);
        ServerHooks.onStopping(s -> {
            server = null;
            sweptServer = null;
            ONLINE.clear();
        });
    }

    static void tick(MinecraftServer running) {
        server = running;
        if (running.getTickCount() % JOIN_SWEEP_INTERVAL != 0) {
            return;
        }
        if (sweptServer != running) {
            ONLINE.clear();
            sweptServer = running;
        }
        Set<UUID> now = new HashSet<>();
        for (ServerPlayer player : running.getPlayerList().getPlayers()) {
            now.add(player.getUUID());
            if (ONLINE.add(player.getUUID()) && ProgressionGates.isOpen(player, BLANK_CARTRIDGES)) {
                deliver(player);
                ProgressionGates.close(player, BLANK_CARTRIDGES);
            }
        }
        ONLINE.retainAll(now);
    }

    private static void deliver(ServerPlayer player) {
        ItemGiving.give(player, new ItemStack(ModItems.BLANK_CARTRIDGE.get(), BLANKS));
        player.sendSystemMessage(Component.translatable("neroarcade.quest.hook.blank_cartridges", BLANKS));
    }
}
