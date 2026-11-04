import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.spec.PKCS8EncodedKeySpec;
import za.co.neroland.neroarcade.diagnostics.DebugEncryption;

public final class DebugDumpTool {
    public static void main(String[] args) throws Exception {
        if (args.length == 3 && args[0].equals("keygen")) {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(3072);
            var pair = generator.generateKeyPair();
            Files.write(Path.of(args[1]), pair.getPrivate().getEncoded(), StandardOpenOption.CREATE_NEW);
            Files.write(Path.of(args[2]), pair.getPublic().getEncoded(), StandardOpenOption.CREATE_NEW);
        } else if (args.length == 4 && args[0].equals("decrypt")) {
            var key = KeyFactory.getInstance("RSA").generatePrivate(
                    new PKCS8EncodedKeySpec(Files.readAllBytes(Path.of(args[1]))));
            if (Files.size(Path.of(args[2])) > 264192) throw new IllegalArgumentException("Dump too large");
            byte[] report = DebugEncryption.decrypt(Files.readAllBytes(Path.of(args[2])), key);
            Files.write(Path.of(args[3]), report, StandardOpenOption.CREATE_NEW);
        } else {
            throw new IllegalArgumentException("keygen <private.der> <public.der> | decrypt <private.der> <dump.nad> <report.txt>");
        }
    }
}
