package com.calidadaire.core_ingesta.service;

import java.time.Instant;
import java.util.UUID;

// Lectura ya traducida, sin importar en qué formato llegó. Las variables ausentes son null.
public record LecturaNormalizada(
        UUID nodoId,
        String nombreOrigen,   // id original si no era UUID (p. ej. "S_AIRE_06"); si era UUID, null
        Instant timestampOrigen,
        String mid,
        Double co2Ppm,
        Double pm25Ugm3,
        Double pm10Ugm3,
        Double temperaturaC,
        Double humedadPct) {

    public boolean tieneContaminantes() {
        return co2Ppm != null || pm25Ugm3 != null || pm10Ugm3 != null;
    }
}