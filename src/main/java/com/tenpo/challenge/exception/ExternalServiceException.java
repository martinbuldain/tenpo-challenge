package com.tenpo.challenge.exception;

/**
 * Se lanza cuando el servicio externo de porcentajes responde con error
 * o no esta disponible. Spring Retry usa esta excepcion como criterio de reintento.
 */
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String message) {
        super(message);
    }

    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
