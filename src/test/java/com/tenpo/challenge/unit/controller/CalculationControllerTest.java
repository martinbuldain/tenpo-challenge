package com.tenpo.challenge.unit.controller;

import com.tenpo.challenge.controller.CalculationController;
import com.tenpo.challenge.domain.dto.CalculationResponse;
import com.tenpo.challenge.exception.CacheUnavailableException;
import com.tenpo.challenge.exception.GlobalExceptionHandler;
import com.tenpo.challenge.service.CalculationService;
import com.tenpo.challenge.service.HistoryService;
import com.tenpo.challenge.service.RateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tests de capa web para {@link CalculationController}.
 *
 * <p>Usa {@code @WebFluxTest} en lugar de {@code @WebMvcTest}: levanta solo
 * el contexto de WebFlux (router, filters desactivados en slice) y configura
 * {@link WebTestClient} para llamadas HTTP no-bloqueantes.
 *
 * <p>Los mocks devuelven {@code Mono<T>} en lugar de valores directos,
 * ya que los servicios reactivos retornan publishers.
 */
@WebFluxTest(CalculationController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("CalculationController Unit Tests")
class CalculationControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private CalculationService calculationService;

    @MockBean
    private HistoryService historyService;

    @MockBean
    private RateLimiterService rateLimiterService;

    private static final String URL = "/api/v1/calculate";

    @BeforeEach
    void setUpFilters() {
        when(rateLimiterService.isAllowed(anyString())).thenReturn(Mono.just(true));
        when(historyService.recordCall(any(), any(), any(), any(), any(), anyInt(), any()))
            .thenReturn(Mono.empty());
    }

    // 200 OK

    @Nested
    @DisplayName("POST /api/v1/calculate – respuestas exitosas")
    class SuccessfulRequests {

        @Test
        @DisplayName("devuelve 200 con el resultado correcto para num1=5, num2=5")
        void shouldReturn200WithCorrectResult() {
            var response = new CalculationResponse(
                new BigDecimal("5"), new BigDecimal("5"),
                new BigDecimal("10"), new BigDecimal("10"),
                new BigDecimal("11"), "EXTERNAL");

            when(calculationService.calculate(any())).thenReturn(Mono.just(response));

            webTestClient.post().uri(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {"num1": 5, "num2": 5}
                    """)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.num1").isEqualTo(5)
                .jsonPath("$.num2").isEqualTo(5)
                .jsonPath("$.sum").isEqualTo(10)
                .jsonPath("$.appliedPercentage").isEqualTo(10)
                .jsonPath("$.result").isEqualTo(11)
                .jsonPath("$.percentageSource").isEqualTo("EXTERNAL");
        }

        @Test
        @DisplayName("deveuelve el body deserializado")
        void shouldDeserializeResponseCorrectly() {
            var expected = new CalculationResponse(
                new BigDecimal("3"), new BigDecimal("7"),
                new BigDecimal("10"), new BigDecimal("20"),
                new BigDecimal("12"), "CACHE");

            when(calculationService.calculate(any())).thenReturn(Mono.just(expected));

            webTestClient.post().uri(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {"num1": 3, "num2": 7}
                    """)
                .exchange()
                .expectStatus().isOk()
                .expectBody(CalculationResponse.class)
                .value(resp -> {
                    assertThat(resp.result()).isEqualByComparingTo("12");
                    assertThat(resp.percentageSource()).isEqualTo("CACHE");
                });
        }
    }

    // 400 Bad Request

    @Nested
    @DisplayName("POST /api/v1/calculate – validacion de entrada")
    class ValidationErrors {

        @Test
        @DisplayName("devuelve 400 cuando num1 es nulo")
        void shouldReturn400WhenNum1IsNull() {
            webTestClient.post().uri(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {"num2": 5}
                    """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.error").isEqualTo("Bad Request")
                .jsonPath("$.details").isArray();
        }

        @Test
        @DisplayName("devuelve 400 cuando num2 es nulo")
        void shouldReturn400WhenNum2IsNull() {
            webTestClient.post().uri(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {"num1": 5}
                    """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400);
        }

        @Test
        @DisplayName("devuelve 400 cuando el cuerpo JSON es invalido")
        void shouldReturn400ForMalformedJson() {
            webTestClient.post().uri(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("not-a-json")
                .exchange()
                .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("devuelve 400 cuando el body este vacio")
        void shouldReturn400ForEmptyBody() {
            webTestClient.post().uri(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isBadRequest();
        }
    }

    // 503 Service Unavailable

    @Nested
    @DisplayName("POST /api/v1/calculate – errores del servicio externo")
    class ServiceErrors {

        @Test
        @DisplayName("devuelve 503 cuando el servicio externo falla sin cache disponible")
        void shouldReturn503WhenCacheUnavailable() {
            when(calculationService.calculate(any()))
                .thenReturn(Mono.error(new CacheUnavailableException("Sin cache disponible")));

            webTestClient.post().uri(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {"num1": 5, "num2": 5}
                    """)
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.status").isEqualTo(503);
        }
    }
}
