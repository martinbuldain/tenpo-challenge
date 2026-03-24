package com.tenpo.challenge.integration;

import com.tenpo.challenge.service.CacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockserver.model.MediaType;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Tests de integracion para el endpoint {@code GET /api/v1/history}.
 *
 * <p>Verifica que el historial se registre correctamente y que
 * la paginacion funcione como se espera.
 */
@DisplayName("History API Integration Tests")
class HistoryApiIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CacheService cacheService;

    private static final String HISTORY_URL   = "/api/v1/history";
    private static final String CALCULATE_URL = "/api/v1/calculate";

    @BeforeEach
    void setUp() {
        mockServerClient.reset();
        cacheService.evict().block();

        // Configura MockServer para responder con 10%
        mockServerClient.when(request().withMethod("GET").withPath("/mock/percentage"))
            .respond(response()
                .withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON)
                .withBody("{\"percentage\": 10}"));
    }

    @Test
    @DisplayName("retorna historial con estructura correcta")
    void shouldReturnHistoryWithCorrectStructure() {
        webTestClient.get().uri(HISTORY_URL)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content").isArray()
            .jsonPath("$.totalElements").isNumber()
            .jsonPath("$.page").isEqualTo(0)
            .jsonPath("$.size").isEqualTo(10);
    }

    @Test
    @DisplayName("registra llamadas exitosas en el historial")
    void shouldRecordSuccessfulCallsInHistory() throws Exception {
        // Realiza un request de calculo para generar historial
        webTestClient.post().uri(CALCULATE_URL)
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .bodyValue("{\"num1\": 5, \"num2\": 5}")
            .exchange()
            .expectStatus().isOk();

        // Espera a que el registro asincrono se complete
        Thread.sleep(500);

        webTestClient.get().uri(HISTORY_URL + "?page=0&size=10")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content").isArray()
            .jsonPath("$.totalElements").value(total ->
                assertThat((Integer) total).isGreaterThan(0));
    }

    @Test
    @DisplayName("soporta paginacion")
    void shouldSupportPagination() {
        webTestClient.get().uri(uriBuilder -> uriBuilder
                .path(HISTORY_URL)
                .queryParam("page", "0")
                .queryParam("size", "5")
                .build())
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.page").isEqualTo(0)
            .jsonPath("$.size").isEqualTo(5)
            .jsonPath("$.totalPages").isNumber()
            .jsonPath("$.first").isBoolean()
            .jsonPath("$.last").isBoolean();
    }

    @Test
    @DisplayName("retorna 400 cuando page es negativo")
    void shouldReturn400ForNegativePage() {
        webTestClient.get().uri(HISTORY_URL + "?page=-1")
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("retorna 400 cuando size excede 100")
    void shouldReturn400WhenSizeExceedsMax() {
        webTestClient.get().uri(HISTORY_URL + "?size=200")
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("registra errores 4xx en el historial")
    void shouldRecordErrorCallsInHistory() throws Exception {
        // Request invalida → genera registro ERROR
        webTestClient.post().uri(CALCULATE_URL)
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .bodyValue("{\"num2\": 5}") // num1 faltante
            .exchange()
            .expectStatus().isBadRequest();

        Thread.sleep(500);

        webTestClient.get().uri(HISTORY_URL + "?page=0&size=50")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.totalElements").value(total ->
                assertThat((Integer) total).isGreaterThanOrEqualTo(1));
    }
}
