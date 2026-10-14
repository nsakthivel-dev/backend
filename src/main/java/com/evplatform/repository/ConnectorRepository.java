package com.evplatform.repository;

import com.evplatform.domain.Connector;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConnectorRepository extends JpaRepository<Connector, Long> {

    @Query("SELECT c FROM Connector c WHERE c.charger.station.id = :stationId")
    List<Connector> findByStationId(@Param("stationId") Long stationId);

    @Query("SELECT c FROM Connector c WHERE c.charger.station.shop.id = :shopId")
    List<Connector> findByShopId(@Param("shopId") Long shopId);

    @Query("SELECT c FROM Connector c WHERE c.charger.station.shop.transformer.id = :transformerId")
    List<Connector> findByTransformerId(@Param("transformerId") Long transformerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Connector c WHERE c.id = :id")
    Optional<Connector> findByIdForUpdate(@Param("id") Long id);

    List<Connector> findByChargerId(Long chargerId);
}
