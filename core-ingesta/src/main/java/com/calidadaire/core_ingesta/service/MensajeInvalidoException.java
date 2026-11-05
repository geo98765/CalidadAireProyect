package com.calidadaire.core_ingesta.service;

public class MensajeInvalidoException extends RuntimeException {
    public MensajeInvalidoException(String mensaje) {
        super(mensaje);
    }
}