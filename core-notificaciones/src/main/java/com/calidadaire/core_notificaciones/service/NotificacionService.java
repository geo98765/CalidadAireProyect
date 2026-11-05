package com.calidadaire.core_notificaciones.service;

import static java.lang.Math.log;
import static java.lang.StrictMath.log;
import static java.rmi.server.LogStream.log;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import com.calidadaire.core_notificaciones.dto.NotificacionAlertaDTO;
import com.calidadaire.core_notificaciones.entity.AlertaCritica;
import com.calidadaire.core_notificaciones.repository.AlertaCriticaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class NotificacionService {

    private final AlertaCriticaRepository alertaCriticaRepository;
    private final JavaMailSender mailSender;
    private final String remitente;
    private final String[] destinatarios;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public NotificacionService(
            AlertaCriticaRepository alertaCriticaRepository,
            JavaMailSender mailSender,
            @Value("${spring.mail.username}") String remitente,
            @Value("${notificaciones.destinatario}") String destinatario) {
        this.alertaCriticaRepository = alertaCriticaRepository;
        this.mailSender = mailSender;
        this.remitente = remitente;
        // Admite varios destinatarios separados por coma; ignora los vacíos
        this.destinatarios = Arrays.stream(destinatario.split(","))
                .map(String::trim)
                .filter(texto -> !texto.isEmpty())
                .toArray(String[]::new);
    }

    @ServiceActivator(inputChannel = "notificacionesEntrantesCanal")
    public void procesarNotificacion(Message<String> mensaje) {
        NotificacionAlertaDTO dto;
        try {
            dto = objectMapper.readValue(mensaje.getPayload(), NotificacionAlertaDTO.class);
        } catch (Exception e) {
            log.error("Notificación entrante ilegible: {}", mensaje.getPayload(), e);
            return;
        }
        enviarCorreo(dto);
    }

    private void enviarCorreo(NotificacionAlertaDTO dto) {
        if (dto.id() == null) {
            log.warn("Notificación sin id de alerta; se ignora: {}", dto);
            return;
        }

        AlertaCritica alerta = alertaCriticaRepository.findById(dto.id()).orElse(null);
        if (alerta == null) {
            log.warn("No se encontró la alerta id={} en la base de datos", dto.id());
            return;
        }

        // Si el mensaje llega repetido, no se manda un segundo correo
        if (Boolean.TRUE.equals(alerta.getEstadoNotificacion())) {
            log.info("La alerta id={} ya fue notificada antes", dto.id());
            return;
        }

        if (destinatarios.length == 0) {
            log.warn("No hay destinatarios configurados (MAIL_DESTINATARIO): la alerta id={} no se envió por correo", dto.id());
            return;
        }

        log.info("Enviando el correo de la alerta id={} (nodo {})", dto.id(), dto.nodoId());
        try {
            SimpleMailMessage correo = new SimpleMailMessage();
            correo.setFrom(remitente);
            correo.setTo(destinatarios);
            correo.setSubject("ALERTA CRÍTICA DE CALIDAD DEL AIRE: " + dto.tipoAlerta());
            correo.setText("Se ha detectado un dato crítico.\n\n"
                    + "Nodo afectado: " + dto.nodoId() + "\n"
                    + "Tipo de alerta: " + dto.tipoAlerta() + "\n"
                    + "Valor registrado: " + dto.valorRegistrado() + "\n"
                    + "Fecha: " + dto.timestampOrigen() + "\n"
                    + "Detalle: " + dto.mensaje() + "\n\n"
                    + "Le recomiendo que revise el dashboard.");
            mailSender.send(correo);

            alerta.setEstadoNotificacion(true);
            alertaCriticaRepository.save(alerta);
            log.info("Correo enviado y alerta id={} marcada como notificada", dto.id());
        } catch (Exception e) {
            log.error("No se pudo enviar el correo de la alerta id={}", dto.id(), e);
        }
    }
}