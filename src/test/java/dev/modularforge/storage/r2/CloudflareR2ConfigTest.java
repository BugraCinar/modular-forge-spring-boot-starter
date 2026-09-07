package dev.modularforge.storage.r2;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CloudflareR2ConfigTest {

    @Test
    void exposesPropertiesAndBuildsTheClient() {
        var properties = new CloudflareR2Config.R2Properties(
                "access", "secret", "account", "bucket", "https://cdn.example.com");
        CloudflareR2Config config = new CloudflareR2Config(properties);

        assertThat(properties.accessKey()).isEqualTo("access");
        assertThat(properties.secretKey()).isEqualTo("secret");
        assertThat(properties.accountId()).isEqualTo("account");
        assertThat(config.getBucketName()).isEqualTo("bucket");
        assertThat(config.getPublicDomain()).isEqualTo("https://cdn.example.com");

        try (var client = config.cloudflareR2Client()) {
            assertThat(client.serviceName()).isEqualTo("s3");
        }
    }
}
