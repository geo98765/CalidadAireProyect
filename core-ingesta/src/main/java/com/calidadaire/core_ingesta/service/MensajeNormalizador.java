package com.calidadaire.core_ingesta.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Traduce los distintos JSON que puede publicar el nodo fog a un objeto interno único.
 * No usa base de datos ni mensajería, para poder probarlo con pruebas unitarias simples.
 */
@Service
public class MensajeNormalizador {

    // Mismo namespace que usa formato.js del nodo para derivar un UUID desde el nombre de un sensor
    static final UUID NAMESPACE_NODOS = UUID.fromString("6f1c1b0e-5a52-4c43-9e0a-3d6b1b0f7a10");

    private static final Pattern PATRON_UUID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Instant FECHA_MINIMA = Instant.parse("2020-01-01T00:00:00Z");
    private static final Duration TOLERANCIA_FUTURO = Duration.ofDays(1);
    private static final int MAX_MID = 100;
    private static final int MAX_NOMBRE = 100;
    private static final int MAX_TIPO_ALERTA = 50;
    private static final double LIMITE_VALOR_ALERTA = 999_999;

    // Nombre largo (formato calidadaire) primero, alias corto (formato nativo) después
    private static final List<String> CO2 = List.of("co2_ppm", "co2");
    private static final List<String> PM25 = List.of("pm25_ugm3", "pm25");
    private static final List<String> PM10 = List.of("pm10_ugm3", "pm10");
    private static final List<String> TEMPERATURA = List.of("temperatura_c", "temp");
    private static final List<String> HUMEDAD = List.of("humedad_pct", "humedad");

    private record Identidad(UUID id, String nombre) {}

    private final Clock reloj;

    public MensajeNormalizador() {
        this(Clock.systemUTC());
    }

    MensajeNormalizador(Clock reloj) {
        this.reloj = reloj;
    }

    public LecturaNormalizada normalizarLectura(JsonNode mensaje) {
        exigirObjeto(mensaje);
        Identidad identidad = leerIdentidad(mensaje);
        Instant momento = leerMomento(mensaje);
        String mid = leerMid(mensaje);

        // Las variables pueden venir en la raíz (formato plano) o dentro de "lecturas" (anidado)
        JsonNode origen = mensaje.path("lecturas").isObject() ? mensaje.get("lecturas") : mensaje;
        Double co2 = leerNumero(origen, CO2, "CO2", 0, 100_000);
        Double pm25 = leerNumero(origen, PM25, "PM2.5", 0, 10_000);
        Double pm10 = leerNumero(origen, PM10, "PM10", 0, 10_000);
        Double temperatura = leerNumero(origen, TEMPERATURA, "Temperatura", -90, 100);
        Double humedad = leerNumero(origen, HUMEDAD, "Humedad", 0, 100);

        if (co2 == null && pm25 == null && pm10 == null && temperatura == null && humedad == null) {
            throw new MensajeInvalidoException(
                    "El mensaje no trae ninguna variable reconocida (co2, pm25, pm10, temp, humedad)");
        }
        return new LecturaNormalizada(identidad.id(), identidad.nombre(), momento, mid,
                co2, pm25, pm10, temperatura, humedad);
    }

    /** Devuelve vacío cuando la alerta se debe ignorar a propósito (alarma ya resuelta). */
    public Optional<AlertaNormalizada> normalizarAlerta(JsonNode mensaje) {
        exigirObjeto(mensaje);

        String estado = textoOpcional(mensaje, "estado");
        if (estado != null && estado.equalsIgnoreCase("resuelta")) {
            return Optional.empty();
        }

        Identidad identidad = leerIdentidad(mensaje);
        Instant momento = leerMomento(mensaje);
        String mid = leerMid(mensaje);

        String tipoAlerta;
        double valor;
        String texto;

        if (textoOpcional(mensaje, "tipo_alerta") != null) {
            // Formato calidadaire
            tipoAlerta = recortar(textoOpcional(mensaje, "tipo_alerta"), MAX_TIPO_ALERTA);
            valor = leerNumeroObligatorio(mensaje, "valor_registrado");
            String mensajeOriginal = textoOpcional(mensaje, "mensaje");
            texto = mensajeOriginal != null ? mensajeOriginal : "Alerta " + tipoAlerta;
        } else if (textoOpcional(mensaje, "variable") != null) {
            // Formato nativo de alarma
            String variable = textoOpcional(mensaje, "variable");
            String nivel = textoOpcional(mensaje, "nivel");
            String base = nivel != null ? variable + "_" + nivel : variable;
            tipoAlerta = recortar(base.toUpperCase(Locale.ROOT), MAX_TIPO_ALERTA);
            valor = leerNumeroObligatorio(mensaje, "valor");
            String unidad = textoOpcional(mensaje, "unidad");
            String detalle = textoOpcional(mensaje, "detalle");
            texto = variable + " " + formatear(valor)
                    + (unidad != null ? " " + unidad : "")
                    + (detalle != null ? " — " + detalle : "");
        } else {
            throw new MensajeInvalidoException(
                    "No se reconoce el formato de la alerta (falta tipo_alerta o variable)");
        }

        return Optional.of(new AlertaNormalizada(identidad.id(), identidad.nombre(), momento, mid,
                tipoAlerta, valor, texto));
    }

    // ---------- Identidad, tiempo y mid ----------

