package com.tenpo.challenge.exception;

/**
 * Se lanza cuando el servicio externo falla y no hay ningun valor en cache disponible.
 * El cliente recibe un 503.
 */
public class CacheUnavailableException extends RuntimeException {

    public CacheUnavailableException(String message) {
        super(message);
    }

    public CacheUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
