package dev.modularforge.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfig {

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${server.port}")
    private int serverPort;

    @Value("${app.security.pepper}")
    private String pepper;
    @Value("${app.security.argon2.memory-cost}")
    private int argon2MemoryCost;

    @Value("${app.security.argon2.time-cost}")
    private int argon2TimeCost;

    @Value("${app.security.argon2.parallelism}")
    private int argon2Parallelism;

    @Value("${app.security.argon2.salt-length}")
    private int argon2SaltLength;

    @Value("${app.security.argon2.hash-length}")
    private int argon2HashLength;
    public String getApplicationName() { return applicationName; }
    public int getServerPort() { return serverPort; }
    public String getPepper() { return pepper; }
    public int getArgon2MemoryCost() { return argon2MemoryCost; }
    public int getArgon2TimeCost() { return argon2TimeCost; }
    public int getArgon2Parallelism() { return argon2Parallelism; }
    public int getArgon2SaltLength() { return argon2SaltLength; }
    public int getArgon2HashLength() { return argon2HashLength; }
}