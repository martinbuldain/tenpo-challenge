package com.tenpo.challenge.service.impl;

import com.tenpo.challenge.domain.dto.PercentageResponse;
import com.tenpo.challenge.domain.dto.PercentageResult;
import com.tenpo.challenge.exception.CacheUnavailableException;
import com.tenpo.challenge.exception.ExternalServiceException;
import com.tenpo.challenge.service.CacheService;
import com.tenpo.challenge.service.ExternalPercentageService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.Duration;

import static com.tenpo.challenge.domain.dto.PercentageResult.*;

/**
 * <pre>
 * fetchPercentage()
 *   ├─ CacheService.getFreshPercentage()  → HIT  → retorna (Mono con valor)
 *   └─ MISS (Mono.empty) → switchIfEmpty(refreshPercentage())
 *        └─ acquireDistributedLock() [Redis SETNX reactivo]
 *             ├─ lock acquired → callExternalService()
 *             │    └─ WebClient.get()
 *             │         └─ .retryWhen(Retry.backoff(3, 500ms))
 *             │              └─ transformDeferred(CircuitBreakerOperator)
 *             │                   ├─ OK  → updateCache() → emite BigDecimal
 *             │                   └─ FAIL → onErrorResume → getFallbackPercentage()
 *             └─ lock NOT acquired → getFallbackPercentage()
 * </pre>
 */
@Service
@Slf4j
public class ExternalPercentageServiceImpl implements ExternalPercentageService {

    private static final String REFRESH_LOCK_KEY  = "lock:percentage:refresh";
    private static final Duration LOCK_TTL        = Duration.ofSeconds(10);
    private static final String CB_NAME           = "externalPercentage";
    private static final int MAX_RETRY_ATTEMPTS   = 3;
    private static final Duration RETRY_MIN_BACKOFF = Duration.ofMillis(500);

    private final WebClient          webClient;
    private final CacheService       cacheService;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final CircuitBreaker     circuitBreaker;

    @Value("${app.external-service.url}")
    private String externalServiceUrl;

    public ExternalPercentageServiceImpl(
            WebClient externalPercentageWebClient,
            CacheService cacheService,
            ReactiveStringRedisTemplate redisTemplate,
            CircuitBreakerRegistry circuitBreakerRegistry) {

        this.webClient      = externalPercentageWebClient;
        this.cacheService   = cacheService;
        this.redisTemplate  = redisTemplate;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(CB_NAME);
    }

    @Override
    public Mono<PercentageResult> fetchPercentage() {
        return cacheService.getFreshPercentage()
            .map(pct -> new PercentageResult(pct, SOURCE_CACHE))
            .switchIfEmpty(Mono.defer(this::refreshWithLock));
    }

    private Mono<PercentageResult> refreshWithLock() {
        return redisTemplate.opsForValue()
            .setIfAbsent(REFRESH_LOCK_KEY, "1", LOCK_TTL)
            .flatMap(lockAcquired -> {
                if (Boolean.TRUE.equals(lockAcquired)) {
                    return fetchFromExternalWithResilience()
                        .doFinally(signal -> redisTemplate.delete(REFRESH_LOCK_KEY).subscribe());
                } else {
                    log.info("Lock held by another instance; using fallback cache");
                    return resolveFallback(null);
                }
            });
    }

    // Retry está DENTRO del circuit breaker: si el CB está abierto, el error es inmediato sin reintentos.
    private Mono<PercentageResult> fetchFromExternalWithResilience() {
        return callExternalService()
            .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, RETRY_MIN_BACKOFF)
                .filter(ex -> ex instanceof ExternalServiceException)
                .doBeforeRetry(signal ->
                    log.warn("Retry attempt {} after failure: {}",
                        signal.totalRetries() + 1, signal.failure().getMessage())))
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
            .onErrorResume(ex -> {
                log.warn("All attempts failed [cause={}]; trying fallback cache", ex.getMessage());
                return resolveFallback(ex);
            });
    }

    private Mono<PercentageResult> callExternalService() {
        log.info("Calling external percentage service [url={}]", externalServiceUrl);
        return webClient.get()
            .retrieve()
            .bodyToMono(PercentageResponse.class)
            .flatMap(response -> {
                if (response == null || response.percentage() == null) {
                    return Mono.error(new ExternalServiceException("Respuesta vacía del servicio externo"));
                }
                return cacheService.updatePercentage(response.percentage())
                    .thenReturn(new PercentageResult(response.percentage(), SOURCE_EXTERNAL))
                    .doOnSuccess(r -> log.info("External percentage fetched: {}", r.value()));
            })
            .onErrorMap(WebClientException.class, ex ->
                new ExternalServiceException("Error HTTP al servicio externo: " + ex.getMessage(), ex));
    }

    private Mono<PercentageResult> resolveFallback(Throwable cause) {
        return cacheService.getFallbackPercentage()
            .map(pct -> new PercentageResult(pct, SOURCE_FALLBACK_CACHE))
            .switchIfEmpty(Mono.error(new CacheUnavailableException(
                "Servicio externo no disponible y sin caché de respaldo", cause)));
    }
}
