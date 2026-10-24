package za.co.neroland.neroarcade.link;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.link.LinkActionHandler;
import za.co.neroland.nerolandcore.link.LinkActionResult;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.exchange.Draft;
import za.co.neroland.neroarcade.exchange.ExchangeRules;
import za.co.neroland.neroarcade.exchange.ExchangeTable;
import za.co.neroland.neroarcade.exchange.Listing;
import za.co.neroland.neroarcade.game.CartridgeIds;
import za.co.neroland.neroarcade.game.CartridgeValidator;
import za.co.neroland.neroarcade.game.GameDef;
import za.co.neroland.neroarcade.game.ValidationError;
import za.co.neroland.neroarcade.privacy.Tombstones;

/**
 * The companion app's two actions, and nothing else (007 §3; {@code challenge} is cut, D1): {@code export_cartridge}
 * and {@code import_cartridge}. No action publishes, unpublishes, hearts, seats a player or starts a match.
 *
 * <h2>Pipeline (every call, in order, on the server thread)</h2>
 * <ol>
 *   <li>a player id and a known action;</li>
 *   <li>{@code link.enabled}, {@code link.actions.<name>}, then {@code exchange.enabled}: {@code ACTION_DISABLED};</li>
 *   <li>a {@code requestId} longer than 64 characters: {@code VALIDATION}; a replayed one returns the first result
 *       with nothing applied twice;</li>
 *   <li>the per-player rate limit ({@code VALIDATION "rate limited, retry in <s>s"}; refused calls are not
 *       counted);</li>
 *   <li>parameters, ownership, the action; the result is remembered for the request id.</li>
 * </ol>
 *
 * <p>Results are the bridge's {@code data} object (the bridge wraps it as {@code {"ok":true,"data":…}}). Messages
 * are fixed text; logs carry the action id and exception class only.
 */
final class ArcadeActionHandler implements LinkActionHandler {

    /** The JSON cap on a cartridge in either direction (007 §3, G18). */
    static final int MAX_GAME_DEF_BYTES = 12 * 1024;
    static final int MAX_REQUEST_ID = LinkRequestDedup.MAX_REQUEST_ID;
    /**
     * The answer while the server's chat filter has not decided on an imported title yet (security review L3): no
     * draft is added, and the result is not remembered for the request id, so a retry asks the filter again.
     */
    static final String TITLE_PENDING = "title pending review; try again";

    private final ArcadeLinkContext ctx;
    private final LinkRequestDedup dedup;
    private final LinkRateLimiter limiter;

    ArcadeActionHandler(ArcadeLinkContext ctx, LinkRequestDedup dedup, LinkRateLimiter limiter) {
        this.ctx = ctx;
        this.dedup = dedup;
        this.limiter = limiter;
    }

    @Override
    public String moduleId() {
        return NeroArcadeLinkModule.MODULE_ID;
    }

    @Override
    public List<String> actionIds() {
        return NeroArcadeLinkModule.ACTIONS;
    }

    /** Neither action works offline: a draft needs the player at a kiosk anyway (the bridge answers 409). */
    @Override
    public boolean allowOffline(String actionId) {
        return false;
    }

    @Override
    public LinkActionResult execute(UUID playerId, String actionId, JsonObject params) {
        try {
            return run(playerId, actionId, params);
        } catch (RuntimeException | LinkageError e) {
            NeroArcadeCommon.LOGGER.warn("[NeroArcade] link action {} failed ({}).",
                    actionId != null && NeroArcadeLinkModule.ACTIONS.contains(actionId) ? actionId : "?",
                    e.getClass().getName());
            return LinkActionResult.error(LinkActionResult.Error.INTERNAL, "Something went wrong; nothing was changed.");
        }
    }

