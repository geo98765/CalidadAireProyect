package com.calidadaire.core_ingesta.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.calidadaire.core_ingesta.entity.LecturaNormal;


@Repository
public interface LecturaNormalRepository extends JpaRepository<LecturaNormal, Long> {
    boolean existsByNodoIdAndTimestampOrigen(UUID nodoId, Instant timestampOrigen);
    // Busca la lectura más reciente de un nodo específico basándose en la fecha
    java.util.Optional<LecturaNormal> findTopByNodoIdOrderByTimestampOrigenDesc(java.util.UUID nodoId);
}

