package com.calidadaire.core_ingesta.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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


    @GetMapping("/historico")
    @Operation(
        summary = "Obtener histórico de lecturas",
        description = "Devuelve lecturas en un rango de fechas. Por defecto, últimas 24 horas. Acepta horas, o desde/hasta (yyyy-MM-dd) para un rango personalizado. Filtra por nodoId si se envía."
    )
    public ResponseEntity<List<LecturaNormal>> obtenerHistorico(
            @RequestParam(required = false) String nodoId,
            @RequestParam(required = false) Integer horas,
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta) {

        Instant hastaInstant = (hasta != null && !hasta.isBlank())
                ? LocalDate.parse(hasta).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
                : Instant.now();

        Instant desdeInstant = (desde != null && !desde.isBlank())
                ? LocalDate.parse(desde).atStartOfDay(ZoneOffset.UTC).toInstant()
                : hastaInstant.minus((horas != null ? horas : 24), ChronoUnit.HOURS);

        if (nodoId != null && !nodoId.isBlank()) {
            UUID id = UUID.fromString(nodoId);
            return ResponseEntity.ok(lecturaRepository.findByNodoIdAndTimestampOrigenBetweenOrderByTimestampOrigenAsc(id, desdeInstant, hastaInstant));
        }
        return ResponseEntity.ok(lecturaRepository.findByTimestampOrigenBetweenOrderByTimestampOrigenAsc(desdeInstant, hastaInstant));
    }

    @GetMapping("/primera")
    @Operation(
        summary = "Obtener la fecha de la lectura más antigua registrada",
        description = "Útil para limitar el selector de fechas del histórico personalizado."
    )
    public ResponseEntity<Map<String, Instant>> obtenerPrimeraLectura() {
        return lecturaRepository.findTopByOrderByTimestampOrigenAsc()
                .map(l -> ResponseEntity.ok(Map.of("timestampOrigen", l.getTimestampOrigen())))
                .orElse(ResponseEntity.ok(Map.of()));
    }
}