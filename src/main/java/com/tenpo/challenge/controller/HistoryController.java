package com.tenpo.challenge.controller;

import com.tenpo.challenge.domain.dto.CallHistoryDto;
import com.tenpo.challenge.domain.dto.ErrorResponse;
import com.tenpo.challenge.domain.dto.PageResponse;
import com.tenpo.challenge.service.HistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Endpoint reactivo para consultar el historial de llamadas a la API.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "History", description = "Historial de llamadas a la API")
@Slf4j
@RequiredArgsConstructor
@Validated
public class HistoryController {

    private final HistoryService historyService;

    @Operation(
        summary     = "Obtiene el historial de llamadas con paginacion",
        description = """
            Retorna el historial de todas las llamadas a los endpoints de la API,
            ordenado por fecha descendente. El historial incluye endpoint, parametros,
            respuesta o error, IP del cliente y timestamp.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200"),
        @ApiResponse(responseCode = "400", description = "Parametros invalidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "429", description = "Rate limit excedido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/history")
    public Mono<ResponseEntity<PageResponse<CallHistoryDto>>> getHistory(
            @Parameter(description = "Numero de pagina (0-based)", example = "0")
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page debe ser mayor o igual a 0")
            int page,

            @Parameter(description = "Tamano de pagina (max. 100)", example = "10")
            @RequestParam(defaultValue = "10")
            @Min(value = 1,   message = "size debe ser mayor o igual a 1")
            @Max(value = 100, message = "size debe ser menor o igual a 100")
            int size) {

        log.info("Historial [page={}, size={}]", page, size);
        return historyService.getHistory(page, size)
            .map(ResponseEntity::ok);
    }
}
