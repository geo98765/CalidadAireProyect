package com.calidadaire.core_ingesta.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "alertas")
public class PoliticaAlertas {
    private boolean correoDesdeNodo = false;
    private boolean correoDesdeMotor = true;
    private int enfriamientoMinutos = 60;
    private int antiguedadMaximaMinutos = 30;
}