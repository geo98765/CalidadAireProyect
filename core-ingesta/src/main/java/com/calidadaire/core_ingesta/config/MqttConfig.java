package com.calidadaire.core_ingesta.config;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
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

@Configuration
public class MqttConfig {


    @Value("${mqtt.broker.url:tcp://localhost:1883}")
    private String brokerUrl;

    @Value("${mqtt.broker.client-id:core-ingesta-client}")
    private String clientId;

    @Value("${mqtt.topics.normales:fog/lecturas/normales}")
    private String topicNormales;

    @Value("${mqtt.topics.criticas:fog/alertas/criticas}")
    private String topicAlertas;

    @Value("${mqtt.topics.notificaciones:interno/notificaciones/criticas}")
    private String topicNotificaciones;

    //metodo para configurar la conexion al broker MQTT
    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        
        options.setServerURIs(new String[]{brokerUrl}); 
        options.setCleanSession(false);
        
        factory.setConnectionOptions(options);
        return factory;
    }

    //crea el canal que recibe los mensajes entrantes antes de ser clasificados
    @Bean
    public MessageChannel mqttInboundRouterChannel() {
        return new DirectChannel();
    }

    //crea el canal interno de lecturas
    @Bean
    public MessageChannel lecturasNormalesCanal() {
        return new DirectChannel();
    }
    //Crea el canal interno para alertas recibidas desde el Fog
    @Bean
    public MessageChannel alertasCriticasCanal() {
        return new DirectChannel();
    }


    //Crea el canal interno para avisar al microservicio de notificaciones
    @Bean
    public MessageChannel notificacionesSalientesCanal() {
        return new DirectChannel();
    }

    //Crea el componente que publica las notificaciones internas hacia Mosquitto
    @Bean
    @ServiceActivator(inputChannel = "notificacionesSalientesCanal")
    public MessageHandler mqttOutbound(MqttPahoClientFactory mqttClientFactory) {
        MqttPahoMessageHandler messageHandler =
                new MqttPahoMessageHandler(clientId + "-notificador", mqttClientFactory);
        messageHandler.setAsync(true);
        messageHandler.setDefaultTopic(topicNotificaciones);
        return messageHandler;
    }


    //Crea el componente que se suscribe a los tópicos de lecturas y alertas
    @Bean
    public MessageProducer inboundAdapter(MqttPahoClientFactory mqttClientFactory) {
        String[] topics = { topicNormales, topicAlertas };
        
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(
                        clientId + "-ingesta", 
                        mqttClientFactory, 
                        topics
                );
        
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(1);
        
        adapter.setOutputChannel(mqttInboundRouterChannel()); 
        
        return adapter;
    }

    //Revisa el topico del mensaje y decide a que canal enviarlo
    @Router(inputChannel = "mqttInboundRouterChannel")
    public String routeMqttMessage(Message<?> message) {
        String receivedTopic = (String) message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC);
        
        if (receivedTopic != null && receivedTopic.equals(topicAlertas)) {
            return "alertasCriticasCanal";
        }
        
        return "lecturasNormalesCanal";
    }
}