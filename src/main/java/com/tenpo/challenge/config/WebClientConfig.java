package com.tenpo.challenge.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Configuración del {@link WebClient} reactivo para el servicio externo.
 *
 * <p>Se configuran timeouts a nivel de Netty para evitar que conexiones
 * colgadas acumulen resources del event loop.
 */
@Configuration
public class WebClientConfig {

    @Value("${app.external-service.url}")
    private String externalServiceUrl;

    @Value("${app.external-service.connect-timeout-ms:3000}")
    private int connectTimeoutMs;

    @Value("${app.external-service.read-timeout-ms:5000}")
    private int readTimeoutMs;

    @Bean
    public WebClient externalPercentageWebClient() {
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
            .responseTimeout(Duration.ofMillis(readTimeoutMs))
            .doOnConnected(conn -> conn
                .addHandlerLast(new ReadTimeoutHandler(readTimeoutMs, TimeUnit.MILLISECONDS))
                .addHandlerLast(new WriteTimeoutHandler(connectTimeoutMs, TimeUnit.MILLISECONDS)));

        return WebClient.builder()
            .baseUrl(externalServiceUrl)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }
}
