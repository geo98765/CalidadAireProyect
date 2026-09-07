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


    // 1. Externalizamos las variables (Preparando el terreno para el Paso 5)
    @Value("${mqtt.broker.url:tcp://localhost:1883}")
    private String brokerUrl;

    @Value("${mqtt.client.id:core-ingesta-client}")
    private String clientId;

    @Value("${mqtt.topic.normales:fog/lecturas/normales}")
    private String topicNormales;

    @Value("${mqtt.topic.alertas:fog/alertas/criticas}")
    private String topicAlertas;

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        
        // Usamos la variable que definiste arriba en lugar de ponerlo en duro
        options.setServerURIs(new String[]{brokerUrl}); 
        options.setCleanSession(true);
        
        factory.setConnectionOptions(options);
        return factory;
    }
    // --- DEFINICIÓN DE CANALES ---

    // Canal "puente" donde llegan todos los mensajes crudos del broker
    @Bean
    public MessageChannel mqttInboundRouterChannel() {
        return new DirectChannel();
    }

    // Canal final para lecturas normales
    @Bean
    public MessageChannel lecturasNormalesCanal() {
        return new DirectChannel();
    }

    // Canal final para alertas críticas
    @Bean
    public MessageChannel alertasCriticasCanal() {
        return new DirectChannel();
    }

    // --- ADAPTADOR MQTT (ENTRADA) ---

    @Bean
    public MessageProducer inboundAdapter(MqttPahoClientFactory mqttClientFactory) {
        // Nos suscribimos a ambos tópicos usando un array
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
        
        // ¡Cambio clave! Ahora todo va al canal del router, no directo a lecturasNormales
        adapter.setOutputChannel(mqttInboundRouterChannel()); 
        
        return adapter;
    }

    // --- EL ROUTER (CEREbro DEL ENRUTAMIENTO) ---

    /**
     * Intercepta los mensajes del canal mqttInboundRouterChannel, 
     * revisa el tópico de origen y los dirige al canal correspondiente.
     */
    @Router(inputChannel = "mqttInboundRouterChannel")
    public String routeMqttMessage(Message<?> message) {
        // Extraemos el tópico por el que llegó el mensaje
        String receivedTopic = (String) message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC);
        
        // Si el tópico coincide con el de alertas, lo mandamos a su canal
        if (receivedTopic != null && receivedTopic.equals(topicAlertas)) {
            return "alertasCriticasCanal";
        }
        
        // Por defecto (o si es el tópico de normales), lo mandamos al canal de lecturas
        return "lecturasNormalesCanal";
    }
}