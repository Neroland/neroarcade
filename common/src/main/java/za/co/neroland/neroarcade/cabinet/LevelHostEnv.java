package za.co.neroland.neroarcade.cabinet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.neroarcade.platform.Services;
import za.co.neroland.neroarcade.runtime.Budgets;

/**
 * The live {@link HostEnv}: a server level, the host block and its power check. On a Link Bus the viewer group is
 * every player within {@code screenRenderRange} of <b>any</b> node or marquee on the bus (003 §1 step 3), so the
 * host's one snapshot stream reaches every screen that mirrors it.
 */
public final class LevelHostEnv implements HostEnv {

    private final ServerLevel level;
    private final Vec3 centre;
    private final BooleanSupplier powered;
    private final Supplier<List<BlockPos>> busScreens;

    public LevelHostEnv(ServerLevel level, BlockPos pos, BooleanSupplier powered) {
        this(level, pos, powered, List::of);
    }

    /** @param busScreens the other nodes and marquees on the host's bus (re-read every call) */
    public LevelHostEnv(ServerLevel level, BlockPos pos, BooleanSupplier powered, Supplier<List<BlockPos>> busScreens) {
        this.level = level;
        this.centre = Vec3.atCenterOf(pos);
        this.powered = powered;
        this.busScreens = busScreens;
    }

    @Override
    public long gameTime() {
        return level.getGameTime();
    }

    @Override
    public long newSeed() {
        return level.getRandom().nextLong();
    }

    @Override
    public Collection<UUID> viewers() {
        List<BlockPos> others = busScreens.get();
        if (others.isEmpty()) {
            return viewers(level, centre);
        }
        Set<UUID> out = new LinkedHashSet<>(viewers(level, centre));
        for (BlockPos screen : others) {
            out.addAll(viewers(level, Vec3.atCenterOf(screen)));
        }
        return out;
    }

    /** UUIDs of the players in {@code level} within {@code screenRenderRange} of {@code centre}. */
    public static List<UUID> viewers(ServerLevel level, Vec3 centre) {
        double range = Budgets.screenRenderRange();
        double rangeSq = range * range;
        List<UUID> out = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && player.distanceToSqr(centre) <= rangeSq) {
                out.add(player.getUUID());
            }
        }
        return out;
    }

    @Override
    public void send(UUID player, CustomPacketPayload payload) {
        ServerPlayer target = level.getServer().getPlayerList().getPlayer(player);
        if (target != null) {
            Services.NETWORK.sendToPlayer(target, payload);
        }
    }

    @Override
    public void message(UUID player, String langKey) {
        ServerPlayer target = level.getServer().getPlayerList().getPlayer(player);
        if (target != null) {
            target.sendSystemMessage(net.minecraft.network.chat.Component.translatable(langKey));
        }
    }

    @Override
    public boolean powered() {
        return powered.getAsBoolean();
    }

    /** The holder at {@code at} (loaded, this level) drops its seat; nothing happens for an unloaded block. */
    @Override
    public void unseat(BlockPos at, DropReason why) {
        if (at != null && level.isLoaded(at) && level.getBlockEntity(at) instanceof SeatHolder holder) {
            holder.dropSeat(why);
        }
    }
}