    private LinkActionResult run(@Nullable UUID player, @Nullable String actionId, @Nullable JsonObject params) {
        if (player == null || actionId == null || !NeroArcadeLinkModule.ACTIONS.contains(actionId)) {
            return validation("Unknown action.");
        }
        LinkSettings settings = ctx.settings();
        if (!settings.moduleEnabled()) {
            return disabled("NeroArcade's companion-app module is switched off on this server.");
        }
        boolean export = NeroArcadeLinkModule.ACTION_EXPORT.equals(actionId);
        if (export ? !settings.exportEnabled() : !settings.importEnabled()) {
            return disabled(export ? "Exporting cartridges from the app is switched off on this server."
                    : "Importing cartridges from the app is switched off on this server.");
        }
        if (!settings.exchangeEnabled()) {
            return disabled("The Arcade Exchange is switched off on this server.");
        }
        JsonElement rawId = params == null ? null : params.get("requestId");
        String requestId = null;
        if (rawId != null && !rawId.isJsonNull()) {
            if (!rawId.isJsonPrimitive() || rawId.getAsString().length() > MAX_REQUEST_ID) {
                return validation("Bad request id.");
            }
            requestId = rawId.getAsString();
        }
        long now = ctx.nowMillis();
        LinkActionResult replay = dedup.lookup(player, actionId, requestId, now);
        if (replay != null) {
            return replay;
        }
        long wait = limiter.tryAcquire(player, actionId, now);
        if (wait > 0L) {
            long seconds = Math.max(1L, (wait + 999L) / 1000L);
            return validation("rate limited, retry in " + seconds + "s");
        }
        LinkActionResult result = export ? export(player, params) : importCartridge(player, params, now);
        if (result.ok() || !TITLE_PENDING.equals(result.message())) {
            dedup.remember(player, actionId, requestId, result, now);
        }
        return result;
    }

    // --- export_cartridge -------------------------------------------------------------------------

