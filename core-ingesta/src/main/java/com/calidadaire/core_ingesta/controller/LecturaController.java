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
@CrossOrigin(origins = "*")
@Tag(name = "Lecturas", description = "Endpoints para consultar el estado actual de la calidad del aire")
public class LecturaController {

    @Autowired
    private LecturaNormalRepository lecturaRepository;

    @GetMapping("/ultima/{nodoId}")
    @Operation(
        summary = "Obtener la lectura mas reciente", 
        description = "Devuelve el registro mas nuevo de un nodo de monitoreo especifico"
    )
    public ResponseEntity<?> obtenerUltimaLectura(@PathVariable String nodoId) {
        try {
            UUID id = UUID.fromString(nodoId);
            Optional<LecturaNormal> ultimaLectura = lecturaRepository.findTopByNodoIdOrderByTimestampOrigenDesc(id);

            if (ultimaLectura.isPresent()) {
                return ResponseEntity.ok(ultimaLectura.get());
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("el formato es invalido");
        }
    }
}