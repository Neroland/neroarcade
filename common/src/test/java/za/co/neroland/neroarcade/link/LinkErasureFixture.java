package za.co.neroland.neroarcade.link;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import za.co.neroland.neroarcade.board.RecordOutcome;
import za.co.neroland.neroarcade.cabinet.BusMatchResult;
import za.co.neroland.neroarcade.data.NeroArcadeData;

/**
 * Seeds the link module's transient per-player state for the erasure conformance tests in other packages: a match
 * that leaves an event for the {@code bystander} (they won) and a "record beaten" window for the {@code subject}
 * (displaced from rank 1), plus the {@code "link"} eraser over that pipeline.
 */
public final class LinkErasureFixture {

    /** The eraser to add to the composite and the probe (pipeline, request dedup and rate limiter). */
    public record Seeded(NeroArcadeData.StoreEraser eraser, Predicate<UUID> holds) {
    }

    private LinkErasureFixture() {
    }

    public static Seeded seed(UUID subject, UUID bystander) {
        FakeLinkContext ctx = new FakeLinkContext();
        ArcadeEvents.Pipeline events = LinkFixtures.pipeline(ctx);
        String root = LinkFixtures.id(1);
        events.onMatchEnd(new ArcadeEvents.MatchEndInfo(root, root, "Neon Dodge", "minecraft:overworld", 1,
                List.of(new BusMatchResult.SeatResult(bystander, 0, 99, 1, false)), "WIN",
                new RecordOutcome(1, 1, 0, 0, List.of(new RecordOutcome.Displaced(subject, 2))), false));
        return new Seeded(NeroArcadeLinkModule.eraser(events),
                u -> events.holdsAnyFor(u) || NeroArcadeLinkModule.holdsAnyFor(u));
    }
}
