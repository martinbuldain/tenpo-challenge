package com.tenpo.challenge.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Servicio de cache distribuida usando Redis Reactivo.
 *
 * <p><strong>Estrategia de dos claves:</strong>
 * <ul>
 *   <li>{@code percentage:fresh}    – TTL 30 min. Expira al isntante</li>
 *   <li>{@code percentage:fallback} – Sin TTL. Ultimo valor conocido</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CacheService {

    public static final String FRESH_KEY    = "percentage:fresh";
    public static final String FALLBACK_KEY = "percentage:fallback";

    private final ReactiveStringRedisTemplate redisTemplate;

    @Value("${app.cache.percentage-ttl-minutes:30}")
    private long percentageTtlMinutes;

    /**
     * Retorna el porcentaje fresco, dentro del TTL configurado
     * {@code Mono.empty()} si no existe o expiro.
     */
    public Mono<BigDecimal> getFreshPercentage() {
        return redisTemplate.opsForValue()
            .get(FRESH_KEY)
            .doOnNext(v -> log.debug("Cache HIT: fresh percentage = {}", v))
            .switchIfEmpty(Mono.defer(() -> { log.debug("Cache MISS: fresh percentage not found"); return Mono.empty(); }))
            .map(BigDecimal::new);
    }

    /**
     * Retorna el ultimo porcentaje conocido, que podria ser obsoleto); pero
     * usado como fallback cuando el servicio externo falla.
     */
    public Mono<BigDecimal> getFallbackPercentage() {
        return redisTemplate.opsForValue()
            .get(FALLBACK_KEY)
            .doOnNext(v -> log.warn("Using FALLBACK (potentially stale) percentage = {}", v))
            .switchIfEmpty(Mono.defer(() -> { log.warn("Cache MISS: no fallback percentage available"); return Mono.empty(); }))
            .map(BigDecimal::new);
    }

    /**
     * Guarda el porcentaje actualizado en ambas claves de Redis.
     * Solo se llama cuando el servicio externo responde bien.
     */
    public Mono<Void> updatePercentage(BigDecimal percentage) {
        String value = percentage.toPlainString();
        return Mono.when(
            redisTemplate.opsForValue().set(FRESH_KEY, value, Duration.ofMinutes(percentageTtlMinutes)),
            redisTemplate.opsForValue().set(FALLBACK_KEY, value)
        ).doOnSuccess(ignored -> log.info("Percentage cache updated: {}", value));
    }

    /** Borra ambas claves. Sirve para tests. */
    public Mono<Void> evict() {
        return Mono.when(
            redisTemplate.delete(FRESH_KEY),
            redisTemplate.delete(FALLBACK_KEY)
        ).doOnSuccess(ignored -> log.info("Percentage cache evicted"));
    }
}
