package dev.modularforge.security;

import java.security.SecureRandom;
import java.util.Base64;
public class SecretGenerator {

    private static final SecureRandom secureRandom = new SecureRandom();
    public static String generateSecret(int byteLength) {
        byte[] randomBytes = new byte[byteLength];
        secureRandom.nextBytes(randomBytes);
        return Base64.getEncoder().encodeToString(randomBytes);
    }

    public static void main(String[] args) {
        System.out.println("JWT_SECRET=" + generateSecret(64));
        System.out.println("PEPPER=" + generateSecret(32));
        System.out.println("TOKEN_HASH_SECRET=" + generateSecret(32));
        System.out.println("TWO_FACTOR_ENCRYPTION_KEY=" + generateSecret(32));
        System.out.println();
        System.out.println("Store these values in environment variables or a secret manager; never commit them.");
        System.out.println("Rotating PEPPER invalidates existing passwords. Rotating TOKEN_HASH_SECRET invalidates stored tokens.");
    }
}
