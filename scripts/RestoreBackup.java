import java.nio.file.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** java scripts/RestoreBackup.java backup.sql.aes restored.sql */
class RestoreBackup {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: RestoreBackup encrypted-file output-file");
        byte[] key = Base64.getDecoder().decode(Objects.requireNonNull(
                System.getenv("DATABASE_BACKUP_ENCRYPTION_KEY"), "Set DATABASE_BACKUP_ENCRYPTION_KEY"));
        if (key.length != 32) throw new IllegalArgumentException("Key must decode to 32 bytes");
        Path output = Path.of(args[1]).toAbsolutePath();
        if (Files.exists(output)) throw new FileAlreadyExistsException(output.toString());
        Path temporary = Files.createTempFile(output.getParent(), ".restore-", ".tmp");
        try (var input = Files.newInputStream(Path.of(args[0]))) {
            if (!Arrays.equals(input.readNBytes(4), new byte[]{'M', 'F', 'B', 1}))
                throw new IllegalArgumentException("Unsupported backup format");
            byte[] nonce = input.readNBytes(12);
            if (nonce.length != 12) throw new IllegalArgumentException("Truncated backup header");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            try (var plaintext = new CipherInputStream(input, cipher);
                 var target = Files.newOutputStream(temporary)) {
                plaintext.transferTo(target);
            }
            // Publish only after the complete ciphertext/tag has been authenticated.
            Files.move(temporary, output);
        } finally {
            Files.deleteIfExists(temporary);
            Arrays.fill(key, (byte) 0);
        }
        System.out.println("Authenticated backup restored to " + output);
    }
}
