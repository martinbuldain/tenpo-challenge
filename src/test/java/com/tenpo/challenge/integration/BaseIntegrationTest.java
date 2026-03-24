package com.tenpo.challenge.integration;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * Clase base para tests de integracion.
 *
 * <p>Levanta:
 * <ul>
 *   <li><strong>PostgreSQL</strong> via Testcontainers</li>
 *   <li><strong>Redis</strong> via Testcontainers</li>
 *   <li><strong>MockServer</strong> para simular el servicio externo de porcentajes</li>
 * </ul>
 *
 * <p>Los contenedores son iniciados una sola vez para toda la suite (bloque
 * estatico) y nunca se detienen entre clases de test, evitando que el
 * contexto de Spring cacheado apunte a puertos que ya no existen.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    // Testcontainers – iniciados una sola vez para toda la suite

    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("tenpo_test")
            .withUsername("tenpo")
            .withPassword("tenpo_pass");

    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
        new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    // MockServer

    static ClientAndServer mockServer;
    static MockServerClient mockServerClient;

    static final int MOCK_SERVER_PORT = 1090;

    @BeforeAll
    static void startMockServer() {
        if (mockServer == null || !mockServer.isRunning()) {
            mockServer       = ClientAndServer.startClientAndServer(MOCK_SERVER_PORT);
            mockServerClient = new MockServerClient("localhost", MOCK_SERVER_PORT);
        }
    }

    @AfterAll
    static void stopMockServer() {
        // MockServer se mantiene entre clases para que el contexto cacheado
        // pueda seguir usandolo; se detiene al final de la suite por el JVM.
    }

    // Spring Dynamic Properties

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // JDBC datasource → usado únicamente por Flyway en el arranque
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        // R2DBC datasource → usado por Spring Data R2DBC en runtime
        registry.add("spring.r2dbc.url", () ->
            "r2dbc:postgresql://" + POSTGRES.getHost() + ":"
            + POSTGRES.getMappedPort(5432) + "/tenpo_test");
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);

        // Flyway uses its own JDBC connection (DataSourceAutoConfiguration is skipped when R2DBC is present)
        registry.add("spring.flyway.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user",     POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);

        // Redis
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        // Servicio externo → MockServer
        registry.add("app.external-service.url",
            () -> "http://localhost:" + MOCK_SERVER_PORT + "/mock/percentage");
    }

    // Helpers

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    protected WebTestClient webTestClient;

    /**
     * Antes de cada test:
     * - Resetea el circuit breaker para evitar que el estado acumulado de un test
     *   afecte al siguiente (el CB puede quedar OPEN después de múltiples fallos).
     * - Extiende el timeout del WebTestClient a 30s para tests que ejercitan retries
     *   (hasta 3 retries con backoff exponencial pueden tomar ~4-5 segundos).
     */
    @BeforeEach
    void resetIntegrationState() {
        circuitBreakerRegistry.circuitBreaker("externalPercentage").reset();
        webTestClient = webTestClient.mutate()
            .responseTimeout(Duration.ofSeconds(30))
            .build();
    }
}
