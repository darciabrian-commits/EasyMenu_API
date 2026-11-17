package org.esfe.easymenu.excepcion;

public class ConflictoIdempotenciaException extends RuntimeException {

    public ConflictoIdempotenciaException(String message) {
        super(message);
    }
}
