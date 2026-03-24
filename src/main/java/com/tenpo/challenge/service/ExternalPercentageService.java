package com.tenpo.challenge.service;

import com.tenpo.challenge.domain.dto.PercentageResult;
import reactor.core.publisher.Mono;

public interface ExternalPercentageService {

    /**
     * Obtiene el porcentaje junto con su origen (EXTERNAL, CACHE, FALLBACK_CACHE).
     * Emite {@link com.tenpo.challenge.exception.CacheUnavailableException} si no hay ninguna fuente disponible.
     */
    Mono<PercentageResult> fetchPercentage();
}
