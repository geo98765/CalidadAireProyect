package com.calidadaire.core_ingesta.DTO;

import java.time.Instant;

public class LecturaNormalDTO {
    private String nodoId;
    private Instant timestampOrigen; // Manejo en UTC
    private Lecturas lecturas;

    public String getNodoId() {
        return nodoId;
    }

    public void setNodoId(String nodoId) {
        this.nodoId = nodoId;
    }

    public Instant getTimestampOrigen() {
        return timestampOrigen;
    }

    public void setTimestampOrigen(Instant timestampOrigen) {
        this.timestampOrigen = timestampOrigen;
    }

    public Lecturas getLecturas() {
        return lecturas;
    }

    public void setLecturas(Lecturas lecturas) {
        this.lecturas = lecturas;
    }

    // Getters y Setters...

    public static class Lecturas {
        private Double co2Ppm;
        private Double pm25Ugm3;
        private Double pm10Ugm3;
        
        // Getters y Setters...

        public Double getCo2Ppm() {
            return co2Ppm;
        }

        public void setCo2Ppm(Double co2Ppm) {
            this.co2Ppm = co2Ppm;
        }

        public Double getPm25Ugm3() {
            return pm25Ugm3;
        }

        public void setPm25Ugm3(Double pm25Ugm3) {
            this.pm25Ugm3 = pm25Ugm3;
        }

        public Double getPm10Ugm3() {
            return pm10Ugm3;
        }

        public void setPm10Ugm3(Double pm10Ugm3) {
            this.pm10Ugm3 = pm10Ugm3;
        }

    }
    
}