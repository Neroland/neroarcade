package za.co.neroland.neroarcade.diagnostics;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

public final class DebugEncryption {
    private static final int MAGIC = 0x4e414431;
    private static final int MAX_BYTES = 262144;
    private static final OAEPParameterSpec OAEP = new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);

    private DebugEncryption() { }

    public static byte[] encrypt(byte[] report, Key recipient) throws GeneralSecurityException {
        if (report.length > MAX_BYTES) throw new IllegalArgumentException("Report too large");
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        Key session = generator.generateKey();
        byte[] nonce = new byte[12];
        new SecureRandom().nextBytes(nonce);
        Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
        rsa.init(Cipher.ENCRYPT_MODE, recipient, OAEP);
        byte[] wrapped = rsa.doFinal(session.getEncoded());
        byte[] header = ByteBuffer.allocate(8 + wrapped.length + nonce.length)
                .putInt(MAGIC).putInt(wrapped.length).put(wrapped).put(nonce).array();
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, session, new GCMParameterSpec(128, nonce));
        aes.updateAAD(header);
        byte[] encrypted = aes.doFinal(report);
        return ByteBuffer.allocate(header.length + encrypted.length).put(header).put(encrypted).array();
    }

    public static byte[] decrypt(byte[] dump, Key recipient) throws GeneralSecurityException, IOException {
        if (dump.length < 40 || dump.length > MAX_BYTES + 2048) throw new IOException("Invalid dump size");
        ByteBuffer buffer = ByteBuffer.wrap(dump);
        if (buffer.getInt() != MAGIC) throw new IOException("Unknown dump format");
        int keySize = buffer.getInt();
        if (keySize < 256 || keySize > 1024 || buffer.remaining() < keySize + 28)
            throw new IOException("Invalid dump header");
        byte[] wrapped = new byte[keySize];
        byte[] nonce = new byte[12];
        buffer.get(wrapped).get(nonce);
        int headerSize = buffer.position();
        Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
        rsa.init(Cipher.DECRYPT_MODE, recipient, OAEP);
        Key session = new SecretKeySpec(rsa.doFinal(wrapped), "AES");
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.DECRYPT_MODE, session, new GCMParameterSpec(128, nonce));
        aes.updateAAD(dump, 0, headerSize);
        return aes.doFinal(dump, headerSize, dump.length - headerSize);
    }
}
