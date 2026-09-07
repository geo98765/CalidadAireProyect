package com.calidadaire.core_ingesta.DTO;


import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LecturaNormalDTO {
    
    @JsonProperty("nodo_id")
    private String nodoId;

    @JsonProperty("timestamp_origen")
    private Instant timestampOrigen;

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


    // ... (Tus Getters y Setters se quedan exactamente igual) ...

    public static class Lecturas {
        
        @JsonProperty("co2_ppm")
        private Double co2Ppm;

        @JsonProperty("pm25_ugm3")
        private Double pm25Ugm3;

        @JsonProperty("pm10_ugm3")
        private Double pm10Ugm3;
        
        
        // ... (Tus Getters y Setters se quedan exactamente igual) ...

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