package za.co.neroland.neroarcade.bus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.block.ArcadeBlockProperties;
import za.co.neroland.neroarcade.block.LinkCableBlock;
import za.co.neroland.neroarcade.board.Boards;
import za.co.neroland.neroarcade.board.LeaderboardStore;
import za.co.neroland.neroarcade.cabinet.ArcadeCabinetBlockEntity;
import za.co.neroland.neroarcade.cabinet.BusPhase;
import za.co.neroland.neroarcade.cabinet.BusRole;
import za.co.neroland.neroarcade.cabinet.LevelHostEnv;
import za.co.neroland.neroarcade.cabinet.MatchHost;
import za.co.neroland.neroarcade.config.NeroArcadeConfig;
import za.co.neroland.neroarcade.game.ResolvedGame;
import za.co.neroland.neroarcade.platform.Services;
import za.co.neroland.neroarcade.registry.ModBlockEntities;

/**
 * The Link Bus hub (003 §3): owns its bus's discovery. Runtime only — nothing is saved; a hub that loads simply
 * discovers again, and a cabinet's cached bus id is just a hint.
 *
 * <p>Server tick: when a block on or beside the bus changed ({@link BusRegistry#touch}), and every 20 ticks
 * anyway, it walks the cables ({@link BusTopology#discover}), elects the host ({@link BusElection}) and assigns
 * every cabinet and marquee its role; cabinets that fell off the bus go solo. Every 20 ticks it also mirrors the
 * host's phase onto the cables' {@code active} pulse and sends the host cartridge's leaderboard to players near a
 * marquee when it changed (≤ 1 page per second per viewer). {@code multiplayer.enabled=false}: the bus holds
 * nothing.
 */
public class LinkBusBlockEntity extends BlockEntity {

    private @Nullable BusTopology topology;
    private final List<BlockPos> fullCabinets = new ArrayList<>();
    private boolean active;
    private final Map<UUID, Integer> sentBoards = new HashMap<>();
    private String sentBoard = "";

