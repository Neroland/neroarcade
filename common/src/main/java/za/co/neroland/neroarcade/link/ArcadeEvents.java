package za.co.neroland.neroarcade.link;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.link.LinkAlert;
import za.co.neroland.nerolandcore.link.LinkEvent;

import za.co.neroland.neroarcade.board.RecordOutcome;
import za.co.neroland.neroarcade.cabinet.BusMatchResult;
import za.co.neroland.neroarcade.exchange.Listing;
import za.co.neroland.neroarcade.privacy.Tombstones;

/**
 * NeroArcade's live events for the companion app (007 §4), topic {@code neroarcade.events}, every one
 * {@link LinkEvent#forPlayer player-scoped} (never a broadcast), every payload discriminated by {@code type}:
 *
 * <ul>
 *   <li>{@code RECORD_BEATEN} — a player's top-10 row on a board lost ground. Payload {@code id} (the cartridge id,
 *       which is the board), {@code lineageRoot} (informational), {@code title}, {@code newRank} only: never who beat
 *       it, never where. At most one notification per (player, board) per
 *       {@code link.push.recordCooldownMinutes}; the persisted alert {@code neroarcade.<root>.record} is raised with
 *       it. Falls inside the window are kept in the {@code events} section only and folded into one summary
 *       (notification + alert with the newest rank) when the window closes.</li>
 *   <li>{@code MATCH_RESULT} — each seat of a finished, non-abandoned match: score, rank, seats, outcome, new-best and
 *       entered-top flags and the cabinet label (D6). No notification: it is kept in the {@code events} section
 *       only.</li>
 *   <li>{@code CARTRIDGE_DOWNLOADED} — the daily digest at {@code link.digestHour}: one event per listing with
 *       downloads, at most five per author (the rest folded into the fifth as {@code more}).</li>
 * </ul>
 *
 * <p>Every payload carries {@code id} = the cartridge id (the board key; the notification window, the alert id
 * {@code neroarcade.<id>.record} and the deep link use it) and {@code lineageRoot} for information.
 *
 * <p><b>Why some events stay off the link bus.</b> NeroLink 1.x turns every player-scoped event without
 * {@code _notify} into a generic phone notification. An event that must not notify (a match result, a fall inside
 * the cooldown window) is therefore recorded in the {@code events} section (which the app reads, and which serves
 * the WebSocket on-subscribe snapshot) and not published on the bus.
 *
 * <p>All notification and alert text comes from {@link ArcadePushCopy}. {@code pushNotifications.enabled=false}
 * (the kill switch, security review M1) means <b>nothing is published on the link bus</b>: every event is recorded in
 * the {@code events} section only (the app reads it on open and every 30 s), because NeroLink 1.x would turn a
 * published event without {@code _notify} into a generic push. The persisted alert is still raised (an offline
 * player still needs it); Core's {@code LinkAlerts.raise} itself publishes an {@code alerts} "raised" event, which
 * a NeroLink 1.x bridge may announce as a generic "alerts update" naming nothing (a bridge honouring
 * {@code _push:false} plus its quiet shadow removes that too). The {@code events} ring never holds {@code _notify}
 * (L2): only the copy on the bus carries it. Nothing here is persisted except Core's
 * alert and the per-listing download count: the {@code events} ring, the windows and the title cache are memory
 * only and keyed by recipient (the title cache by lineage root); the {@code "link"} eraser forgets a player's.
 */
public final class ArcadeEvents {

    static final String TOPIC = "events";
    static final String RECORD_BEATEN = "RECORD_BEATEN";
    static final String MATCH_RESULT = "MATCH_RESULT";
    static final String CARTRIDGE_DOWNLOADED = "CARTRIDGE_DOWNLOADED";
    /** Events kept per player for the {@code events} section. */
    static final int RING_MAX = 20;
    static final int MAX_PLAYERS = 4096;
    static final int MAX_TITLES = 1024;
    /** Digest notifications per author per day. */
    static final int DIGEST_PER_AUTHOR = 5;

    private ArcadeEvents() {
    }

    /**
     * One finished match, reduced to what the events need (built by {@link LiveArcadeLinkContext}; no coordinate).
     *
     * @param cartridgeId the cartridge's id: the board key, the notification window key and the deep-link target
     * @param dimensionId the cabinet's dimension id (only its display name is ever sent)
     * @param cabinets    cabinets on the bus that can seat a player (1 = solo)
     * @param outcome     {@code WIN} / {@code LOSE} / {@code DRAW}
     */
    record MatchEndInfo(String cartridgeId, String lineageRoot, String title, String dimensionId, int cabinets,
            List<BusMatchResult.SeatResult> seats, String outcome, RecordOutcome boardOutcome, boolean hotSeat) {

        MatchEndInfo {
            seats = seats == null ? List.of() : List.copyOf(seats);
            boardOutcome = boardOutcome == null ? RecordOutcome.NONE : boardOutcome;
        }
    }

    /** The event pipeline over a context (tests build one over a fake). */
    static final class Pipeline {

