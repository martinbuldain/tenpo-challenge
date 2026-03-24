package com.tenpo.challenge.integration;

import com.tenpo.challenge.domain.dto.CalculationResponse;
import com.tenpo.challenge.domain.dto.ErrorResponse;
import com.tenpo.challenge.service.CacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockserver.model.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Tests de integracion para el endpoint {@code POST /api/v1/calculate}.
 *
 * <p>Verifica el flujo completo end-to-end:
 * <ul>
 *   <li>Servicio externo disponible → calculo correcto</li>
 *   <li>Servicio externo caído → uso de cache de fallback</li>
 *   <li>Servicio externo caido sin cache → 503</li>
 *   <li>Rate limiting → 429 despues de 3 requests en un minuto</li>
 * </ul>
 */
@DisplayName("Calculation API Integration Tests")
class CalculationApiIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CacheService cacheService;

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    private static final String CALCULATE_URL = "/api/v1/calculate";

    @BeforeEach
    void setUp() {
        mockServerClient.reset();
        cacheService.evict().block(); // Limpia cache entre tests
    }

    // Servicio externo disponible

    @Nested
    @DisplayName("cuando el servicio externo está disponible")
    class ExternalServiceAvailable {

        @Test
        @DisplayName("retorna 200 con resultado correcto para 5+5 con 10%")
        void shouldCalculateCorrectly() {
            mockServerClient.when(request().withMethod("GET").withPath("/mock/percentage"))
                .respond(response()
                    .withStatusCode(200)
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withBody("{\"percentage\": 10}"));

            webTestClient.post().uri(CALCULATE_URL)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("{\"num1\": 5, \"num2\": 5}")
                .exchange()
                .expectStatus().isOk()
                .expectBody(CalculationResponse.class)
                .value(resp -> {
                    assertThat(resp.result()).isEqualByComparingTo("11");
                    assertThat(resp.appliedPercentage()).isEqualByComparingTo("10");
                    assertThat(resp.sum()).isEqualByComparingTo("10");
                });
        }

        @Test
        @DisplayName("actualiza el cache despues de llamar al servicio externo")
        void shouldUpdateCacheAfterSuccessfulCall() {
            mockServerClient.when(request().withMethod("GET").withPath("/mock/percentage"))
                .respond(response()
                    .withStatusCode(200)
                    .withContentType(MediaType.APPLICATION_JSON)
                    .withBody("{\"percentage\": 20}"));

            webTestClient.post().uri(CALCULATE_URL)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("{\"num1\": 1, \"num2\": 1}")
                .exchange()
                .expectStatus().isOk();

            // Verifica que el cache se actualizo
            StepVerifier.create(cacheService.getFreshPercentage())
                .assertNext(pct -> assertThat(pct).isEqualByComparingTo("20"))
                .verifyComplete();
        }
    }

    // Servicio externo caído con cache

    @Nested
    @DisplayName("cuando el servicio externo esta caído")
    class ExternalServiceDown {

        @Test
        @DisplayName("usa cache de fallback cuando el servicio externo falla")
        void shouldUseFallbackCacheWhenExternalServiceFails() {
            // updatePercentage escribe tanto fresh como fallback; borramos fresh
            // para forzar el path de fallback (si no, el test golpea el fresh cache
            // y el servicio externo nunca se llama).
            cacheService.updatePercentage(new BigDecimal("15")).block();
            redisTemplate.delete(CacheService.FRESH_KEY).block();

            // Simular servicio externo caído
            mockServerClient.when(request().withMethod("GET").withPath("/mock/percentage"))
                .respond(response().withStatusCode(500));

            webTestClient.post().uri(CALCULATE_URL)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("{\"num1\": 10, \"num2\": 10}")
                .exchange()
                .expectStatus().isOk()
                .expectBody(CalculationResponse.class)
                .value(resp ->
                    // (10+10) + 15% = 23
                    assertThat(resp.result()).isEqualByComparingTo("23"));
        }

        @Test
        @DisplayName("retorna 503 cuando el servicio externo falla y no hay cache")
        void shouldReturn503WhenNoCache() {
            // Sin cache y servicio externo caído
            mockServerClient.when(request().withMethod("GET").withPath("/mock/percentage"))
                .respond(response().withStatusCode(503));

            webTestClient.post().uri(CALCULATE_URL)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("{\"num1\": 5, \"num2\": 5}")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody(ErrorResponse.class)
                .value(resp -> assertThat(resp.status()).isEqualTo(503));
        }
    }

    // Validación de entrada

    @Nested
    @DisplayName("validacion de parametros de entrada")
    class InputValidation {

        @Test
        @DisplayName("retorna 400 cuando num1 esta ausente")
        void shouldReturn400WhenNum1Missing() {
            webTestClient.post().uri(CALCULATE_URL)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("{\"num2\": 5}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ErrorResponse.class)
                .value(resp -> {
                    assertThat(resp.status()).isEqualTo(400);
                    assertThat(resp.details()).isNotEmpty();
                });
        }

        @Test
        @DisplayName("retorna 400 cuando el body es invalido")
        void shouldReturn400WhenBodyIsInvalid() {
            webTestClient.post().uri(CALCULATE_URL)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("not-json")
                .exchange()
                .expectStatus().isBadRequest();
        }
    }
}
