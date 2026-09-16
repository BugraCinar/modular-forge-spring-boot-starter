package dev.modularforge.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import static org.assertj.core.api.Assertions.*;

class BackupCipherTest {
    @TempDir Path directory;
    @Test void encryptedBackupCanBeRestoredAndRejectsTampering() throws Exception {
        byte[] key = new byte[32]; new java.security.SecureRandom().nextBytes(key);
        var encryptor = new BackupCipher(Base64.getEncoder().encodeToString(key));
        Path input = directory.resolve("backup.sql"); Files.writeString(input, "sensitive database contents");
        byte[] output = Files.readAllBytes(encryptor.encrypt(input));
        assertThat(Arrays.copyOf(output, 4)).containsExactly((byte)'M', (byte)'F', (byte)'B', (byte)1);
        assertThat(new String(output, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("sensitive database contents");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, Arrays.copyOfRange(output, 4, 16)));
        assertThat(cipher.doFinal(Arrays.copyOfRange(output, 16, output.length))).isEqualTo(Files.readAllBytes(input));
        output[output.length - 1] ^= 1;
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, Arrays.copyOfRange(output, 4, 16)));
        assertThatThrownBy(() -> cipher.doFinal(Arrays.copyOfRange(output, 16, output.length))).isInstanceOf(javax.crypto.AEADBadTagException.class);
    }
    @Test void invalidKeysAndPartialOutputsAreRejected() {
        assertThatThrownBy(() -> new BackupCipher("not-base64!")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new BackupCipher("")).isInstanceOf(IllegalStateException.class);
        var cipher = new BackupCipher(Base64.getEncoder().encodeToString(new byte[32]));
        assertThatThrownBy(() -> cipher.encrypt(directory.resolve("missing.sql"))).isInstanceOf(java.io.IOException.class);
        assertThat(directory.resolve("missing.sql.aes")).doesNotExist();
    }
}
