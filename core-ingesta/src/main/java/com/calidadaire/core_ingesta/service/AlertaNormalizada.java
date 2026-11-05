package com.calidadaire.core_ingesta.service;

import java.time.Instant;
import java.util.UUID;

public record AlertaNormalizada(
        UUID nodoId,
        String nombreOrigen,
        Instant timestampOrigen,
        String mid,
        String tipoAlerta,
        Double valorRegistrado,
        String mensaje) {}