package com.calidadaire.core_ingesta.service;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MotorReglasServiceTest {

    private final MotorReglasService motor = new MotorReglasService();

    private void assertNivel(String esperado, Double pm25, Double pm10, Double co2) {
        assertEquals(esperado, motor.calcularIndice(pm25, pm10, co2).nivel());
    }

    private static void assertValor(double esperado, Double real) {
        assertNotNull(real);
        assertEquals(esperado, real.doubleValue(), 0.0001);
    }

    @Test
    void sinNingunaVariableNoHayNivel() {
        ResultadoClasificacion r = motor.calcularIndice(null, null, null);

        assertTrue(r.sinDatos());
        assertNull(r.nivel());
        assertNull(r.variableCausante());
        assertFalse(r.esAlertable());
    }

    @Test
    void unValorNoNumericoSeTrataComoAusente() {
        assertTrue(motor.calcularIndice(Double.NaN, null, null).sinDatos());
    }

    @Test
    void lasVariablesAusentesNoCuentan() {
        // Solo CO2 en 1200: ACEPTABLE. Un nulo no debe contar como "cero = bueno".
        ResultadoClasificacion r = motor.calcularIndice(null, null, 1200.0);

        assertEquals("ACEPTABLE", r.nivel());
        assertEquals("CO2", r.variableCausante());
        assertValor(1200, r.valorCausante());
    }

    @Test
    void elPeorNivelDeLasVariablesPresentesGana() {
        // PM2.5 BUENO (10), PM10 MALO (100), CO2 ACEPTABLE (1200)
        ResultadoClasificacion r = motor.calcularIndice(10.0, 100.0, 1200.0);

        assertEquals("MALO", r.nivel());
        assertEquals("PM10", r.variableCausante());
        assertValor(100, r.valorCausante());
    }

    @Test
    void enEmpateLaVariableCausanteSigueElOrdenPm25Pm10Co2() {
        // Las tres en MALO: PM2.5 = 50, PM10 = 100, CO2 = 1600
        assertEquals("PM2.5", motor.calcularIndice(50.0, 100.0, 1600.0).variableCausante());
        // Sin PM2.5 empatan PM10 y CO2
        assertEquals("PM10", motor.calcularIndice(null, 100.0, 1600.0).variableCausante());
    }

    @Test
    void extremadamenteMaloTambienEsAlertable() {
        ResultadoClasificacion r = motor.calcularIndice(200.0, null, null);   // PM2.5 por encima de 150

        assertEquals("EXTREMADAMENTE MALO", r.nivel());
        assertTrue(r.esAlertable());
    }

    @Test
    void soloMaloOPeorEsAlertable() {
        assertFalse(motor.calcularIndice(5.0, null, null).esAlertable());     // BUENO
        assertFalse(motor.calcularIndice(30.0, null, null).esAlertable());    // ACEPTABLE
        assertTrue(motor.calcularIndice(60.0, null, null).esAlertable());     // MALO
        assertTrue(motor.calcularIndice(120.0, null, null).esAlertable());    // MUY MALO
    }

    @Test
    void bordesDePm25() {
        assertNivel("BUENO", 12.0, null, null);       // el límite exacto pertenece al nivel de abajo
        assertNivel("ACEPTABLE", 12.1, null, null);
    }

        @Test
    void elTipoDeAlertaSeArmaConElNivel() {
        assertEquals("RIESGO_MALO", motor.calcularIndice(60.0, null, null).tipoAlerta());
        assertEquals("RIESGO_MUY_MALO", motor.calcularIndice(120.0, null, null).tipoAlerta());
        assertEquals("RIESGO_EXTREMADAMENTE_MALO", motor.calcularIndice(200.0, null, null).tipoAlerta());
        assertNull(ResultadoClasificacion.SIN_DATOS.tipoAlerta());
    }

    @Test
    void tiposIgualesOPeoresParaElEnfriamiento() {
        assertEquals(List.of("RIESGO_MALO", "RIESGO_MUY_MALO", "RIESGO_EXTREMADAMENTE_MALO"),
                motor.calcularIndice(60.0, null, null).tiposDeAlertaIgualesOPeores());
        assertEquals(List.of("RIESGO_EXTREMADAMENTE_MALO"),
                motor.calcularIndice(200.0, null, null).tiposDeAlertaIgualesOPeores());
        assertTrue(motor.calcularIndice(30.0, null, null).tiposDeAlertaIgualesOPeores().isEmpty());   // ACEPTABLE no alerta
        assertTrue(ResultadoClasificacion.SIN_DATOS.tiposDeAlertaIgualesOPeores().isEmpty());
    }
    // TODO (tú): completa los bordes. Cada rango cambia de nivel justo después de su límite superior:
    //   PM2.5: 12.0 | 45.0 | 97.0 | 150.0
    //   PM10:  50.0 | 75.0 | 155.0 | 235.0
    //   CO2:   1000 | 1500 | 2000 | 5000
    // Para cada límite escribe dos casos con assertNivel: el límite exacto (nivel de abajo)
    // y el límite + 0.1 (nivel de arriba). Puedes copiar el formato de bordesDePm25.
}