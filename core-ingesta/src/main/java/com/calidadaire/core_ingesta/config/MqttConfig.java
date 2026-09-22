package com.calidadaire.core_ingesta.config;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.Router;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

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

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        
        options.setServerURIs(new String[]{brokerUrl}); 
        options.setCleanSession(false);
        
        factory.setConnectionOptions(options);
        return factory;
    }

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

    @Router(inputChannel = "mqttInboundRouterChannel")
    public String routeMqttMessage(Message<?> message) {
        String receivedTopic = (String) message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC);
        
        if (receivedTopic != null && receivedTopic.equals(topicAlertas)) {
            return "alertasCriticasCanal";
        }
        
        return "lecturasNormalesCanal";
    }
}