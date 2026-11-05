package com.calidadaire.core_ingesta.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracionReloj {

    // Reloj inyectable: en las pruebas se sustituye por uno fijo
    @Bean
    public Clock reloj() {
        return Clock.systemUTC();
    }
}