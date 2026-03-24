package com.tenpo.challenge.unit.service;

import com.tenpo.challenge.service.CacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de {@link CacheService}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CacheService Unit Tests")
class CacheServiceTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @SuppressWarnings("unchecked")
    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    @InjectMocks
    private CacheService cacheService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(cacheService, "percentageTtlMinutes", 30L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Nested
    @DisplayName("getFreshPercentage")
    class GetFreshPercentage {

        @Test
        @DisplayName("emite vacio cuando la clave no existe en Redis")
        void shouldEmitEmptyWhenKeyNotPresent() {
            when(valueOperations.get(CacheService.FRESH_KEY)).thenReturn(Mono.empty());

            StepVerifier.create(cacheService.getFreshPercentage())
                .verifyComplete();
        }

        @Test
        @DisplayName("emite el valor cuando existe en cache fresca")
        void shouldEmitValueWhenKeyPresent() {
            when(valueOperations.get(CacheService.FRESH_KEY)).thenReturn(Mono.just("15.50"));

            StepVerifier.create(cacheService.getFreshPercentage())
                .assertNext(pct -> assertThat(pct).isEqualByComparingTo(new BigDecimal("15.50")))
                .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getFallbackPercentage")
    class GetFallbackPercentage {

        @Test
        @DisplayName("emite vacio cuando no hay valor de fallback")
        void shouldEmitEmptyWhenNoFallback() {
            when(valueOperations.get(CacheService.FALLBACK_KEY)).thenReturn(Mono.empty());

            StepVerifier.create(cacheService.getFallbackPercentage())
                .verifyComplete();
        }

        @Test
        @DisplayName("emite el ultimo valor conocido aunque sea obsoleto")
        void shouldEmitStaleValueAsFallback() {
            when(valueOperations.get(CacheService.FALLBACK_KEY)).thenReturn(Mono.just("10.0"));

            StepVerifier.create(cacheService.getFallbackPercentage())
                .assertNext(pct -> assertThat(pct).isEqualByComparingTo(BigDecimal.TEN))
                .verifyComplete();
        }
    }

    @Nested
    @DisplayName("updatePercentage")
    class UpdatePercentage {

        @Test
        @DisplayName("persiste el valor en FRESH_KEY con TTL y en FALLBACK_KEY sin TTL")
        void shouldPersistBothKeys() {
            when(valueOperations.set(eq(CacheService.FRESH_KEY), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));
            when(valueOperations.set(eq(CacheService.FALLBACK_KEY), anyString()))
                .thenReturn(Mono.just(true));

            StepVerifier.create(cacheService.updatePercentage(new BigDecimal("12.5")))
                .verifyComplete();

            verify(valueOperations).set(eq(CacheService.FRESH_KEY), eq("12.5"), eq(Duration.ofMinutes(30)));
            verify(valueOperations).set(eq(CacheService.FALLBACK_KEY), eq("12.5"));
        }

        @Test
        @DisplayName("uso de toPlainString() para evitar la notacion cientifica")
        void shouldUsePlainStringRepresentation() {
            when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));
            when(valueOperations.set(anyString(), anyString()))
                .thenReturn(Mono.just(true));

            // 1E+2 en BigDecimal → toPlainString() → "100"
            StepVerifier.create(cacheService.updatePercentage(new BigDecimal("1E+2")))
                .verifyComplete();

            verify(valueOperations).set(eq(CacheService.FRESH_KEY), eq("100"), any(Duration.class));
        }
    }

    @Nested
    @DisplayName("evict")
    class Evict {

        @Test
        @DisplayName("elimina ambas claves de Redis")
        void shouldDeleteBothKeysAndComplete() {
            when(redisTemplate.delete(CacheService.FRESH_KEY)).thenReturn(Mono.just(1L));
            when(redisTemplate.delete(CacheService.FALLBACK_KEY)).thenReturn(Mono.just(1L));

            StepVerifier.create(cacheService.evict())
                .verifyComplete();

            verify(redisTemplate).delete(CacheService.FRESH_KEY);
            verify(redisTemplate).delete(CacheService.FALLBACK_KEY);
        }
    }
}