    private Identidad leerIdentidad(JsonNode mensaje) {
        String texto = null;
        for (String clave : List.of("nodo_id", "id")) {
            JsonNode valor = mensaje.get(clave);
            if (valor != null && (valor.isTextual() || valor.isNumber()) && !valor.asText().isBlank()) {
                texto = valor.asText().trim();
                break;
            }
        }
        if (texto == null) {
            throw new MensajeInvalidoException("Falta el identificador del nodo (nodo_id o id)");
        }
        if (PATRON_UUID.matcher(texto).matches()) {
            return new Identidad(UUID.fromString(texto), null);
        }
        // No es UUID: se deriva uno estable, igual que hace formato.js, y se conserva el nombre
        return new Identidad(uuidV5(texto, NAMESPACE_NODOS), recortar(texto, MAX_NOMBRE));
    }

    private Instant leerMomento(JsonNode mensaje) {
        JsonNode valor = mensaje.get("timestamp_origen");
        String campo = "timestamp_origen";
        if (valor == null || valor.isNull()) {
            valor = mensaje.get("ts");
            campo = "ts";
        }
        if (valor == null || valor.isNull()) {
            throw new MensajeInvalidoException("Falta el momento de la lectura (timestamp_origen o ts)");
        }

        Instant momento = interpretarInstante(valor, campo);
        Instant ahora = reloj.instant();
        if (momento.isBefore(FECHA_MINIMA) || momento.isAfter(ahora.plus(TOLERANCIA_FUTURO))) {
            throw new MensajeInvalidoException(
                    "El campo '" + campo + "' tiene una fecha fuera de rango: " + momento);
        }
        return momento;
    }

    private Instant interpretarInstante(JsonNode valor, String campo) {
        try {
            // Un número es milisegundos desde 1970; no se deja a Jackson, que lo leería como segundos
            if (valor.isNumber()) {
                return Instant.ofEpochMilli(valor.longValue());
            }
            if (valor.isTextual()) {
                String texto = valor.asText().trim();
                if (texto.matches("-?\\d+")) {
                    return Instant.ofEpochMilli(Long.parseLong(texto));
                }
                return Instant.parse(texto);
            }
        } catch (DateTimeException | NumberFormatException e) {
            // se reporta abajo
        }
        throw new MensajeInvalidoException("El campo '" + campo + "' no tiene un formato de fecha válido");
    }

    private String leerMid(JsonNode mensaje) {
        String mid = textoOpcional(mensaje, "mid");
        if (mid != null && mid.length() > MAX_MID) {
            throw new MensajeInvalidoException("El campo 'mid' supera los " + MAX_MID + " caracteres");
        }
        return mid;
    }

    // ---------- Utilidades ----------

    private static void exigirObjeto(JsonNode mensaje) {
        if (mensaje == null || !mensaje.isObject()) {
            throw new MensajeInvalidoException("El mensaje no es un objeto JSON");
        }
    }

    private static Double leerNumero(JsonNode nodo, List<String> alias, String etiqueta, double min, double max) {
        for (String clave : alias) {
            JsonNode valor = nodo.get(clave);
            if (valor == null || valor.isNull()) {
                continue;
            }
            double numero;
            if (valor.isNumber()) {
                numero = valor.doubleValue();
            } else if (valor.isTextual()) {
                try {
                    numero = Double.parseDouble(valor.asText().trim());
                } catch (NumberFormatException e) {
                    throw new MensajeInvalidoException("El campo '" + clave + "' no es numérico");
                }
            } else {
                throw new MensajeInvalidoException("El campo '" + clave + "' no es numérico");
            }
            if (Double.isNaN(numero) || Double.isInfinite(numero) || numero < min || numero > max) {
                throw new MensajeInvalidoException(etiqueta + " fuera de rango físico: " + numero);
            }
            return numero;
        }
        return null;
    }

    private static double leerNumeroObligatorio(JsonNode nodo, String clave) {
        Double valor = leerNumero(nodo, List.of(clave), clave, -LIMITE_VALOR_ALERTA, LIMITE_VALOR_ALERTA);
        if (valor == null) {
            throw new MensajeInvalidoException("Falta el campo '" + clave + "'");
        }
        return valor;
    }

    private static String textoOpcional(JsonNode nodo, String clave) {
        JsonNode valor = nodo.get(clave);
        if (valor == null || valor.isNull()) {
            return null;
        }
        String texto = valor.asText().trim();
        return texto.isEmpty() ? null : texto;
    }

    private static String recortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }

    private static String formatear(double valor) {
        return valor == Math.rint(valor) ? String.valueOf((long) valor) : String.valueOf(valor);
    }

    /** UUID v5 (SHA-1 de namespace + nombre), idéntico al uuidV5 de formato.js. */
    static UUID uuidV5(String nombre, UUID espacio) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            sha1.update(bytes(espacio));
            sha1.update(nombre.getBytes(StandardCharsets.UTF_8));
            byte[] h = sha1.digest();
            h[6] = (byte) ((h[6] & 0x0f) | 0x50);
            h[8] = (byte) ((h[8] & 0x3f) | 0x80);
            long alto = 0;
            long bajo = 0;
            for (int i = 0; i < 8; i++) {
                alto = (alto << 8) | (h[i] & 0xff);
            }
            for (int i = 8; i < 16; i++) {
                bajo = (bajo << 8) | (h[i] & 0xff);
            }
            return new UUID(alto, bajo);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 no disponible", e);
        }
    }

    private static byte[] bytes(UUID uuid) {
        byte[] salida = new byte[16];
        long alto = uuid.getMostSignificantBits();
        long bajo = uuid.getLeastSignificantBits();
        for (int i = 0; i < 8; i++) {
            salida[i] = (byte) (alto >>> (8 * (7 - i)));
            salida[8 + i] = (byte) (bajo >>> (8 * (7 - i)));
        }
        return salida;
    }
}