package com.tenpo.challenge.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 *
 * <p>La UI esta disponible en {@code /swagger-ui.html} y la spec en {@code /v3/api-docs}.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI tenpoOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Tenpo Challenge API")
                .description("""
                    API REST para realizar un calculo aplicando un porcentaje externo.

                    **Funcionalidades:**
                    - Calculo (num1 + num2) con porcentaje dinamico obtenido de servicio externo
                    - Cache distribuida en Redis (TTL 30 min) con fallback ante fallos
                    - Rate limiting distribuido: 3 RPM por IP
                    - Historial de llamadas con paginacion
                    - Circuit Breaker + Retry para el servicio externo
                    """)
                .version("1.0.0"))
            .servers(List.of(
                new Server().url("http://localhost:8080").description("Local"),
                new Server().url("http://localhost:8080").description("Docker Compose")
            ));
    }
}
