package za.co.neroland.neroarcade.diagnostics;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DebugEncryptionTest {
    private static KeyPair recipient;
    private static KeyPair stranger;

    @BeforeAll
    static void keys() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        recipient = generator.generateKeyPair();
        stranger = generator.generateKeyPair();
    }

    @Test
    void onlyRecipientCanReadAndEachDumpUsesFreshRandomness() throws Exception {
        byte[] report = "Technical diagnostic fixture".getBytes(StandardCharsets.UTF_8);
        byte[] first = DebugEncryption.encrypt(report, recipient.getPublic());
        byte[] second = DebugEncryption.encrypt(report, recipient.getPublic());
        assertArrayEquals(report, DebugEncryption.decrypt(first, recipient.getPrivate()));
        assertFalse(Arrays.equals(first, second));
        assertThrows(GeneralSecurityException.class, () -> DebugEncryption.decrypt(first, stranger.getPrivate()));
    }

    @Test
    void rejectsTamperedTruncatedAndOversizedReports() throws Exception {
        byte[] dump = DebugEncryption.encrypt(new byte[30], recipient.getPublic());
        dump[dump.length - 1] ^= 1;
        assertThrows(GeneralSecurityException.class, () -> DebugEncryption.decrypt(dump, recipient.getPrivate()));
        assertThrows(IOException.class, () -> DebugEncryption.decrypt(new byte[20], recipient.getPrivate()));
        assertThrows(IOException.class, () -> DebugEncryption.decrypt(new byte[300000], recipient.getPrivate()));
        assertThrows(IllegalArgumentException.class,
                () -> DebugEncryption.encrypt(new byte[262145], recipient.getPublic()));
        dump[0] ^= 1;
        assertThrows(IOException.class, () -> DebugEncryption.decrypt(dump, recipient.getPrivate()));
    }

    @Test
    void shippedRecipientIsAnRsaPublicKey() throws Exception {
        try (var input = getClass().getResourceAsStream("/neroarcade-support-public.der")) {
            assertNotNull(input);
            var key = java.security.KeyFactory.getInstance("RSA").generatePublic(
                    new java.security.spec.X509EncodedKeySpec(input.readAllBytes()));
            assertTrue(((java.security.interfaces.RSAPublicKey) key).getModulus().bitLength() >= 3072);
        }
    }
}
