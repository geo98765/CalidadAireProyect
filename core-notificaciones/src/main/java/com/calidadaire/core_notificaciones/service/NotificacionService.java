package com.calidadaire.core_notificaciones.service;

import org.springframework.beans.factory.annotation.Autowired;
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

@Service
public class NotificacionService {

    @Autowired
    private AlertaCriticaRepository alertaCriticaRepository;

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String remitente;

    @Value("${notificaciones.destinatario}")
    private String destinatario;
    

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @ServiceActivator(inputChannel = "notificacionesEntrantesCanal")
    public void procesarNotificacion(Message<String> mensaje) {
        try {
            NotificacionAlertaDTO dto = objectMapper.readValue(mensaje.getPayload(), NotificacionAlertaDTO.class);
            dispararAlertaCritica(dto);
        } catch (Exception e) {
            System.err.println("Error en la notificacion " + e.getMessage());
        }
    }

        private void dispararAlertaCritica(NotificacionAlertaDTO dto) {
        AlertaCritica alerta = alertaCriticaRepository.findById(dto.id()).orElse(null);

        if (alerta == null) {
            System.err.println("No se encontró la alerta id=" + dto.id() + " en la bd");
            return;
        }

        if (Boolean.TRUE.equals(alerta.getEstadoNotificacion())) {
            System.out.println("La alerta id=" + dto.id() + " ya fue notificada antes.");
            return;
        }

        try {
            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setFrom(remitente);
               mensaje.setTo(destinatario.split("\\s*,\\s*"));
            mensaje.setSubject("ALERTA CRITICA DE CALIDAD DEL AIRE: " + dto.tipoAlerta());
            mensaje.setText("Se ha detectado un nivel critico en el sistema.\n\n" +
                            "Nodo afectado: " + dto.nodoId() + "\n" +
                            "Tipo de Alerta: " + dto.tipoAlerta() + "\n" +
                            "Valor Registrado: " + dto.valorRegistrado() + "\n" +
                            "Mensaje del Sensor: " + dto.mensaje() + "\n\n" +
                            "Revise el dashboard inmediatamente.");

            mailSender.send(mensaje);

            alerta.setEstadoNotificacion(true);
            alertaCriticaRepository.save(alerta);

            System.out.println("gmail enviado y alerta marcada en BD");
        } catch (Exception e) {
            System.err.println("Fallo al enviar gmail" + e.getMessage());
        }
    }
}