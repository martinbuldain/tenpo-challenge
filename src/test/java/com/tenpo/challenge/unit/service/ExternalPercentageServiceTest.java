package com.tenpo.challenge.unit.service;

import com.tenpo.challenge.domain.dto.PercentageResponse;
import com.tenpo.challenge.domain.dto.PercentageResult;
import com.tenpo.challenge.exception.CacheUnavailableException;
import com.tenpo.challenge.exception.ExternalServiceException;
import com.tenpo.challenge.service.CacheService;
import com.tenpo.challenge.service.impl.ExternalPercentageServiceImpl;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de {@link ExternalPercentageServiceImpl}.
 * <p>Las aserciones usan {@link StepVerifier} para controlar la suscripción.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ExternalPercentageService Unit Tests")
class ExternalPercentageServiceTest {

    @Mock private CacheService                     cacheService;
    @Mock private ReactiveStringRedisTemplate      redisTemplate;
    @SuppressWarnings("unchecked")
    @Mock private ReactiveValueOperations<String, String> valueOps;
    @Mock private CircuitBreakerRegistry           circuitBreakerRegistry;
    @Mock private CircuitBreaker                   circuitBreaker;
    @Mock private WebClient                        webClient;
    @SuppressWarnings("rawtypes")
    @Mock private WebClient.RequestHeadersUriSpec  requestHeadersUriSpec;
    @Mock private WebClient.ResponseSpec           responseSpec;

    private ExternalPercentageServiceImpl service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        when(circuitBreakerRegistry.circuitBreaker("externalPercentage")).thenReturn(circuitBreaker);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        when(circuitBreaker.getName()).thenReturn("externalPercentage");
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        when(circuitBreaker.tryAcquirePermission()).thenReturn(true);

        service = new ExternalPercentageServiceImpl(
            webClient, cacheService, redisTemplate, circuitBreakerRegistry);

        ReflectionTestUtils.setField(service, "externalServiceUrl", "http://mock/percentage");
    }

    @Nested
    @DisplayName("cuando hay un valor fresco en cache")
    class CacheHit {

        @Test
        @DisplayName("emite el valor cacheado sin llamar al servicio externo")
        void shouldEmitCachedValueWithoutExternalCall() {
            when(cacheService.getFreshPercentage())
                .thenReturn(Mono.just(new BigDecimal("10")));

            StepVerifier.create(service.fetchPercentage())
                .assertNext(result -> {
                    assertThat(result.value()).isEqualByComparingTo("10");
                    assertThat(result.source()).isEqualTo(PercentageResult.SOURCE_CACHE);
                })
                .verifyComplete();

            // El lock de Redis no se tiene que haber invocado (no hubo cache miss)
            verify(valueOps, never()).setIfAbsent(anyString(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("cuando hay cache miss y el servicio externo responde")
    class CacheMissExternalAvailable {

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("llama al servicio externo y emite el porcentaje obtenido")
        void shouldCallExternalAndEmitPercentage() {
            // Cache miss en fresh
            when(cacheService.getFreshPercentage()).thenReturn(Mono.empty());

            // Lock disponible para actualizar cache
            when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));
            when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));

            // Servicio externo responde con 10%
            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(PercentageResponse.class))
                .thenReturn(Mono.just(new PercentageResponse(new BigDecimal("10"))));

            // se actualiza la cache
            when(cacheService.updatePercentage(any())).thenReturn(Mono.empty());

            StepVerifier.create(service.fetchPercentage())
                .assertNext(result -> {
                    assertThat(result.value()).isEqualByComparingTo("10");
                    assertThat(result.source()).isEqualTo(PercentageResult.SOURCE_EXTERNAL);
                })
                .verifyComplete();
        }
    }

    @Nested
    @DisplayName("cuando el servicio externo falla y hay cache de fallback")
    class FallbackCache {

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("emite el fallback cuando el servicio externo falla")
        void shouldEmitFallbackWhenExternalFails() {
            when(cacheService.getFreshPercentage()).thenReturn(Mono.empty());

            // Lock adquirido
            when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));
            when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));

            // Servicio externo falla
            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(PercentageResponse.class))
                .thenReturn(Mono.error(new ExternalServiceException("HTTP 500")));

            // Fallback disponible
            when(cacheService.getFallbackPercentage())
                .thenReturn(Mono.just(new BigDecimal("8")));

            StepVerifier.create(service.fetchPercentage())
                .assertNext(result -> {
                    assertThat(result.value()).isEqualByComparingTo("8");
                    assertThat(result.source()).isEqualTo(PercentageResult.SOURCE_FALLBACK_CACHE);
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("emite CacheUnavailableException cuando no hay fallback")
        void shouldEmitCacheUnavailableWhenNoFallback() {
            when(cacheService.getFreshPercentage()).thenReturn(Mono.empty());

            when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));
            when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));

            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(PercentageResponse.class))
                .thenReturn(Mono.error(new ExternalServiceException("HTTP 503")));

            // Sin fallback
            when(cacheService.getFallbackPercentage()).thenReturn(Mono.empty());

            StepVerifier.create(service.fetchPercentage())
                .expectErrorMatches(ex -> ex instanceof CacheUnavailableException)
                .verify();
        }
    }

    @Nested
    @DisplayName("cuando el lock de Redis no se puede adquirir")
    class LockNotAcquired {

        @Test
        @DisplayName("usa fallback cuando otra replica ya tiene el lock")
        void shouldUseFallbackWhenLockHeld() {
            when(cacheService.getFreshPercentage()).thenReturn(Mono.empty());

            // Lock ya adquirido por otra instancia
            when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(false));

            when(cacheService.getFallbackPercentage())
                .thenReturn(Mono.just(new BigDecimal("12")));

            StepVerifier.create(service.fetchPercentage())
                .assertNext(result -> {
                    assertThat(result.value()).isEqualByComparingTo("12");
                    assertThat(result.source()).isEqualTo(PercentageResult.SOURCE_FALLBACK_CACHE);
                })
                .verifyComplete();

            // El servicio externo no se debio haber llamado
            verify(webClient, never()).get();
        }
    }
}
