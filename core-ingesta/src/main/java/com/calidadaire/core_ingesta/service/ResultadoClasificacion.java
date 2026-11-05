package com.calidadaire.core_ingesta.service;

import java.util.List;

public record ResultadoClasificacion(String nivel, String variableCausante, Double valorCausante) {

    /** La lectura no traía ninguna variable de contaminantes: no hay nivel que reportar. */
    public static final ResultadoClasificacion SIN_DATOS = new ResultadoClasificacion(null, null, null);

    // Niveles que generan alerta, de menor a mayor gravedad
    private static final List<String> NIVELES_ALERTABLES = List.of(
            MotorReglasService.RIESGO_MALO,
            MotorReglasService.RIESGO_MUY_MALO,
            MotorReglasService.RIESGO_EXTREMADAMENTE_MALO);

    public boolean sinDatos() {
        return nivel == null;
    }

    /** MALO o peor: a partir de este nivel se genera una alerta. */
    public boolean esAlertable() {
        return nivel != null && NIVELES_ALERTABLES.contains(nivel);
    }

    /** Tipo con el que se guarda la alerta (por ejemplo RIESGO_MUY_MALO). Nulo si no hay nivel. */
    public String tipoAlerta() {
        return nivel == null ? null : nombreDeTipo(nivel);
    }

    /** Tipos de alerta del mismo nivel o más graves; se usan para el enfriamiento. */
    public List<String> tiposDeAlertaIgualesOPeores() {
        if (nivel == null) {
            return List.of();
        }
        int posicion = NIVELES_ALERTABLES.indexOf(nivel);
        if (posicion < 0) {
            return List.of();
        }
        return NIVELES_ALERTABLES.subList(posicion, NIVELES_ALERTABLES.size()).stream()
                .map(ResultadoClasificacion::nombreDeTipo)
                .toList();
    }

    private static String nombreDeTipo(String nivel) {
        return "RIESGO_" + nivel.replace(" ", "_");
    }
}