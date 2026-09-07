package dev.modularforge.identity.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleTest {

    @Test
    void exposesSupportedIdentityRoles() {
        assertThat(Role.values()).containsExactly(Role.USER, Role.ADMIN);
        assertThat(Role.valueOf("ADMIN")).isEqualTo(Role.ADMIN);
    }
}
