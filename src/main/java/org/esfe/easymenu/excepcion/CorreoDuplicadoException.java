package org.esfe.easymenu.excepcion;

public class CorreoDuplicadoException extends RuntimeException {
    public CorreoDuplicadoException() {
        super("Ya existe un usuario con ese correo");
    }
}
