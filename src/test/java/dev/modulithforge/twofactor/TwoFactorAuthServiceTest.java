package dev.modulithforge.twofactor;

import dev.modulithforge.auth.SecondFactorGateway;
import dev.modulithforge.identity.AdminRepository;
import dev.modulithforge.identity.model.Admin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TwoFactorAuthServiceTest {

    @Mock
    private AdminRepository adminRepository;
    @Mock
    private TwoFactorCredentialRepository credentialRepository;
    @Mock
    private TwoFactorSecretCipher secretCipher;

    private TwoFactorAuthService service;

    @BeforeEach
    void setUp() {
        service = new TwoFactorAuthService(adminRepository, credentialRepository, secretCipher);
        ReflectionTestUtils.setField(service, "appName", "ModulithForge");
        ReflectionTestUtils.setField(service, "challengeTtlSeconds", 300L);
    }

    @Test
    void enabledCredentialCreatesHashedSingleUseChallenge() {
        TwoFactorCredential credential = credential(7L, true);
        when(credentialRepository.findByAdminId(7L)).thenReturn(Optional.of(credential));
        when(credentialRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        Optional<SecondFactorGateway.Challenge> result = service.beginChallenge(7L);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().token()).isNotBlank();
        assertThat(credential.getChallengeHash())
                .hasSize(64)
                .isNotEqualTo(result.orElseThrow().token());
        assertThat(credential.getChallengeExpiresAt()).isAfter(LocalDateTime.now());
        verify(credentialRepository).save(credential);
    }

    @Test
    void disabledCredentialDoesNotCreateAChallenge() {
        when(credentialRepository.findByAdminId(7L)).thenReturn(Optional.of(credential(7L, false)));

        assertThat(service.beginChallenge(7L)).isEmpty();
        verify(credentialRepository, never()).save(any());
    }

    @Test
    void setupStoresOnlyEncryptedSecret() {
        Admin admin = new Admin();
        admin.setId(7L);
        admin.setUsername("admin");
        when(adminRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(credentialRepository.findByAdminId(7L)).thenReturn(Optional.empty());
        when(secretCipher.encrypt(any())).thenReturn("encrypted-value");
        when(credentialRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        var response = service.generateSecret(7L);

        ArgumentCaptor<TwoFactorCredential> captor = ArgumentCaptor.forClass(TwoFactorCredential.class);
        verify(credentialRepository).save(captor.capture());
        assertThat(captor.getValue().getEncryptedSecret()).isEqualTo("encrypted-value");
        assertThat(response.getSecret()).isNotBlank();
        assertThat(response.getQrCodeUrl()).startsWith("data:image/png;base64,");
    }

    @Test
    void invalidChallengeIncrementsAttemptsWithoutCheckingTotp() {
        TwoFactorCredential credential = credential(7L, true);
        credential.setChallengeHash("not-the-presented-token-hash");
        credential.setChallengeExpiresAt(LocalDateTime.now().plusMinutes(2));
        Admin admin = new Admin();
        admin.setId(7L);
        when(adminRepository.findByUsernameOrEmail("admin", "admin")).thenReturn(Optional.of(admin));
        when(credentialRepository.findForUpdateByAdminId(7L)).thenReturn(Optional.of(credential));

        assertThat(service.verifyCodeByUsername("admin", "123456", "wrong-token")).isFalse();
        assertThat(credential.getChallengeAttempts()).isEqualTo(1);
        verify(credentialRepository).save(credential);
        verify(secretCipher, never()).decrypt(any());
    }

    private TwoFactorCredential credential(Long adminId, boolean enabled) {
        TwoFactorCredential credential = new TwoFactorCredential();
        credential.setAdminId(adminId);
        credential.setEnabled(enabled);
        credential.setEncryptedSecret("encrypted-secret");
        return credential;
    }
}
