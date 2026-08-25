package com.calidadaire.core_ingesta.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import com.calidadaire.core_ingesta.DTO.AlertaCriticaDTO;
import com.calidadaire.core_ingesta.DTO.LecturaNormalDTO;
import com.calidadaire.core_ingesta.entity.AlertaCritica;
import com.calidadaire.core_ingesta.entity.LecturaNormal;
import com.calidadaire.core_ingesta.repository.AlertaCriticaRepository;
import com.calidadaire.core_ingesta.repository.LecturaNormalRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class MqttSubscriberService {

    @Autowired
    private LecturaNormalRepository lecturaRepository;

    @Autowired
    private AlertaCriticaRepository alertaCriticaRepository;

    @Autowired
    private NotificacionService notificacionService; // Placeholder para el Mes 2

    private final ObjectMapper objectMapper = new ObjectMapper();

    // --- CANAL DE LECTURAS NORMALES ---
    @ServiceActivator(inputChannel = "lecturasNormalesCanal")
    public void procesarLecturasNormales(Message<String> mensaje) {
        String payload = mensaje.getPayload();
        try {
            List<LecturaNormalDTO> listaLecturas;
            try {
                // Intentamos leer como lista (para manejar lotes store-and-forward)
                listaLecturas = objectMapper.readValue(payload, new TypeReference<List<LecturaNormalDTO>>() {});
                System.out.println("📦 Lote detectado. Procesando " + listaLecturas.size() + " lecturas atrasadas.");
            } catch (Exception e) {
                // Si falla, asumimos que es un solo objeto JSON
                LecturaNormalDTO lecturaUnica = objectMapper.readValue(payload, LecturaNormalDTO.class);
                listaLecturas = List.of(lecturaUnica);
            }

            for (LecturaNormalDTO dto : listaLecturas) {
                procesarLecturaIndividual(dto);
            }

        } catch (Exception e) {
            System.err.println("❌ Error procesando lecturas normales: " + e.getMessage());
        }
    }

    private void procesarLecturaIndividual(LecturaNormalDTO dto) {
        try {
            // 1. Validación básica
            if (dto.getNodoId() == null || dto.getTimestampOrigen() == null) {
                throw new IllegalArgumentException("nodo_id y timestamp_origen son obligatorios.");
            }

            UUID nodoId = UUID.fromString(dto.getNodoId());
            Instant timestampOrigen = dto.getTimestampOrigen();

            // 2. Deduplicación (Capa de seguridad en código)
            if (lecturaRepository.existsByNodoIdAndTimestampOrigen(nodoId, timestampOrigen)) {
                System.out.println("⚠️ Lectura duplicada ignorada (Store-and-forward) para nodo: " + nodoId);
                return;
            }

            // 3. Mapeo a Entidad
            LecturaNormal lectura = new LecturaNormal();
            lectura.setNodoId(nodoId);
            lectura.setTimestampOrigen(timestampOrigen);
            
            if (dto.getLecturas() != null) {
                validarRangosFisicos(dto.getLecturas());
                lectura.setCo2Ppm(dto.getLecturas().getCo2Ppm());
                lectura.setPm25Ugm3(dto.getLecturas().getPm25Ugm3());
                lectura.setPm10Ugm3(dto.getLecturas().getPm10Ugm3());
            }

            // 4. Guardar en PostgreSQL
            lecturaRepository.save(lectura);
            System.out.println("✅ Lectura normal guardada. Timestamp origen: " + timestampOrigen);

        } catch (IllegalArgumentException e) {
            System.err.println("❌ Error de validación: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("❌ Error procesando lectura individual: " + e.getMessage());
        }
    }

    private void validarRangosFisicos(LecturaNormalDTO.Lecturas lecturas) {
        if (lecturas.getCo2Ppm() != null && lecturas.getCo2Ppm() < 0) {
            throw new IllegalArgumentException("CO2 ppm no puede ser negativo.");
        }
        if (lecturas.getPm25Ugm3() != null && lecturas.getPm25Ugm3() < 0) {
            throw new IllegalArgumentException("PM2.5 no puede ser negativo.");
        }
        if (lecturas.getPm10Ugm3() != null && lecturas.getPm10Ugm3() < 0) {
            throw new IllegalArgumentException("PM10 no puede ser negativo.");
        }
    }

    // --- CANAL DE ALERTAS CRÍTICAS ---
    @ServiceActivator(inputChannel = "alertasCriticasCanal")
    public void procesarAlertaCritica(Message<String> mensaje) {
        String payload = mensaje.getPayload();
        try {
            AlertaCriticaDTO dto = objectMapper.readValue(payload, AlertaCriticaDTO.class);

            // 1. Validación básica
            if (dto.getNodoId() == null || dto.getTimestampOrigen() == null || dto.getTipoAlerta() == null) {
                throw new IllegalArgumentException("Datos incompletos para alerta crítica.");
            }

            // 2. Mapeo a Entidad
            AlertaCritica alerta = new AlertaCritica();
            alerta.setNodoId(UUID.fromString(dto.getNodoId()));
            alerta.setTimestampOrigen(dto.getTimestampOrigen());
            alerta.setTipoAlerta(dto.getTipoAlerta());
            alerta.setValorRegistrado(dto.getValorRegistrado());
            alerta.setMensaje(dto.getMensaje());
            alerta.setEstadoNotificacion(false); // Marcado como pendiente

            // 3. Guardar en BD (Fast-track)
            AlertaCritica alertaGuardada = alertaCriticaRepository.save(alerta);
            System.out.println("🚨 Alerta crítica guardada en BD: " + alertaGuardada.getTipoAlerta());

            // 4. Disparar llamada preliminar hacia notificaciones (Ultra-baja latencia)
            notificacionService.dispararAlertaCritica(alertaGuardada);

        } catch (Exception e) {
            System.err.println("❌ Error procesando alerta crítica: " + e.getMessage());
        }
    }
}