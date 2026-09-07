package com.calidadaire.core_ingesta.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Habilitamos un "megáfono" interno con el prefijo /topic
        // Todo lo que publiquemos en /topic/alertas le llegará a quien esté escuchando
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Esta es la URL (el túnel) a la que se va a conectar el código de tu compañero (Residente 1)
        registry.addEndpoint("/ws-calidad-aire")
                .setAllowedOriginPatterns("*") // Permite que el Dashboard en otro puerto (ej. React/Angular) se conecte sin bloqueos de seguridad
                .withSockJS(); // Un "plan B" por si el internet de la empresa bloquea WebSockets puros
    }
}