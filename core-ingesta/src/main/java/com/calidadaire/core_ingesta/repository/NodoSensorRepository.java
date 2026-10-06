package com.calidadaire.core_ingesta.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.calidadaire.core_ingesta.entity.NodoSensor;

@Repository
public interface NodoSensorRepository extends JpaRepository<NodoSensor, UUID> {
}