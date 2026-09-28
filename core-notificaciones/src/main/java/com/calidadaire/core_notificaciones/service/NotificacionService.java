package com.calidadaire.core_notificaciones.service;

import org.springframework.beans.factory.annotation.Autowired;
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

@Service
public class NotificacionService {

    @Autowired
    private AlertaCriticaRepository alertaCriticaRepository;

    @Autowired
    private JavaMailSender mailSender;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @ServiceActivator(inputChannel = "notificacionesEntrantesCanal")
    public void procesarNotificacion(Message<String> mensaje) {
        try {
            NotificacionAlertaDTO dto = objectMapper.readValue(mensaje.getPayload(), NotificacionAlertaDTO.class);
            dispararAlertaCritica(dto);
        } catch (Exception e) {
            System.err.println("Error procesando notificación " + e.getMessage());
        }
    }

        private void dispararAlertaCritica(NotificacionAlertaDTO dto) {
        AlertaCritica alerta = alertaCriticaRepository.findById(dto.id()).orElse(null);

        if (alerta == null) {
            System.err.println("⚠️ No se encontró la alerta id=" + dto.id() + " en la base de datos.");
            return;
        }

        if (Boolean.TRUE.equals(alerta.getEstadoNotificacion())) {
            System.out.println("⚠️ La alerta id=" + dto.id() + " ya fue notificada antes. Se omite envío duplicado (posible reentrega de MQTT).");
            return;
        }

        System.out.println("🚀 [NOTIFICADOR] Iniciando envío de E-MAIL para el nodo: " + dto.nodoId());

        try {
            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setFrom("tu_correo_aqui@gmail.com");
            mensaje.setTo("correo_destino@gmail.com");
            mensaje.setSubject("🚨 ALERTA CRÍTICA DE CALIDAD DEL AIRE: " + dto.tipoAlerta());
            mensaje.setText("Se ha detectado una anomalía crítica en el sistema.\n\n" +
                            "Nodo afectado: " + dto.nodoId() + "\n" +
                            "Tipo de Alerta: " + dto.tipoAlerta() + "\n" +
                            "Valor Registrado: " + dto.valorRegistrado() + "\n" +
                            "Mensaje del Sensor: " + dto.mensaje() + "\n\n" +
                            "Por favor, revise el dashboard inmediatamente.");

            mailSender.send(mensaje);

            alerta.setEstadoNotificacion(true);
            alertaCriticaRepository.save(alerta);

            System.out.println("E-mail enviado con éxito y alerta marcada en BD.");
        } catch (Exception e) {
            System.err.println("Fallo al enviar el e-mail: " + e.getMessage());
        }
    }
}