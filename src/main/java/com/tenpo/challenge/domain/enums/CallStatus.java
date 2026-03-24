package com.tenpo.challenge.domain.enums;

/**
 * Estado de la llamada registrada en el historial.
 */
public enum CallStatus {
    /** El endpoint respondio exitosamente (2xx). */
    SUCCESS,
    /** El endpoint respondio con un error (4xx / 5xx). */
    ERROR
}
