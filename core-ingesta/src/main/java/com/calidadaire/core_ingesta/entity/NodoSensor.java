package com.calidadaire.core_ingesta.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "nodos_sensores")
@Data
public class NodoSensor {

    @Id
    private UUID id;

    @Column(name = "mac_address", unique = true)
    private String macAddress;

    @Column(name = "ubicacion")
    private String ubicacion;

    @Column(name = "estatus")
    private Boolean estatus;
}