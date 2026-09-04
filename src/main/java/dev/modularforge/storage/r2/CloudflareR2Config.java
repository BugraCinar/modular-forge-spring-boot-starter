package dev.modularforge.storage.r2;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Configuration
@ConditionalOnProperty(prefix = "app.modules.image-storage", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CloudflareR2Config.R2Properties.class)
public class CloudflareR2Config {

    private final R2Properties properties;

    public CloudflareR2Config(R2Properties properties) {
        this.properties = properties;
    }

    @Bean
    public S3Client cloudflareR2Client() {
        return S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                .endpointOverride(URI.create("https://" + properties.accountId() + ".r2.cloudflarestorage.com"))
                .region(Region.US_EAST_1)
                .build();
    }

    public String getBucketName() {
        return properties.bucketName();
    }

    public String getPublicDomain() {
        return properties.publicDomain();
    }

    @ConfigurationProperties("app.modules.image-storage.r2")
    public record R2Properties(
            @NotBlank String accessKey,
            @NotBlank String secretKey,
            @NotBlank String accountId,
            @NotBlank String bucketName,
            @NotBlank String publicDomain) {
    }
}
