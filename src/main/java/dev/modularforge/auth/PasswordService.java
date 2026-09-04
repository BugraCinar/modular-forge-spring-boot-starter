package dev.modularforge.auth;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

@Service
public class PasswordService {

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.security.pepper}")
    private String pepper;

    @Value("${app.security.argon2.memory-cost}")
    private int memoryCost;

    @Value("${app.security.argon2.time-cost}")
    private int timeCost;

    @Value("${app.security.argon2.parallelism}")
    private int parallelism;

    @Value("${app.security.argon2.salt-length}")
    private int saltLength;

    @Value("${app.security.argon2.hash-length}")
    private int hashLength;

    private Argon2 argon2;

    @PostConstruct
    void validateConfiguration() {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(pepper);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("app.security.pepper must be base64 encoded", exception);
        }
        if (decoded.length < 32) {
            throw new IllegalStateException("app.security.pepper must contain at least 32 random bytes");
        }
        if (memoryCost < 19_456 || timeCost < 2 || parallelism < 1 || saltLength < 16 || hashLength < 32) {
            throw new IllegalStateException("Argon2 settings are below the supported security floor");
        }
    }

    private Argon2 getArgon2() {
        if (argon2 == null) {
            argon2 = Argon2Factory.create(
                Argon2Factory.Argon2Types.ARGON2id,
                saltLength,
                hashLength
            );
        }
        return argon2;
    }

    public String generateSalt() {
        byte[] salt = new byte[saltLength];
        secureRandom.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    public String hashPassword(String password, String salt) {
        if (password == null || salt == null) {
            throw new IllegalArgumentException("Password and salt are required");
        }
        String passwordWithPepper = password + pepper;
        char[] passwordChars = (passwordWithPepper + salt).toCharArray();
        try {
            return getArgon2().hash(timeCost, memoryCost, parallelism, passwordChars);
        } finally {
            java.util.Arrays.fill(passwordChars, '\0');
        }
    }

    public boolean verifyPassword(String password, String salt, String hash) {
        if (password == null || salt == null || hash == null) {
            return false;
        }
        String passwordWithPepper = password + pepper;
        char[] passwordChars = (passwordWithPepper + salt).toCharArray();
        try {
            return getArgon2().verify(hash, passwordChars);
        } finally {
            java.util.Arrays.fill(passwordChars, '\0');
        }
    }
}
