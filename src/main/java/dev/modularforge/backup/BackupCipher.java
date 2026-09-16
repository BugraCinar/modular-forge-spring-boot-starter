package dev.modularforge.backup;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

@Component
@ConditionalOnProperty(prefix="app.modules.database-backup", name="enabled", havingValue="true")
public class BackupCipher {
    private final SecretKeySpec key;

    public BackupCipher(@Value("${app.database.backup.encryption-key:}") String encodedKey) {
        byte[] decoded;
        try { decoded = Base64.getDecoder().decode(encodedKey); }
        catch (IllegalArgumentException exception) { throw new IllegalStateException("Backup key must be base64", exception); }
        if (decoded.length != 32) throw new IllegalStateException("Backup encryption requires a separate 32-byte key");
        key = new SecretKeySpec(decoded, "AES");
    }

    public Path encrypt(Path plaintext) throws Exception {
        Path encrypted = plaintext.resolveSibling(plaintext.getFileName() + ".aes");
        byte[] nonce = new byte[12];
        new SecureRandom().nextBytes(nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
        try (OutputStream output = Files.newOutputStream(encrypted)) {
            output.write(new byte[]{'M','F','B',1});
            output.write(nonce);
            try (CipherOutputStream ciphertext = new CipherOutputStream(output, cipher)) {
                Files.copy(plaintext, ciphertext);
            }
        } catch (Exception exception) {
            Files.deleteIfExists(encrypted);
            throw exception;
        }
        return encrypted;
    }
}
