package com.calidadaire.core_ingesta.DTO;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public class NodoSensorDTO {

    private UUID id;

    @JsonProperty("mac_address")
    private String macAddress;

    private String ubicacion;

    private Boolean estatus;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getMacAddress() { return macAddress; }
    public void setMacAddress(String macAddress) { this.macAddress = macAddress; }
    public String getUbicacion() { return ubicacion; }
    public void setUbicacion(String ubicacion) { this.ubicacion = ubicacion; }
    public Boolean getEstatus() { return estatus; }
    public void setEstatus(Boolean estatus) { this.estatus = estatus; }
}