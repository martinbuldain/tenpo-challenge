package com.tenpo.challenge.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Wrapper para respuestas paginadas porque R2DBC no soporta directamente {@code Page<T>}.
 * @param <T> tipo del contenido
 */
@Schema(description = "Respuesta paginada")
public record PageResponse<T>(

    @Schema(description = "Contenido de la pagina actual")
    List<T> content,

    @Schema(description = "Numero de pagina actual", example = "0")
    int page,

    @Schema(description = "Tamaño de pagina", example = "10")
    int size,

    @Schema(description = "Total de elementos", example = "42")
    long totalElements,

    @Schema(description = "Total de paginas", example = "5")
    int totalPages,

    @Schema(description = "¿Es la primera pagina?", example = "true")
    boolean first,

    @Schema(description = "¿Es la ultima pagina?", example = "false")
    boolean last
) {}
