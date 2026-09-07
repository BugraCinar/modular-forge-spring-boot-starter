package dev.modularforge.security;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class SecretGeneratorTest {

    @Test
    void generatesTheRequestedNumberOfRandomBytes() {
        String first = SecretGenerator.generateSecret(32);
        String second = SecretGenerator.generateSecret(32);

        assertThat(Base64.getDecoder().decode(first)).hasSize(32);
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void commandLineOutputContainsEveryRequiredSecret() {
        PrintStream original = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            SecretGenerator.main(new String[0]);
        } finally {
            System.setOut(original);
        }

        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("JWT_SECRET=")
                .contains("PEPPER=")
                .contains("TOKEN_HASH_SECRET=")
                .contains("TWO_FACTOR_ENCRYPTION_KEY=")
                .contains("never commit them");
    }

    @Test
    void canBeConstructedByTooling() {
        assertThat(new SecretGenerator()).isNotNull();
    }
}
