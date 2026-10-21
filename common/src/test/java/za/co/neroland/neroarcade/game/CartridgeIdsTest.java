package za.co.neroland.neroarcade.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.util.RandomSource;

import org.junit.jupiter.api.Test;

class CartridgeIdsTest {

    @Test
    void regexAndLength() {
        assertTrue(CartridgeIds.isValid("abcdefghijklmnop"));
        assertTrue(CartridgeIds.isValid("234567abcdefghij"));
        assertFalse(CartridgeIds.isValid("abcdefghijklmno"));
        assertFalse(CartridgeIds.isValid("abcdefghijklmnopq"));
        assertFalse(CartridgeIds.isValid("abcdefghijklmn01"));
        assertFalse(CartridgeIds.isValid("ABCDEFGHIJKLMNOP"));
        assertFalse(CartridgeIds.isValid(null));
    }

    @Test
    void tenThousandMintsAreUniqueAndValid() {
        RandomSource random = RandomSource.create(1234L);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            String id = CartridgeIds.mint(random);
            assertEquals(CartridgeIds.LENGTH, id.length());
            assertTrue(CartridgeIds.isValid(id), id);
            assertTrue(seen.add(id), "duplicate " + id);
        }
    }

    @Test
    void lineageCapKeepsTheRoot() {
        List<String> lineage = new ArrayList<>();
        String parent = "rootrootrootroot";
        for (int i = 0; i < 20; i++) {
            lineage = CartridgeIds.remixLineage(lineage, parent);
            parent = "aaaaaaaaaaaaaa" + (char) ('a' + i / 26) + (char) ('a' + i % 26);
            assertTrue(lineage.size() <= CartridgeIds.MAX_LINEAGE);
            assertEquals("rootrootrootroot", lineage.get(0));
        }
        assertEquals(CartridgeIds.MAX_LINEAGE, lineage.size());
        assertEquals(List.of("rootrootrootroot", "x"), CartridgeIds.remixLineage(List.of("rootrootrootroot"), "x"));
    }
}
