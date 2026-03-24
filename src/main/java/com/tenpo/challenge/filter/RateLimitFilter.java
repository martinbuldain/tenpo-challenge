package com.tenpo.challenge.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenpo.challenge.domain.dto.ErrorResponse;
import com.tenpo.challenge.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * Rate Limiting (3 RPM por IP).
 * Order=2: se ejecuta despues del filtro de historial para que los rechazos 429 queden registrados.
 */
@Component
@Order(2)
@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter implements WebFilter {

    private static final Set<String> EXCLUDED_PATHS = Set.of(
        "/swagger-ui", "/v3/api-docs", "/actuator", "/mock", "/webjars"
    );

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper       objectMapper;

    @Value("${app.rate-limit.max-requests-per-minute:3}")
    private int maxRpm;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path     = exchange.getRequest().getPath().value();
        boolean excluded = EXCLUDED_PATHS.stream().anyMatch(path::startsWith);

        if (excluded) {
            return chain.filter(exchange);
        }

        String clientIp = extractClientIp(exchange);

        return rateLimiterService.isAllowed(clientIp)
            .flatMap(allowed -> {
                if (allowed) {
                    return chain.filter(exchange);
                } else {
                    return writeRateLimitResponse(exchange, path, clientIp);
                }
            });
    }

    private Mono<Void> writeRateLimitResponse(
            ServerWebExchange exchange, String path, String clientIp) {

        long retryAfter = rateLimiterService.getWindowSeconds();
        log.warn("Rate limit exceeded [clientIp={}, retryAfter={}s]", clientIp, retryAfter);

        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set("Retry-After", String.valueOf(retryAfter));

        ErrorResponse error = ErrorResponse.of(
            429,
            "Too Many Requests",
            "Se excedio el limite de %d requests por minuto. Reintentar en %d segundos."
                .formatted(maxRpm, retryAfter),
            path
        );

        try {
            byte[] body = objectMapper.writeValueAsBytes(error);
            DataBuffer buffer = response.bufferFactory().wrap(body);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            return response.setComplete();
        }
    }

    public static String extractClientIp(ServerWebExchange exchange) {
        var headers = exchange.getRequest().getHeaders();
        String[] proxyHeaders = {
            "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"
        };

        for (String header : proxyHeaders) {
            String ip = headers.getFirst(header);
            if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }

        var remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
    }
}
