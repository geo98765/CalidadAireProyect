package com.calidadaire.core_ingesta.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.calidadaire.core_ingesta.DTO.AlertaCriticaDTO;
import com.calidadaire.core_ingesta.DTO.LecturaNormalDTO;
import com.calidadaire.core_ingesta.entity.AlertaCritica;
import com.calidadaire.core_ingesta.entity.LecturaNormal;
import com.calidadaire.core_ingesta.repository.AlertaCriticaRepository;
import com.calidadaire.core_ingesta.repository.LecturaNormalRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Service
public class MqttSubscriberService {

    @Autowired
    private LecturaNormalRepository lecturaRepository;

    @Autowired
    private MotorReglasService motorReglasService;

    @Autowired
    private AlertaCriticaRepository alertaCriticaRepository;

    @Autowired
    private NotificacionService notificacionService; 

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @ServiceActivator(inputChannel = "lecturasNormalesCanal")
    public void procesarLecturasNormales(Message<String> mensaje) {
        String payload = mensaje.getPayload();
        try {
            JsonNode rootNode = objectMapper.readTree(payload);

            if (rootNode.isArray()) {
                System.out.println("📦 [STORE-AND-FORWARD] Lote detectado. Procesando " + rootNode.size() + " lecturas atrasadas.");
                for (JsonNode nodo : rootNode) {
                    LecturaNormalDTO dto = objectMapper.treeToValue(nodo, LecturaNormalDTO.class);
                    procesarLecturaIndividual(dto);
                }
            } else {
                LecturaNormalDTO dto = objectMapper.treeToValue(rootNode, LecturaNormalDTO.class);
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

            if (lecturaRepository.existsByNodoIdAndTimestampOrigen(nodoId, timestampOrigen)) {
                System.out.println("lectura duplicada " + nodoId);
                return;
            }

            LecturaNormal lectura = new LecturaNormal();
            lectura.setNodoId(nodoId);
            lectura.setTimestampOrigen(timestampOrigen);
            
            String nivelRiesgo = "DESCONOCIDO";

            if (dto.getLecturas() != null) {
                validarRangosFisicos(dto.getLecturas());
                lectura.setCo2Ppm(dto.getLecturas().getCo2Ppm());
                lectura.setPm25Ugm3(dto.getLecturas().getPm25Ugm3());
                lectura.setPm10Ugm3(dto.getLecturas().getPm10Ugm3());

                nivelRiesgo = motorReglasService.calcularIndice(lectura.getPm25Ugm3(), lectura.getPm10Ugm3(), lectura.getCo2Ppm());
                lectura.setNivelRiesgo(nivelRiesgo);
            }

            lecturaRepository.save(lectura);
            System.out.println("✅ Lectura guardada. Riesgo: " + nivelRiesgo + " | TS: " + timestampOrigen);

            messagingTemplate.convertAndSend("/topic/lecturas", lectura);
            System.out.println("📊 [WEBSOCKET] Lectura rutinaria transmitida al Dashboard.");

            if ("MALO".equals(nivelRiesgo) || "MUY MALO".equals(nivelRiesgo)) {
                System.out.println("⚠️ [MOTOR DE REGLAS] Calidad de aire peligrosa detectada. Generando alerta interna...");
                
                AlertaCritica alertaInterna = new AlertaCritica();
                alertaInterna.setNodoId(nodoId);
                alertaInterna.setTimestampOrigen(timestampOrigen);
                alertaInterna.setTipoAlerta("RIESGO_" + nivelRiesgo.replace(" ", "_"));
                alertaInterna.setValorRegistrado(lectura.getCo2Ppm()); // Tomamos el CO2 como referencia
                alertaInterna.setMensaje("El Motor de Reglas clasificó la lectura normal rutinaria como: " + nivelRiesgo);
                alertaInterna.setEstadoNotificacion(false);

                AlertaCritica alertaGuardada = alertaCriticaRepository.save(alertaInterna);
                notificacionService.dispararAlertaCritica(alertaGuardada);
            }

        } catch (Exception e) {
            System.err.println("❌ Error procesando lectura individual: " + e.getMessage());
        }
    }

    private void validarRangosFisicos(LecturaNormalDTO.Lecturas lecturas) {
        if (lecturas.getCo2Ppm() != null && lecturas.getCo2Ppm() < 0) throw new IllegalArgumentException("CO2 ppm no puede ser negativo.");
        if (lecturas.getPm25Ugm3() != null && lecturas.getPm25Ugm3() < 0) throw new IllegalArgumentException("PM2.5 no puede ser negativo.");
        if (lecturas.getPm10Ugm3() != null && lecturas.getPm10Ugm3() < 0) throw new IllegalArgumentException("PM10 no puede ser negativo.");
    }

    @ServiceActivator(inputChannel = "alertasCriticasCanal")
    public void procesarAlertaCritica(Message<String> mensaje) {
        String payload = mensaje.getPayload();
        try {
            AlertaCriticaDTO dto = objectMapper.readValue(payload, AlertaCriticaDTO.class);

            if (dto.getNodoId() == null || dto.getTimestampOrigen() == null || dto.getTipoAlerta() == null) {
                throw new IllegalArgumentException("Datos incompletos para alerta crítica.");
            }

            AlertaCritica alerta = new AlertaCritica();
            alerta.setNodoId(UUID.fromString(dto.getNodoId()));
            alerta.setTimestampOrigen(dto.getTimestampOrigen());
            alerta.setTipoAlerta(dto.getTipoAlerta());
            alerta.setValorRegistrado(dto.getValorRegistrado());
            alerta.setMensaje(dto.getMensaje());
            alerta.setEstadoNotificacion(false); 

            AlertaCritica alertaGuardada = alertaCriticaRepository.save(alerta);
            System.out.println("Alerta guardada en BD: " + alertaGuardada.getTipoAlerta());

            notificacionService.dispararAlertaCritica(alertaGuardada);

        } catch (Exception e) {
            System.err.println("Error" + e.getMessage());
        }
    }
}