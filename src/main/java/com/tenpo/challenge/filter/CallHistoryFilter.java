package com.tenpo.challenge.filter;

import com.tenpo.challenge.service.HistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Registra todas las llamadas a la API de forma asincrona.
 * Order=1: se usa como filtro mas externo para atrapar tanto rechazos 429 como requests exitosas.
 */
@Component
@Order(1)
@Slf4j
@RequiredArgsConstructor
public class CallHistoryFilter implements WebFilter {

    private static final Set<String> EXCLUDED_PATHS = Set.of(
        "/swagger-ui", "/v3/api-docs", "/actuator", "/mock",
        "/api/v1/history", "/webjars"
    );
    private static final int MAX_BODY_SIZE = 4096;

    private final HistoryService historyService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (EXCLUDED_PATHS.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        AtomicReference<String> requestBodyRef  = new AtomicReference<>("");
        AtomicReference<String> responseBodyRef = new AtomicReference<>("");

        ServerHttpRequest decoratedRequest = decorateRequest(exchange.getRequest(), requestBodyRef, exchange.getResponse().bufferFactory());
        ServerHttpResponse decoratedResponse = decorateResponse(exchange.getResponse(), responseBodyRef);

        ServerWebExchange decoratedExchange = exchange.mutate()
            .request(decoratedRequest)
            .response(decoratedResponse)
            .build();

        return chain.filter(decoratedExchange)
            .doFinally(signal -> {
                int status = decoratedResponse.getRawStatusCode() != null
                    ? decoratedResponse.getRawStatusCode() : 200;
                boolean isError = status >= 400;

                historyService.recordCall(
                    path,
                    exchange.getRequest().getMethod().name(),
                    requestBodyRef.get(),
                    isError ? null : responseBodyRef.get(),
                    isError ? responseBodyRef.get() : null,
                    status,
                    RateLimitFilter.extractClientIp(exchange)
                ).subscribe(
                    unused -> {},
                    err -> log.error("History record failed: {}", err.getMessage())
                );
            });
    }

    private ServerHttpRequest decorateRequest(
            ServerHttpRequest original, AtomicReference<String> bodyRef, DataBufferFactory bufferFactory) {

        return new ServerHttpRequestDecorator(original) {
            @Override
            public Flux<DataBuffer> getBody() {
                return DataBufferUtils.join(super.getBody())
                    .flatMapMany(buffer -> {
                        byte[] bytes = new byte[Math.min(buffer.readableByteCount(), MAX_BODY_SIZE)];
                        buffer.read(bytes);
                        DataBufferUtils.release(buffer);
                        bodyRef.set(new String(bytes, StandardCharsets.UTF_8));
                        DataBuffer newBuffer = bufferFactory.wrap(bytes);
                        return Flux.just(newBuffer);
                    });
            }
        };
    }

    private ServerHttpResponse decorateResponse(
            ServerHttpResponse original, AtomicReference<String> bodyRef) {

        return new ServerHttpResponseDecorator(original) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                return DataBufferUtils.join(Flux.from(body))
                    .flatMap(buffer -> {
                        byte[] bytes = new byte[Math.min(buffer.readableByteCount(), MAX_BODY_SIZE)];
                        buffer.read(bytes);
                        DataBufferUtils.release(buffer);
                        bodyRef.set(new String(bytes, StandardCharsets.UTF_8));
                        DataBuffer newBuffer = original.bufferFactory().wrap(bytes);
                        return super.writeWith(Mono.just(newBuffer));
                    });
            }
        };
    }
}
