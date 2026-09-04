package dev.modulithforge.twofactor;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import com.warrenstrange.googleauth.HmacHashFunction;
import dev.modulithforge.auth.SecondFactorGateway;
import dev.modulithforge.identity.AdminRepository;
import dev.modulithforge.identity.model.Admin;
import dev.modulithforge.shared.error.ResourceNotFoundException;
import dev.modulithforge.twofactor.dto.TwoFactorSetupResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(prefix = "app.modules.two-factor", name = "enabled", havingValue = "true")
public class TwoFactorAuthService implements SecondFactorGateway {

    private static final int MAX_CHALLENGE_ATTEMPTS = 5;
    private static final int CHALLENGE_BYTES = 32;

    private final AdminRepository adminRepository;
    private final TwoFactorCredentialRepository credentialRepository;
    private final TwoFactorSecretCipher secretCipher;
    private final GoogleAuthenticator authenticator;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${spring.application.name:ModulithForge}")
    private String appName;

    @Value("${app.modules.two-factor.challenge-ttl-seconds:300}")
    private long challengeTtlSeconds;

    public TwoFactorAuthService(
            AdminRepository adminRepository,
            TwoFactorCredentialRepository credentialRepository,
            TwoFactorSecretCipher secretCipher) {
        this.adminRepository = adminRepository;
        this.credentialRepository = credentialRepository;
        this.secretCipher = secretCipher;
        GoogleAuthenticatorConfig config = new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder()
                .setTimeStepSizeInMillis(TimeUnit.SECONDS.toMillis(30))
                .setWindowSize(1)
                .setCodeDigits(6)
                .setHmacHashFunction(HmacHashFunction.HmacSHA1)
                .build();
        this.authenticator = new GoogleAuthenticator(config);
    }

    @Override
    @Transactional
    public Optional<Challenge> beginChallenge(Long adminId) {
        return credentialRepository.findByAdminId(adminId)
                .filter(TwoFactorCredential::isEnabled)
                .map(credential -> {
                    String token = randomToken();
                    credential.setChallengeHash(hash(token));
                    credential.setChallengeExpiresAt(LocalDateTime.now().plusSeconds(challengeTtlSeconds));
                    credential.setChallengeAttempts(0);
                    credentialRepository.save(credential);
                    return new Challenge(token, "Two-factor authentication required");
                });
    }

    @Transactional
    public TwoFactorSetupResponse generateSecret(Long adminId) {
        Admin admin = requireAdmin(adminId);
        GoogleAuthenticatorKey generated = authenticator.createCredentials();
        String secret = generated.getKey();

        TwoFactorCredential credential = credentialRepository.findByAdminId(adminId)
                .orElseGet(TwoFactorCredential::new);
        credential.setAdminId(adminId);
        credential.setEnabled(false);
        credential.setEncryptedSecret(secretCipher.encrypt(secret));
        clearChallenge(credential);
        credentialRepository.save(credential);

        String issuer = urlEncode(appName);
        String account = urlEncode(admin.getUsername());
        String otpAuthUrl = "otpauth://totp/" + issuer + ":" + account
                + "?secret=" + secret + "&issuer=" + issuer + "&algorithm=SHA1&digits=6&period=30";
        return new TwoFactorSetupResponse(secret, generateQrCode(otpAuthUrl), secret);
    }

    @Transactional
    public boolean verifyAndEnable(Long adminId, String code) {
        requireAdmin(adminId);
        TwoFactorCredential credential = credentialRepository.findForUpdateByAdminId(adminId)
                .orElseThrow(() -> new IllegalStateException("Set up two-factor authentication first"));
        if (!acceptCode(credential, code)) {
            return false;
        }
        credential.setEnabled(true);
        credentialRepository.save(credential);
        return true;
    }

    @Transactional
    public boolean verifyCode(Long adminId, String code) {
        TwoFactorCredential credential = credentialRepository.findForUpdateByAdminId(adminId)
                .filter(TwoFactorCredential::isEnabled)
                .orElseThrow(() -> new IllegalStateException("Two-factor authentication is not enabled"));
        boolean accepted = acceptCode(credential, code);
        if (accepted) {
            credentialRepository.save(credential);
        }
        return accepted;
    }