        private final ArcadeLinkContext ctx;
        private final RecordCoalescer coalescer = new RecordCoalescer();
        /** Access-ordered: the least recently touched player is evicted first. */
        private final LinkedHashMap<UUID, Deque<JsonObject>> rings = new LinkedHashMap<>(16, 0.75f, true);
        /** cartridge id → (title, lineage root) seen at a finished match (public cartridge data, no player). */
        private final LinkedHashMap<String, String[]> titles = new LinkedHashMap<>(16, 0.75f, true);
        private long lastDigestDay = Long.MIN_VALUE;

        Pipeline(ArcadeLinkContext ctx) {
            this.ctx = ctx;
        }

        // --- match ends ---------------------------------------------------------------------------

        void onMatchEnd(MatchEndInfo end) {
            if (end == null || !ctx.settings().moduleEnabled()) {
                return;
            }
            String title = ArcadePushCopy.safeTitle(end.title());
            rememberTitle(end.cartridgeId(), end.lineageRoot(), title);
            if (end.hotSeat()) {
                return; // turns are not identities: nothing is credited or sent
            }
            long now = ctx.nowMillis();
            String label = ArcadePushCopy.cabinetLabel(end.dimensionId(), end.cabinets());
            int seats = end.seats().size();
            for (BusMatchResult.SeatResult seat : end.seats()) {
                JsonObject payload = base(MATCH_RESULT, now, end.cartridgeId(), end.lineageRoot(), title);
                payload.addProperty("label", label);
                payload.addProperty("score", seat.score());
                payload.addProperty("rank", seat.rank());
                payload.addProperty("seats", seats);
                payload.addProperty("outcome", end.outcome());
                payload.addProperty("newBest", (end.boardOutcome().newBestMask() & (1 << seat.seat())) != 0);
                payload.addProperty("enteredTop", (end.boardOutcome().enteredTopMask() & (1 << seat.seat())) != 0);
                remember(seat.uuid(), now, payload); // section only: no notification for one's own match
            }
            for (RecordOutcome.Displaced fall : end.boardOutcome().displaced()) {
                recordBeaten(fall.player(), end.cartridgeId(), end.lineageRoot(), title, fall.newRank(), now);
            }
        }

        private void recordBeaten(UUID player, String id, String root, String title, @Nullable Integer newRank,
                long now) {
            JsonObject payload = base(RECORD_BEATEN, now, id, root, title);
            if (newRank == null) {
                payload.add("newRank", JsonNull.INSTANCE);
            } else {
                payload.addProperty("newRank", newRank);
            }
            if (coalescer.offer(player, id, newRank, now, cooldownMs())) {
                send(player, id, title, newRank, payload, now);
            } else {
                remember(player, now, payload); // inside the window: section only, folded into the summary
            }
        }

        /** Leading edge or window summary: one bus event with rich copy (ring only when killed) and the alert. */
        private void send(UUID player, String id, String title, @Nullable Integer newRank, JsonObject payload,
                long now) {
            LinkSettings settings = ctx.settings();
            publish(player, now, payload,
                    settings.pushEnabled() ? ArcadePushCopy.recordNotify(title, newRank, id) : null);
            ctx.raiseAlert(player, LinkAlert.raise(ArcadePushCopy.recordAlertId(id), NeroArcadeLinkModule.MODULE_ID,
                    LinkAlert.Severity.INFO, ArcadePushCopy.recordAlertText(title, newRank)));
        }

        // --- the tick: window summaries and the daily digest ---------------------------------------

        void tick() {
            if (!ctx.settings().moduleEnabled()) {
                return;
            }
            long now = ctx.nowMillis();
            for (RecordCoalescer.Due due : coalescer.drain(now, cooldownMs())) {
                String title = titleFor(due.cartridgeId()).orElse(ArcadePushCopy.safeTitle(null));
                JsonObject payload = base(RECORD_BEATEN, now, due.cartridgeId(),
                        rootFor(due.cartridgeId()).orElse(due.cartridgeId()), title);
                if (due.newRank() == null) {
                    payload.add("newRank", JsonNull.INSTANCE);
                } else {
                    payload.addProperty("newRank", due.newRank());
                }
                send(due.player(), due.cartridgeId(), title, due.newRank(), payload, now);
            }
            long day = ctx.localDay(now);
            if (day != lastDigestDay && ctx.localHour(now) == ctx.settings().digestHour()) {
                lastDigestDay = day;
                digest(now);
            }
        }

