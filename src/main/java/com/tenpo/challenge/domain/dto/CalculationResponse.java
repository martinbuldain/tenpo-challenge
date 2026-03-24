package com.tenpo.challenge.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * DTO de respuesta del calculo.
 *
 * @param num1             primer operando original
 * @param num2             segundo operando original
 * @param sum              suma de num1 + num2
 * @param appliedPercentage porcentaje aplicado obtenido del servicio externo
 * @param result           resultado final: sum + (sum * appliedPercentage / 100)
 * @param percentageSource origen del porcentaje ("EXTERNAL" | "CACHE" | "FALLBACK_CACHE")
 */
@Schema(description = "Resultado del calculo con porcentaje dinamico")
public record CalculationResponse(

    @Schema(description = "Primer operando", example = "5")
    BigDecimal num1,

    @Schema(description = "Segundo operando", example = "5")
    BigDecimal num2,

    @Schema(description = "Suma de num1 + num2", example = "10")
    BigDecimal sum,

    @Schema(description = "Porcentaje aplicado", example = "10")
    BigDecimal appliedPercentage,

    @Schema(description = "Resultado final", example = "11")
    BigDecimal result,

    @Schema(description = "Origen del porcentaje", example = "EXTERNAL",
            allowableValues = {"EXTERNAL", "CACHE", "FALLBACK_CACHE"})
    String percentageSource
) {}