    private LinkActionResult export(UUID player, @Nullable JsonObject params) {
        // By id only (boards and listings are per cartridge id, D-owner-1): a published remix exports as itself,
        // never as the original that shares its lineage root. `lineageRoot` is ignored: there is no by-root lookup
        // (security review M2: lineage is author-controlled, so "the newest listing of a root" could be hijacked).
        String id = string(params, "id");
        if (id == null || !CartridgeIds.isValid(id)) {
            return validation("bad cartridge");
        }
        GameDef def = null;
        Optional<String> credit = Optional.empty();
        // 1. the requester's own draft with that id (only ever their own bucket)
        Optional<Draft> draft = ctx.draft(player, id);
        if (draft.isPresent()) {
            def = draft.get().gameDef();
            credit = def.authorCredit();
        }
        // 2. the published listing of that id (public data: anyone may export it)
        if (def == null) {
            Optional<Listing> listing = ctx.listingByCartridgeId(id);
            if (listing.isPresent()) {
                def = listing.get().gameDef();
                credit = listing.get().credit(); // only as the listing already shows it (opt-in)
            }
        }
        if (def == null) {
            return LinkActionResult.error(LinkActionResult.Error.NOT_OWNER, "not published and not yours");
        }
        JsonObject gameDef = exportJson(def, credit, player);
        int bytes = gameDef.toString().getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_GAME_DEF_BYTES) {
            return LinkActionResult.error(LinkActionResult.Error.INTERNAL, "cartridge too large to export");
        }
        JsonObject state = new JsonObject();
        state.addProperty("id", def.id());
        state.addProperty("lineageRoot", def.lineageRoot());
        state.addProperty("title", ArcadePushCopy.safeTitle(def.title()));
        state.addProperty("revision", def.revision());
        state.addProperty("bytes", bytes);
        state.add("gameDef", gameDef);
        return LinkActionResult.ok(state);
    }

    /**
     * The cartridge in its canonical JSON form (the same fields an in-game {@code .nac} import reads back), with the
     * stored {@code author} field replaced by {@code authorUuid}: the requester's own id when they are the author,
     * otherwise the tombstone (D3) — another player's UUID never leaves the server. Credit is the listing's (opt-in).
     */
    static JsonObject exportJson(GameDef def, Optional<String> credit, UUID requester) {
        GameDef shown = new GameDef(def.schemaVersion(), def.id(), def.title(), Optional.empty(), credit,
                def.lineage(), def.seed(), def.slots(), def.createdAt(), def.revision());
        JsonObject json = GameDef.CODEC.encodeStart(JsonOps.INSTANCE, shown).getOrThrow().getAsJsonObject();
        json.remove("author");
        boolean own = def.authorUuid().filter(requester::equals).isPresent();
        json.addProperty("authorUuid", (own ? requester : Tombstones.ERASED_AUTHOR).toString());
        return json;
    }

    // --- import_cartridge -------------------------------------------------------------------------

    private LinkActionResult importCartridge(UUID player, @Nullable JsonObject params, long now) {
        JsonElement raw = params == null ? null : params.get("gameDef");
        if (raw == null || !raw.isJsonObject()) {
            return validation("no cartridge");
        }
        JsonObject json = raw.getAsJsonObject().deepCopy();
        // Never trusted from the phone: the author is always the requester and credit is written at save, per opt-in.
        json.remove("author");
        json.remove("authorUuid");
        json.remove("author_credit");
        json.remove("authorCredit");
        JsonElement idElement = json.get("id");
        boolean minted = false;
        if (idElement == null || !idElement.isJsonPrimitive() || !CartridgeIds.isValid(idElement.getAsString())) {
            json.addProperty("id", ctx.mintId());
            minted = true;
        }
        GameDef decoded;
        try {
            decoded = CartridgeValidator.decodeJson(json.toString(), MAX_GAME_DEF_BYTES);
        } catch (ValidationError refused) {
            return validation("cartridge refused: " + refused.code.name().toLowerCase(Locale.ROOT));
        }
        if (ExchangeRules.title(decoded.title()).isPresent()) {
            return validation("title not allowed");
        }
        String id = decoded.id();
        List<String> lineage = decoded.lineage();
        if (!minted && !authoredHere(player, id)) {
            // An id the requester did not author on this server (someone else's listing, draft or unpublished
            // cartridge, or a .nac from another server): the import becomes a remix with a fresh id on the same
            // lineage, so it never shares (and can never write to) that id's board (security review H1).
            lineage = CartridgeIds.remixLineage(lineage, id);
            id = ctx.mintId();
        }
        GameDef def = new GameDef(decoded.schemaVersion(), id, decoded.title(), Optional.of(player), Optional.empty(),
                lineage, decoded.seed(), decoded.slots(), now / 1000L, decoded.revision());
        try {
            CartridgeValidator.validate(def);
        } catch (ValidationError refused) {
            return validation("cartridge refused: " + refused.code.name().toLowerCase(Locale.ROOT));
        }
        CompletableFuture<Boolean> filter = ctx.titleAllowed(player, def.title());
        if (!filter.isDone()) {
            // A slow chat filter: never answer ok and then take the draft away (security review L3). Nothing is
            // added; the phone is told to retry.
            return validation(TITLE_PENDING);
        }
        if (filter.isCompletedExceptionally() || !Boolean.TRUE.equals(filter.join())) {
            return validation("title not allowed");
        }
        ExchangeTable.DraftStatus status = ctx.addDraft(player, def);
        if (status == ExchangeTable.DraftStatus.FULL) {
            return validation("drafts full");
        }
        JsonObject state = new JsonObject();
        state.addProperty("draftId", def.id());
        state.addProperty("lineageRoot", def.lineageRoot());
        state.addProperty("title", ArcadePushCopy.safeTitle(def.title()));
        state.addProperty("draftsUsed", ctx.drafts(player).size());
        state.addProperty("draftsMax", ctx.settings().draftsMax());
        return LinkActionResult.ok(state);
    }

    /** {@code player} holds a draft with {@code id}, or {@code id} is their own listing on this server. */
    private boolean authoredHere(UUID player, String id) {
        return ctx.draft(player, id).isPresent()
                || ctx.listingByCartridgeId(id).filter(l -> l.authorUuid().equals(player)).isPresent();
    }

    // --- shared -----------------------------------------------------------------------------------

    private static @Nullable String string(@Nullable JsonObject params, String key) {
        if (params == null) {
            return null;
        }
        JsonElement el = params.get(key);
        if (el == null || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
            return null;
        }
        String value = el.getAsString();
        return value.length() > 64 ? null : value;
    }

    private static LinkActionResult validation(String message) {
        return LinkActionResult.error(LinkActionResult.Error.VALIDATION, message);
    }

    private static LinkActionResult disabled(String message) {
        return LinkActionResult.error(LinkActionResult.Error.ACTION_DISABLED, message);
    }
}
