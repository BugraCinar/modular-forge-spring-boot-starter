package dev.modularforge.ratelimit;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
@Service
public class RedisRateLimitStore implements RateLimitStore {

    private static final DefaultRedisScript<Long> INCREMENT_WITH_TTL = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]); "
                    + "if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]); end; "
                    + "return count;",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimitStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public long incrementWithTtl(String key, long windowMs) {
        Long count = redisTemplate.execute(INCREMENT_WITH_TTL, List.of(key), String.valueOf(windowMs));
        if (count == null) {
            throw new IllegalStateException("Redis did not return a rate-limit counter value");
        }
        return count;
    }

    @Override
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    @Override
    public Long getCount(String key) {
        String value = redisTemplate.opsForValue().get(key);
        return value == null ? null : Long.parseLong(value);
    }

    @Override
    public Long getTtlMillis(String key) {
        return redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
    }
}
