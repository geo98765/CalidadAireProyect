package com.calidadaire.core_notificaciones.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.calidadaire.core_notificaciones.entity.AlertaCritica;

@Repository
public interface AlertaCriticaRepository extends JpaRepository<AlertaCritica, Long> {
}