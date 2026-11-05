package com.calidadaire.core_ingesta.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.calidadaire.core_ingesta.DTO.NotificacionAlertaDTO;
import com.calidadaire.core_ingesta.config.PoliticaAlertas;
import com.calidadaire.core_ingesta.entity.AlertaCritica;
import com.calidadaire.core_ingesta.entity.LecturaNormal;
import com.calidadaire.core_ingesta.entity.NodoSensor;
import com.calidadaire.core_ingesta.repository.AlertaCriticaRepository;
import com.calidadaire.core_ingesta.repository.LecturaNormalRepository;
import com.calidadaire.core_ingesta.repository.NodoSensorRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class MqttSubscriberService {

    private static final int MAX_ORIGINAL_EN_LOG = 500;

    private final LecturaNormalRepository lecturaRepository;
    private final AlertaCriticaRepository alertaCriticaRepository;
    private final NodoSensorRepository nodoSensorRepository;
    private final MotorReglasService motorReglasService;
    private final MensajeNormalizador normalizador;
    private final SimpMessagingTemplate messagingTemplate;
    private final PoliticaAlertas politica;
    private final Clock reloj;
    private final MessageChannel notificacionesSalientesCanal;

    // Solo se usa para armar el aviso interno (lleva un Instant)
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public MqttSubscriberService(
            LecturaNormalRepository lecturaRepository,
            AlertaCriticaRepository alertaCriticaRepository,
            NodoSensorRepository nodoSensorRepository,
            MotorReglasService motorReglasService,
            MensajeNormalizador normalizador,
            SimpMessagingTemplate messagingTemplate,
            PoliticaAlertas politica,
            Clock reloj,
            @Qualifier("notificacionesSalientesCanal") MessageChannel notificacionesSalientesCanal) {
        this.lecturaRepository = lecturaRepository;
        this.alertaCriticaRepository = alertaCriticaRepository;
        this.nodoSensorRepository = nodoSensorRepository;
        this.motorReglasService = motorReglasService;
        this.normalizador = normalizador;
        this.messagingTemplate = messagingTemplate;
        this.politica = politica;
        this.reloj = reloj;
        this.notificacionesSalientesCanal = notificacionesSalientesCanal;
    }

    // ---------- Puntos de entrada ----------

    @ServiceActivator(inputChannel = "lecturasNormalesCanal")
    public void procesarLecturas(Message<String> mensaje) {
        procesarPayload(mensaje.getPayload(), "lectura", this::procesarLectura);
    }

    @ServiceActivator(inputChannel = "alertasCriticasCanal")
    public void procesarAlertas(Message<String> mensaje) {
        procesarPayload(mensaje.getPayload(), "alerta", this::procesarAlerta);
    }

    // Acepta un objeto o un arreglo de objetos, y aísla los errores mensaje por mensaje
    private void procesarPayload(String payload, String tipo, Consumer<JsonNode> procesador) {
        JsonNode raiz;
        try {
            raiz = objectMapper.readTree(payload);
        } catch (Exception e) {
            log.warn("Mensaje de {} descartado: no es JSON válido ({}). Original: {}",
                    tipo, e.getMessage(), resumir(payload));
            return;
        }
        if (raiz == null || raiz.isMissingNode()) {
            log.warn("Mensaje de {} descartado: llegó vacío", tipo);
            return;
        }
        if (raiz.isArray()) {
            for (JsonNode elemento : raiz) {
                procesarElemento(elemento, tipo, procesador);
            }
        } else {
            procesarElemento(raiz, tipo, procesador);
        }
    }

    private void procesarElemento(JsonNode elemento, String tipo, Consumer<JsonNode> procesador) {
        try {
            procesador.accept(elemento);
        } catch (MensajeInvalidoException e) {
            log.warn("Mensaje de {} rechazado: {}. Original: {}", tipo, e.getMessage(), resumir(elemento.toString()));
        } catch (Exception e) {
            log.error("Error procesando un mensaje de {}. Original: {}", tipo, resumir(elemento.toString()), e);
        }
    }

    // ---------- Lecturas ----------

    private void procesarLectura(JsonNode mensaje) {
        LecturaNormalizada l = normalizador.normalizarLectura(mensaje);

        boolean duplicada = l.mid() != null
                ? lecturaRepository.existsByMid(l.mid())
                : lecturaRepository.existsByNodoIdAndTimestampOrigen(l.nodoId(), l.timestampOrigen());
        if (duplicada) {
            log.info("Lectura duplicada ignorada (nodo {}, mid {}, momento {})", l.nodoId(), l.mid(), l.timestampOrigen());
            return;
        }

        asegurarNodoRegistrado(l.nodoId(), l.nombreOrigen());

        ResultadoClasificacion resultado = motorReglasService.calcularIndice(l.pm25Ugm3(), l.pm10Ugm3(), l.co2Ppm());

        LecturaNormal lectura = new LecturaNormal();
        lectura.setMid(l.mid());
        lectura.setNodoId(l.nodoId());
        lectura.setTimestampOrigen(l.timestampOrigen());
        lectura.setCo2Ppm(l.co2Ppm());
        lectura.setPm25Ugm3(l.pm25Ugm3());
        lectura.setPm10Ugm3(l.pm10Ugm3());
        lectura.setTemperaturaC(l.temperaturaC());
        lectura.setHumedadPct(l.humedadPct());
        lectura.setNivelRiesgo(resultado.nivel());   // nulo si el mensaje no traía contaminantes

        try {
            lectura = lecturaRepository.save(lectura);
        } catch (DataIntegrityViolationException e) {
            log.info("Lectura duplicada ignorada por restricción de la base (nodo {}, mid {})", l.nodoId(), l.mid());
            return;
        }
        log.info("Lectura guardada: nodo {} | riesgo {} | momento {}",
                l.nodoId(), resultado.sinDatos() ? "sin datos" : resultado.nivel(), l.timestampOrigen());

        messagingTemplate.convertAndSend("/topic/lecturas", lectura);

        if (resultado.esAlertable()) {
            crearAlertaDeRiesgo(l, resultado);
        }
    }

    private void crearAlertaDeRiesgo(LecturaNormalizada l, ResultadoClasificacion resultado) {
        String tipo = resultado.tipoAlerta();

        if (alertaCriticaRepository.existsByNodoIdAndTimestampOrigenAndTipoAlerta(l.nodoId(), l.timestampOrigen(), tipo)) {
            log.info("Ya existe la alerta {} para el nodo {} en ese momento", tipo, l.nodoId());
            return;
        }

        // Enfriamiento: no repetir mientras haya una alerta igual o más grave dentro de la ventana
        if (politica.getEnfriamientoMinutos() > 0) {
            Instant desde = l.timestampOrigen().minus(Duration.ofMinutes(politica.getEnfriamientoMinutos()));
            if (alertaCriticaRepository.existsByNodoIdAndTipoAlertaInAndTimestampOrigenAfter(
                    l.nodoId(), resultado.tiposDeAlertaIgualesOPeores(), desde)) {
                log.info("Alerta {} omitida por enfriamiento (nodo {}): ya hubo una igual o más grave en los últimos {} min",
                        tipo, l.nodoId(), politica.getEnfriamientoMinutos());
                return;
            }
        }

        AlertaCritica alerta = new AlertaCritica();
        alerta.setNodoId(l.nodoId());
        alerta.setTimestampOrigen(l.timestampOrigen());
        alerta.setTipoAlerta(tipo);
        alerta.setValorRegistrado(resultado.valorCausante());
        alerta.setMensaje("El motor de reglas clasificó la lectura como " + resultado.nivel()
                + " (variable causante: " + resultado.variableCausante() + ")");
        alerta.setEstadoNotificacion(false);

        AlertaCritica guardada = alertaCriticaRepository.save(alerta);
        log.warn("Alerta {} generada por el motor de reglas (nodo {}, alerta id {})", tipo, l.nodoId(), guardada.getId());
        avisar(guardada, politica.isCorreoDesdeMotor());
    }

    // ---------- Alertas que llegan del nodo ----------

    private void procesarAlerta(JsonNode mensaje) {
        Optional<AlertaNormalizada> normalizada = normalizador.normalizarAlerta(mensaje);
        if (normalizada.isEmpty()) {
            log.debug("Alarma resuelta ignorada: {}", resumir(mensaje.toString()));
            return;
        }
        AlertaNormalizada a = normalizada.get();

        boolean duplicada = a.mid() != null
                ? alertaCriticaRepository.existsByMid(a.mid())
                : alertaCriticaRepository.existsByNodoIdAndTimestampOrigenAndTipoAlerta(
                        a.nodoId(), a.timestampOrigen(), a.tipoAlerta());
        if (duplicada) {
            log.info("Alerta duplicada ignorada (nodo {}, mid {}, tipo {})", a.nodoId(), a.mid(), a.tipoAlerta());
            return;
        }

        asegurarNodoRegistrado(a.nodoId(), a.nombreOrigen());

        AlertaCritica alerta = new AlertaCritica();
        alerta.setMid(a.mid());
        alerta.setNodoId(a.nodoId());
        alerta.setTimestampOrigen(a.timestampOrigen());
        alerta.setTipoAlerta(a.tipoAlerta());
        alerta.setValorRegistrado(a.valorRegistrado());
        alerta.setMensaje(a.mensaje());
        alerta.setEstadoNotificacion(false);

        AlertaCritica guardada;
        try {
            guardada = alertaCriticaRepository.save(alerta);
        } catch (DataIntegrityViolationException e) {
            log.info("Alerta duplicada ignorada por restricción de la base (nodo {}, mid {})", a.nodoId(), a.mid());
            return;
        }
        log.warn("Alerta del nodo guardada: {} (nodo {}, alerta id {})", a.tipoAlerta(), a.nodoId(), guardada.getId());
        avisar(guardada, politica.isCorreoDesdeNodo());
    }

    // ---------- Utilidades ----------

    private void asegurarNodoRegistrado(UUID nodoId, String nombreOrigen) {
        if (!nodoSensorRepository.existsById(nodoId)) {
            NodoSensor nuevo = new NodoSensor();
            nuevo.setId(nodoId);
            // Si el nodo se identificó con un nombre (p. ej. S_AIRE_06), sirve de ubicación inicial
            nuevo.setUbicacion(nombreOrigen != null ? nombreOrigen : "Sin asignar");
            nuevo.setEstatus(true);
            nodoSensorRepository.save(nuevo);
            log.info("Nodo auto-registrado: {} ({})", nodoId, nuevo.getUbicacion());
        }
    }

    // Transmite al dashboard y, si la política lo permite, publica el aviso para core-notificaciones
    private void avisar(AlertaCritica alerta, boolean enviarCorreo) {
        if (!esReciente(alerta.getTimestampOrigen())) {
            log.info("La alerta id={} es de hace más de {} min: se guarda sin avisar",
                    alerta.getId(), politica.getAntiguedadMaximaMinutos());
            return;
        }

        messagingTemplate.convertAndSend("/topic/alertas", alerta);

        if (!enviarCorreo) {
            log.info("Alerta id={} transmitida al dashboard; el correo está desactivado para este origen", alerta.getId());
            return;
        }

        try {
            NotificacionAlertaDTO dto = new NotificacionAlertaDTO(
                    alerta.getId(),
                    alerta.getNodoId().toString(),
                    alerta.getTipoAlerta(),
                    alerta.getValorRegistrado(),
                    alerta.getMensaje(),
                    alerta.getTimestampOrigen());
            String payload = objectMapper.writeValueAsString(dto);
            notificacionesSalientesCanal.send(MessageBuilder.withPayload(payload).build());
            log.info("Notificación publicada para la alerta id={}", alerta.getId());
        } catch (Exception e) {
            log.error("No se pudo publicar la notificación de la alerta id={}", alerta.getId(), e);
        }
    }

    private boolean esReciente(Instant momento) {
        int limite = politica.getAntiguedadMaximaMinutos();
        if (limite <= 0) {
            return true;
        }
        return !momento.isBefore(reloj.instant().minus(Duration.ofMinutes(limite)));
    }

    private static String resumir(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.length() <= MAX_ORIGINAL_EN_LOG ? texto : texto.substring(0, MAX_ORIGINAL_EN_LOG) + "…";
    }
}