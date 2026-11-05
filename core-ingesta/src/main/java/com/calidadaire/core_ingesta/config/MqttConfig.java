package com.calidadaire.core_ingesta.config;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.Router;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class MqttConfig {

    @Value("${mqtt.broker.url:tcp://localhost:1883}")
    private String brokerUrl;

    @Value("${mqtt.broker.client-id:core-ingesta-client}")
    private String clientId;

    // Filtros de suscripción (pueden llevar comodines + y #), separados por coma
    @Value("${mqtt.topics.lecturas:fog/lecturas/#,planta1/limpio/#}")
    private String[] filtrosLecturas;

    @Value("${mqtt.topics.alertas:fog/alertas/#,planta1/alarma/#}")
    private String[] filtrosAlertas;

    @Value("${mqtt.topics.notificaciones:interno/notificaciones/criticas}")
    private String topicNotificaciones;

    // Conexión al broker MQTT
    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{brokerUrl});
        options.setCleanSession(false);
        factory.setConnectionOptions(options);
        return factory;
    }

    // Canal donde llegan todos los mensajes antes de clasificarse
    @Bean
    public MessageChannel mqttInboundRouterChannel() {
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

    // Canal interno para avisar al microservicio de notificaciones
    @Bean
    public MessageChannel notificacionesSalientesCanal() {
        return new DirectChannel();
    }

    // Publica las notificaciones internas hacia Mosquitto
    @Bean
    @ServiceActivator(inputChannel = "notificacionesSalientesCanal")
    public MessageHandler mqttOutbound(MqttPahoClientFactory mqttClientFactory) {
        MqttPahoMessageHandler messageHandler =
                new MqttPahoMessageHandler(clientId + "-notificador", mqttClientFactory);
        messageHandler.setAsync(true);
        messageHandler.setDefaultTopic(topicNotificaciones);
        messageHandler.setDefaultQos(1);
        return messageHandler;
    }

    // Se suscribe a los filtros de lecturas y de alertas
    @Bean
    public MessageProducer inboundAdapter(MqttPahoClientFactory mqttClientFactory) {
        List<String> topics = new ArrayList<>();
        for (String filtro : filtrosLecturas) {
            topics.add(filtro.trim());
        }
        for (String filtro : filtrosAlertas) {
            topics.add(filtro.trim());
        }
        log.info("Suscripción MQTT a los tópicos: {}", topics);

        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(
                        clientId + "-ingesta",
                        mqttClientFactory,
                        topics.toArray(new String[0]));

        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(1);
        adapter.setOutputChannel(mqttInboundRouterChannel());
        return adapter;
    }

    // Decide a qué canal va cada mensaje según su tópico
    @Router(inputChannel = "mqttInboundRouterChannel")
    public String routeMqttMessage(Message<?> message) {
        String topic = (String) message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC);
        if (topic != null) {
            if (coincide(filtrosAlertas, topic)) {
                return "alertasCriticasCanal";
            }
            if (coincide(filtrosLecturas, topic)) {
                return "lecturasNormalesCanal";
            }
        }
        log.warn("Mensaje en un tópico que no corresponde a ningún filtro conocido: {}", topic);
        return "nullChannel";
    }

    private boolean coincide(String[] filtros, String topic) {
        for (String filtro : filtros) {
            if (MqttTopic.isMatched(filtro.trim(), topic)) {
                return true;
            }
        }
        return false;
    }
}