    @Transactional
    public boolean verifyCodeByUsername(String username, String code, String challengeToken) {
        Optional<Admin> admin = adminRepository.findByUsernameOrEmail(username, username);
        if (admin.isEmpty()) {
            return false;
        }
        Optional<TwoFactorCredential> stored = credentialRepository.findForUpdateByAdminId(admin.get().getId());
        if (stored.isEmpty() || !stored.get().isEnabled()) {
            return false;
        }

        TwoFactorCredential credential = stored.get();
        if (!validChallenge(credential, challengeToken)) {
            registerChallengeFailure(credential);
            return false;
        }
        if (!acceptCode(credential, code)) {
            registerChallengeFailure(credential);
            return false;
        }

        clearChallenge(credential);
        credentialRepository.save(credential);
        return true;
    }

    @Transactional
    public void disable(Long adminId, String code) {
        if (!verifyCode(adminId, code)) {
            throw new IllegalArgumentException("Invalid verification code");
        }
        credentialRepository.deleteByAdminId(adminId);
    }

    public boolean isTwoFactorEnabled(Long adminId) {
        requireAdmin(adminId);
        return credentialRepository.findByAdminId(adminId)
                .map(TwoFactorCredential::isEnabled)
                .orElse(false);
    }

    public boolean isTwoFactorEnabledByUsername(String username) {
        Admin admin = adminRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));
        return credentialRepository.findByAdminId(admin.getId())
                .map(TwoFactorCredential::isEnabled)
                .orElse(false);
    }

    public Admin getAdminByUsername(String username) {
        return adminRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));
    }

    @Transactional
    public Admin markLoginSuccessful(String username) {
        Admin admin = getAdminByUsername(username);
        admin.setLastLoginAt(LocalDateTime.now());
        return adminRepository.save(admin);
    }

    private Admin requireAdmin(Long adminId) {
        return adminRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));
    }

    private boolean validChallenge(TwoFactorCredential credential, String presentedToken) {
        return credential.getChallengeHash() != null
                && presentedToken != null
                && credential.getChallengeExpiresAt() != null
                && credential.getChallengeExpiresAt().isAfter(LocalDateTime.now())
                && credential.getChallengeAttempts() < MAX_CHALLENGE_ATTEMPTS
                && MessageDigest.isEqual(
                        credential.getChallengeHash().getBytes(StandardCharsets.US_ASCII),
                        hash(presentedToken).getBytes(StandardCharsets.US_ASCII));
    }

    private void registerChallengeFailure(TwoFactorCredential credential) {
        credential.setChallengeAttempts(credential.getChallengeAttempts() + 1);
        if (credential.getChallengeAttempts() >= MAX_CHALLENGE_ATTEMPTS
                || credential.getChallengeExpiresAt() == null
                || !credential.getChallengeExpiresAt().isAfter(LocalDateTime.now())) {
            clearChallenge(credential);
        }
        credentialRepository.save(credential);
    }

    private boolean acceptCode(TwoFactorCredential credential, String code) {
        if (code == null || !code.matches("\\d{6}")) {
            return false;
        }
        String codeHash = hash(code);
        if (codeHash.equals(credential.getLastCodeHash())
                && credential.getLastCodeAcceptedAt() != null
                && credential.getLastCodeAcceptedAt().isAfter(LocalDateTime.now().minusSeconds(90))) {
            return false;
        }
        if (!authenticator.authorize(secretCipher.decrypt(credential.getEncryptedSecret()), Integer.parseInt(code))) {
            return false;
        }
        credential.setLastCodeHash(codeHash);
        credential.setLastCodeAcceptedAt(LocalDateTime.now());
        return true;
    }

    private void clearChallenge(TwoFactorCredential credential) {
        credential.setChallengeHash(null);
        credential.setChallengeExpiresAt(null);
        credential.setChallengeAttempts(0);
    }

    private String randomToken() {
        byte[] bytes = new byte[CHALLENGE_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String generateQrCode(String value) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 250, 250);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (WriterException | IOException exception) {
            throw new IllegalStateException("Could not generate the QR code", exception);
        }
    }
}
