package com.tenpo.challenge.exception;

/**
 * Se lanza cuando un cliente supera el umbral de Rate Limiting (3 RPM).
 * El cliente recibe un 429 Too Many Requests.
 */
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
