package com.calidadaire.core_ingesta.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.calidadaire.core_ingesta.DTO.NodoSensorDTO;
import com.calidadaire.core_ingesta.entity.NodoSensor;
import com.calidadaire.core_ingesta.repository.NodoSensorRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/nodos")
@CrossOrigin(origins = "*")
@Tag(name = "3. Catálogo de Nodos", description = "Endpoints para administrar las estaciones de monitoreo")
public class NodoSensorController {

    @Autowired
    private NodoSensorRepository nodoSensorRepository;

    @GetMapping
    @Operation(summary = "Listar todos los nodos",
            description = "Incluye tanto los registrados manualmente como los autoregistrados por primer contacto MQTT.")
    public ResponseEntity<List<NodoSensor>> listarNodos() {
        return ResponseEntity.ok(nodoSensorRepository.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un nodo por ID")
    public ResponseEntity<?> obtenerNodo(@PathVariable String id) {
        try {
            UUID nodoId = UUID.fromString(id);
            return nodoSensorRepository.findById(nodoId)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("El formato del ID de nodo es invalido.");
        }
    }

    @PostMapping
    @Operation(summary = "Registrar un nodo manualmente",
            description = "Para precargar un nodo antes de que la placa mande su primer mensaje. Falla con 409 si el ID ya existe.")
    public ResponseEntity<?> crearNodo(@RequestBody NodoSensorDTO dto) {
        if (dto.getId() == null) {
            return ResponseEntity.badRequest().body("El campo 'id' es obligatorio.");
        }
        if (nodoSensorRepository.existsById(dto.getId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Ya existe un nodo con ese ID.");
        }

        NodoSensor nodo = new NodoSensor();
        nodo.setId(dto.getId());
        nodo.setMacAddress(dto.getMacAddress());
        nodo.setUbicacion(dto.getUbicacion());
        nodo.setEstatus(dto.getEstatus() != null ? dto.getEstatus() : true);

        NodoSensor guardado = nodoSensorRepository.save(nodo);
        return ResponseEntity.status(HttpStatus.CREATED).body(guardado);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar ubicación/estatus de un nodo existente")
    public ResponseEntity<?> actualizarNodo(@PathVariable String id, @RequestBody NodoSensorDTO dto) {
        UUID nodoId;
        try {
            nodoId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("El formato del ID de nodo es inválido.");
        }

        return nodoSensorRepository.findById(nodoId)
                .map(nodo -> {
                    if (dto.getUbicacion() != null) nodo.setUbicacion(dto.getUbicacion());
                    if (dto.getEstatus() != null) nodo.setEstatus(dto.getEstatus());
                    if (dto.getMacAddress() != null) nodo.setMacAddress(dto.getMacAddress());
                    return ResponseEntity.ok(nodoSensorRepository.save(nodo));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}