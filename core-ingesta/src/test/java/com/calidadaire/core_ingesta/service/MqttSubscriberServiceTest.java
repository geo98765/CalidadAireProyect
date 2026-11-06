package com.calidadaire.core_ingesta.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageBuilder;

import com.calidadaire.core_ingesta.config.PoliticaAlertas;
import com.calidadaire.core_ingesta.entity.AlertaCritica;
import com.calidadaire.core_ingesta.entity.LecturaNormal;
import com.calidadaire.core_ingesta.entity.NodoSensor;
import com.calidadaire.core_ingesta.repository.AlertaCriticaRepository;
import com.calidadaire.core_ingesta.repository.LecturaNormalRepository;
import com.calidadaire.core_ingesta.repository.NodoSensorRepository;

class MqttSubscriberServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-05T18:00:00Z");
    private static final String NODO = "11111111-1111-1111-1111-111111111111";
    private static final UUID NODO_UUID = UUID.fromString(NODO);

    private LecturaNormalRepository lecturas;
    private AlertaCriticaRepository alertas;
    private NodoSensorRepository nodos;
    private SimpMessagingTemplate dashboard;
    private MessageChannel canalNotificaciones;
    private PoliticaAlertas politica;
    private MqttSubscriberService servicio;

    @BeforeEach
    void preparar() {
        lecturas = mock(LecturaNormalRepository.class);
        alertas = mock(AlertaCriticaRepository.class);
        nodos = mock(NodoSensorRepository.class);
        dashboard = mock(SimpMessagingTemplate.class);
        canalNotificaciones = mock(MessageChannel.class);
        politica = new PoliticaAlertas();   // por defecto: correo solo desde el motor, enfriamiento de 60 min y antigüedad de 30 min

        Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
        servicio = new MqttSubscriberService(lecturas, alertas, nodos, new MotorReglasService(),
                new MensajeNormalizador(reloj), dashboard, politica, reloj, canalNotificaciones);

        // La "base de datos" guarda devolviendo la misma entidad; a las alertas les asigna el id 99
        when(lecturas.save(any(LecturaNormal.class))).thenAnswer(i -> i.getArgument(0));
        when(alertas.save(any(AlertaCritica.class))).thenAnswer(i -> {
            AlertaCritica alerta = i.getArgument(0);
            alerta.setId(99L);
            return alerta;
        });
    }

    // ---------- Utilidades ----------

    private void lecturaLlega(String json) {
        servicio.procesarLecturas(MessageBuilder.withPayload(json).build());
    }

    private void alertaLlega(String json) {
        servicio.procesarAlertas(MessageBuilder.withPayload(json).build());
    }

    /** Lectura en formato plano (con mid): la que publica el modo nativo del nodo. */
    private static String plano(String mid, String id, Instant momento, String variables) {
        return "{\"mid\":\"" + mid + "\",\"id\":\"" + id + "\",\"ts\":" + momento.toEpochMilli() + "," + variables + "}";
    }

    /** Lectura en formato anidado (sin mid): la que publica el modo calidadaire. */
    private static String anidado(String nodo, Instant momento, String variables) {
        return "{\"nodo_id\":\"" + nodo + "\",\"timestamp_origen\":\"" + momento + "\",\"lecturas\":{" + variables + "}}";
    }

    private static String alarma(String estado, String mid, String id, Instant momento) {
        return "{\"mid\":\"" + mid + "\",\"tipo\":\"alarma\",\"estado\":\"" + estado + "\",\"id\":\"" + id
                + "\",\"variable\":\"pm25\",\"valor\":55,\"unidad\":\"ug/m3\",\"nivel\":\"peligro\",\"detalle\":\"Mala\",\"ts\":"
                + momento.toEpochMilli() + "}";
    }

    private AlertaCritica alertaGuardada() {
        ArgumentCaptor<AlertaCritica> captor = ArgumentCaptor.forClass(AlertaCritica.class);
        verify(alertas).save(captor.capture());
        return captor.getValue();
    }

    /** Contenido del aviso que se publicó para core-notificaciones. */
    @SuppressWarnings("rawtypes")
    private String avisoPublicado() {
        ArgumentCaptor<Message> aviso = ArgumentCaptor.forClass(Message.class);
        verify(canalNotificaciones).send(aviso.capture());
        return aviso.getValue().getPayload().toString();
    }

    // ---------- Lecturas ----------

    @Test
    void lecturaNuevaSeGuardaYSeTransmiteAlDashboard() {
        lecturaLlega(plano("m1", NODO, AHORA, "\"co2\":600,\"pm25\":10,\"temp\":22.5,\"humedad\":55"));

        ArgumentCaptor<LecturaNormal> captor = ArgumentCaptor.forClass(LecturaNormal.class);
        verify(lecturas).save(captor.capture());
        LecturaNormal l = captor.getValue();
        assertEquals("m1", l.getMid());
        assertEquals(NODO_UUID, l.getNodoId());
        assertEquals(AHORA, l.getTimestampOrigen());
        assertEquals(600.0, l.getCo2Ppm(), 0.0001);
        assertEquals(22.5, l.getTemperaturaC(), 0.0001);
        assertEquals(55.0, l.getHumedadPct(), 0.0001);
        assertEquals("BUENO", l.getNivelRiesgo());

        verify(dashboard).convertAndSend(eq("/topic/lecturas"), any(LecturaNormal.class));
        verify(alertas, never()).save(any(AlertaCritica.class));
    }

    @Test
    void nodoConNombreDeTextoSeRegistraConEseNombreComoUbicacion() {
        lecturaLlega(plano("m2", "S_AIRE_06", AHORA, "\"co2\":500"));

        ArgumentCaptor<NodoSensor> captor = ArgumentCaptor.forClass(NodoSensor.class);
        verify(nodos).save(captor.capture());
        assertEquals("S_AIRE_06", captor.getValue().getUbicacion());
        assertEquals(MensajeNormalizador.uuidV5("S_AIRE_06", MensajeNormalizador.NAMESPACE_NODOS),
                captor.getValue().getId());
        assertEquals(Boolean.TRUE, captor.getValue().getEstatus());
    }

    @Test
    void nodoYaRegistradoNoSeVuelveAGuardar() {
        when(nodos.existsById(NODO_UUID)).thenReturn(true);

        lecturaLlega(plano("m3", NODO, AHORA, "\"co2\":500"));

        verify(nodos, never()).save(any(NodoSensor.class));
        verify(lecturas).save(any(LecturaNormal.class));
    }

    @Test
    void lecturaConMidRepetidoSeIgnora() {
        when(lecturas.existsByMid("m4")).thenReturn(true);

        lecturaLlega(plano("m4", NODO, AHORA, "\"co2\":500"));

        verify(lecturas, never()).save(any(LecturaNormal.class));
        verifyNoInteractions(dashboard);
    }

    @Test
    void lecturaSinMidSeCompruebaPorNodoYMomento() {
        Instant momento = AHORA.minusSeconds(60);
        when(lecturas.existsByNodoIdAndTimestampOrigen(NODO_UUID, momento)).thenReturn(true);

        lecturaLlega(anidado(NODO, momento, "\"co2_ppm\":500"));

        verify(lecturas, never()).save(any(LecturaNormal.class));
    }

    @Test
    void lecturaSoloDeAmbienteQuedaSinNivelYSinAlerta() {
        lecturaLlega(plano("m5", NODO, AHORA, "\"temp\":23.1,\"humedad\":57"));

        ArgumentCaptor<LecturaNormal> captor = ArgumentCaptor.forClass(LecturaNormal.class);
        verify(lecturas).save(captor.capture());
        assertNull(captor.getValue().getNivelRiesgo());
        verify(alertas, never()).save(any(AlertaCritica.class));
    }

    @Test
    void mensajeInvalidoSeRechazaSinGuardarNada() {
        assertDoesNotThrow(() -> lecturaLlega(plano("m6", NODO, AHORA, "\"humedad\":140")));

        verifyNoInteractions(lecturas, nodos, dashboard);
    }

    @Test
    void textoQueNoEsJsonSeDescartaSinFallar() {
        assertDoesNotThrow(() -> lecturaLlega("esto no es json"));

        verifyNoInteractions(lecturas, nodos, dashboard);
    }

    @Test
    void unArregloProcesaCadaElementoPorSeparado() {
        String arreglo = "[" + plano("a1", NODO, AHORA, "\"co2\":500") + ","
                + plano("a2", NODO, AHORA.plusSeconds(1), "\"humedad\":140") + ","   // este se rechaza
                + plano("a3", NODO, AHORA.plusSeconds(2), "\"co2\":520") + "]";

        lecturaLlega(arreglo);

        verify(lecturas, times(2)).save(any(LecturaNormal.class));
    }

    @Test
    void siLaBaseRechazaUnDuplicadoNoSeTransmiteNada() {
        when(lecturas.save(any(LecturaNormal.class))).thenThrow(new DataIntegrityViolationException("duplicada"));

        assertDoesNotThrow(() -> lecturaLlega(plano("m7", NODO, AHORA, "\"co2\":500")));

        verifyNoInteractions(dashboard);
    }

    // ---------- Alertas del motor de reglas ----------

    @Test
    void lecturaMalaGeneraAlertaYPideElCorreo() {
        lecturaLlega(plano("m8", NODO, AHORA, "\"pm25\":60"));

        AlertaCritica alerta = alertaGuardada();
        assertEquals("RIESGO_MALO", alerta.getTipoAlerta());
        assertEquals(60.0, alerta.getValorRegistrado(), 0.0001);
        assertEquals(Boolean.FALSE, alerta.getEstadoNotificacion());
        verify(dashboard).convertAndSend(eq("/topic/alertas"), any(AlertaCritica.class));
        assertTrue(avisoPublicado().contains("\"tipoAlerta\":\"RIESGO_MALO\""));
    }

    @Test
    void extremadamenteMaloTambienGeneraAlerta() {
        lecturaLlega(plano("m9", NODO, AHORA, "\"pm25\":200"));

        assertEquals("RIESGO_EXTREMADAMENTE_MALO", alertaGuardada().getTipoAlerta());
    }

    @Test
    void unaAlertaRepetidaDentroDelEnfriamientoSeOmite() {
        when(alertas.existsByNodoIdAndTipoAlertaInAndTimestampOrigenAfter(
                eq(NODO_UUID), anyCollection(), any(Instant.class))).thenReturn(true);

        lecturaLlega(plano("m10", NODO, AHORA, "\"pm25\":60"));

        verify(lecturas).save(any(LecturaNormal.class));          // la lectura sí se guarda
        verify(alertas, never()).save(any(AlertaCritica.class));  // la alerta no se repite
        verifyNoInteractions(canalNotificaciones);
    }

    @Test
    void siElNivelEmpeoraLaAlertaNoSeOmite() {
        // Hay una alerta MALO reciente. Para una nueva alerta MALO cuenta como "igual o peor" (se omite),
        // pero para una MUY MALO se consulta otra lista de tipos y no hay coincidencia.
        when(alertas.existsByNodoIdAndTipoAlertaInAndTimestampOrigenAfter(
                eq(NODO_UUID),
                eq(List.of("RIESGO_MALO", "RIESGO_MUY_MALO", "RIESGO_EXTREMADAMENTE_MALO")),
                any(Instant.class))).thenReturn(true);

        lecturaLlega(plano("m11", NODO, AHORA, "\"pm25\":120"));   // MUY MALO

        assertEquals("RIESGO_MUY_MALO", alertaGuardada().getTipoAlerta());
    }

    @Test
    void sinEnfriamientoNoSeConsultaElHistorial() {
        politica.setEnfriamientoMinutos(0);

        lecturaLlega(plano("m12", NODO, AHORA, "\"pm25\":60"));

        verify(alertas, never()).existsByNodoIdAndTipoAlertaInAndTimestampOrigenAfter(
                any(UUID.class), anyCollection(), any(Instant.class));
        verify(alertas).save(any(AlertaCritica.class));
    }

    @Test
    void unaLecturaViejaGuardaLaAlertaPeroNoAvisa() {
        lecturaLlega(plano("m13", NODO, AHORA.minusSeconds(2 * 3600), "\"pm25\":60"));

        verify(alertas).save(any(AlertaCritica.class));
        verify(dashboard, never()).convertAndSend(eq("/topic/alertas"), any(AlertaCritica.class));
        verifyNoInteractions(canalNotificaciones);
    }

    // ---------- Alertas que llegan del nodo ----------

    @Test
    void alarmaDelNodoSeGuardaYSeMuestraSinCorreoPorDefecto() {
        alertaLlega(alarma("activa", "al1", NODO, AHORA));

        AlertaCritica alerta = alertaGuardada();
        assertEquals("PM25_PELIGRO", alerta.getTipoAlerta());
        assertEquals("al1", alerta.getMid());
        verify(dashboard).convertAndSend(eq("/topic/alertas"), any(AlertaCritica.class));
        verifyNoInteractions(canalNotificaciones);
    }

    @Test
    void alarmaDelNodoConCorreoActivadoPublicaElAviso() {
        politica.setCorreoDesdeNodo(true);

        alertaLlega(alarma("activa", "al2", NODO, AHORA));

        assertTrue(avisoPublicado().contains("\"tipoAlerta\":\"PM25_PELIGRO\""));
    }

    @Test
    void alarmaResueltaNoCreaNada() {
        alertaLlega(alarma("resuelta", "al3", NODO, AHORA));

        verifyNoInteractions(alertas, nodos, dashboard, canalNotificaciones);
    }

    @Test
    void alarmaConMidRepetidoSeIgnora() {
        when(alertas.existsByMid("al4")).thenReturn(true);

        alertaLlega(alarma("activa", "al4", NODO, AHORA));

        verify(alertas, never()).save(any(AlertaCritica.class));
    }

    @Test
    void alarmaViejaSeGuardaPeroNoAvisa() {
        politica.setCorreoDesdeNodo(true);

        alertaLlega(alarma("activa", "al5", NODO, AHORA.minusSeconds(2 * 3600)));

        verify(alertas).save(any(AlertaCritica.class));
        verify(dashboard, never()).convertAndSend(eq("/topic/alertas"), any(AlertaCritica.class));
        verifyNoInteractions(canalNotificaciones);
    }
}