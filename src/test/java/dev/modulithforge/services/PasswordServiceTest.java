package dev.modulithforge.services;

import dev.modulithforge.auth.PasswordService;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordService Unit Tests")
class PasswordServiceTest {

    private PasswordService passwordService;

    @BeforeEach
    void setUp() {
        passwordService = new PasswordService();
        ReflectionTestUtils.setField(passwordService, "pepper", "test-pepper-secret");
        ReflectionTestUtils.setField(passwordService, "memoryCost", 65536);
        ReflectionTestUtils.setField(passwordService, "timeCost", 2);   // lower for faster test
        ReflectionTestUtils.setField(passwordService, "parallelism", 1);
        ReflectionTestUtils.setField(passwordService, "saltLength", 16);
        ReflectionTestUtils.setField(passwordService, "hashLength", 32);
    }

    @Nested
    @DisplayName("generateSalt()")
    class GenerateSalt {

        @Test
        @DisplayName("Returns a non-null, non-blank Base64 string")
        void generated_isNonBlank() {
            String salt = passwordService.generateSalt();
            assertThat(salt).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("Two calls produce different salts (randomness)")
        void twoCalls_produceDifferentSalts() {
            String salt1 = passwordService.generateSalt();
            String salt2 = passwordService.generateSalt();
            assertThat(salt1).isNotEqualTo(salt2);
        }

        @Test
        @DisplayName("Returned string is valid Base64")
        void returned_isValidBase64() {
            String salt = passwordService.generateSalt();
            byte[] decoded = java.util.Base64.getDecoder().decode(salt);
            assertThat(decoded).hasSize(16); // saltLength = 16
        }
    }

    @Nested
    @DisplayName("hashPassword()")
    class HashPassword {

        @Test
        @DisplayName("Returns a non-null, non-blank hash string")
        void hash_isNonBlank() {
            String salt = passwordService.generateSalt();
            String hash = passwordService.hashPassword("MySecurePass1!", salt);
            assertThat(hash).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("Hash starts with Argon2id prefix")
        void hash_hasArgon2idPrefix() {
            String salt = passwordService.generateSalt();
            String hash = passwordService.hashPassword("password", salt);
            assertThat(hash).startsWith("$argon2id$");
        }

        @Test
        @DisplayName("Same password with different salts produces different hashes")
        void differentSalts_produceDifferentHashes() {
            String salt1 = passwordService.generateSalt();
            String salt2 = passwordService.generateSalt();
            String hash1 = passwordService.hashPassword("same-password", salt1);
            String hash2 = passwordService.hashPassword("same-password", salt2);
            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        @DisplayName("Different passwords with same salt produce different hashes")
        void differentPasswords_sameOutput_neverEqual() {
            String salt = passwordService.generateSalt();
            String hash1 = passwordService.hashPassword("password-A", salt);
            String hash2 = passwordService.hashPassword("password-B", salt);
            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        @DisplayName("Two hashes of the same input are different (Argon2 uses random internal salt)")
        void sameInput_twiceHashed_areDifferent() {
            String salt = passwordService.generateSalt();
            String hash1 = passwordService.hashPassword("password", salt);
            String hash2 = passwordService.hashPassword("password", salt);
            assertThat(hash1).isNotEqualTo(hash2);
        }
    }

    @Nested
    @DisplayName("verifyPassword()")
    class VerifyPassword {

        @Test
        @DisplayName("Correct password and salt verifies successfully")
        void correctPassword_returnsTrue() {
            String salt = passwordService.generateSalt();
            String hash = passwordService.hashPassword("correct-password", salt);
            assertThat(passwordService.verifyPassword("correct-password", salt, hash)).isTrue();
        }

        @Test
        @DisplayName("Wrong password returns false")
        void wrongPassword_returnsFalse() {
            String salt = passwordService.generateSalt();
            String hash = passwordService.hashPassword("correct-password", salt);
            assertThat(passwordService.verifyPassword("wrong-password", salt, hash)).isFalse();
        }

        @Test
        @DisplayName("Wrong salt returns false (salt is part of the hashed input)")
        void wrongSalt_returnsFalse() {
            String salt = passwordService.generateSalt();
            String hash = passwordService.hashPassword("correct-password", salt);
            String differentSalt = passwordService.generateSalt();
            assertThat(passwordService.verifyPassword("correct-password", differentSalt, hash)).isFalse();
        }

        @Test
        @DisplayName("Corrupted hash returns false")
        void corruptedHash_returnsFalse() {
            String salt = passwordService.generateSalt();
            String hash = passwordService.hashPassword("password", salt);
            String corrupted = hash.replace("$", "#");
            assertThat(passwordService.verifyPassword("password", salt, corrupted)).isFalse();
        }

        @Test
        @DisplayName("Empty string password hashes and verifies correctly")
        void emptyPassword_roundTrip() {
            String salt = passwordService.generateSalt();
            String hash = passwordService.hashPassword("", salt);
            assertThat(passwordService.verifyPassword("", salt, hash)).isTrue();
            assertThat(passwordService.verifyPassword("non-empty", salt, hash)).isFalse();
        }

        @Test
        @DisplayName("Special-character password round-trip succeeds")
        void specialChars_roundTrip() {
            String salt = passwordService.generateSalt();
            String pw = "P@$$w0rd!#%^&*()_+-=[]{}|;':\",./<>?";
            String hash = passwordService.hashPassword(pw, salt);
            assertThat(passwordService.verifyPassword(pw, salt, hash)).isTrue();
        }

        @Test
        @DisplayName("Long password (100+ chars) round-trip succeeds")
        void longPassword_roundTrip() {
            String salt = passwordService.generateSalt();
            String pw = "a".repeat(100);
            String hash = passwordService.hashPassword(pw, salt);
            assertThat(passwordService.verifyPassword(pw, salt, hash)).isTrue();
        }
    }
}
