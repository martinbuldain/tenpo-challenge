package com.tenpo.challenge.controller;

import com.tenpo.challenge.domain.dto.PercentageResponse;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * Mock del servicio externo de porcentajes.
 */
@RestController
@RequestMapping("/mock")
@Profile({"mock", "default"})
@Slf4j
@Hidden // No aparece en la documentación Swagger de prod
public class MockPercentageController {

    @Value("${app.mock.percentage:10}")
    private BigDecimal mockPercentage;

    @GetMapping("/percentage")
    public Mono<PercentageResponse> getPercentage() {
        log.debug("Llamada al mock percentage service, retornando {}", mockPercentage);
        return Mono.just(new PercentageResponse(mockPercentage));
    }
}