    public LinkBusBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LINK_BUS.get(), pos, state);
    }

    public @Nullable BusTopology topology() {
        return topology;
    }

    private String dimension(ServerLevel serverLevel) {
        return serverLevel.dimension().identifier().toString();
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        long time = serverLevel.getGameTime();
        String dim = dimension(serverLevel);
        boolean dirty = topology == null || BusRegistry.SERVER.consumeDirty(dim, worldPosition.asLong());
        if (dirty || time % 20 == 0) {
            rediscover(serverLevel);
        }
        if (time % 20 == 0) {
            pulseCables(serverLevel);
            sendBoards(serverLevel);
        }
    }

    /** Walks, elects and assigns (003 §3.2). */
    void rediscover(ServerLevel serverLevel) {
        String dim = dimension(serverLevel);
        long busId = worldPosition.asLong();
        BusTopology previous = topology;
        BusTopology next = BusTopology.discoverIf(NeroArcadeConfig.MULTIPLAYER_ENABLED.get(), worldPosition,
                () -> BusTopology.discover(new LevelBusWorld(serverLevel), worldPosition,
                        pos -> BusRegistry.SERVER.claimedElsewhere(dim, pos, busId)));
        if (previous != null) {
            next = next.keeping(previous.cabinets()); // a newcomer never pushes a member off the bus
        }
        topology = next;
        BusRegistry.SERVER.put(dim, next);
        if (previous != null) {
            for (BlockPos gone : previous.cabinets()) {
                if (!next.cabinets().contains(gone) && cabinetAt(gone) instanceof ArcadeCabinetBlockEntity c) {
                    c.leaveBus(busId);
                }
            }
            for (BlockPos gone : previous.marquees()) {
                if (!next.marquees().contains(gone) && marqueeAt(gone) instanceof MarqueeScreenBlockEntity m) {
                    m.leaveBus(busId);
                }
            }
        }
        for (BlockPos gone : fullCabinets) {
            if (!next.overflow().contains(gone) && cabinetAt(gone) instanceof ArcadeCabinetBlockEntity c) {
                c.leaveBus(busId);
            }
        }
        fullCabinets.clear();
        assign(next);
    }

    /** Sticky host and seat indexes across rediscoveries (review 003b F1). Runtime only. */
    private final BusAssignment assignment = new BusAssignment();

    private void assign(BusTopology t) {
        long busId = t.busId();
        List<ArcadeCabinetBlockEntity> cabinets = new ArrayList<>();
        List<BusAssignment.Member> members = new ArrayList<>();
        for (BlockPos pos : t.cabinets()) {
            ArcadeCabinetBlockEntity c = cabinetAt(pos);
            if (c == null) {
                continue;
            }
            cabinets.add(c);
            members.add(new BusAssignment.Member(pos.asLong(), c.hasCartridge(), c.game() != null, c.insertedAt(),
                    c.ownMatchRunning(), c.heldSeat() != null));
        }
        BusElection.Part[] parts = assignment.assign(members);
        BlockPos hostPos = null;
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] != null && parts[i].role() == BusElection.Role.HOST) {
                hostPos = cabinets.get(i).getBlockPos();
            }
        }
        int slots = BusAssignment.slots(parts);
        int present = BusAssignment.present(parts);
        List<BlockPos> screens = new ArrayList<>();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] != null && parts[i].role() != BusElection.Role.HOST) {
                screens.add(cabinets.get(i).getBlockPos());
            }
        }
        screens.addAll(t.marquees());
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] == null) {
                continue; // a linked stranger's own match is running: assigned once it ends
            }
            cabinets.get(i).assignBus(busId, BusRole.of(parts[i].role()), parts[i].seat(), hostPos, slots, present,
                    screens);
        }
        for (BlockPos pos : t.overflow()) {
            ArcadeCabinetBlockEntity c = cabinetAt(pos);
            if (c != null && (c.role() == BusRole.SOLO || c.busId() == busId) && !c.ownMatchRunning()) {
                c.assignBus(busId, BusRole.FULL, -1, null, 0, 0, List.of());
                fullCabinets.add(pos);
            }
        }
        for (BlockPos pos : t.marquees()) {
            if (marqueeAt(pos) instanceof MarqueeScreenBlockEntity m) {
                m.assignBus(busId, hostPos);
            }
        }
    }

    /** The host cabinet now, or null. */
    public @Nullable ArcadeCabinetBlockEntity host() {
        BusTopology t = topology;
        if (t == null) {
            return null;
        }
        for (BlockPos pos : t.cabinets()) {
            ArcadeCabinetBlockEntity c = cabinetAt(pos);
            if (c != null && c.role() == BusRole.HOST) {
                return c;
            }
        }
        return null;
    }

    /** {@code active} on every cable while the host counts down or plays (≤ 1 Hz, only on change). */
    private void pulseCables(ServerLevel serverLevel) {
        BusTopology t = topology;
        if (t == null) {
            return;
        }
        ArcadeCabinetBlockEntity h = host();
        MatchHost match = h == null ? null : h.matchHost();
        boolean want = match != null && match.phase().running();
        if (want == active && serverLevel.getGameTime() % 100 != 0) {
            return;
        }
        active = want;
        for (BlockPos cable : t.cables()) {
            if (!serverLevel.isLoaded(cable)) {
                continue;
            }
            BlockState state = serverLevel.getBlockState(cable);
            if (state.getBlock() instanceof LinkCableBlock && state.getValue(ArcadeBlockProperties.ACTIVE) != want) {
                serverLevel.setBlock(cable, state.setValue(ArcadeBlockProperties.ACTIVE, want), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** The host cartridge's top ten to players near a marquee, when it changed for them (003 §5). */
    private void sendBoards(ServerLevel serverLevel) {
        BusTopology t = topology;
        ArcadeCabinetBlockEntity h = host();
        ResolvedGame game = h == null ? null : h.game();
        if (t == null || h == null || t.marquees().isEmpty() || game == null) {
            sentBoards.clear();
            return;
        }
        BusPhase phase = h.shownPhase();
        if (phase != null && phase != BusPhase.IDLE && phase != BusPhase.LOBBY) {
            return; // the marquee mirrors the match; the board waits for the idle loop
        }
        String boardKey = game.def().id(); // boards are per cartridge id (D-owner-1)
        if (!boardKey.equals(sentBoard)) {
            sentBoard = boardKey;
            sentBoards.clear();
        }
        Set<UUID> viewers = new HashSet<>();
        for (BlockPos marquee : t.marquees()) {
            viewers.addAll(LevelHostEnv.viewers(serverLevel, Vec3.atCenterOf(marquee)));
        }
        sentBoards.keySet().retainAll(viewers);
        if (viewers.isEmpty()) {
            return;
        }
        try {
            LeaderboardStore store = LeaderboardStore.get(serverLevel.getServer());
            int version = store.version();
            for (UUID viewer : viewers) {
                Integer sent = sentBoards.get(viewer);
                if (sent != null && sent == version) {
                    continue;
                }
                ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(viewer);
                if (player != null) {
                    Services.NETWORK.sendToPlayer(player, Boards.page(boardKey, store.board(boardKey), viewer, 10));
                    sentBoards.put(viewer, version);
                }
            }
        } catch (RuntimeException e) {
            NeroArcadeCommon.LOGGER.debug("[NeroArcade] board page skipped ({})", e.getClass().getSimpleName());
        }
    }

    /** Hub broken: every cabinet goes solo, every marquee idle (003 §3.5). */
    private void dissolve(ServerLevel serverLevel) {
        long busId = worldPosition.asLong();
        BusTopology t = topology;
        BusRegistry.SERVER.remove(dimension(serverLevel), busId);
        topology = null;
        assignment.clear();
        if (t == null) {
            return;
        }
        for (BlockPos pos : t.cabinets()) {
            ArcadeCabinetBlockEntity c = cabinetAt(pos);
            if (c != null) {
                c.leaveBus(busId);
            }
        }
        for (BlockPos pos : fullCabinets) {
            ArcadeCabinetBlockEntity c = cabinetAt(pos);
            if (c != null) {
                c.leaveBus(busId);
            }
        }
        fullCabinets.clear();
        for (BlockPos pos : t.marquees()) {
            if (marqueeAt(pos) instanceof MarqueeScreenBlockEntity m) {
                m.leaveBus(busId);
            }
        }
        for (BlockPos cable : t.cables()) {
            if (serverLevel.isLoaded(cable)) {
                BlockState state = serverLevel.getBlockState(cable);
                if (state.getBlock() instanceof LinkCableBlock && state.getValue(ArcadeBlockProperties.ACTIVE)) {
                    serverLevel.setBlock(cable, state.setValue(ArcadeBlockProperties.ACTIVE, false),
                            Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private @Nullable ArcadeCabinetBlockEntity cabinetAt(BlockPos pos) {
        if (level == null || !level.isLoaded(pos)) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof ArcadeCabinetBlockEntity c ? c : null;
    }

    private @Nullable MarqueeScreenBlockEntity marqueeAt(BlockPos pos) {
        if (level == null || !level.isLoaded(pos)) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof MarqueeScreenBlockEntity m ? m : null;
    }

    /** Real removal only: dissolve the bus with world access. */
    //? if >=1.21.5 {
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
    //?} else {
    /*// Called from the block's onRemove(..) before 1.21.5 (no such BlockEntity hook there).
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
    *///?}
        if (level instanceof ServerLevel serverLevel) {
            dissolve(serverLevel);
        }
    }

    /** Removal or unload: forget the bus (memory only); members notice on their next bus check. */
    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel) {
            BusRegistry.SERVER.remove(dimension(serverLevel), worldPosition.asLong());
        }
        topology = null;
        super.setRemoved();
    }

    /** Debug readout for {@code /neroarcade} and tests; never a player. */
    public Optional<String> describe() {
        BusTopology t = topology;
        return t == null ? Optional.empty()
                : Optional.of(t.cabinets().size() + " cabinets, " + t.marquees().size() + " marquees, "
                        + t.cables().size() + " cables");
    }
}
