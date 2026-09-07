package dev.modularforge.identity.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityAuthVersionTest {

    @Test
    void userAndAdminExposeBothDefaultAndPersistedAuthorizationVersions() {
        User user = new User();
        Admin admin = new Admin();
        user.setAuthVersion(null);
        admin.setAuthVersion(null);
        assertThat(user.currentAuthVersion()).isZero();
        assertThat(admin.currentAuthVersion()).isZero();

        user.setAuthVersion(4L);
        admin.setAuthVersion(7L);
        assertThat(user.currentAuthVersion()).isEqualTo(4L);
        assertThat(admin.currentAuthVersion()).isEqualTo(7L);
    }
}
