package com.calidadaire.core_ingesta.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.calidadaire.core_ingesta.entity.AlertaCritica;

@Repository
public interface AlertaCriticaRepository extends JpaRepository<AlertaCritica, Long> {
    List<AlertaCritica> findTop20ByOrderByTimestampOrigenDesc();
}