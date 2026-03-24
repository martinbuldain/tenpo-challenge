package com.tenpo.challenge.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Rate limiting distribuido con Bucket4j + Redis: el estado del bucket se almacena en Redis y es compartido por todas las replicas
 */
@Service
@Slf4j
public class RateLimiterService {

    @Value("${app.rate-limit.max-requests-per-minute:3}")
    private int maxRequestsPerMinute;

    @Value("${app.rate-limit.window-seconds:60}")
    private int windowSeconds;

    private final LettuceConnectionFactory lettuceConnectionFactory;
    private ProxyManager<String> proxyManager;
    private Supplier<BucketConfiguration> configurationSupplier;

    public RateLimiterService(LettuceConnectionFactory lettuceConnectionFactory) {
        this.lettuceConnectionFactory = lettuceConnectionFactory;
    }

    @PostConstruct
    void init() {
        RedisClient redisClient = (RedisClient) lettuceConnectionFactory.getNativeClient();
        StatefulRedisConnection<String, byte[]> connection =
            redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));

        this.proxyManager = LettuceBasedProxyManager.builderFor(connection).build();

        this.configurationSupplier = () -> BucketConfiguration.builder()
            .addLimit(Bandwidth.builder()
                .capacity(maxRequestsPerMinute)
                .refillGreedy(maxRequestsPerMinute, Duration.ofSeconds(windowSeconds))
                .build())
            .build();
    }

    public Mono<Boolean> isAllowed(String clientIp) {
        return Mono.fromCallable(() -> {
            String key    = "bucket4j:rate_limit:" + clientIp;
            var    bucket = proxyManager.builder().build(key, configurationSupplier);
            boolean allowed = bucket.tryConsume(1);

            if (!allowed) {
                log.warn("Rate limit exceeded [clientIp={}, limit={}/{}s]",
                    clientIp, maxRequestsPerMinute, windowSeconds);
            } else {
                log.debug("Rate limit OK [clientIp={}]", clientIp);
            }
            return allowed;
        });
    }

    public long getWindowSeconds() {
        return windowSeconds;
    }
}
