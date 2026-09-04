package dev.modularforge.ratelimit;
public interface RateLimitStore {

    long incrementWithTtl(String key, long windowMs);

    void delete(String key);

    Long getCount(String key);

    Long getTtlMillis(String key);
}
