package com.calidadaire.core_ingesta.service;

import org.springframework.stereotype.Service;

@Service
public class MotorReglasService {

    // Etiquetas constantes de la NOM-172
    public static final String RIESGO_BUENO = "BUENO";
    public static final String RIESGO_ACEPTABLE = "ACEPTABLE";
    public static final String RIESGO_MALO = "MALO";
    public static final String RIESGO_MUY_MALO = "MUY MALO";
    public static final String RIESGO_EXTREMADAMENTE_MALO = "EXTREMADAMENTE MALO";

    /**
     * Calcula el nivel de riesgo global basándose en el principio del 
     * "peor escenario" entre los contaminantes evaluados.
     */
    public String calcularIndice(Double pm25, Double pm10, Double co2) {
        int nivelPm25 = evaluarPM25(pm25 != null ? pm25 : 0.0);
        int nivelPm10 = evaluarPM10(pm10 != null ? pm10 : 0.0);
        int nivelCo2 = evaluarCO2(co2 != null ? co2 : 0.0);

        // Obtenemos el nivel más alto de riesgo (0 = Bueno, 4 = Extremadamente Malo)
        int peorEscenario = Math.max(nivelPm25, Math.max(nivelPm10, nivelCo2));

        return traducirNivelAString(peorEscenario);
    }

    private int evaluarPM25(Double valor) {
        if (valor <= 12.0) return 0; // BUENO
        if (valor <= 45.0) return 1; // ACEPTABLE
        if (valor <= 97.0) return 2; // MALO
        if (valor <= 150.0) return 3; // MUY MALO
        return 4; // EXTREMADAMENTE MALO
    }

    private int evaluarPM10(Double valor) {
        if (valor <= 50.0) return 0;
        if (valor <= 75.0) return 1;
        if (valor <= 155.0) return 2;
        if (valor <= 235.0) return 3;
        return 4;
    }

    private int evaluarCO2(Double valor) {
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