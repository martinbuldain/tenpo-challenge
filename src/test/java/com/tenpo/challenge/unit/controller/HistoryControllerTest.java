package com.tenpo.challenge.unit.controller;

import com.tenpo.challenge.controller.HistoryController;
import com.tenpo.challenge.domain.dto.CallHistoryDto;
import com.tenpo.challenge.domain.dto.PageResponse;
import com.tenpo.challenge.domain.enums.CallStatus;
import com.tenpo.challenge.exception.GlobalExceptionHandler;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;


/**
 * Tests de capa web para {@link HistoryController}.
 */
@WebFluxTest(HistoryController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("HistoryController Unit Tests")
class HistoryControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private HistoryService historyService;

    @MockBean
    private RateLimiterService rateLimiterService;

    private static final String URL = "/api/v1/history";

    @BeforeEach
    void setUpFilters() {
        when(rateLimiterService.isAllowed(anyString())).thenReturn(Mono.just(true));
        when(historyService.recordCall(any(), any(), any(), any(), any(), anyInt(), any()))
            .thenReturn(Mono.empty());
    }

    // 200 OK

    @Nested
    @DisplayName("GET /api/v1/history – respuestas exitosas")
    class SuccessfulRequests {

        @Test
        @DisplayName("devuelve 200 con lista vacia cuando no hay historial")
        void shouldReturn200WithEmptyList() {
            var emptyPage = new PageResponse<CallHistoryDto>(
                List.of(), 0, 10, 0, 0, true, true);

            when(historyService.getHistory(0, 10)).thenReturn(Mono.just(emptyPage));

            webTestClient.get().uri(URL)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content").isArray()
                .jsonPath("$.content").isEmpty()
                .jsonPath("$.totalElements").isEqualTo(0);
        }

        @Test
        @DisplayName("devuelve 200 con registros cuando hay historial")
        void shouldReturn200WithRecords() {
            var dto = new CallHistoryDto(
                1L, "/api/v1/calculate", "POST",
                "{\"num1\":5,\"num2\":5}", "{\"result\":11}",
                null, 200, CallStatus.SUCCESS, "127.0.0.1",
                LocalDateTime.now());

            var page = new PageResponse<>(List.of(dto), 0, 10, 1, 1, true, true);

            when(historyService.getHistory(0, 10)).thenReturn(Mono.just(page));

            webTestClient.get().uri(URL)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content").isArray()
                .jsonPath("$.content[0].id").isEqualTo(1)
                .jsonPath("$.content[0].endpoint").isEqualTo("/api/v1/calculate")
                .jsonPath("$.content[0].httpStatus").isEqualTo(200)
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.first").isEqualTo(true);
        }

        @Test
        @DisplayName("acepta parametros de paginacion personalizados")
        void shouldAcceptCustomPaginationParams() {
            var emptyPage = new PageResponse<CallHistoryDto>(
                List.of(), 2, 5, 15, 3, false, false);

            when(historyService.getHistory(2, 5)).thenReturn(Mono.just(emptyPage));

            webTestClient.get().uri(uriBuilder -> uriBuilder
                    .path(URL)
                    .queryParam("page", "2")
                    .queryParam("size", "5")
                    .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.page").isEqualTo(2)
                .jsonPath("$.size").isEqualTo(5)
                .jsonPath("$.first").isEqualTo(false);
        }
    }

    // ── 400 Bad Request ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/history – validacin de parametros")
    class ValidationErrors {

        @Test
        @DisplayName("devuelve 400 cuando page es negativo")
        void shouldReturn400WhenPageIsNegative() {
            webTestClient.get().uri(URL + "?page=-1")
                .exchange()
                .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("devuelve 400 cuando size es 0")
        void shouldReturn400WhenSizeIsZero() {
            webTestClient.get().uri(URL + "?size=0")
                .exchange()
                .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("devuelve 400 cuando size excede 100")
        void shouldReturn400WhenSizeExceedsMax() {
            webTestClient.get().uri(URL + "?size=101")
                .exchange()
                .expectStatus().isBadRequest();
        }
    }
}
