package com.calidadaire.core_ingesta.config;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.Router;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

@Configuration
public class MqttConfig {

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{"tcp://localhost:1883"});
        options.setCleanSession(true);
        // Descomenta si tu Mosquitto local tiene credenciales:
        // options.setUserName("tu_usuario");
        // options.setPassword("tu_password".toCharArray());
        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel lecturasNormalesCanal() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel alertasCriticasCanal() {
        return new DirectChannel();
    }

    @Bean
    public MessageProducer inbound() {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter("cliente-backend-ingesta", mqttClientFactory(),
                        "fog/lecturas/normales", "fog/alertas/criticas");
        
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        // Enviamos todo al canal de entrada para que el Router decida
        adapter.setOutputChannel(mqttInputChannel()); 
        return adapter;
    }

    @Router(inputChannel = "mqttInputChannel")
    public String routeMqttMessages(Message<?> message) {
        String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
        if ("fog/alertas/criticas".equals(topic)) {
            return "alertasCriticasCanal";
        }
        return "lecturasNormalesCanal"; // Por defecto a lecturas normales
    }
}