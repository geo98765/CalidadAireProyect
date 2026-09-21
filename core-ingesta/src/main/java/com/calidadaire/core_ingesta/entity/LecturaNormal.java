package com.calidadaire.core_ingesta.entity;


import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "lecturas_normales")
@Data
public class LecturaNormal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nodo_id")
    private UUID nodoId;

    @Column(name = "co2_ppm")
    private Double co2Ppm;

    @Column(name = "pm25_ugm3")
    private Double pm25Ugm3;

    @Column(name = "pm10_ugm3")
    private Double pm10Ugm3;

    @Column(name = "timestamp_origen", nullable = false)
    private Instant timestampOrigen;

    @Column(name = "timestamp_recepcion", insertable = false, updatable = false)
    private Instant timestampRecepcion;

    @Column(name = "nivel_riesgo")
    private String nivelRiesgo;
}