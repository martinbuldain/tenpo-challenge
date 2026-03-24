package com.tenpo.challenge.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Estructura estandar para respuestas de error (4xx / 5xx).
 *
 * @param timestamp  cuando ocurrio el error
 * @param status     codigo HTTP
 * @param error      nombre corto del error (Ejemplo "Bad Request")
 * @param message    descripcion del error
 * @param path       path del endpoint que genero el error
 * @param details    detalles de error adicionales
 */
@Schema(description = "Respuesta de error estandar")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(

    @Schema(description = "Timestamp del error")
    LocalDateTime timestamp,

    @Schema(description = "Codigo HTTP", example = "400")
    int status,

    @Schema(description = "Tipo de error", example = "Bad Request")
    String error,

    @Schema(description = "Mensaje descriptivo del error")
    String message,

    @Schema(description = "Endpoint que genero el error", example = "/api/v1/calculate")
    String path,

    @Schema(description = "Detalles de error adicionales")
    List<String> details
) {
    /** Constructor sin detalles. */
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(LocalDateTime.now(), status, error, message, path, null);
    }

    /** Constructor con detalles. */
    public static ErrorResponse of(int status, String error, String message, String path, List<String> details) {
        return new ErrorResponse(LocalDateTime.now(), status, error, message, path, details);
    }
}
