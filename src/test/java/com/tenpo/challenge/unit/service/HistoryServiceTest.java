package com.tenpo.challenge.unit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenpo.challenge.domain.entity.CallHistory;
import com.tenpo.challenge.domain.enums.CallStatus;
import com.tenpo.challenge.repository.CallHistoryRepository;
import com.tenpo.challenge.service.HistoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de {@link HistoryService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HistoryService Unit Tests")
class HistoryServiceTest {

    @Mock
    private CallHistoryRepository repository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private HistoryService historyService;

    @Nested
    @DisplayName("recordCall – registro asincornico")
    class RecordCall {

        @Test
        @DisplayName("guarda un registro SUCCESS para respuestas 2xx")
        void shouldPersistSuccessRecord() {
            CallHistory savedEntity = CallHistory.builder()
                .id(1L).endpoint("/api/v1/calculate").httpMethod("POST")
                .httpStatus(200).status(CallStatus.SUCCESS)
                .clientIp("127.0.0.1").createdAt(LocalDateTime.now())
                .build();

            when(repository.save(any())).thenReturn(Mono.just(savedEntity));

            StepVerifier.create(historyService.recordCall(
                    "/api/v1/calculate", "POST",
                    "{\"num1\":5,\"num2\":5}", "{\"result\":11}",
                    null, 200, "127.0.0.1"))
                .verifyComplete();

            var captor = ArgumentCaptor.forClass(CallHistory.class);
            verify(repository).save(captor.capture());

            CallHistory saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(CallStatus.SUCCESS);
            assertThat(saved.getHttpStatus()).isEqualTo(200);
            assertThat(saved.getEndpoint()).isEqualTo("/api/v1/calculate");
            assertThat(saved.getClientIp()).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("guarda un registro ERROR para respuestas 4xx/5xx")
        void shouldPersistErrorRecord() {
            CallHistory savedEntity = CallHistory.builder()
                .id(2L).endpoint("/api/v1/calculate").httpMethod("POST")
                .httpStatus(503).status(CallStatus.ERROR)
                .errorMessage("Servicio no disponible")
                .clientIp("10.0.0.1").createdAt(LocalDateTime.now())
                .build();

            when(repository.save(any())).thenReturn(Mono.just(savedEntity));

            StepVerifier.create(historyService.recordCall(
                    "/api/v1/calculate", "POST",
                    null, null,
                    "Servicio no disponible", 503, "10.0.0.1"))
                .verifyComplete();

            var captor = ArgumentCaptor.forClass(CallHistory.class);
            verify(repository).save(captor.capture());

            CallHistory saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(CallStatus.ERROR);
            assertThat(saved.getHttpStatus()).isEqualTo(503);
            assertThat(saved.getErrorMessage()).isEqualTo("Servicio no disponible");
        }

        @Test
        @DisplayName("termina sin error cuando el repositorio falla")
        void shouldCompleteWithoutErrorWhenRepositoryFails() {
            when(repository.save(any()))
                .thenReturn(Mono.error(new RuntimeException("DB connection lost")));

            // onErrorResume absorbe el error
            StepVerifier.create(historyService.recordCall(
                    "/api/v1/calculate", "POST", null, null, null, 200, "127.0.0.1"))
                .verifyComplete();
        }

        @Test
        @DisplayName("trunca errorMessage largo a 1000 caracteres + '...'")
        void shouldTruncateLongErrorMessages() {
            String longError = "E".repeat(1500);

            CallHistory savedEntity = CallHistory.builder().id(3L).build();
            when(repository.save(any())).thenReturn(Mono.just(savedEntity));

            StepVerifier.create(historyService.recordCall(
                    "/api/v1/calculate", "POST", null, null, longError, 500, "127.0.0.1"))
                .verifyComplete();

            var captor = ArgumentCaptor.forClass(CallHistory.class);
            verify(repository).save(captor.capture());

            assertThat(captor.getValue().getErrorMessage()).hasSize(1003); // 1000 + "..."
        }
    }

    @Nested
    @DisplayName("getHistory – paginacion")
    class GetHistory {

        @Test
        @DisplayName("devuelve una pagina con metadatos correctos")
        void shouldReturnPageWithMetadata() {
            CallHistory history = CallHistory.builder()
                .id(1L).endpoint("/api/v1/calculate").httpMethod("POST")
                .httpStatus(200).status(CallStatus.SUCCESS)
                .clientIp("127.0.0.1").createdAt(LocalDateTime.now())
                .build();

            when(repository.count()).thenReturn(Mono.just(1L));
            when(repository.findAllBy(any())).thenReturn(Flux.just(history));

            StepVerifier.create(historyService.getHistory(0, 10))
                .assertNext(result -> {
                    assertThat(result.content()).hasSize(1);
                    assertThat(result.totalElements()).isEqualTo(1);
                    assertThat(result.page()).isZero();
                    assertThat(result.size()).isEqualTo(10);
                    assertThat(result.first()).isTrue();
                    assertThat(result.last()).isTrue();
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("devuelve una pagina vacia cuando no hay registros")
        void shouldReturnEmptyPageWhenNoRecords() {
            when(repository.count()).thenReturn(Mono.just(0L));
            when(repository.findAllBy(any())).thenReturn(Flux.empty());

            StepVerifier.create(historyService.getHistory(0, 10))
                .assertNext(result -> {
                    assertThat(result.content()).isEmpty();
                    assertThat(result.totalElements()).isZero();
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("limita el tamaño de pagina al maximo permitido, que es 100")
        void shouldCapPageSizeAt100() {
            when(repository.count()).thenReturn(Mono.just(0L));
            when(repository.findAllBy(any())).thenReturn(Flux.empty());

            StepVerifier.create(historyService.getHistory(0, 999))
                .assertNext(result -> assertThat(result.size()).isEqualTo(100))
                .verifyComplete();
        }

        @Test
        @DisplayName("ejecuta count y findAllBy en paralelo (Mono.zip)")
        void shouldExecuteCountAndFindInParallel() {
            List<CallHistory> records = List.of(
                CallHistory.builder().id(1L).endpoint("/e1").httpMethod("POST")
                    .httpStatus(200).status(CallStatus.SUCCESS)
                    .clientIp("127.0.0.1").createdAt(LocalDateTime.now()).build(),
                CallHistory.builder().id(2L).endpoint("/e2").httpMethod("GET")
                    .httpStatus(200).status(CallStatus.SUCCESS)
                    .clientIp("127.0.0.1").createdAt(LocalDateTime.now()).build()
            );

            when(repository.count()).thenReturn(Mono.just(2L));
            when(repository.findAllBy(any())).thenReturn(Flux.fromIterable(records));

            StepVerifier.create(historyService.getHistory(0, 10))
                .assertNext(result -> {
                    assertThat(result.content()).hasSize(2);
                    assertThat(result.totalElements()).isEqualTo(2);
                })
                .verifyComplete();

            verify(repository, times(1)).count();
            verify(repository, times(1)).findAllBy(any());
        }
    }
}
