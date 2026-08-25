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
@Table(name = "alertas_criticas")
@Data
public class AlertaCritica {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nodo_id")
    private UUID nodoId;

    @Column(name = "tipo_alerta")
    private String tipoAlerta;

    @Column(name = "valor_registrado")
    private Double valorRegistrado;

    @Column(name = "mensaje")
    private String mensaje;

    @Column(name = "timestamp_origen")
    private Instant timestampOrigen;

    @Column(name = "estado_notificacion")
    private Boolean estadoNotificacion;
}