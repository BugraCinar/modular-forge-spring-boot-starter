package dev.modularforge;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

class ModularForgeApplicationMainTest {

    @Test
    void mainSetsApplicationTimezoneAndStartsSpring() {
        TimeZone previous = TimeZone.getDefault();
        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            String[] args = {"--spring.main.web-application-type=none"};

            ModularForgeApplication.main(args);

            assertThat(TimeZone.getDefault().getID()).isEqualTo("UTC");
            springApplication.verify(() -> SpringApplication.run(ModularForgeApplication.class, args));
        } finally {
            TimeZone.setDefault(previous);
        }
    }
}