        /** The daily download digest: per author, at most {@value #DIGEST_PER_AUTHOR} events, busiest first. */
        void digest(long now) {
            Map<Integer, Integer> counts = ctx.drainDownloads();
            Map<UUID, List<Map.Entry<Listing, Integer>>> byAuthor = new LinkedHashMap<>();
            for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
                Optional<Listing> listing = ctx.listing(e.getKey());
                if (listing.isEmpty() || e.getValue() <= 0 || Tombstones.isTombstone(listing.get().authorUuid())) {
                    continue;
                }
                byAuthor.computeIfAbsent(listing.get().authorUuid(), k -> new ArrayList<>())
                        .add(Map.entry(listing.get(), e.getValue()));
            }
            boolean push = ctx.settings().pushEnabled();
            for (Map.Entry<UUID, List<Map.Entry<Listing, Integer>>> author : byAuthor.entrySet()) {
                List<Map.Entry<Listing, Integer>> rows = author.getValue();
                rows.sort(Comparator.comparing((Map.Entry<Listing, Integer> r) -> r.getValue()).reversed()
                        .thenComparingInt(r -> r.getKey().listingId()));
                int shown = Math.min(DIGEST_PER_AUTHOR, rows.size());
                for (int i = 0; i < shown; i++) {
                    Listing listing = rows.get(i).getKey();
                    int count = rows.get(i).getValue();
                    int more = i == shown - 1 ? rows.size() - shown : 0;
                    String title = ArcadePushCopy.safeTitle(listing.title());
                    JsonObject payload = base(CARTRIDGE_DOWNLOADED, now, listing.cartridgeId(), listing.lineageRoot(),
                            title);
                    payload.addProperty("count", count);
                    if (more > 0) {
                        payload.addProperty("more", more);
                    }
                    publish(author.getKey(), now, payload,
                            push ? ArcadePushCopy.downloadNotify(title, count, more, listing.cartridgeId()) : null);
                }
            }
        }

        // --- the events section ---------------------------------------------------------------------

        /** {@code player}'s newest events first, at most {@code limit}. */
        synchronized List<JsonObject> events(UUID player, int limit) {
            Deque<JsonObject> ring = rings.get(player);
            if (ring == null) {
                return List.of();
            }
            List<JsonObject> out = new ArrayList<>();
            Iterator<JsonObject> it = ring.descendingIterator();
            while (it.hasNext() && out.size() < limit) {
                out.add(it.next().deepCopy());
            }
            return out;
        }

        /** The title seen for cartridge {@code cartridgeId} at a finished match. */
        synchronized Optional<String> titleFor(String cartridgeId) {
            String[] seen = titles.get(cartridgeId);
            return seen == null ? Optional.empty() : Optional.of(seen[0]);
        }

        /** The lineage root seen for cartridge {@code cartridgeId} at a finished match. */
        synchronized Optional<String> rootFor(String cartridgeId) {
            String[] seen = titles.get(cartridgeId);
            return seen == null ? Optional.empty() : Optional.of(seen[1]);
        }

        // --- erasure and lifecycle -------------------------------------------------------------------

        synchronized void forget(UUID player) {
            rings.remove(player);
            coalescer.forget(player);
        }

        synchronized boolean holdsAnyFor(UUID player) {
            return rings.containsKey(player) || coalescer.holdsAnyFor(player);
        }

        synchronized void reset() {
            rings.clear();
            titles.clear();
            coalescer.clear();
            lastDigestDay = Long.MIN_VALUE;
        }

        // --- shared ----------------------------------------------------------------------------------

        private long cooldownMs() {
            return Math.max(1, ctx.settings().recordCooldownMinutes()) * 60_000L;
        }

        private static JsonObject base(String type, long now, String id, String root, String title) {
            JsonObject payload = new JsonObject();
            payload.addProperty("type", type);
            payload.addProperty("at", now);
            payload.addProperty("id", id);
            payload.addProperty("lineageRoot", root);
            payload.addProperty("title", title);
            return payload;
        }

        /**
         * Records {@code payload} in the player's ring (never with {@code _notify}, L2) and, unless the kill switch is
         * on ({@code notify == null}), publishes a copy carrying {@code _notify} on the link bus (M1: killed means
         * nothing reaches the bridge).
         */
        private void publish(UUID player, long now, JsonObject payload, @Nullable JsonElement notify) {
            remember(player, now, payload);
            if (notify == null) {
                return;
            }
            JsonObject event = payload.deepCopy();
            event.add(ArcadePushCopy.FIELD, notify);
            ctx.publish(LinkEvent.forPlayer(NeroArcadeLinkModule.MODULE_ID, TOPIC, player, event));
        }

        private synchronized void remember(UUID player, long now, JsonObject payload) {
            Deque<JsonObject> ring = rings.get(player);
            if (ring == null) {
                while (rings.size() >= MAX_PLAYERS) {
                    Iterator<UUID> oldest = rings.keySet().iterator();
                    oldest.next();
                    oldest.remove();
                }
                ring = new ArrayDeque<>();
                rings.put(player, ring);
            }
            JsonObject row = new JsonObject();
            row.addProperty("topic", TOPIC);
            row.addProperty("at", now);
            row.add("payload", payload.deepCopy());
            ring.addLast(row);
            while (ring.size() > RING_MAX) {
                ring.pollFirst();
            }
        }

        private synchronized void rememberTitle(String id, String root, String title) {
            if (id == null) {
                return;
            }
            titles.put(id, new String[] {title, root == null ? id : root});
            while (titles.size() > MAX_TITLES) {
                Iterator<String> oldest = titles.keySet().iterator();
                oldest.next();
                oldest.remove();
            }
        }
    }
}
