package com.tenpo.challenge.service.impl;

import com.tenpo.challenge.domain.dto.CalculationRequest;
import com.tenpo.challenge.domain.dto.CalculationResponse;
import com.tenpo.challenge.domain.dto.PercentageResult;
import com.tenpo.challenge.service.CalculationService;
import com.tenpo.challenge.service.ExternalPercentageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Implementación reactiva del servicio de cálculo.
 *
 * <p>Encadena de forma declarativa la obtención del porcentaje y el cálculo aritmético.
 * Al retornar {@code Mono}, no bloquea ningún thread; el scheduler de Reactor
 * decide cuándo y en qué hilo ejecutar cada paso.
 *
 * <p>Usa {@link BigDecimal} con precisión DECIMAL128 para evitar errores de
 * punto flotante en contextos financieros.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CalculationServiceImpl implements CalculationService {

    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;
    private static final int         SCALE        = 4;
    private static final RoundingMode ROUNDING    = RoundingMode.HALF_UP;
    private static final BigDecimal  HUNDRED      = BigDecimal.valueOf(100);

    private final ExternalPercentageService externalPercentageService;

    @Override
    public Mono<CalculationResponse> calculate(CalculationRequest request) {
        BigDecimal sum = request.num1().add(request.num2(), MATH_CONTEXT);

        return externalPercentageService.fetchPercentage()
            .map(result -> buildResponse(request, sum, result))
            .doOnSuccess(response ->
                log.info("Calculate complete [result={}, percentage={}%, source={}]",
                    response.result(), response.appliedPercentage(), response.percentageSource()));
    }

    private CalculationResponse buildResponse(
            CalculationRequest request, BigDecimal sum, PercentageResult percentageResult) {

        BigDecimal percentage = percentageResult.value();
        BigDecimal increment  = sum.multiply(percentage, MATH_CONTEXT)
                                   .divide(HUNDRED, SCALE, ROUNDING);
        BigDecimal result     = sum.add(increment, MATH_CONTEXT)
                                   .setScale(SCALE, ROUNDING);

        return new CalculationResponse(
            request.num1(),
            request.num2(),
            sum.setScale(SCALE, ROUNDING),
            percentage,
            result,
            percentageResult.source()
        );
    }
}
