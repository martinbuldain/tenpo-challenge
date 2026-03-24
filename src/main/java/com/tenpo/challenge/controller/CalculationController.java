package com.tenpo.challenge.controller;

import com.tenpo.challenge.domain.dto.CalculationRequest;
import com.tenpo.challenge.domain.dto.CalculationResponse;
import com.tenpo.challenge.domain.dto.ErrorResponse;
import com.tenpo.challenge.service.CalculationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Endpoint reactivo para el calculo con porcentaje dinámico.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Calculation", description = "Calculo con porcentaje dinámico")
@Slf4j
@RequiredArgsConstructor
public class CalculationController {

    private final CalculationService calculationService;

    @Operation(
        summary = "Calcula (num1 + num2) + porcentaje externo",
        description = "Suma los dos numeros y aplica el porcentaje obtenido del servicio externo " +
                      "(o del caché Redis si el servicio falla). Ejemplo: 5+5 con 10% = 11."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "",
            content = @Content(schema = @Schema(implementation = CalculationResponse.class))),
        @ApiResponse(responseCode = "400", description = "Parametros invalidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "429", description = "Rate limit excedido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "Servicio externo caido sin cache",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/calculate")
    public Mono<ResponseEntity<CalculationResponse>> calculate(
            @Valid @RequestBody CalculationRequest request) {

        log.info("Calculando [num1={}, num2={}]", request.num1(), request.num2());
        return calculationService.calculate(request)
            .map(ResponseEntity::ok);
    }
}
