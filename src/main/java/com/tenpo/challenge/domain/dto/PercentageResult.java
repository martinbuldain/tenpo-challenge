package com.tenpo.challenge.domain.dto;

import java.math.BigDecimal;

/**
 * Porcentaje obtenido junto con su origen, para poblar {@code percentageSource} en la respuesta.
 *
 * @param value  el porcentaje a aplicar
 * @param source origen: "EXTERNAL", "CACHE" o "FALLBACK_CACHE"
 */
public record PercentageResult(BigDecimal value, String source) {

    public static final String SOURCE_EXTERNAL      = "EXTERNAL"; // Porcentaje obtenido del servicio externo
    public static final String SOURCE_CACHE         = "CACHE"; // Porcentaje obtenido de la cache (si el servicio externo falla y hay un valor en cache)
    public static final String SOURCE_FALLBACK_CACHE = "FALLBACK_CACHE"; // Porcentaje obtenido de la cache de respaldo (si no hay valor en cache principal)
}
