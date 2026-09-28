package com.calidadaire.core_ingesta.DTO;

import java.time.Instant;

public record NotificacionAlertaDTO(
        Long id,
        String nodoId,
        String tipoAlerta,
        Double valorRegistrado,
        String mensaje,
        Instant timestampOrigen
) {}