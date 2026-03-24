package com.tenpo.challenge.unit.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenpo.challenge.domain.dto.ErrorResponse;
import com.tenpo.challenge.filter.RateLimitFilter;
import com.tenpo.challenge.service.RateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de {@link RateLimitFilter}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitFilter Unit Tests")
class RateLimitFilterTest {

    @Mock
    private RateLimiterService rateLimiterService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private RateLimitFilter rateLimitFilter;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(rateLimitFilter, "maxRpm", 3);
    }

    // Request permitida

    @Nested
    @DisplayName("peticiones permitidas")
    class AllowedRequests {

        @Test
        @DisplayName("pasa el request al siguiente filtro cuando no se excede el limite")
        void shouldPassThroughWhenAllowed() {
            when(rateLimiterService.isAllowed(anyString())).thenReturn(Mono.just(true));

            MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/calculate").build());

            WebFilterChain chain = ex -> Mono.empty();

            StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                .verifyComplete();

            // El response no se escribio con 429
            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }
    }

    // Request bloqueado

    @Nested
    @DisplayName("requests bloqueados por rate limiting")
    class BlockedRequests {

        @Test
        @DisplayName("devuelve 429 cuando se excede el limite de RPM")
        void shouldReturn429WhenRateLimitExceeded() throws Exception {
            when(rateLimiterService.isAllowed(anyString())).thenReturn(Mono.just(false));
            when(rateLimiterService.getWindowSeconds()).thenReturn(60L);
            when(objectMapper.writeValueAsBytes(any(ErrorResponse.class)))
                .thenReturn("{\"status\":429}".getBytes());

            MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/calculate").build());

            WebFilterChain chain = ex -> Mono.empty();

            StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                .verifyComplete();

            assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After"))
                .isEqualTo("60");
        }

        @Test
        @DisplayName("no invoca la cadena cuando el rate limit esta excedido")
        void shouldNotInvokeChainWhenRateLimitExceeded() throws Exception {
            when(rateLimiterService.isAllowed(anyString())).thenReturn(Mono.just(false));
            when(rateLimiterService.getWindowSeconds()).thenReturn(60L);
            when(objectMapper.writeValueAsBytes(any())).thenReturn("{}".getBytes());

            MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/calculate").build());

            WebFilterChain chainMock = mock(WebFilterChain.class);

            StepVerifier.create(rateLimitFilter.filter(exchange, chainMock))
                .verifyComplete();

            verify(chainMock, never()).filter(any());
        }
    }

    // Exclusión de rutas

    @Nested
    @DisplayName("rutas excluidas del rate limiting")
    class ExcludedPaths {

        @ParameterizedTest
        @ValueSource(strings = {
            "/swagger-ui/index.html",
            "/v3/api-docs",
            "/actuator/health",
            "/mock/percentage"
        })
        @DisplayName("pasa sin aplicar rate limiting a las rutas excluidas")
        void shouldPassThroughExcludedPaths(String path) {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(path).build());

            WebFilterChain chain = ex -> Mono.empty();

            StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                .verifyComplete();

            // isAllowed NO debe haberse invocado para rutas excluidas
            verify(rateLimiterService, never()).isAllowed(anyString());
        }

        @Test
        @DisplayName("aplica rate limiting a /api/v1/calculate")
        void shouldApplyRateLimitingToApiPath() {
            when(rateLimiterService.isAllowed(anyString())).thenReturn(Mono.just(true));

            MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/calculate").build());

            StepVerifier.create(rateLimitFilter.filter(exchange, ex -> Mono.empty()))
                .verifyComplete();

            verify(rateLimiterService).isAllowed(anyString());
        }
    }

    // Extraccion de IP

    @Nested
    @DisplayName("extractClientIp")
    class ExtractClientIp {

        @Test
        @DisplayName("extrae IP de X-Forwarded-For cuando el header esta presente")
        void shouldExtractIpFromXForwardedFor() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test")
                    .header("X-Forwarded-For", "203.0.113.1, 10.0.0.1")
                    .build());

            String ip = RateLimitFilter.extractClientIp(exchange);

            assertThat(ip).isEqualTo("203.0.113.1");
        }

        @Test
        @DisplayName("usa la direccion remota cuando no hay headers de proxy")
        void shouldUseRemoteAddressWhenNoProxyHeaders() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test").build());

            String ip = RateLimitFilter.extractClientIp(exchange);

            // MockServerHttpRequest no tiene remote address → "unknown"
            assertThat(ip).isNotNull();
        }

        @Test
        @DisplayName("ignora valor 'unknown' en X-Forwarded-For y usa X-Real-IP")
        void shouldIgnoreUnknownValueInXForwardedFor() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test")
                    .header("X-Forwarded-For", "unknown")
                    .header("X-Real-IP", "172.16.0.5")
                    .build());

            String ip = RateLimitFilter.extractClientIp(exchange);

            assertThat(ip).isEqualTo("172.16.0.5");
        }

        @Test
        @DisplayName("extrae solo la primera IP cuando X-Forwarded-For tiene múltiples valores")
        void shouldExtractFirstIpFromMultipleForwardedFor() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test")
                    .header("X-Forwarded-For", "10.10.10.10, 20.20.20.20, 30.30.30.30")
                    .build());

            String ip = RateLimitFilter.extractClientIp(exchange);

            assertThat(ip).isEqualTo("10.10.10.10");
        }
    }
}
