package com.calidadaire.core_ingesta.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.calidadaire.core_ingesta.entity.AlertaCritica;
import com.calidadaire.core_ingesta.repository.AlertaCriticaRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/alertas")
@CrossOrigin(origins = "*") // De nuevo, vital para el Dashboard

@Tag(name = "2. Bitácora de Alertas", description = "Endpoints para consultar el historial de emergencias y riesgos de salud")
public class AlertaController {

    @Autowired
    private AlertaCriticaRepository alertaRepository;

    @GetMapping("/recientes")
    @Operation(
        summary = "Obtener el historial reciente de alertas", 
        description = "Devuelve una lista con las 20 alertas críticas más recientes registradas en toda la red, ordenadas de la más nueva a la más antigua."
    )
    public ResponseEntity<List<AlertaCritica>> obtenerAlertasRecientes() {
        // Obtenemos la lista directamente de PostgreSQL
        List<AlertaCritica> alertas = alertaRepository.findTop20ByOrderByTimestampOrigenDesc();
        
        // Devolvemos un HTTP 200 (OK) con el arreglo JSON
        return ResponseEntity.ok(alertas);
    }
}