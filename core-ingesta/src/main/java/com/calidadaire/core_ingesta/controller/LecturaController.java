package com.calidadaire.core_ingesta.controller;

import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.calidadaire.core_ingesta.entity.LecturaNormal;
import com.calidadaire.core_ingesta.repository.LecturaNormalRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/lecturas")
@CrossOrigin(origins = "*") // ¡Clave para que el frontend del Residente 1 no sea bloqueado por seguridad!
@Tag(name = "Lecturas", description = "Endpoints para consultar el estado actual de la calidad del aire")
public class LecturaController {

    @Autowired
    private LecturaNormalRepository lecturaRepository;

    @GetMapping("/ultima/{nodoId}")
    @Operation(
        summary = "Obtener la lectura más reciente", 
        description = "Devuelve el registro más nuevo de un nodo de monitoreo específico. Ideal para actualizar medidores en tiempo real."
    )
    public ResponseEntity<?> obtenerUltimaLectura(@PathVariable String nodoId) {
        try {
            UUID id = UUID.fromString(nodoId);
            Optional<LecturaNormal> ultimaLectura = lecturaRepository.findTopByNodoIdOrderByTimestampOrigenDesc(id);

            if (ultimaLectura.isPresent()) {
                // Devuelve un HTTP 200 (OK) con el JSON de la lectura
                return ResponseEntity.ok(ultimaLectura.get());
            } else {
                // Devuelve un HTTP 404 si el sensor existe pero no tiene lecturas
                return ResponseEntity.notFound().build();
            }
            
        } catch (IllegalArgumentException e) {
            // Devuelve un HTTP 400 si el texto enviado no es un UUID válido
            return ResponseEntity.badRequest().body("El formato del ID de nodo es inválido.");
        }
    }
}