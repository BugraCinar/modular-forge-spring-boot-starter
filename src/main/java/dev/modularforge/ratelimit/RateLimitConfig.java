package dev.modularforge.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfig {

    @Value("${app.rate-limit.global.requests}")
    private int globalRequests;

    @Value("${app.rate-limit.global.window}")
    private long globalWindow;

    @Value("${app.rate-limit.api.calls}")
    private int apiCalls;

    @Value("${app.rate-limit.api.window}")
    private long apiWindow;

    @Value("${app.rate-limit.chat.requests}")
    private int chatRequests;

    @Value("${app.rate-limit.chat.window}")
    private long chatWindow;

    @Value("${app.rate-limit.login.attempts}")
    private int loginAttempts;

    @Value("${app.rate-limit.login.window}")
    private long loginWindow;

    @Value("${app.rate-limit.email-verification.attempts}")
    private int emailVerificationAttempts;

    @Value("${app.rate-limit.email-verification.window}")
    private long emailVerificationWindow;
    public int getGlobalRequests() { return globalRequests; }
    public long getGlobalWindow() { return globalWindow; }
    public int getApiCalls() { return apiCalls; }
    public long getApiWindow() { return apiWindow; }
    public int getChatRequests() { return chatRequests; }
    public long getChatWindow() { return chatWindow; }
    public int getLoginAttempts() { return loginAttempts; }
    public long getLoginWindow() { return loginWindow; }
    public int getEmailVerificationAttempts() { return emailVerificationAttempts; }
    public long getEmailVerificationWindow() { return emailVerificationWindow; }
}