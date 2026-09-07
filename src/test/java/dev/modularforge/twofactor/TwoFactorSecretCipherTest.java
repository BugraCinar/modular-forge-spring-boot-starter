package dev.modularforge.twofactor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TwoFactorSecretCipherTest {

    private TwoFactorSecretCipher cipher;

    @BeforeEach
    void setUp() {
        cipher = new TwoFactorSecretCipher();
    }

    @Test
    void encryptsAndDecryptsWithAValidKey() {
        setKey(new byte[32]);

        String encrypted = cipher.encrypt("JBSWY3DPEHPK3PXP");

        assertThat(encrypted).isNotEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    void rejectsKeysThatAreNotBase64Encoded() {
        ReflectionTestUtils.setField(cipher, "configuredKey", "%%%not-base64%%%");

        assertThatThrownBy(cipher::validateKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("base64");
    }

    @Test
    void rejectsKeysThatAreNotExactlyThirtyTwoBytes() {
        setConfiguredKey(new byte[16]);

        assertThatThrownBy(cipher::validateKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void rejectsShortInvalidAndTamperedCiphertexts() {
        setKey(new byte[32]);
        String encrypted = cipher.encrypt("secret");
        byte[] tampered = Base64.getDecoder().decode(encrypted);
        tampered[tampered.length - 1] ^= 1;

        assertThatThrownBy(() -> cipher.decrypt("not-base64"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt(Base64.getEncoder().encodeToString(new byte[12])))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt(Base64.getEncoder().encodeToString(tampered)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void wrapsEncryptionFailures() {
        assertThatThrownBy(() -> cipher.encrypt("secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("encrypt");
    }

    private void setKey(byte[] bytes) {
        setConfiguredKey(bytes);
        cipher.validateKey();
    }

    private void setConfiguredKey(byte[] bytes) {
        ReflectionTestUtils.setField(cipher, "configuredKey",
                Base64.getEncoder().encodeToString(bytes));
    }
}
