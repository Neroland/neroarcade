package za.co.neroland.neroarcade.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

/**
 * WIKI CONTRACT v1 (007 §2.5): the index is {@code {mod, title, pages[{slug,title}], asOf}}; an unknown or unsafe
 * slug answers {@code {"error":"unknown page"}} (slugs come only from the index, no path traversal).
 */
class WikiContractTest {

    @Test
    void indexShape() {
        JsonObject index = ArcadeSnapshotProvider.wiki(Map.of());
        assertEquals("neroarcade", index.get("mod").getAsString());
        assertEquals("NeroArcade", index.get("title").getAsString());
        assertTrue(index.getAsJsonArray("pages").size() > 0);
        JsonObject first = index.getAsJsonArray("pages").get(0).getAsJsonObject();
        assertTrue(first.has("slug") && first.has("title"));
        assertTrue(index.get("asOf").getAsLong() > 0);
    }

    @Test
    void unknownAndUnsafeSlugs() {
        for (String slug : new String[] {"no-such-page", "../build.gradle", "a/b", "%2e%2e"}) {
            JsonObject page = ArcadeSnapshotProvider.wiki(Map.of("page", slug));
            assertEquals("unknown page", page.get("error").getAsString(), slug);
            assertFalse(page.has("content"));
        }
        assertFalse(WikiLibrary.isSafeSlug("../x"));
        assertFalse(WikiLibrary.isSafeSlug(""));
        assertTrue(WikiLibrary.isSafeSlug("getting-started"));
    }

    @Test
    void sectionDispatchServesTheWiki() {
        FakeLinkContext ctx = new FakeLinkContext();
        JsonObject out = LinkFixtures.provider(ctx, LinkFixtures.pipeline(ctx))
                .snapshot(LinkFixtures.ALICE, "wiki", Map.of());
        assertEquals("neroarcade", out.get("mod").getAsString());
        assertEquals("unknown section", LinkFixtures.provider(ctx, LinkFixtures.pipeline(ctx))
                .snapshot(LinkFixtures.ALICE, "roster", Map.of()).get("error").getAsString());
    }
}
