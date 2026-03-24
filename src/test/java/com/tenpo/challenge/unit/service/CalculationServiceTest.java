package com.tenpo.challenge.unit.service;

import com.tenpo.challenge.domain.dto.CalculationRequest;
import com.tenpo.challenge.domain.dto.PercentageResult;
import com.tenpo.challenge.exception.CacheUnavailableException;
import com.tenpo.challenge.service.ExternalPercentageService;
import com.tenpo.challenge.service.impl.CalculationServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de {@link CalculationServiceImpl}.
 * Las aserciones usan {@link StepVerifier} para controlar la suscripcion.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CalculationService Unit Tests")
class CalculationServiceTest {

    @Mock
    private ExternalPercentageService externalPercentageService;

    @InjectMocks
    private CalculationServiceImpl calculationService;

    @Test
    @DisplayName("calcula (5 + 5) + 10% = 11")
    void shouldCalculateExampleFromChallenge() {
        when(externalPercentageService.fetchPercentage()).thenReturn(Mono.just(new PercentageResult(BigDecimal.TEN, PercentageResult.SOURCE_EXTERNAL)));

        StepVerifier.create(calculationService.calculate(
                new CalculationRequest(new BigDecimal("5"), new BigDecimal("5"))))
            .assertNext(response -> {
                assertThat(response.num1()).isEqualByComparingTo("5");
                assertThat(response.num2()).isEqualByComparingTo("5");
                assertThat(response.sum()).isEqualByComparingTo("10");
                assertThat(response.appliedPercentage()).isEqualByComparingTo("10");
                assertThat(response.result()).isEqualByComparingTo("11");
            })
            .verifyComplete();
    }

    @ParameterizedTest(name = "({0} + {1}) + {2}% = {3}")
    @CsvSource({
        "5,    5,   10,  11.0000",
        "100,  50,  20,  180.0000",
        "0,    0,    0,   0.0000",
        "10,  -10,  50,   0.0000",
        "1,    1,   100,  4.0000"
    })
    @DisplayName("valida multiples escenarios de calculo con diferentes entradas y porcentajes")
    void shouldCalculateCorrectly(
            String num1Str, String num2Str, String pctStr, String expectedStr) {

        BigDecimal num1 = new BigDecimal(num1Str);
        BigDecimal num2 = new BigDecimal(num2Str);
        BigDecimal pct  = new BigDecimal(pctStr);

        when(externalPercentageService.fetchPercentage())
                .thenReturn(Mono.just(new PercentageResult(pct, PercentageResult.SOURCE_EXTERNAL)));

        StepVerifier.create(calculationService.calculate(new CalculationRequest(num1, num2)))
            .assertNext(response ->
                assertThat(response.result()).isEqualByComparingTo(new BigDecimal(expectedStr)))
            .verifyComplete();
    }

    @Nested
    @DisplayName("manejo de excepciones del servicio externo")
    class ExceptionHandling {

        @Test
        @DisplayName("emite CacheUnavailableException cuando el servicio externo falla sin cache")
        void shouldEmitCacheUnavailableException() {
            when(externalPercentageService.fetchPercentage())
                .thenReturn(Mono.error(new CacheUnavailableException("Sin cache disponible")));

            StepVerifier.create(calculationService.calculate(
                    new CalculationRequest(BigDecimal.ONE, BigDecimal.ONE)))
                .expectErrorMatches(ex ->
                    ex instanceof CacheUnavailableException &&
                    ex.getMessage().contains("Sin cache disponible"))
                .verify();
        }

        @Test
        @DisplayName("invoca el servicio externo exactamente una vez por cálculo")
        void shouldCallExternalServiceExactlyOnce() {
            when(externalPercentageService.fetchPercentage()).thenReturn(Mono.just(new PercentageResult(BigDecimal.TEN, PercentageResult.SOURCE_EXTERNAL)));

            StepVerifier.create(calculationService.calculate(
                    new CalculationRequest(BigDecimal.ONE, BigDecimal.TEN)))
                .expectNextCount(1)
                .verifyComplete();

            verify(externalPercentageService, times(1)).fetchPercentage();
        }
    }


    @Nested
    @DisplayName("validaciones de estructura de la respuesta")
    class ResponseStructure {

        @Test
        @DisplayName("devuelve todos los campos del DTO de respuesta con valores")
        void shouldReturnAllResponseFields() {
            when(externalPercentageService.fetchPercentage())
                .thenReturn(Mono.just(new PercentageResult(new BigDecimal("15"), PercentageResult.SOURCE_EXTERNAL)));

            StepVerifier.create(calculationService.calculate(
                    new CalculationRequest(new BigDecimal("20"), new BigDecimal("30"))))
                .assertNext(response -> {
                    assertThat(response.num1()).isNotNull();
                    assertThat(response.num2()).isNotNull();
                    assertThat(response.sum()).isNotNull();
                    assertThat(response.appliedPercentage()).isNotNull();
                    assertThat(response.result()).isNotNull();
                    assertThat(response.percentageSource()).isNotNull();
                })
                .verifyComplete();
        }

        @Test
        @DisplayName("sum = num1 + num2")
        void shouldComputeSumCorrectly() {
            when(externalPercentageService.fetchPercentage())
                .thenReturn(Mono.just(new PercentageResult(BigDecimal.ZERO, PercentageResult.SOURCE_EXTERNAL)));

            StepVerifier.create(calculationService.calculate(
                    new CalculationRequest(new BigDecimal("7"), new BigDecimal("3"))))
                .assertNext(response -> {
                    assertThat(response.sum()).isEqualByComparingTo("10");
                    assertThat(response.result()).isEqualByComparingTo("10");
                })
                .verifyComplete();
        }
    }
}
