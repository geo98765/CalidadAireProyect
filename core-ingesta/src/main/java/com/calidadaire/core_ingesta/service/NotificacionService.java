package com.calidadaire.core_ingesta.service;

import org.springframework.stereotype.Service;

import com.calidadaire.core_ingesta.entity.AlertaCritica;

@Service
public class NotificacionService {
    
    /**
     * Llamada preliminar para el fast-track de emergencias.
     * En el Mes 2 (Semana 7) se integrará con los canales reales (Telegram, WS, etc).
     */
    public void dispararAlertaCritica(AlertaCritica alerta) {
        System.out.println("📩 [PRELIMINAR] Disparando notificación de alerta crítica para el nodo: " + alerta.getNodoId());
        System.out.println("📩 [PRELIMINAR] Mensaje: " + alerta.getMensaje());
        
        // TODO (Mes 2): Enviar a cola de RabbitMQ/Kafka o llamar directamente al microservicio de notificaciones.
    }
}