package com.calidadaire.core_ingesta.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.calidadaire.core_ingesta.entity.AlertaCritica;
import com.calidadaire.core_ingesta.repository.AlertaCriticaRepository;

@Service
public class NotificacionService {

    @Autowired
    private AlertaCriticaRepository alertaCriticaRepository;

    @Autowired
    private JavaMailSender mailSender; // Inyectamos el cliente de correo

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Async // Hilo separado para no bloquear la ingesta
    public void dispararAlertaCritica(AlertaCritica alerta) {
        System.out.println("🚀 [NOTIFICADOR] Iniciando envío de E-MAIL para el nodo: " + alerta.getNodoId());

        try {
            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setFrom("tu_correo_aqui@gmail.com"); // El correo que configuraste en el yaml
            mensaje.setTo("correo_destino@gmail.com"); // A quién le va a llegar la alerta (puedes ser tú mismo)
            mensaje.setSubject("🚨 ALERTA CRÍTICA DE CALIDAD DEL AIRE: " + alerta.getTipoAlerta());
            mensaje.setText("Se ha detectado una anomalía crítica en el sistema.\n\n" +
                            "Nodo afectado: " + alerta.getNodoId() + "\n" +
                            "Tipo de Alerta: " + alerta.getTipoAlerta() + "\n" +
                            "Valor Registrado: " + alerta.getValorRegistrado() + "\n" +
                            "Mensaje del Sensor: " + alerta.getMensaje() + "\n\n" +
                            "Por favor, revise el dashboard inmediatamente.");

            mailSender.send(mensaje);

            // Si no hay errores, marcamos como enviada
            alerta.setEstadoNotificacion(true);
            alertaCriticaRepository.save(alerta);
            
            System.out.println("✅ [NOTIFICADOR] E-mail enviado con éxito y alerta marcada en BD.");

            messagingTemplate.convertAndSend("/topic/alertas", alerta);
            System.out.println("📡 [WEBSOCKET] Alerta transmitida en tiempo real al Dashboard.");
        } catch (Exception e) {
            System.err.println("❌ [NOTIFICADOR] Fallo al enviar el e-mail: " + e.getMessage());
        }
    }
}