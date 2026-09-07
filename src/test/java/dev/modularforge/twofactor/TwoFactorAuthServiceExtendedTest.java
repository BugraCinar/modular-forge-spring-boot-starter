package dev.modularforge.twofactor;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import dev.modularforge.identity.AdminRepository;
import dev.modularforge.identity.model.Admin;
import dev.modularforge.shared.error.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TwoFactorAuthServiceExtendedTest {

    private static final String SECRET = "JBSWY3DPEHPK3PXP";

    @Mock AdminRepository admins;
    @Mock TwoFactorCredentialRepository credentials;
    @Mock TwoFactorSecretCipher cipher;

    private TwoFactorAuthService service;
    private Admin admin;

    @BeforeEach
    void setUp() {
        service = new TwoFactorAuthService(admins, credentials, cipher);
        ReflectionTestUtils.setField(service, "appName", "Modular Forge");
        ReflectionTestUtils.setField(service, "challengeTtlSeconds", 300L);
        admin = new Admin();
        admin.setId(7L);
        admin.setUsername("root admin");
        when(cipher.decrypt("encrypted-secret")).thenReturn(SECRET);
        when(credentials.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void beginChallengeAlsoReturnsEmptyWhenCredentialIsMissing() {
        when(credentials.findByAdminId(7L)).thenReturn(Optional.empty());
        assertThat(service.beginChallenge(7L)).isEmpty();
    }

    @Test
    void setupReusesCredentialClearsChallengeAndEncodesIssuerAndAccount() {
        TwoFactorCredential credential = credential(true);
        credential.setChallengeHash("old");
        credential.setChallengeExpiresAt(LocalDateTime.now());
        credential.setChallengeAttempts(4);
        when(admins.findById(7L)).thenReturn(Optional.of(admin));
        when(credentials.findByAdminId(7L)).thenReturn(Optional.of(credential));
        when(cipher.encrypt(any())).thenReturn("encrypted-new-secret");

        var setup = service.generateSecret(7L);

        assertThat(setup.getSecret()).isNotBlank();
        assertThat(setup.getManualEntryKey()).isEqualTo(setup.getSecret());
        assertThat(setup.getQrCodeUrl()).startsWith("data:image/png;base64,");
        assertThat(credential.isEnabled()).isFalse();
        assertThat(credential.getChallengeHash()).isNull();
        assertThat(credential.getChallengeExpiresAt()).isNull();
        assertThat(credential.getChallengeAttempts()).isZero();
    }

    @Test
    void setupRequiresExistingAdmin() {
        when(admins.findById(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.generateSecret(7L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void verifyAndEnableRejectsMissingSetupAndInvalidCodeThenAcceptsTotp() {
        when(admins.findById(7L)).thenReturn(Optional.of(admin));
        when(credentials.findForUpdateByAdminId(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.verifyAndEnable(7L, "123456"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Set up");

        TwoFactorCredential credential = credential(false);
        when(credentials.findForUpdateByAdminId(7L)).thenReturn(Optional.of(credential));
        assertThat(service.verifyAndEnable(7L, "invalid")).isFalse();

        String code = currentCode();
        assertThat(service.verifyAndEnable(7L, code)).isTrue();
        assertThat(credential.isEnabled()).isTrue();
        assertThat(credential.getLastCodeHash()).isNotBlank();
    }

    @Test
    void verifyCodeRequiresEnabledCredentialRejectsReplayAndSavesAcceptedCode() {
        when(credentials.findForUpdateByAdminId(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.verifyCode(7L, "123456")).isInstanceOf(IllegalStateException.class);

        when(credentials.findForUpdateByAdminId(7L)).thenReturn(Optional.of(credential(false)));
        assertThatThrownBy(() -> service.verifyCode(7L, "123456")).isInstanceOf(IllegalStateException.class);

        TwoFactorCredential enabled = credential(true);
        when(credentials.findForUpdateByAdminId(7L)).thenReturn(Optional.of(enabled));
        assertThat(service.verifyCode(7L, null)).isFalse();
        verify(credentials, never()).save(enabled);

        String code = currentCode();
        enabled.setLastCodeHash(hash(code));
        enabled.setLastCodeAcceptedAt(null);
        assertThat(service.verifyCode(7L, code)).isTrue();
        verify(credentials).save(enabled);
        assertThat(service.verifyCode(7L, code)).isFalse();

        enabled.setLastCodeAcceptedAt(LocalDateTime.now().minusSeconds(91));
        assertThat(service.verifyCode(7L, code)).isTrue();
    }

    @Test
    void usernameVerificationRejectsMissingAdminCredentialAndDisabledCredential() {
        when(admins.findByUsernameOrEmail("missing", "missing")).thenReturn(Optional.empty());
        assertThat(service.verifyCodeByUsername("missing", "123456", "challenge")).isFalse();

        when(admins.findByUsernameOrEmail("root", "root")).thenReturn(Optional.of(admin));
        when(credentials.findForUpdateByAdminId(7L)).thenReturn(Optional.empty());
        assertThat(service.verifyCodeByUsername("root", "123456", "challenge")).isFalse();
        when(credentials.findForUpdateByAdminId(7L)).thenReturn(Optional.of(credential(false)));
        assertThat(service.verifyCodeByUsername("root", "123456", "challenge")).isFalse();
    }

    @Test
    void usernameVerificationChecksEveryChallengeConditionAndLocksAfterFailures() {
        when(admins.findByUsernameOrEmail("root", "root")).thenReturn(Optional.of(admin));
        TwoFactorCredential credential = credential(true);
        when(credentials.findForUpdateByAdminId(7L)).thenReturn(Optional.of(credential));

        credential.setChallengeHash(null);
        assertThat(service.verifyCodeByUsername("root", "123456", "challenge")).isFalse();
        credential.setChallengeHash(hash("challenge"));
        credential.setChallengeExpiresAt(LocalDateTime.now().plusMinutes(1));
        assertThat(service.verifyCodeByUsername("root", "123456", null)).isFalse();
        credential.setChallengeHash(hash("challenge"));
        credential.setChallengeExpiresAt(null);
        assertThat(service.verifyCodeByUsername("root", "123456", "challenge")).isFalse();
        credential.setChallengeHash(hash("challenge"));
        credential.setChallengeExpiresAt(LocalDateTime.now().minusSeconds(1));
        assertThat(service.verifyCodeByUsername("root", "123456", "challenge")).isFalse();
        credential.setChallengeHash(hash("challenge"));
        credential.setChallengeExpiresAt(LocalDateTime.now().plusMinutes(1));
        credential.setChallengeAttempts(5);
        assertThat(service.verifyCodeByUsername("root", "123456", "challenge")).isFalse();
        assertThat(credential.getChallengeHash()).isNull();

        credential.setChallengeHash(hash("challenge"));
        credential.setChallengeExpiresAt(LocalDateTime.now().plusMinutes(1));
        credential.setChallengeAttempts(0);
        assertThat(service.verifyCodeByUsername("root", "123456", "wrong")).isFalse();
        assertThat(credential.getChallengeAttempts()).isEqualTo(1);
    }

    @Test
    void usernameVerificationHandlesInvalidTotpAndConsumesValidChallenge() {
        when(admins.findByUsernameOrEmail("root", "root")).thenReturn(Optional.of(admin));
        TwoFactorCredential credential = credential(true);
        credential.setChallengeHash(hash("challenge"));
        credential.setChallengeExpiresAt(LocalDateTime.now().plusMinutes(1));
        when(credentials.findForUpdateByAdminId(7L)).thenReturn(Optional.of(credential));

        assertThat(service.verifyCodeByUsername("root", "bad", "challenge")).isFalse();
        assertThat(credential.getChallengeAttempts()).isEqualTo(1);

        credential.setChallengeHash(hash("challenge"));
        credential.setChallengeExpiresAt(LocalDateTime.now().plusMinutes(1));
        credential.setChallengeAttempts(0);
        assertThat(service.verifyCodeByUsername("root", currentCode(), "challenge")).isTrue();
        assertThat(credential.getChallengeHash()).isNull();
        assertThat(credential.getChallengeExpiresAt()).isNull();
    }

    @Test
    void disableRejectsInvalidCodeAndDeletesCredentialAfterValidCode() {
        TwoFactorCredential credential = credential(true);
        when(credentials.findForUpdateByAdminId(7L)).thenReturn(Optional.of(credential));
        assertThatThrownBy(() -> service.disable(7L, "bad")).isInstanceOf(IllegalArgumentException.class);
        service.disable(7L, currentCode());
        verify(credentials).deleteByAdminId(7L);
    }

    @Test
    void statusLookupAdminLookupAndSuccessfulLoginCoverAllResults() {
        when(admins.findById(7L)).thenReturn(Optional.of(admin));
        when(credentials.findByAdminId(7L)).thenReturn(Optional.empty(), Optional.of(credential(false)),
                Optional.of(credential(true)));
        assertThat(service.isTwoFactorEnabled(7L)).isFalse();
        assertThat(service.isTwoFactorEnabled(7L)).isFalse();
        assertThat(service.isTwoFactorEnabled(7L)).isTrue();

        when(admins.findByUsernameOrEmail("root", "root")).thenReturn(Optional.of(admin));
        when(credentials.findByAdminId(7L)).thenReturn(Optional.empty(), Optional.of(credential(true)));
        assertThat(service.isTwoFactorEnabledByUsername("root")).isFalse();
        assertThat(service.isTwoFactorEnabledByUsername("root")).isTrue();
        assertThat(service.getAdminByUsername("root")).isSameAs(admin);
        when(admins.save(admin)).thenReturn(admin);
        assertThat(service.markLoginSuccessful("root").getLastLoginAt()).isNotNull();

        when(admins.findByUsernameOrEmail("missing", "missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.isTwoFactorEnabledByUsername("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.getAdminByUsername("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
        when(admins.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.isTwoFactorEnabled(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void invalidTotpAndUnavailableSha256FailSafely() {
        TwoFactorCredential credential = credential(true);
        GoogleAuthenticator authenticator = mock(GoogleAuthenticator.class);
        ReflectionTestUtils.setField(service, "authenticator", authenticator);
        when(cipher.decrypt("encrypted-secret")).thenReturn(SECRET);
        when(authenticator.authorize(SECRET, 0)).thenReturn(false);
        assertThat((boolean) ReflectionTestUtils.invokeMethod(service, "acceptCode", credential, "000000"))
                .isFalse();

        try (var digest = mockStatic(MessageDigest.class)) {
            digest.when(() -> MessageDigest.getInstance("SHA-256"))
                    .thenThrow(new NoSuchAlgorithmException("missing"));
            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "hash", "value"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SHA-256");
        }
    }

    @Test
    void qrWriterFailureIsMappedToStableException() {
        try (var writers = mockConstruction(com.google.zxing.qrcode.QRCodeWriter.class,
                (mock, context) -> when(mock.encode(any(), any(), any(Integer.class), any(Integer.class)))
                        .thenThrow(new com.google.zxing.WriterException("cannot encode")))) {
            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "generateQrCode", "otpauth://test"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("QR code");
        }
    }

    private TwoFactorCredential credential(boolean enabled) {
        TwoFactorCredential credential = new TwoFactorCredential();
        credential.setAdminId(7L);
        credential.setEnabled(enabled);
        credential.setEncryptedSecret("encrypted-secret");
        credential.setChallengeAttempts(0);
        return credential;
    }

    private String currentCode() {
        return "%06d".formatted(new GoogleAuthenticator().getTotpPassword(SECRET));
    }

    private String hash(String value) {
        return ReflectionTestUtils.invokeMethod(service, "hash", value);
    }
}
