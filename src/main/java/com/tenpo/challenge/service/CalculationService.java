package com.tenpo.challenge.service;

import com.tenpo.challenge.domain.dto.CalculationRequest;
import com.tenpo.challenge.domain.dto.CalculationResponse;
import reactor.core.publisher.Mono;

public interface CalculationService {

    /**
     * Suma num1 + num2, aplica el porcentaje dinamico y devuelve el resultado.
     *
     * @param request parametros de entrada
     * @return {@code Mono<CalculationResponse>} con el resultado
     */
    Mono<CalculationResponse> calculate(CalculationRequest request);
}
