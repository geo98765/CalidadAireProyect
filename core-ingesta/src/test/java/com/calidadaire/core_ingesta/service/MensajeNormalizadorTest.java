package com.calidadaire.core_ingesta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class MensajeNormalizadorTest {

    private static final UUID NODO = UUID.fromString("11111111-1111-1111-1111-111111111111");

    // Reloj fijo: las pruebas no dependen del día en que se ejecuten
    private final MensajeNormalizador normalizador = new MensajeNormalizador(
            Clock.fixed(Instant.parse("2026-10-30T18:00:00Z"), ZoneOffset.UTC));

    private static JsonNode json(String texto) {
        try {
            return new ObjectMapper().readTree(texto);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void assertValor(double esperado, Double real) {
        assertNotNull(real);
        assertEquals(esperado, real.doubleValue(), 0.0001);
    }

    private void assertLecturaRechazada(String mensaje) {
        assertThrows(MensajeInvalidoException.class, () -> normalizador.normalizarLectura(json(mensaje)));
    }

    // ---------- Lecturas ----------

    @Test
    void lecturaAnidadaDelModoCalidadAire() {
        LecturaNormalizada l = normalizador.normalizarLectura(json("""
            {"nodo_id":"11111111-1111-1111-1111-111111111111",
             "timestamp_origen":"2026-10-26T20:00:00Z",
             "lecturas":{"co2_ppm":720,"pm25_ugm3":60,"pm10_ugm3":65}}"""));

        assertEquals(NODO, l.nodoId());
        assertEquals(Instant.parse("2026-10-26T20:00:00Z"), l.timestampOrigen());
        assertValor(720, l.co2Ppm());
        assertValor(60, l.pm25Ugm3());
        assertValor(65, l.pm10Ugm3());
        assertNull(l.temperaturaC());
        assertNull(l.mid());
        assertNull(l.nombreOrigen());
    }

    @Test
    void lecturaPlanaConTodasLasVariables() {
        LecturaNormalizada l = normalizador.normalizarLectura(json("""
            {"mid":"abc:1792956548722:be5120","id":"11111111-1111-1111-1111-111111111111",
             "ts":1792956548722,"pm25":1.8,"pm10":13.3,"co2":492,"temp":23.4,"humedad":48.6}"""));

        assertEquals(NODO, l.nodoId());
        assertEquals("abc:1792956548722:be5120", l.mid());
        assertEquals(Instant.ofEpochMilli(1792956548722L), l.timestampOrigen());
        assertValor(1.8, l.pm25Ugm3());
        assertValor(13.3, l.pm10Ugm3());
        assertValor(492, l.co2Ppm());
        assertValor(23.4, l.temperaturaC());
        assertValor(48.6, l.humedadPct());
    }

    @Test
    void idTextualDerivaUnUuidEstableYConservaElNombre() {
        String mensaje = """
            {"mid":"a:1:x","id":"S_AIRE_06","ts":1792956548722,"co2":492}""";

        LecturaNormalizada primera = normalizador.normalizarLectura(json(mensaje));
        LecturaNormalizada segunda = normalizador.normalizarLectura(json(mensaje));
        LecturaNormalizada otra = normalizador.normalizarLectura(json(mensaje.replace("S_AIRE_06", "S_CO2_01")));

        assertEquals(5, primera.nodoId().version());
        assertEquals(primera.nodoId(), segunda.nodoId());
        assertNotEquals(primera.nodoId(), otra.nodoId());
        assertEquals("S_AIRE_06", primera.nombreOrigen());
    }

    @Test
    void lecturaParcialSoloConCo2() {
        LecturaNormalizada l = normalizador.normalizarLectura(json("""
            {"id":"11111111-1111-1111-1111-111111111111","ts":1792956548722,"co2":492}"""));

        assertValor(492, l.co2Ppm());
        assertNull(l.pm25Ugm3());
        assertNull(l.humedadPct());
        assertTrue(l.tieneContaminantes());
    }

    @Test
    void lecturaSoloDeTemperaturaYHumedadNoTieneContaminantes() {
        LecturaNormalizada l = normalizador.normalizarLectura(json("""
            {"id":"11111111-1111-1111-1111-111111111111","ts":1792956548722,"temp":-5,"humedad":80}"""));

        assertFalse(l.tieneContaminantes());
        assertValor(-5, l.temperaturaC());   // la temperatura sí puede ser negativa
    }

    @Test
    void valorNuloSeTrataComoAusenteYCamposDesconocidosSeIgnoran() {
        LecturaNormalizada l = normalizador.normalizarLectura(json("""
            {"id":"11111111-1111-1111-1111-111111111111","ts":1792956548722,
             "co2":null,"pm25":10,"presion":1010}"""));

        assertNull(l.co2Ppm());
        assertValor(10, l.pm25Ugm3());
    }

    @Test
    void mensajesInvalidosSeRechazan() {
        // sin identificador
        assertLecturaRechazada("{\"ts\":1792956548722,\"co2\":492}");
        // sin momento
        assertLecturaRechazada("{\"id\":\"11111111-1111-1111-1111-111111111111\",\"co2\":492}");
        // sin ninguna variable reconocida
        assertLecturaRechazada("{\"id\":\"11111111-1111-1111-1111-111111111111\",\"ts\":1792956548722,\"presion\":1010}");
        // campo no numérico
        assertLecturaRechazada("{\"id\":\"11111111-1111-1111-1111-111111111111\",\"ts\":1792956548722,\"co2\":\"abc\"}");
        // humedad imposible
        assertLecturaRechazada("{\"id\":\"11111111-1111-1111-1111-111111111111\",\"ts\":1792956548722,\"humedad\":140}");
        // contaminante negativo
        assertLecturaRechazada("{\"id\":\"11111111-1111-1111-1111-111111111111\",\"ts\":1792956548722,\"pm25\":-3}");
    }

    @Test
    void fechasAbsurdasSeRechazan() {
        // segundos en lugar de milisegundos: caería en 1970
        assertLecturaRechazada("{\"id\":\"11111111-1111-1111-1111-111111111111\",\"ts\":1792956548,\"co2\":492}");
        // más de un día en el futuro respecto al reloj de la prueba
        assertLecturaRechazada("{\"nodo_id\":\"11111111-1111-1111-1111-111111111111\","
                + "\"timestamp_origen\":\"2026-12-01T00:00:00Z\",\"lecturas\":{\"co2_ppm\":500}}");
        // texto que no es una fecha
        assertLecturaRechazada("{\"nodo_id\":\"11111111-1111-1111-1111-111111111111\","
                + "\"timestamp_origen\":\"ayer\",\"lecturas\":{\"co2_ppm\":500}}");
    }

    @Test
    void siNoEsUnObjetoSeRechaza() {
        assertLecturaRechazada("[1,2,3]");
    }

    // ---------- Alertas ----------

    @Test
    void alertaDelModoCalidadAire() {
        AlertaNormalizada a = normalizador.normalizarAlerta(json("""
            {"nodo_id":"11111111-1111-1111-1111-111111111111","timestamp_origen":"2026-10-26T20:05:00Z",
             "tipo_alerta":"CO2_PELIGRO","valor_registrado":1650,"mensaje":"CO2 alto"}""")).orElseThrow();

        assertEquals(NODO, a.nodoId());
        assertEquals("CO2_PELIGRO", a.tipoAlerta());
        assertValor(1650, a.valorRegistrado());
        assertEquals("CO2 alto", a.mensaje());
    }

    @Test
    void alarmaNativaActivaSeConvierteEnAlerta() {
        AlertaNormalizada a = normalizador.normalizarAlerta(json("""
            {"mid":"alarma:x:co2:1792956548722:ab12","tipo":"alarma","estado":"activa",
             "id":"11111111-1111-1111-1111-111111111111","variable":"co2","valor":1650,
             "unidad":"ppm","nivel":"peligro","detalle":"Deficiente","ts":1792956548722}""")).orElseThrow();

        assertEquals("CO2_PELIGRO", a.tipoAlerta());
        assertValor(1650, a.valorRegistrado());
        assertEquals("co2 1650 ppm — Deficiente", a.mensaje());
        assertEquals("alarma:x:co2:1792956548722:ab12", a.mid());
    }

    @Test
    void alarmaResueltaSeIgnora() {
        Optional<AlertaNormalizada> resultado = normalizador.normalizarAlerta(json("""
            {"mid":"alarma:x:co2:1792956548722:clr","tipo":"alarma","estado":"resuelta",
             "id":"11111111-1111-1111-1111-111111111111","variable":"co2","valor":900,
             "duracion_s":120,"ts":1792956548722}"""));

        assertTrue(resultado.isEmpty());
    }

    @Test
    void alertaSinFormatoReconocidoSeRechaza() {
        assertThrows(MensajeInvalidoException.class, () -> normalizador.normalizarAlerta(json("""
            {"id":"11111111-1111-1111-1111-111111111111","ts":1792956548722}""")));
    }

    @Test
    void uuidDerivadoCoincideConElDeFormatoJs() {
        // Valor que imprimió node uuid-prueba.js
        UUID esperado = UUID.fromString("80f869fd-d1ae-5cbc-94e2-2dc43adf6035");

        assertEquals(esperado, MensajeNormalizador.uuidV5("S_AIRE_06", MensajeNormalizador.NAMESPACE_NODOS));
    }
}