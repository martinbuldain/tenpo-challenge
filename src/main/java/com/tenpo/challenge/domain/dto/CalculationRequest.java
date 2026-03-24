package com.tenpo.challenge.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * DTO de entrada para el endpoint de calculo.
 * @param num1 primer operando
 * @param num2 segundo operando
 */
@Schema(description = "Parametros de entrada para el calculo con porcentaje dinamico")
public record CalculationRequest(

    @Schema(description = "Primer numero", example = "5")
    @NotNull(message = "num1 es obligatorio")
    @DecimalMin(value = "-999999999", message = "num1 fuera de rango")
    @DecimalMax(value = "999999999",  message = "num1 fuera de rango")
    BigDecimal num1,

    @Schema(description = "Segundo numero", example = "5")
    @NotNull(message = "num2 es obligatorio")
    @DecimalMin(value = "-999999999", message = "num2 fuera de rango")
    @DecimalMax(value = "999999999",  message = "num2 fuera de rango")
    BigDecimal num2
) {}
