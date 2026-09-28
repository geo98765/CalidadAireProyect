package com.calidadaire.core_ingesta.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.calidadaire.core_ingesta.DTO.AlertaCriticaDTO;
import com.calidadaire.core_ingesta.DTO.LecturaNormalDTO;
import com.calidadaire.core_ingesta.DTO.NotificacionAlertaDTO;
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
    @Qualifier("notificacionesSalientesCanal")
    private MessageChannel notificacionesSalientesCanal;

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
            ResultadoClasificacion resultado = null;

            if (dto.getLecturas() != null) {
                validarRangosFisicos(dto.getLecturas());
                lectura.setCo2Ppm(dto.getLecturas().getCo2Ppm());
                lectura.setPm25Ugm3(dto.getLecturas().getPm25Ugm3());
                lectura.setPm10Ugm3(dto.getLecturas().getPm10Ugm3());

                resultado = motorReglasService.calcularIndice(lectura.getPm25Ugm3(), lectura.getPm10Ugm3(), lectura.getCo2Ppm());
                nivelRiesgo = resultado.nivel();
                lectura.setNivelRiesgo(nivelRiesgo);
            }

            lecturaRepository.save(lectura);
            System.out.println("lectura guardada Riesgo " + nivelRiesgo + " | TS: " + timestampOrigen);

            messagingTemplate.convertAndSend("/topic/lecturas", lectura);
            System.out.println("transmitida al Dashboard");

            if (resultado != null && ("MALO".equals(nivelRiesgo) || "MUY MALO".equals(nivelRiesgo))) {
                if (alertaCriticaRepository.existsByNodoIdAndTimestampOrigen(nodoId, timestampOrigen)) {
                    System.out.println("ya existe una alerta critica para este nodo");
                } else {
                    System.out.println("calidad de aire peligrosa");

                    AlertaCritica alertaInterna = new AlertaCritica();
                    alertaInterna.setNodoId(nodoId);
                    alertaInterna.setTimestampOrigen(timestampOrigen);
                    alertaInterna.setTipoAlerta("RIESGO_" + nivelRiesgo.replace(" ", "_"));
                    alertaInterna.setValorRegistrado(resultado.valorCausante());
                    alertaInterna.setMensaje("se clasifico la lectura normal como: " + nivelRiesgo
                            + " (variable causante: " + resultado.variableCausante() + ")");
                    alertaInterna.setEstadoNotificacion(false);

                    AlertaCritica alertaGuardada = alertaCriticaRepository.save(alertaInterna);
                    notificarYTransmitirAlerta(alertaGuardada);
                }
            }

        } catch (Exception e) {
            System.err.println("Error" + e.getMessage());
        }
    }

    private void validarRangosFisicos(LecturaNormalDTO.Lecturas lecturas) {
        if (lecturas.getCo2Ppm() != null && lecturas.getCo2Ppm() < 0) throw new IllegalArgumentException("CO2 ppm no puede ser negativo.");
        if (lecturas.getPm25Ugm3() != null && lecturas.getPm25Ugm3() < 0) throw new IllegalArgumentException("PM2.5 no puede ser negativo.");
        if (lecturas.getPm10Ugm3() != null && lecturas.getPm10Ugm3() < 0) throw new IllegalArgumentException("PM10 no puede ser negativo.");
    }

    private void notificarYTransmitirAlerta(AlertaCritica alerta) {
        messagingTemplate.convertAndSend("/topic/alertas", alerta);

        try {
            NotificacionAlertaDTO dto = new NotificacionAlertaDTO(
                    alerta.getId(),
                    alerta.getNodoId().toString(),
                    alerta.getTipoAlerta(),
                    alerta.getValorRegistrado(),
                    alerta.getMensaje(),
                    alerta.getTimestampOrigen()
            );
            String payload = objectMapper.writeValueAsString(dto);
            notificacionesSalientesCanal.send(MessageBuilder.withPayload(payload).build());
            System.out.println("notificacion publicada para alerta id=" + alerta.getId());
        } catch (Exception e) {
            System.err.println("Error" + e.getMessage());
        }
    }




    @ServiceActivator(inputChannel = "alertasCriticasCanal")
    public void procesarAlertaCritica(Message<String> mensaje) {
        String payload = mensaje.getPayload();
        try {
            AlertaCriticaDTO dto = objectMapper.readValue(payload, AlertaCriticaDTO.class);

            if (dto.getNodoId() == null || dto.getTimestampOrigen() == null || dto.getTipoAlerta() == null) {
                throw new IllegalArgumentException("Datos incompletos en alerta critica ");
            }

            UUID nodoId = UUID.fromString(dto.getNodoId());
            Instant timestampOrigen = dto.getTimestampOrigen();

            if (alertaCriticaRepository.existsByNodoIdAndTimestampOrigen(nodoId, timestampOrigen)) {
                System.out.println("alerta critica duplicada " + nodoId);
                return;
            }

            AlertaCritica alerta = new AlertaCritica();
            alerta.setNodoId(nodoId);
            alerta.setTimestampOrigen(timestampOrigen);
            alerta.setTipoAlerta(dto.getTipoAlerta());
            alerta.setValorRegistrado(dto.getValorRegistrado());
            alerta.setMensaje(dto.getMensaje());
            alerta.setEstadoNotificacion(false); 

            AlertaCritica alertaGuardada = alertaCriticaRepository.save(alerta);
            System.out.println("Alerta guardada en BD: " + alertaGuardada.getTipoAlerta());

            notificarYTransmitirAlerta(alertaGuardada);

        } catch (Exception e) {
            System.err.println("Error" + e.getMessage());
        }
    }
}