package com.tenpo.challenge.domain.dto;

import com.tenpo.challenge.domain.entity.CallHistory;
import com.tenpo.challenge.domain.enums.CallStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Registro de una llamada a la API")
public record CallHistoryDto(

    @Schema(description = "ID del registro", example = "1")
    Long id,

    @Schema(description = "Endpoint invocado", example = "/api/v1/calculate")
    String endpoint,

    @Schema(description = "Metodo HTTP", example = "POST")
    String httpMethod,

    @Schema(description = "Parametros de la peticion en formato JSON", example = "{\"num1\":5,\"num2\":5}")
    String requestParams,

    @Schema(description = "Cuerpo de respuesta exitosa en formato JSON")
    String responseBody,

    @Schema(description = "Mensaje de error")
    String errorMessage,

    @Schema(description = "Codigo HTTP de respuesta", example = "200")
    Integer httpStatus,

    @Schema(description = "Estado de la llamada")
    CallStatus status,

    @Schema(description = "IP del cliente", example = "127.0.0.1")
    String clientIp,

    @Schema(description = "Fecha y hora de la llamada")
    LocalDateTime createdAt
) {
    /** Factory para mapear desde la entidad R2DBC. */
    public static CallHistoryDto from(CallHistory entity) {
        return new CallHistoryDto(
            entity.getId(),
            entity.getEndpoint(),
            entity.getHttpMethod(),
            entity.getRequestParams(),
            entity.getResponseBody(),
            entity.getErrorMessage(),
            entity.getHttpStatus(),
            entity.getStatus(),
            entity.getClientIp(),
            entity.getCreatedAt()
        );
    }
}
