package dev.modularforge.ratelimit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisRateLimitStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void executesAtomicIncrementScriptWithWindow() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("rate:key")), eq("5000")))
                .thenReturn(3L);
        RedisRateLimitStore store = new RedisRateLimitStore(redisTemplate);

        assertThat(store.incrementWithTtl("rate:key", 5_000L)).isEqualTo(3L);
    }

    @Test
    void rejectsMissingCounterResult() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("rate:key")), eq("5000")))
                .thenReturn(null);
        RedisRateLimitStore store = new RedisRateLimitStore(redisTemplate);

        assertThatThrownBy(() -> store.incrementWithTtl("rate:key", 5_000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Redis did not return a rate-limit counter value");
    }

    @Test
    void delegatesDeleteCountAndTtlOperations() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("rate:key")).thenReturn("7");
        when(redisTemplate.getExpire("rate:key", TimeUnit.MILLISECONDS)).thenReturn(2_500L);
        RedisRateLimitStore store = new RedisRateLimitStore(redisTemplate);

        store.delete("rate:key");

        verify(redisTemplate).delete("rate:key");
        assertThat(store.getCount("rate:key")).isEqualTo(7L);
        assertThat(store.getTtlMillis("rate:key")).isEqualTo(2_500L);
    }

    @Test
    void returnsNullWhenCounterDoesNotExist() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("missing-key")).thenReturn(null);
        RedisRateLimitStore store = new RedisRateLimitStore(redisTemplate);

        assertThat(store.getCount("missing-key")).isNull();
    }
}
