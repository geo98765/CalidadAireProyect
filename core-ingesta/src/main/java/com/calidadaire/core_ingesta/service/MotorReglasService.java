package com.calidadaire.core_ingesta.service;

import org.springframework.stereotype.Service;

@Service
public class MotorReglasService {

    // Niveles de riesgo según NOM-172
    public static final String RIESGO_BUENO = "BUENO";
    public static final String RIESGO_ACEPTABLE = "ACEPTABLE";
    public static final String RIESGO_MALO = "MALO";
    public static final String RIESGO_MUY_MALO = "MUY MALO";
    public static final String RIESGO_EXTREMADAMENTE_MALO = "EXTREMADAMENTE MALO";

    /**
     * Clasifica una lectura con las variables que traiga: las ausentes (null) no cuentan.
     * Si no llega ninguna, devuelve SIN_DATOS en lugar de suponer "BUENO".
     * Cuando dos variables empatan en nivel, la causante se elige en este orden: PM2.5, PM10, CO2.
     */
    public ResultadoClasificacion calcularIndice(Double pm25, Double pm10, Double co2) {
        int peorNivel = -1;
        String variable = null;
        Double valor = null;

        // Solo se reemplaza si el nivel es estrictamente peor: el orden de estos bloques decide los empates
        if (esUsable(pm25)) {
            int nivel = evaluarPM25(pm25);
            if (nivel > peorNivel) { peorNivel = nivel; variable = "PM2.5"; valor = pm25; }
        }
        if (esUsable(pm10)) {
            int nivel = evaluarPM10(pm10);
            if (nivel > peorNivel) { peorNivel = nivel; variable = "PM10"; valor = pm10; }
        }
        if (esUsable(co2)) {
            int nivel = evaluarCO2(co2);
            if (nivel > peorNivel) { peorNivel = nivel; variable = "CO2"; valor = co2; }
        }

        if (variable == null) {
            return ResultadoClasificacion.SIN_DATOS;
        }
        return new ResultadoClasificacion(traducirNivelAString(peorNivel), variable, valor);
    }

    // Un valor ausente o no numérico no participa en la clasificación
    private boolean esUsable(Double valor) {
        return valor != null && !valor.isNaN() && !valor.isInfinite();
    }

    private int evaluarPM25(double valor) {
        if (valor <= 12.0) return 0;   // bueno
        if (valor <= 45.0) return 1;   // aceptable
        if (valor <= 97.0) return 2;   // malo
        if (valor <= 150.0) return 3;  // muy malo
        return 4;                      // extremadamente malo
    }

    private int evaluarPM10(double valor) {
        if (valor <= 50.0) return 0;
        if (valor <= 75.0) return 1;
        if (valor <= 155.0) return 2;
        if (valor <= 235.0) return 3;
        return 4;
    }

    private int evaluarCO2(double valor) {
        if (valor <= 1000.0) return 0;
        if (valor <= 1500.0) return 1;
        if (valor <= 2000.0) return 2;
        if (valor <= 5000.0) return 3;
        return 4;
    }

    private String traducirNivelAString(int nivelNum) {
        return switch (nivelNum) {
            case 0 -> RIESGO_BUENO;
            case 1 -> RIESGO_ACEPTABLE;
            case 2 -> RIESGO_MALO;
            case 3 -> RIESGO_MUY_MALO;
            case 4 -> RIESGO_EXTREMADAMENTE_MALO;
            default -> "DESCONOCIDO";
        };
    }
}