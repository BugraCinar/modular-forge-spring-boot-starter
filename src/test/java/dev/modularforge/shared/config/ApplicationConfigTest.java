package dev.modularforge.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationConfigTest {

    @Test
    void exposesApplicationAndPasswordHashSettings() {
        ApplicationConfig config = new ApplicationConfig();
        ReflectionTestUtils.setField(config, "applicationName", "modular-forge");
        ReflectionTestUtils.setField(config, "serverPort", 8081);
        ReflectionTestUtils.setField(config, "pepper", "test-pepper");
        ReflectionTestUtils.setField(config, "argon2MemoryCost", 65_536);
        ReflectionTestUtils.setField(config, "argon2TimeCost", 3);
        ReflectionTestUtils.setField(config, "argon2Parallelism", 2);
        ReflectionTestUtils.setField(config, "argon2SaltLength", 16);
        ReflectionTestUtils.setField(config, "argon2HashLength", 32);

        assertThat(config.getApplicationName()).isEqualTo("modular-forge");
        assertThat(config.getServerPort()).isEqualTo(8081);
        assertThat(config.getPepper()).isEqualTo("test-pepper");
        assertThat(config.getArgon2MemoryCost()).isEqualTo(65_536);
        assertThat(config.getArgon2TimeCost()).isEqualTo(3);
        assertThat(config.getArgon2Parallelism()).isEqualTo(2);
        assertThat(config.getArgon2SaltLength()).isEqualTo(16);
        assertThat(config.getArgon2HashLength()).isEqualTo(32);
    }
}
