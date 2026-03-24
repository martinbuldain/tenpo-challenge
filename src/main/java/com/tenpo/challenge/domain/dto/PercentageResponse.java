package com.tenpo.challenge.domain.dto;

import java.math.BigDecimal;

/**
 * DTO de respuesta del servicio externo de porcentajes.
 *
 * @param percentage porcentaje retornado por el servicio externo
 */
public record PercentageResponse(BigDecimal percentage) {}
