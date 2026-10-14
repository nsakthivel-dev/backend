package com.evplatform.repository;

import com.evplatform.domain.LoadForecast;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LoadForecastRepository extends JpaRepository<LoadForecast, Long> {

    @Query("SELECT f FROM LoadForecast f WHERE f.transformer.id = :transformerId " +
           "AND f.targetIntervalStart >= :fromTime " +
           "ORDER BY f.targetIntervalStart ASC")
    List<LoadForecast> findFutureForecasts(
            @Param("transformerId") Long transformerId,
            @Param("fromTime") LocalDateTime fromTime);

    @Query("SELECT f FROM LoadForecast f WHERE f.transformer.id = :transformerId " +
           "AND f.riskStatus IN ('AT_RISK', 'CRITICAL') " +
           "AND f.targetIntervalStart >= :fromTime " +
           "ORDER BY f.targetIntervalStart ASC")
    List<LoadForecast> findActiveRiskForecasts(
            @Param("transformerId") Long transformerId,
            @Param("fromTime") LocalDateTime fromTime);
}
