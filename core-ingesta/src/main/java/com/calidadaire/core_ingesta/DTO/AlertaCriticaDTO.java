package com.calidadaire.core_ingesta.DTO;

import java.time.Instant;

public class AlertaCriticaDTO {
    private String nodoId;
    private Instant timestampOrigen;
    private String tipoAlerta;
    private Double valorRegistrado;
    private String mensaje;

    public String getNodoId() {
        return nodoId;
    }

    public void setNodoId(String nodoId) {
        this.nodoId = nodoId;
    }

    public Instant getTimestampOrigen() {
        return timestampOrigen;
    }

    public void setTimestampOrigen(Instant timestampOrigen) {
        this.timestampOrigen = timestampOrigen;
    }

    public String getTipoAlerta() {
        return tipoAlerta;
    }

    public void setTipoAlerta(String tipoAlerta) {
        this.tipoAlerta = tipoAlerta;
    }

    public Double getValorRegistrado() {
        return valorRegistrado;
    }

    public void setValorRegistrado(Double valorRegistrado) {
        this.valorRegistrado = valorRegistrado;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }


}
